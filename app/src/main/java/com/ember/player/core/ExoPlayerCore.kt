package com.ember.player.core

import android.content.Context
import android.view.Surface
import com.ember.player.FallbackType
import com.ember.player.IPlayerCore
import com.ember.player.PlayerListener
import com.ember.player.PlayerState
import com.ember.player.PlayerType
import com.ember.player.UnsupportedFormatException
import com.ember.utils.ErrorTranslator
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * 基于 ExoPlayer 2.19.1 的内核实现
 *
 * 特性：
 * - LoadControl 预缓冲 30 秒（minBuffer=15s, maxBuffer=50s, backBuffer=30s）
 * - 默认跟随 HTTP 302 跨协议重定向（STRM 直链）
 * - 注入 headers 用于 Emby 鉴权（X-Emby-Token）
 * - 优先硬解码（ExoPlayer 默认 MediaCodec 硬解）
 * - 不支持 ISO/BDMV，检测扩展名后抛 UnsupportedFormatException 触发降级
 */
class ExoPlayerCore : IPlayerCore {

    override val type: PlayerType = PlayerType.EXO

    private var context: Context? = null
    private var exoPlayer: ExoPlayer? = null
    private var listener: PlayerListener? = null
    private var currentHeaders: Map<String, String> = emptyMap()
    private var surface: Surface? = null

    @Volatile
    private var state: PlayerState = PlayerState.Idle

    override fun init(context: Context, surface: Surface?) {
        this.context = context.applicationContext
        this.surface = surface
        updateState(PlayerState.Idle)
        try {
            // LoadControl：预缓冲 30 秒
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
                .setBackBuffer(30_000, true)
                .build()

            val player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()

            if (surface != null) player.setVideoSurface(surface)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> updateState(PlayerState.Buffering)
                        Player.STATE_READY -> {
                            updateState(PlayerState.Ready)
                            listener?.onReady()
                        }
                        Player.STATE_ENDED -> updateState(PlayerState.Ended)
                        Player.STATE_IDLE -> updateState(PlayerState.Idle)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    updateState(if (playing) PlayerState.Playing else PlayerState.Paused)
                }

                override fun onPlayerError(error: PlaybackException) {
                    val msg = ErrorTranslator.translateExoError(error.errorCodeName, error.message)
                    updateState(PlayerState.Error(msg, FallbackType.TO_MPV))
                    listener?.onError(msg, FallbackType.TO_MPV)
                }
            })
            exoPlayer = player
        } catch (e: Exception) {
            val msg = "ExoPlayer 初始化失败：${ErrorTranslator.translate(e)}"
            updateState(PlayerState.Error(msg, FallbackType.TO_MPV))
            listener?.onError(msg, FallbackType.TO_MPV)
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        // ISO/BDMV 检测：ExoPlayer 不支持原盘格式，触发降级
        if (isUnsupportedDiscFormat(url)) {
            val ext = url.substringAfterLast('.', "").lowercase()
            val msg = "ExoPlayer 不支持 .$ext 原盘格式，请切换 MPV/VLC 内核"
            updateState(PlayerState.Error(msg, FallbackType.TO_MPV))
            listener?.onError(msg, FallbackType.TO_MPV)
            return
        }

        val player = exoPlayer ?: run {
            init(context ?: return, surface)
            exoPlayer ?: return
        }
        currentHeaders = headers
        updateState(PlayerState.Buffering)

        // 关键：跟随 302 跨协议重定向 + 注入 headers（Emby token）
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("Ember")
            .setDefaultRequestProperties(headers)

        val ctx = context ?: return
        val dataSourceFactory = DefaultDataSource.Factory(ctx, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    override fun pause() { exoPlayer?.pause() }
    override fun resume() { exoPlayer?.play() }
    override fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    override fun stop() {
        exoPlayer?.stop()
        updateState(PlayerState.Idle)
    }

    override fun release() {
        exoPlayer?.release()
        exoPlayer = null
        updateState(PlayerState.Idle)
    }

    override fun setExtraHeaders(headers: Map<String, String>) { currentHeaders = headers }

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        exoPlayer?.setVideoSurface(surface)
    }

    override fun setListener(listener: PlayerListener) { this.listener = listener }
    override fun getState(): PlayerState = state
    override fun isPlaying(): Boolean = exoPlayer?.isPlaying == true
    override fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    override fun getDuration(): Long = (exoPlayer?.duration ?: 0L).coerceAtLeast(0L)
    override fun setPlaySpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    override fun setVolume(volume: Float) { exoPlayer?.volume = volume.coerceIn(0f, 1f) }

    private fun updateState(newState: PlayerState) {
        state = newState
        listener?.onStateChanged(newState)
    }

    private fun isUnsupportedDiscFormat(url: String): Boolean {
        val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
        return ext in setOf("iso", "bdmv", "m2ts")
    }
}
