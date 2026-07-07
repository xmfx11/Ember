package com.ember.utils

import com.ember.data.model.MediaSource
import com.ember.data.model.MediaStream

/**
 * 播放工具：从 MediaSource 提取最终播放 URL（处理 302 STRM 直链、转码回退）
 *
 * URL 优先级：
 * 1. DirectStreamUrl（相对路径，拼接 server + api_key + MediaSourceId）—— Emby 标准直链
 * 2. TranscodingUrl（转码直播流，拼接 server + api_key）—— 不支持直链时回退
 * 3. Path 完整 http（远程 STRM 等）—— 直接用
 * 4. /Videos/{itemId}/stream?Static=true&MediaSourceId=xxx&api_key=token —— 最终兜底
 */
object PlayerUtils {

    /**
     * 构建播放 URL
     * @param server 服务器地址
     * @param itemId 媒体 id
     * @param mediaSource 媒体源
     * @param token 访问令牌（用于 api_key 参数）
     */
    fun buildPlayUrl(server: String, itemId: String, mediaSource: MediaSource?, token: String?): String? {
        val base = NetworkModule.formatBaseUrl(server)
        val mediaSourceId = mediaSource?.Id

        // 1. DirectStreamUrl（Emby PlaybackInfo 通常返回这个）
        val direct = mediaSource?.DirectStreamUrl
        if (!direct.isNullOrEmpty()) {
            val url = if (direct.startsWith("http")) direct else "${base}${direct.trimStart('/')}"
            // DirectStreamUrl 可能已含 MediaSourceId，没含则补上
            val withMsid = if (mediaSourceId != null && !url.contains("MediaSourceId", ignoreCase = true)) {
                appendParam(url, "MediaSourceId", mediaSourceId)
            } else url
            return appendApiKey(withMsid, token)
        }

        // 2. TranscodingUrl（不支持直链时 Emby 返回转码直播流地址）
        val trans = mediaSource?.TranscodingUrl
        if (!trans.isNullOrEmpty()) {
            val url = if (trans.startsWith("http")) trans else "${base}${trans.trimStart('/')}"
            return appendApiKey(url, token)
        }

        // 3. Path 为完整 http URL（远程 STRM 等已包含直链）
        val path = mediaSource?.Path
        if (!path.isNullOrEmpty() && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path
        }

        // 4. 最终兜底：Emby 通用流接口
        // Static=true 让 Emby 直出原始流；带上 MediaSourceId 让服务器能定位多源媒体
        val sb = StringBuilder("${base}Videos/$itemId/stream?Static=true")
        if (!mediaSourceId.isNullOrEmpty()) sb.append("&MediaSourceId=").append(mediaSourceId)
        if (!token.isNullOrEmpty()) sb.append("&api_key=").append(token)
        return sb.toString()
    }

    // 给 URL 追加 query 参数（自动处理已有 ? 的情况）
    private fun appendParam(url: String, key: String, value: String): String {
        return if (url.contains("?")) "${url}&$key=$value" else "${url}?$key=$value"
    }

    // 给 URL 追加 api_key（自动处理已有 ? 的情况）
    private fun appendApiKey(url: String, token: String?): String {
        if (token.isNullOrEmpty()) return url
        return appendParam(url, "api_key", token)
    }

    // ticks 转 ms（1 tick = 100 ns = 0.0001 ms）
    fun ticksToMs(ticks: Long?): Long {
        return ticks?.div(10000) ?: 0L
    }

    // ticks 转"HH:MM:SS"或"MM:SS"
    fun ticksToTimeStr(ticks: Long?): String {
        val ms = ticksToMs(ticks)
        return msToTimeStr(ms)
    }

    // ms 转"HH:MM:SS"或"MM:SS"
    fun msToTimeStr(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    // 从 MediaStreams 提取字幕轨道（Type=Subtitle）
    fun getSubtitleStreams(mediaSource: MediaSource?): List<MediaStream> {
        return mediaSource?.MediaStreams?.filter { it.Type == "Subtitle" } ?: emptyList()
    }

    // 从 MediaStreams 提取音轨（Type=Audio）
    fun getAudioStreams(mediaSource: MediaSource?): List<MediaStream> {
        return mediaSource?.MediaStreams?.filter { it.Type == "Audio" } ?: emptyList()
    }

    // 提取分辨率描述（如 1080p）
    fun getResolutionLabel(mediaSource: MediaSource?): String? {
        val h = mediaSource?.Height ?: return null
        return when {
            h >= 2160 -> "4K"
            h >= 1080 -> "1080p"
            h >= 720 -> "720p"
            h >= 480 -> "480p"
            else -> "${h}p"
        }
    }

    // 根据 Container 推断 MIME type（让 ExoPlayer 正确识别格式）
    fun containerToMime(container: String?): String? {
        if (container.isNullOrEmpty()) return null
        val c = container.lowercase()
        return when {
            c.startsWith("mp4") -> "video/mp4"
            c == "mkv" || c == "matroska" || c == "webm" -> "video/x-matroska"
            c == "ts" || c == "mpegts" -> "video/mp2t"
            c == "flv" -> "video/x-flv"
            c == "avi" -> "video/x-msvideo"
            c == "mov" -> "video/quicktime"
            c == "wmv" || c == "asf" -> "video/x-ms-wmv"
            c == "m3u8" || c == "hls" -> "application/x-mpegURL"
            c == "mpd" || c == "dash" -> "application/dash+xml"
            else -> null
        }
    }

    // 生成诊断信息（错误时显示，帮助排查）
    fun buildDiagnosticInfo(itemId: String, mediaSource: MediaSource?, url: String?): String {
        val sb = StringBuilder()
        sb.append("itemId: ").append(itemId).append('\n')
        sb.append("url: ").append(url?.take(150) ?: "null").append('\n')
        if (mediaSource != null) {
            sb.append("sourceId: ").append(mediaSource.Id ?: "null").append('\n')
            sb.append("name: ").append(mediaSource.Name ?: "null").append('\n')
            sb.append("container: ").append(mediaSource.Container ?: "null").append('\n')
            sb.append("protocol: ").append(mediaSource.Protocol ?: "null").append('\n')
            sb.append("directStream: ").append(mediaSource.SupportsDirectStream).append('\n')
            sb.append("directPlay: ").append(mediaSource.SupportsDirectPlay).append('\n')
            sb.append("transcoding: ").append(mediaSource.SupportsTranscoding).append('\n')
            sb.append("hasDirectUrl: ").append(!mediaSource.DirectStreamUrl.isNullOrEmpty()).append('\n')
            sb.append("hasTransUrl: ").append(!mediaSource.TranscodingUrl.isNullOrEmpty()).append('\n')
            sb.append("size: ").append(mediaSource.Width).append("x").append(mediaSource.Height).append('\n')
            sb.append("streams: ").append(mediaSource.MediaStreams?.size ?: 0).append('\n')
        } else {
            sb.append("mediaSource: null（兜底 stream URL）\n")
        }
        return sb.toString()
    }
}
