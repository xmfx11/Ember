package com.ember.data.model

// 媒体源信息，用于播放（提取 DirectStreamUrl / Container / Path 等）
// 所有字段可空，Emby 不同配置返回字段差异大
data class MediaSource(
    val Id: String? = null,
    val Name: String? = null,
    val Container: String? = null,
    val Path: String? = null,
    val Protocol: String? = null,        // File / Http / Rtsp 等
    val IsRemote: Boolean? = null,
    val DirectStreamUrl: String? = null,  // 直接流地址（可能为相对路径，需拼接 server）
    val SupportsDirectStream: Boolean? = null,
    val SupportsDirectPlay: Boolean? = null,
    val SupportsTranscoding: Boolean? = null,
    val RunTimeTicks: Long? = null,
    val Bitrate: Long? = null,
    val Width: Int? = null,
    val Height: Int? = null,
    val Size: Long? = null,
    val TranscodingUrl: String? = null,
    val TranscodingSubProtocol: String? = null,
    val TranscodingContainer: String? = null,
    val MediaStreams: List<MediaStream>? = null
)

// 媒体流（视频 / 音频 / 字幕轨道）
data class MediaStream(
    val Index: Int? = null,
    val Type: String? = null,        // Video / Audio / Subtitle / Data
    val Codec: String? = null,
    val Language: String? = null,
    val DisplayTitle: String? = null,
    val IsDefault: Boolean? = null,
    val IsForced: Boolean? = null,
    val Width: Int? = null,
    val Height: Int? = null,
    val BitRate: Long? = null,
    val ChannelLayout: String? = null
)
