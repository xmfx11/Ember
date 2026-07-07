package com.ember

import android.app.Application
import android.util.Log
import com.ember.data.local.AppPrefs
import com.ember.data.local.TokenManager
import com.ember.data.source.ServerManager
import com.ember.utils.AppLogger

/**
 * Application 入口：
 * - 初始化 TokenManager（预热缓存，避免拦截器死锁）
 * - 初始化 ServerManager（多源服务器配置）
 * - 初始化系统级日志
 * - 设置全局未捕获异常处理器（记录日志但不崩溃）
 */
class EmberApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 所有初始化 try-catch 包裹，禁止崩溃
        try {
            AppLogger.init(this)
            AppLogger.i("EmberApplication onCreate")
        } catch (e: Exception) {
            Log.e("Ember", "AppLogger init error", e)
        }
        try {
            TokenManager.init(this)
            AppLogger.i("TokenManager init ok, isLoggedIn=${TokenManager.isLoggedIn()}")
        } catch (e: Exception) {
            AppLogger.e("TokenManager init error", e)
        }
        try {
            ServerManager.init(this)
            AppLogger.i("ServerManager init ok, servers=${ServerManager.getCachedServers().size}")
        } catch (e: Exception) {
            AppLogger.e("ServerManager init error", e)
        }
        try {
            AppPrefs.init(this)
            AppLogger.i("AppPrefs init ok, engine=${AppPrefs.getCachedEngine()}, tmdbKeySet=${AppPrefs.hasTmdbKey()}")
        } catch (e: Exception) {
            AppLogger.e("AppPrefs init error", e)
        }

        // 全局异常捕获：写日志，对部分非致命异常吞掉避免杀进程
        // 致命异常（OOM、AndroidRuntime 特定异常）仍交给系统默认处理器
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("Uncaught exception on ${thread.name}", throwable)
            // 判断是否为可忽略的非致命异常（ExoPlayer 子线程、编解码器异常等）
            val isNonFatal = throwable.stackTrace.any {
                it.className.contains("exoplayer", ignoreCase = true) ||
                it.className.contains("media3", ignoreCase = true) ||
                it.className.contains("MediaCodec", ignoreCase = true)
            } || throwable is Exception
            if (isNonFatal && throwable !is OutOfMemoryError) {
                AppLogger.w("非致命异常已吞掉，避免杀进程：${throwable.javaClass.simpleName}")
            } else {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
