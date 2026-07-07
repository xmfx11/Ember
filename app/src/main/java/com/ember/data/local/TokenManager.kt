package com.ember.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// DataStore 扩展（单例）
// v0.03 起改名为 ember_prefs；自动从旧 embylite_prefs 迁移
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ember_prefs")
private val Context.legacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "embylite_prefs")

/**
 * Token 管理器：存储 serverAddress / accessToken / userId
 * - 同步缓存避免在 OkHttp 拦截器中 runBlocking 死锁
 * - init 由 Application 调用，预热缓存
 * - v0.03：从 embylite_prefs 迁移到 ember_prefs；网络异常不清空 token
 */
object TokenManager {

    private val KEY_SERVER = stringPreferencesKey("server_address")
    private val KEY_TOKEN = stringPreferencesKey("access_token")
    private val KEY_USER_ID = stringPreferencesKey("user_id")

    private lateinit var appContext: Context

    // 内存缓存（避免拦截器中 runBlocking）
    @Volatile private var cachedServer: String? = null
    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedUserId: String? = null
    @Volatile private var initialized: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        // 预热缓存（同步阻塞只在此处，禁止在拦截器里做）
        try {
            val prefs = runBlocking { appContext.dataStore.data.first() }
            cachedServer = prefs[KEY_SERVER]
            cachedToken = prefs[KEY_TOKEN]
            cachedUserId = prefs[KEY_USER_ID]

            // 迁移逻辑：新 DataStore 为空时尝试从旧 embylite_prefs 读取
            if (cachedToken.isNullOrEmpty() && !prefs.contains(KEY_TOKEN)) {
                migrateFromLegacy()
            }
        } catch (e: Exception) {
            // 初始化失败不崩溃
        }
        initialized = true
    }

    // 从旧 embylite_prefs 迁移数据到新 ember_prefs
    private fun migrateFromLegacy() {
        try {
            val legacy = runBlocking { appContext.legacyDataStore.data.first() }
            val oldServer = legacy[KEY_SERVER]
            val oldToken = legacy[KEY_TOKEN]
            val oldUserId = legacy[KEY_USER_ID]
            if (!oldToken.isNullOrEmpty() && !oldUserId.isNullOrEmpty()) {
                runBlocking {
                    appContext.dataStore.edit { prefs ->
                        oldServer?.let { prefs[KEY_SERVER] = it }
                        prefs[KEY_TOKEN] = oldToken
                        prefs[KEY_USER_ID] = oldUserId
                    }
                }
                cachedServer = oldServer
                cachedToken = oldToken
                cachedUserId = oldUserId
                // 清空旧数据避免重复迁移
                runBlocking { appContext.legacyDataStore.edit { it.clear() } }
            }
        } catch (e: Exception) {
            // 迁移失败不崩溃，用户重新登录即可
        }
    }

    fun isInitialized(): Boolean = initialized

    // 同步读取（给 OkHttp 拦截器用）
    fun getCachedToken(): String? = cachedToken
    fun getCachedServer(): String? = cachedServer
    fun getCachedUserId(): String? = cachedUserId

    fun isLoggedIn(): Boolean = !cachedToken.isNullOrEmpty() && !cachedUserId.isNullOrEmpty()

    // 异步保存（登录后调用）
    suspend fun saveCredentials(server: String, token: String, userId: String) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_SERVER] = server
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER_ID] = userId
        }
        cachedServer = server
        cachedToken = token
        cachedUserId = userId
    }

    // 异步读取（ViewModel 用）
    suspend fun getServer(): String? = appContext.dataStore.data.first()[KEY_SERVER].also { cachedServer = it }
    suspend fun getToken(): String? = appContext.dataStore.data.first()[KEY_TOKEN].also { cachedToken = it }
    suspend fun getUserId(): String? = appContext.dataStore.data.first()[KEY_USER_ID].also { cachedUserId = it }

    // 清空（退出登录 / token 真失效时调用；网络异常时不要调用）
    suspend fun clear() {
        appContext.dataStore.edit { it.clear() }
        cachedServer = null
        cachedToken = null
        cachedUserId = null
    }
}
