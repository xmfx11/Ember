package com.ember.player.manager

import android.content.Context
import android.view.Surface
import com.ember.player.FallbackType
import com.ember.player.IPlayerCore
import com.ember.player.PlayerListener
import com.ember.player.PlayerState
import com.ember.player.PlayerType
import com.ember.player.factory.PlayerFactory
import com.ember.player.interceptor.UrlInterceptor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 内核切换策略接口
 */
interface KernelStrategy {
    fun selectKernel(url: String): PlayerType
    fun fallbackChain(): List<PlayerType>
}

/**
 * 默认智能切换策略
 *
 * - 默认 ExoPlayer（省电、支持 HLS/DASH 自适应码率）
 * - 检测到 .iso / .bdmv → MPV（v0.01 暂回退 EXO 并提示）
 * - MPV 失败 → 降级 VLC
 * - 全部失败 → ALL_FAILED
 */
class DefaultKernelStrategy : KernelStrategy {
    override fun selectKernel(url: String): PlayerType {
        val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
        return when (ext) {
            "iso", "bdmv", "m2ts" -> PlayerType.MPV
            else -> PlayerType.EXO
        }
    }
    override fun fallbackChain(): List<PlayerType> = listOf(PlayerType.EXO, PlayerType.MPV, PlayerType.VLC)
}

/**
 * 播放器管理器
 *
 * 封装：
 * - 内核创建、初始化、生命周期管理
 * - 智能内核切换（默认 ExoPlayer，ISO→MPV，MPV 失败→VLC）
 * - 错误降级（按 fallbackChain 依次尝试）
 * - URL 拦截器链（播放前预处理 URL，如 Emby 内网/公网切换）
 *
 * 使用示例：
 * ```kotlin
 * val manager = PlayerManager(context)
 * manager.setSurface(surface)
 * manager.setHeaders(mapOf("X-Emby-Token" to "xxx"))
 * manager.setListener(object : PlayerListener { ... })
 * manager.play("https://emby.com/Movies/Foo.mkv")
 * ```
 */
class PlayerManager(private val context: Context) {

    private var currentCore: IPlayerCore? = null
    private var currentType: PlayerType? = null
    private var listener: PlayerListener? = null
    private var headers: Map<String, String> = emptyMap()
    private var surface: Surface? = null
    private var currentUrl: String? = null

    private val urlInterceptors = CopyOnWriteArrayList<UrlInterceptor>()
    private var kernelStrategy: KernelStrategy = DefaultKernelStrategy()
    private val triedTypes = mutableListOf<PlayerType>()

    private val forwardListener = object : PlayerListener {
        override fun onStateChanged(state: PlayerState) { listener?.onStateChanged(state) }
        override fun onProgress(positionMs: Long, durationMs: Long) { listener?.onProgress(positionMs, durationMs) }
        override fun onBufferingUpdate(percent: Int) { listener?.onBufferingUpdate(percent) }
        override fun onError(message: String, fallbackType: FallbackType) { handleFallback(message, fallbackType) }
        override fun onReady() { listener?.onReady() }
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface
        currentCore?.setSurface(surface)
    }

    fun setHeaders(headers: Map<String, String>) {
        this.headers = headers
        currentCore?.setExtraHeaders(headers)
    }

    fun setListener(listener: PlayerListener) { this.listener = listener }
    fun setKernelStrategy(strategy: KernelStrategy) { this.kernelStrategy = strategy }

    fun addUrlInterceptor(interceptor: UrlInterceptor) { urlInterceptors.add(interceptor) }
    fun removeUrlInterceptor(interceptor: UrlInterceptor) { urlInterceptors.remove(interceptor) }

    /**
     * 播放（自动选择内核 + 错误降级）
     *
     * 流程：
     * 1. URL 拦截器链预处理
     * 2. 策略选择内核
     * 3. 创建/初始化内核（若与当前不同则切换）
     * 4. 调用 core.play()
     * 5. 失败时按 fallbackChain 降级
     */
    fun play(url: String) {
        var processedUrl = url
        urlInterceptors.forEach { processedUrl = it.intercept(processedUrl) }
        triedTypes.clear()
        val targetType = kernelStrategy.selectKernel(processedUrl)
        playWithKernel(processedUrl, targetType)
    }

    /** 手动切换内核 */
    fun switchKernel(type: PlayerType) {
        if (currentType == type && currentCore != null) return
        val lastUrl = currentUrl
        releaseCurrent()
        ensureCore(type)
        if (lastUrl != null) currentCore?.play(lastUrl, headers)
    }

    fun getCurrentKernelType(): PlayerType? = currentType
    fun getCurrentCore(): IPlayerCore? = currentCore

    fun pause() { currentCore?.pause() }
    fun resume() { currentCore?.resume() }
    fun seekTo(positionMs: Long) { currentCore?.seekTo(positionMs) }
    fun stop() { currentCore?.stop() }
    fun setPlaySpeed(speed: Float) { currentCore?.setPlaySpeed(speed) }
    fun setVolume(volume: Float) { currentCore?.setVolume(volume) }

    fun isPlaying(): Boolean = currentCore?.isPlaying() == true
    fun getCurrentPosition(): Long = currentCore?.getCurrentPosition() ?: 0L
    fun getDuration(): Long = currentCore?.getDuration() ?: 0L
    fun getState(): PlayerState = currentCore?.getState() ?: PlayerState.Idle

    fun release() {
        releaseCurrent()
        listener = null
        urlInterceptors.clear()
    }

    private fun playWithKernel(url: String, type: PlayerType) {
        if (type in triedTypes) return
        triedTypes.add(type)
        currentUrl = url
        if (currentType != type || currentCore == null) {
            releaseCurrent()
            ensureCore(type)
        }
        currentCore?.setExtraHeaders(headers)
        currentCore?.play(url, headers)
    }

    private fun ensureCore(type: PlayerType) {
        val core = PlayerFactory.create(type)
        core.setListener(forwardListener)
        core.init(context, surface)
        core.setExtraHeaders(headers)
        currentCore = core
        currentType = type
    }

    private fun releaseCurrent() {
        try { currentCore?.release() } catch (_: Exception) {}
        currentCore = null
        currentType = null
    }

    private fun handleFallback(message: String, fallbackType: FallbackType) {
        val lastUrl = currentUrl
        when (fallbackType) {
            FallbackType.TO_MPV -> {
                if (PlayerType.MPV !in triedTypes && lastUrl != null) {
                    listener?.onStateChanged(PlayerState.Idle)
                    playWithKernel(lastUrl, PlayerType.MPV)
                } else forwardFinalError(message)
            }
            FallbackType.TO_VLC -> {
                if (PlayerType.VLC !in triedTypes && lastUrl != null) {
                    listener?.onStateChanged(PlayerState.Idle)
                    playWithKernel(lastUrl, PlayerType.VLC)
                } else forwardFinalError(message)
            }
            FallbackType.ALL_FAILED, FallbackType.NONE -> forwardFinalError(message)
        }
    }

    private fun forwardFinalError(message: String) {
        val chain = kernelStrategy.fallbackChain()
        val allTried = chain.all { it in triedTypes }
        val finalMsg = if (allTried) "所有内核均播放失败：$message。请检查网络或更换设备。" else message
        listener?.onError(finalMsg, FallbackType.ALL_FAILED)
    }
}
