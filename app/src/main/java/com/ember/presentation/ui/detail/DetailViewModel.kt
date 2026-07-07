package com.ember.presentation.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.model.EmbyItem
import com.ember.data.repository.EmbyRepository
import com.ember.data.local.TokenManager
import com.ember.utils.ErrorTranslator
import com.ember.utils.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val item: EmbyItem) : DetailState()
    data class Error(val message: String) : DetailState()
}

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private var repository: EmbyRepository? = null

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            val server = TokenManager.getCachedServer()
            val userId = TokenManager.getCachedUserId()
            if (server.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _state.value = DetailState.Error("未登录")
                return@launch
            }
            _state.value = DetailState.Loading
            try {
                val repo = repository ?: NetworkModule.createApiService(server).let { EmbyRepository(it) }
                    .also { repository = it }
                // 用独立接口 /Users/{userId}/Items/{itemId} 获取单项详情
                val result = withContext(Dispatchers.IO) { repo.getItem(userId, itemId) }
                result.onSuccess { item ->
                    _state.value = DetailState.Success(item)
                }.onFailure { _state.value = DetailState.Error(ErrorTranslator.translateMessage(it.message ?: "")) }
            } catch (e: Exception) {
                _state.value = DetailState.Error(ErrorTranslator.translate(e))
            }
        }
    }
}
