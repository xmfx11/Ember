package com.ember.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ember.BuildConfig
import com.ember.data.api.GithubService
import com.ember.data.model.GithubRelease
import com.ember.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * OTA 更新管理器：
 * - 检查 GitHub Releases 最新版本
 * - 下载 APK 到外部持久目录（避免被系统清理）
 * - 支持断点续传（Range header）
 * - 失败自动重试（3 次，指数退避）
 * - 调用系统安装器安装
 *
 * 配置：仓库 owner=xmfx11 repo=Ember
 */
object UpdateManager {

    private const val OWNER = "xmfx11"
    private const val REPO = "Ember"
    private const val BASE_URL = "https://api.github.com/"
    private const val MAX_RETRY = 3                  // 下载最大重试次数
    private const val APK_FILENAME = "ember_update.apk"

    // 检查更新：返回 (有更新?, 最新版本号, 下载链接, 更新说明)
    // 带重试，网络不稳定时尝试 3 次
    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val service = retrofit.create(GithubService::class.java)

                AppLogger.i("OTA: checking $OWNER/$REPO (attempt ${attempt + 1}/$MAX_RETRY)")
                val response = service.getLatestRelease(OWNER, REPO)
                if (!response.isSuccessful || response.body() == null) {
                    AppLogger.w("OTA: check failed ${response.code()}")
                    lastError = Exception("GitHub API 返回 ${response.code()}")
                    if (attempt < MAX_RETRY - 1) {
                        delay(2000L * (attempt + 1))  // 指数退避 2s, 4s
                        return@repeat
                    }
                    return@withContext null
                }
                val release = response.body()!!
                val tagName = release.tag_name ?: return@withContext null
                val remoteVersion = tagName.removePrefix("v").trim()
                val currentVersion = BuildConfig.VERSION_NAME.trim()

                AppLogger.i("OTA: remote=$remoteVersion current=$currentVersion")
                val hasUpdate = isNewer(remoteVersion, currentVersion)

                // 找 apk 资源
                val apkAsset = release.assets?.firstOrNull {
                    it.name?.endsWith(".apk") == true
                }
                val url = apkAsset?.browser_download_url
                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = remoteVersion,
                    currentVersion = currentVersion,
                    downloadUrl = url ?: "",
                    releaseNotes = release.body ?: "",
                    releaseName = release.name ?: tagName,
                    publishedAt = release.published_at ?: ""
                )
            } catch (e: Exception) {
                AppLogger.w("OTA checkUpdate attempt ${attempt + 1} failed: ${e.message}")
                lastError = e
                if (attempt < MAX_RETRY - 1) {
                    delay(2000L * (attempt + 1))
                }
            }
        }
        AppLogger.e("OTA checkUpdate 全部重试失败", lastError)
        null
    }

    // 下载 APK，progress 回调 0-100
    // 支持断点续传 + 失败重试
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        // 下载到外部持久目录（不被系统清理）
        val updateDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val target = File(updateDir, APK_FILENAME)
        val tmpFile = File(updateDir, "$APK_FILENAME.tmp")

        var lastError: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                AppLogger.i("OTA download attempt ${attempt + 1}/$MAX_RETRY, url=${url.take(80)}")
                val downloaded = if (tmpFile.exists()) tmpFile.length() else 0L
                AppLogger.i("OTA resume from byte=$downloaded")

                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()

                val requestBuilder = Request.Builder().url(url)
                // 断点续传：如果 tmp 文件已存在部分数据，用 Range 请求
                if (downloaded > 0) {
                    requestBuilder.header("Range", "bytes=$downloaded-")
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                // 416 Range Not Satisfiable：tmp 文件已完整，直接重命名
                val respCode = response.code
                if (respCode == 416) {
                    AppLogger.i("OTA 416: tmp 文件已完整，直接重命名")
                    if (tmpFile.renameTo(target)) {
                        onProgress(100)
                        return@withContext target
                    }
                }

                if (!response.isSuccessful) {
                    AppLogger.w("OTA download failed $respCode")
                    lastError = Exception("下载失败，HTTP $respCode")
                    // 5xx 才重试，4xx 直接放弃（链接错误等）
                    if (respCode in 500..599 && attempt < MAX_RETRY - 1) {
                        delay(2000L * (attempt + 1))
                        return@repeat
                    }
                    return@withContext null
                }

                val body = response.body ?: run {
                    lastError = Exception("响应体为空")
                    return@repeat
                }

                // 判断是否为续传响应（206）还是完整响应（200）
                val isResume = respCode == 206
                val totalHeader = body.contentLength()
                val total = if (isResume) downloaded + totalHeader else totalHeader

                // 用 RandomAccessFile 支持从指定位置写入（续传）
                val raf = RandomAccessFile(tmpFile, "rw")
                try {
                    if (isResume) {
                        raf.seek(downloaded)
                    } else {
                        raf.setLength(0)
                    }

                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)  // 16KB 缓冲区
                        var read: Int
                        var written = if (isResume) downloaded else 0L
                        var lastReport = if (total > 0) ((written * 100 / total).toInt()) else -1

                        while (input.read(buffer).also { read = it } != -1) {
                            raf.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val pct = (written * 100 / total).toInt().coerceIn(0, 100)
                                if (pct != lastReport) {
                                    lastReport = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                } finally {
                    raf.close()
                }

                // 下载完成，重命名 tmp 为目标文件
                if (target.exists()) target.delete()
                if (tmpFile.renameTo(target)) {
                    AppLogger.i("OTA download ok, size=${target.length()}")
                    onProgress(100)
                    return@withContext target
                } else {
                    // 重命名失败，直接用 tmp
                    AppLogger.w("OTA rename failed, 用 tmp 作为目标")
                    onProgress(100)
                    return@withContext tmpFile
                }
            } catch (e: Exception) {
                AppLogger.w("OTA download attempt ${attempt + 1} failed: ${e.message}")
                lastError = e
                // 网络异常重试，保留 tmp 文件以便续传
                if (attempt < MAX_RETRY - 1) {
                    delay(2000L * (attempt + 1))
                }
            }
        }
        AppLogger.e("OTA download 全部重试失败", lastError)
        null
    }

    // 触发系统安装器
    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLogger.i("OTA install triggered, file=${file.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e("OTA install exception", e)
        }
    }

    // 简单版本比较：支持 0.1 / 0.01 / 1.2.3 等
    // 用"逐段数字比较 + 段数补零"逻辑
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(r.size, c.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val releaseName: String,
    val publishedAt: String
)
