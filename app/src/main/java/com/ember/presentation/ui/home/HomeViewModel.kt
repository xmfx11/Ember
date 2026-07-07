package com.ember.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.model.EmbyItem
import com.ember.data.repository.EmbyRepository
import com.ember.utils.ErrorTranslator
import com.ember.utils.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 列表加载状态
sealed class ListState<out T> {
    object Loading : ListState<Nothing>()
    data class Success<T>(val data: List<T>) : ListState<T>()
    data class Error(val message: String) : ListState<Nothing>()
}

/**
 * 首页 / 媒体库 / 列表筛选 通用 ViewModel
 * 根据 server + token 创建 repository
 */
class HomeViewModel : ViewModel() {

    private val _viewsState = MutableStateFlow<ListState<EmbyItem>>(ListState.Loading)
    val viewsState: StateFlow<ListState<EmbyItem>> = _viewsState.asStateFlow()

    private val _itemsState = MutableStateFlow<ListState<EmbyItem>>(ListState.Loading)
    val itemsState: StateFlow<ListState<EmbyItem>> = _itemsState.asStateFlow()

    private var repository: EmbyRepository? = null

    private fun ensureRepository(server: String): EmbyRepository {
        return repository ?: NetworkModule.createApiService(server).let { EmbyRepository(it) }
            .also { repository = it }
    }

    // 加载媒体库列表（首页入口）
    fun loadViews() {
        viewModelScope.launch {
            val server = com.ember.data.local.TokenManager.getCachedServer()
            val userId = com.ember.data.local.TokenManager.getCachedUserId()
            // 未连接 Emby 时返回空列表（不是错误），首页显示引导卡片
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _viewsState.value = ListState.Success(emptyList())
                return@launch
            }
            _viewsState.value = ListState.Loading
            try {
                val repo = ensureRepository(server)
                val result = withContext(Dispatchers.IO) { repo.getViews(userId) }
                result.onSuccess { _viewsState.value = ListState.Success(it) }
                    .onFailure { _viewsState.value = ListState.Error(ErrorTranslator.translateMessage(it.message ?: "")) }
            } catch (e: Exception) {
                _viewsState.value = ListState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    // 加载媒体库内容
    fun loadItems(parentId: String) {
        viewModelScope.launch {
            val server = com.ember.data.local.TokenManager.getCachedServer()
            val userId = com.ember.data.local.TokenManager.getCachedUserId()
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _itemsState.value = ListState.Error("未登录")
                return@launch
            }
            _itemsState.value = ListState.Loading
            try {
                val repo = ensureRepository(server)
                val result = withContext(Dispatchers.IO) { repo.getItems(userId, parentId) }
                result.onSuccess { _itemsState.value = ListState.Success(it) }
                    .onFailure { _itemsState.value = ListState.Error(ErrorTranslator.translateMessage(it.message ?: "")) }
            } catch (e: Exception) {
                _itemsState.value = ListState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    // 按标签 / 演员筛选
    fun loadFiltered(filterType: String, filterId: String) {
        viewModelScope.launch {
            val server = com.ember.data.local.TokenManager.getCachedServer()
            val userId = com.ember.data.local.TokenManager.getCachedUserId()
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _itemsState.value = ListState.Error("未登录")
                return@launch
            }
            _itemsState.value = ListState.Loading
            try {
                val repo = ensureRepository(server)
                val result = withContext(Dispatchers.IO) {
                    when (filterType) {
                        "person" -> repo.getItemsByPerson(userId, filterId)
                        else -> repo.getItemsByTag(userId, filterId)
                    }
                }
                result.onSuccess { _itemsState.value = ListState.Success(it) }
                    .onFailure { _itemsState.value = ListState.Error(ErrorTranslator.translateMessage(it.message ?: "")) }
            } catch (e: Exception) {
                _itemsState.value = ListState.Error(ErrorTranslator.translate(e))
            }
        }
    }
}
