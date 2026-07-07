package com.ember.player.interceptor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Emby URL 拦截器
 *
 * 功能：
 * 1. 从 Emby 服务器返回的原始 302 地址里识别内网 IP（如 192.168.x.x）
 * 2. 根据当前网络状态动态决定：
 *    - WiFi 且同网段 → 保持内网直连（速度最快）
 *    - 移动网络或不同网段 → 替换为预设的公网域名
 */
class EmbyUrlInterceptor(private val context: Context) : UrlInterceptor {

    /** 预设的公网域名（移动网络或异网段时使用） */
    var publicDomain: String? = null
    /** Emby 服务器内网 IP（可选，若不设则从 URL 自动提取） */
    var serverInternalIp: String? = null
    /** 是否强制使用公网域名 */
    var forcePublic: Boolean = false

    override fun intercept(originalUrl: String): String {
        if (originalUrl.isEmpty()) return originalUrl
        val host = extractHost(originalUrl) ?: return originalUrl
        if (!isPrivateIp(host)) return originalUrl
        if (serverInternalIp == null) serverInternalIp = host
        val pub = publicDomain ?: return originalUrl
        if (forcePublic) return replaceHost(originalUrl, pub)

        val isWifi = isWifi(context)
        val localIp = getLocalIpAddress()
        val sameSubnet = isSameSubnet(host, localIp)
        return if (isWifi && sameSubnet) originalUrl else replaceHost(originalUrl, pub)
    }

    private fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun isSameSubnet(ip1: String?, ip2: String?): Boolean {
        if (ip1.isNullOrEmpty() || ip2.isNullOrEmpty()) return false
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        if (p1.size < 3 || p2.size < 3) return false
        return p1[0] == p2[0] && p1[1] == p2[1] && p1[2] == p2[2]
    }

    private fun isPrivateIp(ip: String): Boolean = when {
        ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("127.") -> true
        ip.startsWith("172.") -> ip.split(".").getOrNull(1)?.toIntOrNull() in 16..31
        else -> false
    }

    private fun extractHost(url: String): String? {
        val noProto = url.substringAfter("://", url)
        val hostPort = noProto.substringBefore('/')
        return hostPort.substringBefore(':').takeIf { it.isNotEmpty() }
    }

    private fun replaceHost(url: String, newHost: String): String {
        val proto = url.substringBefore("://", "https")
        val rest = url.substringAfter("://", "")
        val port = rest.substringBefore('/').substringAfter(':', "")
        val path = rest.substringAfter('/', "")
        val portPart = if (port.isNotEmpty()) ":$port" else ""
        val pathPart = if (path.isNotEmpty()) "/$path" else ""
        return "$proto://$newHost$portPart$pathPart"
    }
}
