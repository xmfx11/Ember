package com.ember.presentation.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.source.ServerConfig
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceFactory
import com.ember.data.source.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddServerState(
    val loading: Boolean = false,
    val message: String? = null,
    val success: Boolean = false
)

class AddServerViewModel : ViewModel() {

    private val _state = MutableStateFlow(AddServerState())
    val state: StateFlow<AddServerState> = _state.asStateFlow()

    // 测试连接
    fun testConnection(config: ServerConfig) {
        viewModelScope.launch {
            _state.value = AddServerState(loading = true)
            try {
                // 用临时 context（这里 config 的 serverId 是临时的，但测试只用到 host/cred）
                val source = SourceFactory.create(android.app.Application(), config)
                val result = withContext(Dispatchers.IO) { source.testConnection() }
                if (result.isSuccess) {
                    _state.value = AddServerState(loading = false, message = "连接成功", success = true)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "连接失败"
                    _state.value = AddServerState(loading = false, message = err, success = false)
                }
            } catch (e: Exception) {
                _state.value = AddServerState(loading = false, message = "连接失败：${e.message}", success = false)
            }
        }
    }

    // 保存服务器配置
    fun save(config: ServerConfig, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = ServerManager.addServer(config)
            onDone(ok)
        }
    }
}
