package com.ember.data.source

import com.ember.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * WebDAV 源：使用 OkHttp 直接调用 WebDAV PROPFIND / GET 接口
 *
 * 实现要点：
 * - PROPFIND Depth: 1 列出当前目录下的内容
 * - 解析 multistatus XML 提取 href / prop
 * - 播放 URL 直接用 GET URL（ExoPlayer 原生支持 HTTP）
 * - 鉴权用 Basic Auth
 *
 * 兼容：NextCloud / ownCloud / 坚果云 / 群晖 WebDAV 等
 */
class WebDavProvider(
    private val config: ServerConfig
) : MediaSource {

    override val serverId: String = config.id
    override val type: SourceType = SourceType.WEBDAV

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val authHeader: String? by lazy {
        if (config.username.isNotEmpty()) {
            Credentials.basic(config.username, config.password)
        } else null
    }

    private fun buildUrl(path: String): String {
        val base = config.baseUrl().trimEnd('/')
        val cleanPath = path.trim('/')
        // URL 编码路径中的中文/空格（保留 / 分隔符）
        val encodedPath = cleanPath.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return if (encodedPath.isEmpty()) "$base/" else "$base/$encodedPath/"
    }

    override suspend fun list(path: String): Result<List<UnifiedItem>> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(path)
            AppLogger.i("WebDAV PROPFIND url=$url")

            val body = """<?xml version="1.0" encoding="utf-8"?>
                <propfind xmlns="DAV:">
                    <prop>
                        <displayname/>
                        <getcontentlength/>
                        <getcontenttype/>
                        <getlastmodified/>
                        <resourcetype/>
                    </prop>
                </propfind>""".trimIndent()

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "1")
                .apply { authHeader?.let { header("Authorization", it) } }
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 207) {
                return@withContext Result.failure(Exception("WebDAV 列目录失败：HTTP ${response.code}"))
            }

            val respBody = response.body?.string() ?: ""
            val items = parseMultistatus(respBody, path, config.baseUrl())
            // 过滤掉当前目录自身（href 与请求路径相同）
            val filtered = items.filter { it.name.isNotEmpty() && it.id != path.trimEnd('/') }
            AppLogger.i("WebDAV list ok, count=${filtered.size}")
            Result.success(filtered)
        } catch (e: Exception) {
            AppLogger.e("WebDAV list failed", e)
            Result.failure(Exception("WebDAV 列目录失败：${com.ember.utils.ErrorTranslator.translate(e)}"))
        }
    }

    override suspend fun resolvePlayUrl(item: UnifiedItem): Result<String> {
        // 直接返回 HTTP URL，ExoPlayer 支持
        return Result.success(item.path)
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = config.baseUrl().trimEnd('/') + "/"
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", """<?xml version="1.0" encoding="utf-8"?><propfind xmlns="DAV:"><prop><displayname/></prop></propfind>""".trimIndent()
                    .toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "0")
                .apply { authHeader?.let { header("Authorization", it) } }
                .build()
            val response: Response = client.newCall(request).execute()
            val ok = response.isSuccessful || response.code == 207
            val respCode = response.code
            response.close()
            if (ok) Result.success(true)
            else Result.failure(Exception("WebDAV 连接失败：HTTP $respCode"))
        } catch (e: Exception) {
            Result.failure(Exception("WebDAV 连接失败：${com.ember.utils.ErrorTranslator.translate(e)}"))
        }
    }

    // 解析 WebDAV multistatus XML 响应
    private fun parseMultistatus(xml: String, currentPath: String, baseUrl: String): List<UnifiedItem> {
        val result = mutableListOf<UnifiedItem>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var currentHref: String? = null
            var currentName: String? = null
            var currentSize: Long = 0
            var currentType: String? = null
            var currentModified: String? = null
            var isCollection = false
            var inProp = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name ?: ""
                        when (name) {
                            "href" -> currentHref = parser.nextText()
                            "displayname" -> currentName = parser.nextText()
                            "getcontentlength" -> {
                                val text = parser.nextText()
                                currentSize = text.toLongOrNull() ?: 0
                            }
                            "getcontenttype" -> currentType = parser.nextText()
                            "getlastmodified" -> currentModified = parser.nextText()
                            "collection" -> isCollection = true
                            "prop" -> inProp = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name ?: ""
                        if (name == "response" && currentHref != null) {
                            // 解析完成一个 response，构建 UnifiedItem
                            val decodedHref = java.net.URLDecoder.decode(currentHref, "UTF-8")
                            // 提取相对于 baseUrl 的路径
                            val baseUrlPath = try {
                                java.net.URI(baseUrl).path
                            } catch (e: Exception) { "/" }

                            var relPath = decodedHref
                            // 去掉 host 部分，只保留路径
                            if (decodedHref.startsWith("http://") || decodedHref.startsWith("https://")) {
                                try {
                                    relPath = java.net.URI(decodedHref).path
                                } catch (e: Exception) { relPath = decodedHref }
                            }

                            // 拼接完整 URL
                            val fullUrl = config.baseUrl().trimEnd('/') + relPath

                            // 显示名：优先 displayname，否则用 href 末段
                            val displayName = if (!currentName.isNullOrEmpty()) {
                                currentName
                            } else {
                                relPath.trimEnd('/').substringAfterLast('/').ifEmpty { relPath }
                            }

                            val itemId = relPath.trimEnd('/')

                            // 跳过与当前目录相同的项（自身）
                            if (itemId != currentPath.trimEnd('/')) {
                                result.add(UnifiedItem(
                                    id = itemId,
                                    name = displayName,
                                    path = fullUrl,
                                    isDirectory = isCollection,
                                    size = currentSize,
                                    lastModified = parseModifiedDate(currentModified),
                                    mimeType = currentType,
                                    serverId = config.id,
                                    sourceType = SourceType.WEBDAV
                                ))
                            }

                            // 重置
                            currentHref = null
                            currentName = null
                            currentSize = 0
                            currentType = null
                            currentModified = null
                            isCollection = false
                        }
                        if (name == "prop") inProp = false
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            AppLogger.e("WebDAV XML 解析失败", e)
        }
        return result
    }

    // RFC 1123 日期转时间戳（粗略）
    private fun parseModifiedDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            sdf.parse(date)?.time ?: 0
        } catch (e: Exception) { 0 }
    }
}
