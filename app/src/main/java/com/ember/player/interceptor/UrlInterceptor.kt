package com.ember.player.interceptor

/**
 * URL 拦截器接口
 *
 * 允许在播放前对 URL 进行预处理：
 * - 识别内网 IP 并动态决定是否替换为公网域名
 * - 修改协议、追加查询参数等
 */
interface UrlInterceptor {
    fun intercept(originalUrl: String): String
}
