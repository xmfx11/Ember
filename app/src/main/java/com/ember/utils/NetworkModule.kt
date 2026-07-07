package com.ember.utils

import com.ember.data.api.AuthInterceptor
import com.ember.data.api.EmbyService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块：
 * - API 客户端：followRedirects=false（避免 API 请求被 302 带走）
 * - 播放客户端：followRedirects=true（用于 STRM 302 直链）
 * - 自动格式化服务器地址（补 http:// 和结尾 /）
 */
object NetworkModule {

    private const val EMBY_AUTH =
        "MediaBrowser Client=\"Ember\", Device=\"Android\", DeviceId=\"Ember-Android-001\", Version=\"0.04\""

    // API 客户端（不跟随 302，保持请求纯净）
    fun createApiService(server: String): EmbyService {
        val baseUrl = formatBaseUrl(server)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)          // API 请求不跟随重定向
            .followSslRedirects(false)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EmbyService::class.java)
    }

    // 播放专用 OkHttpClient（跟随 302 跨协议重定向，用于 STRM 直链）
    fun createPlayerClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // 格式化服务器地址
    fun formatBaseUrl(server: String): String {
        var url = server.trim()
        if (url.isEmpty()) return "http://localhost:8096/"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url = "$url/"
        return url
    }

    // 去掉结尾 / 的地址（用于拼接图片 URL 等场景）
    fun trimTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url.dropLast(1) else url
    }
}

/**
 * 图片 URL 构建器（Emby 图片走 /Items/{id}/Images/Primary?tag=xxx）
 * 注意：图片请求需带上 X-Emby-Token 才能访问私有库，Coil 不便注入 header，
 * 这里用 api_key 查询参数替代（Emby 支持用 api_key 代替 X-Emby-Token）
 */
object ImageUrlBuilder {

    fun buildImageUrl(server: String, itemId: String?, imageTag: String?, token: String?): String? {
        if (itemId.isNullOrEmpty() || imageTag.isNullOrEmpty()) return null
        val base = NetworkModule.formatBaseUrl(server)
        val tokenPart = if (!token.isNullOrEmpty()) "&api_key=$token" else ""
        return "${base}Items/$itemId/Images/Primary?tag=$imageTag$tokenPart"
    }

    fun buildBackdropImageUrl(server: String, itemId: String?, imageTag: String?, token: String?): String? {
        if (itemId.isNullOrEmpty() || imageTag.isNullOrEmpty()) return null
        val base = NetworkModule.formatBaseUrl(server)
        val tokenPart = if (!token.isNullOrEmpty()) "&api_key=$token" else ""
        return "${base}Items/$itemId/Images/Backdrop?tag=$imageTag$tokenPart"
    }

    fun buildPersonImageUrl(server: String, personId: String?, imageTag: String?, token: String?): String? {
        if (personId.isNullOrEmpty() || imageTag.isNullOrEmpty()) return null
        val base = NetworkModule.formatBaseUrl(server)
        val tokenPart = if (!token.isNullOrEmpty()) "&api_key=$token" else ""
        return "${base}Items/$personId/Images/Primary?tag=$imageTag$tokenPart"
    }
}
