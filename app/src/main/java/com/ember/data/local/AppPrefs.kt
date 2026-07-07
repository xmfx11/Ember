package com.ember.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ember.player.PlayerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 应用通用偏好设置（复用 ember_prefs DataStore）
 *
 * 存储：
 * - 播放器引擎选择（EXO / MPV / VLC）
 * - TMDB API Key（媒体刮削用）
 *
 * 同步缓存避免主线程/播放器线程 runBlocking 死锁。
 */
// 独立 DataStore 文件，避免与 TokenManager 的 ember_prefs 冲突（DataStore 不允许同文件多实例）
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "ember_app_prefs")

object AppPrefs {

    private val KEY_PLAYER_ENGINE = stringPreferencesKey("player_engine")
    private val KEY_TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")

    private lateinit var appContext: Context

    @Volatile private var cachedEngine: PlayerType = PlayerType.EXO
    @Volatile private var cachedTmdbKey: String = ""
    @Volatile private var initialized: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = runBlocking { appContext.appDataStore.data.first() }
            cachedEngine = prefs[KEY_PLAYER_ENGINE]?.let { runCatching { PlayerType.valueOf(it) }.getOrNull() }
                ?: PlayerType.EXO
            cachedTmdbKey = prefs[KEY_TMDB_API_KEY] ?: ""
        } catch (_: Exception) {
        }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    // ===== 播放器引擎 =====
    fun getCachedEngine(): PlayerType = cachedEngine

    suspend fun setEngine(engine: PlayerType) {
        appContext.appDataStore.edit { it[KEY_PLAYER_ENGINE] = engine.name }
        cachedEngine = engine
    }

    suspend fun getEngine(): PlayerType =
        appContext.appDataStore.data.first()[KEY_PLAYER_ENGINE]?.let { runCatching { PlayerType.valueOf(it) }.getOrNull() }
            ?: PlayerType.EXO

    // ===== TMDB API Key =====
    fun getCachedTmdbKey(): String = cachedTmdbKey

    fun hasTmdbKey(): Boolean = cachedTmdbKey.isNotEmpty()

    suspend fun setTmdbKey(key: String) {
        appContext.appDataStore.edit { it[KEY_TMDB_API_KEY] = key.trim() }
        cachedTmdbKey = key.trim()
    }

    suspend fun getTmdbKey(): String =
        appContext.appDataStore.data.first()[KEY_TMDB_API_KEY] ?: ""
}
