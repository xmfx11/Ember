package com.ember.presentation.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.local.TokenManager
import com.ember.data.model.MediaSource
import com.ember.data.repository.EmbyRepository
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceFactory
import com.ember.data.source.UnifiedItem
import com.ember.data.source.SourceType
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import com.ember.utils.NetworkModule
import com.ember.utils.PlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PlayerState {
    object Loading : PlayerState()
    data class Ready(
        val url: String,
        val mediaSource: MediaSource?,
        val title: String,
        val itemId: String
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

/**
 * v0.05 多源 PlayerViewModel：
 * itemId 格式：
 *   - 普通 Emby itemId（纯字母数字）
 *   - "local:content://..." 本地视频
 *   - "serverId:path" SMB/WebDAV 文件
 */
class PlayerViewModel(
    private val app: Application
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var repository: EmbyRepository? = null

    fun loadAndPrepare(itemId: String) {
        AppLogger.i("Player loadAndPrepare itemId=${itemId.take(120)}")

        // 解析 itemId 格式
        when {
            // 本地视频：local:uri
            itemId.startsWith("local:") -> {
                val uri = itemId.removePrefix("local:")
                handleLocal(uri)
            }
            // SMB/WebDAV：serverId:path
            itemId.contains(':') && !itemId.matches(Regex("^[0-9a-fA-F]+$")) -> {
                val idx = itemId.indexOf(':')
                val serverId = itemId.substring(0, idx)
                val path = itemId.substring(idx + 1)
                handleNetworkSource(serverId, path)
            }
            // 默认：Emby itemId
            else -> handleEmby(itemId)
        }
    }

    // 本地视频：直接返回 URI 给 ExoPlayer
    private fun handleLocal(uri: String) {
        if (uri.isEmpty()) {
            _state.value = PlayerState.Error("本地视频 URI 为空")
            return
        }
        val name = try {
            val parsed = android.net.Uri.parse(uri)
            val cursor = app.contentResolver.query(parsed, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else "本地视频"
                } else "本地视频"
            } ?: "本地视频"
        } catch (e: Exception) { "本地视频" }

        _state.value = PlayerState.Ready(
            url = uri,
            mediaSource = null,
            title = name,
            itemId = itemId_local(uri)
        )
        AppLogger.i("Player local ready: $name")
    }

    // 网络源（SMB/WebDAV）：通过 ServerManager 找配置，resolvePlayUrl
    private fun handleNetworkSource(serverId: String, path: String) {
        viewModelScope.launch {
            _state.value = PlayerState.Loading
            try {
                val config = withContext(Dispatchers.IO) { ServerManager.getServer(serverId) }
                if (config == null) {
                    _state.value = PlayerState.Error("找不到服务器配置：$serverId")
                    return@launch
                }
                val source = SourceFactory.create(app, config)
                // 构造 UnifiedItem
                val item = UnifiedItem(
                    id = path,
                    name = path.substringAfterLast('/').ifEmpty { path },
                    path = path,
                    isDirectory = false,
                    serverId = serverId,
                    sourceType = config.type
                )
                val urlResult = withContext(Dispatchers.IO) { source.resolvePlayUrl(item) }
                urlResult.onSuccess { url ->
                    AppLogger.i("Player network source ready: ${config.name} type=${config.type} url=${url.take(120)}")
                    _state.value = PlayerState.Ready(
                        url = url,
                        mediaSource = null,
                        title = item.name,
                        itemId = "net:$serverId:$path"
                    )
                }.onFailure { e ->
                    _state.value = PlayerState.Error(
                        "${config.type.name} 播放地址获取失败：${ErrorTranslator.translate(e)}"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("Player handleNetworkSource exception", e)
                _state.value = PlayerState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    // Emby 原有逻辑
    private fun handleEmby(itemId: String) {
        viewModelScope.launch {
            val server = TokenManager.getCachedServer()
            val userId = TokenManager.getCachedUserId()
            val token = TokenManager.getCachedToken()
            AppLogger.i("Player emby itemId=$itemId server=${server ?: "null"} userId=${userId ?: "null"}")
            if (server.isNullOrEmpty() || userId.isNullOrEmpty() || itemId.isEmpty()) {
                _state.value = PlayerState.Error("参数无效（server/userId/itemId 缺失）")
                return@launch
            }
            _state.value = PlayerState.Loading
            try {
                val repo = repository ?: EmbyRepository(NetworkModule.createApiService(server))
                    .also { repository = it }
                val result = withContext(Dispatchers.IO) { repo.getPlaybackInfo(itemId, userId) }
                result.onSuccess { sources ->
                    AppLogger.i("Player got ${sources.size} media sources")
                    sources.forEachIndexed { i, s ->
                        AppLogger.i("  source[$i] id=${s.Id} name=${s.Name} container=${s.Container} directStream=${s.SupportsDirectStream} directPlay=${s.SupportsDirectPlay}")
                    }
                    val source = sources.firstOrNull { it.SupportsDirectStream == true && !it.DirectStreamUrl.isNullOrEmpty() }
                        ?: sources.firstOrNull { it.SupportsDirectPlay == true }
                        ?: sources.firstOrNull()
                    val url = PlayerUtils.buildPlayUrl(server, itemId, source, token)
                    if (!url.isNullOrEmpty()) {
                        val title = source?.Name ?: "Ember 播放器"
                        _state.value = PlayerState.Ready(url, source, title, itemId)
                    } else {
                        _state.value = PlayerState.Error("无法获取播放地址")
                    }
                }.onFailure {
                    AppLogger.e("Player getPlaybackInfo failed", it)
                    _state.value = PlayerState.Error(ErrorTranslator.translateMessage(it.message ?: ""))
                }
            } catch (e: Exception) {
                AppLogger.e("Player loadAndPrepare exception", e)
                _state.value = PlayerState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    private fun itemId_local(uri: String): String = "local:$uri"
}
