package com.ember.data.api

import com.ember.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证拦截器：
 * - 统一注入 X-Emby-Authorization（Emby 服务器对所有请求都要求此 header）
 * - 若已登录则注入 X-Emby-Token
 * - 只用同步缓存，禁止 runBlocking（避免 OkHttp 拦截器死锁）
 */
class AuthInterceptor : Interceptor {

    companion object {
        // Emby 要求的客户端标识
        const val EMBY_AUTH =
            "MediaBrowser Client=\"Ember\", Device=\"Android\", DeviceId=\"Ember-Android-001\", Version=\"0.03\""
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.toString()

        val builder = original.newBuilder()
            .header("X-Emby-Authorization", EMBY_AUTH)
            .header("Accept", "application/json")

        // 关键：登录请求（AuthenticateByName）不注入旧 token
        // 否则 Emby 服务器会用旧 token 验证而忽略 body 里的用户名密码，
        // 旧 token 无效时返回 401，被误判为"密码错误"
        if (!url.contains("AuthenticateByName")) {
            val token = TokenManager.getCachedToken()
            if (!token.isNullOrEmpty()) {
                builder.header("X-Emby-Token", token)
            }
        }

        if (original.method == "POST" || original.method == "PUT") {
            builder.header("Content-Type", "application/json")
        }

        return chain.proceed(builder.build())
    }
}
