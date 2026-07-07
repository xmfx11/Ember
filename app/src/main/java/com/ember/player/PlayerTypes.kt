package com.ember.player

/**
 * 播放器内核类型
 * - EXO: ExoPlayer，省电、支持 HLS/DASH 自适应码率（默认）
 * - MPV: mpv-android，支持 ISO/BDMV 蓝光原盘（需引入 mpv 依赖）
 * - VLC: libVLC，兼容性好（需引入 vlc 依赖）
 *
 * 注：v0.01 默认仅编译 EXO 内核；MPV/VLC 为预留扩展点。
 */
enum class PlayerType { EXO, MPV, VLC }

/**
 * 播放器状态
 */
sealed class PlayerState {
    object Idle : PlayerState()
    object Buffering : PlayerState()
    object Ready : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    object Ended : PlayerState()
    data class Error(val message: String, val fallbackType: FallbackType = FallbackType.NONE) : PlayerState()
}

/**
 * 降级策略：上层据此自动尝试下一个内核
 */
enum class FallbackType { NONE, TO_MPV, TO_VLC, ALL_FAILED }

/**
 * 轨道信息（音轨/字幕轨）
 */
data class TrackInfo(
    val index: Int,
    val type: TrackType,
    val language: String? = null,
    val title: String? = null,
    val isSelected: Boolean = false
)

enum class TrackType { AUDIO, SUBTITLE, VIDEO }

/**
 * 播放器回调
 */
interface PlayerListener {
    fun onStateChanged(state: PlayerState) {}
    fun onProgress(positionMs: Long, durationMs: Long) {}
    fun onBufferingUpdate(percent: Int) {}
    fun onError(message: String, fallbackType: FallbackType) {}
    fun onReady() {}
}

/**
 * 不支持的格式异常（如 ExoPlayer 遇到 ISO/BDMV 原盘）
 */
class UnsupportedFormatException(val format: String) : Exception("不支持的格式：$format（请切换 MPV/VLC 内核）")
