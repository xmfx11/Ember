package com.ember.utils

import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import javax.net.ssl.SSLException

/**
 * 错误信息翻译工具
 *
 * 把底层英文异常/错误码统一翻译成中文友好提示，
 * 同时保留原始技术细节便于排查。
 */
object ErrorTranslator {

    /** 翻译通用 Throwable */
    fun translate(e: Throwable): String {
        val msg = e.message ?: ""
        return when (e) {
            is UnknownHostException -> "无法解析服务器地址，请检查地址是否正确"
            is SocketTimeoutException -> "连接超时，请检查服务器是否运行或网络是否通畅"
            is ConnectException -> "拒绝连接，请确认服务器地址和端口正确且服务正在运行"
            is SSLException -> "SSL/HTTPS 证书错误：${msg}"
            else -> translateMessage(msg).ifEmpty { "未知错误：${e.javaClass.simpleName}" }
        }
    }

    /** 翻译字符串消息（用于 Result.failure(Exception("xxx")) 链路） */
    fun translateMessage(msg: String): String {
        if (msg.isEmpty()) return "未知错误"
        val lower = msg.lowercase()
        return when {
            // 网络类
            lower.contains("unable to resolve host") -> "无法解析服务器地址，请检查地址是否正确"
            lower.contains("connect timed out") || lower.contains("timeout") || lower.contains("timed out") -> "连接超时，请检查服务器是否运行或网络是否通畅"
            lower.contains("connection refused") || lower.contains("failed to connect") -> "无法连接到服务器，请检查地址、端口和网络"
            lower.contains("sslhandshakeexception") || lower.contains("ssl") -> "SSL 证书错误：$msg"
            lower.contains("networkonmainthreadexception") -> "网络请求不能在主线程执行"
            lower.contains("unknownhostexception") -> "未知主机，请检查服务器地址"
            lower.contains("socketexception") -> "网络套接字异常：$msg"
            lower.contains("eofexception") -> "数据读取异常（连接被关闭）"
            // HTTP 状态
            lower.contains("401") -> "认证失败（401）：用户名或密码错误，或登录凭据已失效"
            lower.contains("403") -> "拒绝访问（403）：当前用户无权限"
            lower.contains("404") -> "资源不存在（404）：地址错误或服务不支持"
            lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504") -> "服务器内部错误（${msg.substringAfter("(").substringBefore(")").ifEmpty { "5xx" } }，请稍后重试"
            // 已经是中文的直接返回
            containsChinese(msg) -> msg
            else -> msg
        }
    }

    /**
     * 翻译 ExoPlayer PlaybackException 错误码名
     * 把英文常量名映射为中文说明
     */
    fun translateExoError(errorCodeName: String, message: String?): String {
        val detail = message?.let { translateMessage(it) } ?: ""
        val zh = when (errorCodeName) {
            "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE" -> "HTTP 内容类型错误"
            "ERROR_CODE_IO_BAD_HTTP_STATUS" -> "HTTP 状态码错误"
            "ERROR_CODE_IO_FILE_NOT_FOUND" -> "文件未找到"
            "ERROR_CODE_IO_NO_PERMISSION" -> "无访问权限"
            "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED" -> "网络连接失败"
            "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" -> "网络连接超时"
            "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE" -> "读取位置超出范围"
            "ERROR_CODE_DECODER_INIT_FAILED" -> "解码器初始化失败"
            "ERROR_CODE_DECODER_QUERY_FAILED" -> "解码器查询失败"
            "ERROR_CODE_DECODING_FAILED" -> "解码失败（可能是不支持的编码格式）"
            "ERROR_CODE_AUDIO_TRACK_INIT_FAILED" -> "音频轨道初始化失败"
            "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED" -> "音频写入失败"
            "ERROR_CODE_AUDIO_TRACK_OFFLINE_INIT_FAILED" -> "离线音频轨道初始化失败"
            "ERROR_CODE_DRM_DECRYPT_FAILED" -> "DRM 解密失败"
            "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED" -> "DRM 许可证获取失败"
            "ERROR_CODE_DRM_PROVISIONING_FAILED" -> "DRM 配置失败"
            "ERROR_CODE_DRM_UNSPECIFIED" -> "DRM 未知错误"
            "ERROR_CODE_PARSING_CONTAINER_MALFORMED" -> "容器格式损坏"
            "ERROR_CODE_PARSING_MANIFEST_MALFORMED" -> "清单文件损坏"
            "ERROR_CODE_PARSING_BUFFER_READ_FAILED" -> "解析数据读取失败"
            "ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED" -> "视频帧处理器初始化失败"
            "ERROR_CODE_BEHIND_LIVE_WINDOW" -> "已超出直播窗口"
            "ERROR_CODE_RUNTIME_ERROR" -> "运行时错误"
            "ERROR_CODE_UNSPECIFIED" -> "未指定的播放错误"
            else -> "播放错误（$errorCodeName）"
        }
        return if (detail.isNotEmpty()) "$zh：$detail" else zh
    }

    private fun containsChinese(s: String): Boolean {
        return s.any { it.code in 0x4E00..0x9FFF }
    }
}
