package com.ember.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// 服务器配置 DataStore
private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "ember_servers")

/**
 * 服务器管理器：管理 SMB / WebDAV 等服务器配置（不含 Emby 和 LOCAL）
 * - LOCAL 是内置选项，不需要保存配置
 * - Emby 仍走原有 TokenManager，不在此管理
 * - SMB/WebDAV 配置用 Gson 序列化为 JSON 存储
 *
 * 注：密码明文存储于 DataStore（应用沙箱内），未来可改 EncryptedSharedPreferences
 */
object ServerManager {

    private val KEY_SERVERS = stringPreferencesKey("servers_json")
    private lateinit var appContext: Context

    // 内存缓存
    @Volatile private var cachedServers: List<ServerConfig> = emptyList()
    @Volatile private var initialized: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            val prefs = runBlocking { appContext.serverDataStore.data.first() }
            val json = prefs[KEY_SERVERS] ?: "[]"
            val type = object : TypeToken<List<ServerConfig>>() {}.type
            cachedServers = Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            cachedServers = emptyList()
        }
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    // 同步获取所有服务器配置
    fun getCachedServers(): List<ServerConfig> = cachedServers

    suspend fun getServers(): List<ServerConfig> {
        val prefs = appContext.serverDataStore.data.first()
        val json = prefs[KEY_SERVERS] ?: "[]"
        val type = object : TypeToken<List<ServerConfig>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }

    // 添加服务器配置
    suspend fun addServer(config: ServerConfig): Boolean = try {
        val current = getServers().toMutableList()
        current.add(config)
        saveAll(current)
        true
    } catch (e: Exception) {
        false
    }

    // 删除服务器配置
    suspend fun removeServer(id: String): Boolean = try {
        val current = getServers().filter { it.id != id }
        saveAll(current)
        true
    } catch (e: Exception) {
        false
    }

    // 更新服务器配置
    suspend fun updateServer(config: ServerConfig): Boolean = try {
        val current = getServers().toMutableList()
        val idx = current.indexOfFirst { it.id == config.id }
        if (idx >= 0) current[idx] = config
        saveAll(current)
        true
    } catch (e: Exception) {
        false
    }

    // 根据 id 获取配置
    suspend fun getServer(id: String): ServerConfig? = getServers().firstOrNull { it.id == id }
    fun getCachedServer(id: String): ServerConfig? = cachedServers.firstOrNull { it.id == id }

    // 生成 uuid
    fun generateId(): String = java.util.UUID.randomUUID().toString()

    private suspend fun saveAll(servers: List<ServerConfig>) {
        val json = Gson().toJson(servers)
        appContext.serverDataStore.edit { it[KEY_SERVERS] = json }
        cachedServers = servers
    }
}
