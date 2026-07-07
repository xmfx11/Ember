package com.ember.player

import android.content.Context
import android.view.Surface

/**
 * 统一播放器内核接口
 *
 * 三种实现：ExoPlayerCore / MpvCore / VlcCore
 * 通过 PlayerFactory.create(type) 创建实例
 *
 * 所有内核默认跟随 HTTP 302 重定向（用于 STRM 直链）
 */
interface IPlayerCore {

    val type: PlayerType

    fun init(context: Context, surface: Surface? = null)

    /**
     * 开始播放
     * @param url 媒体 URL（直接传给内核，不预处理）
     * @param headers 额外请求头（用于 Emby 鉴权 token）
     * @throws UnsupportedFormatException 当内核不支持该格式时（如 ExoPlayer 遇到 .iso）
     */
    @Throws(UnsupportedFormatException::class)
    fun play(url: String, headers: Map<String, String> = emptyMap())

    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    fun release()

    /** 设置额外请求头（携带 Emby 鉴权 token） */
    fun setExtraHeaders(headers: Map<String, String>)

    fun setSurface(surface: Surface?)
    fun setListener(listener: PlayerListener)

    fun getState(): PlayerState
    fun isPlaying(): Boolean
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun setPlaySpeed(speed: Float)
    fun setVolume(volume: Float)
}
