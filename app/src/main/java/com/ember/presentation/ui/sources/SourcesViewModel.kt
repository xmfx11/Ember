package com.ember.presentation.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.source.ServerConfig
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourcesState(
    val servers: List<ServerConfig> = emptyList(),
    val loading: Boolean = false
)

class SourcesViewModel : ViewModel() {

    private val _state = MutableStateFlow(SourcesState())
    val state: StateFlow<SourcesState> = _state.asStateFlow()

    init {
        loadServers()
    }

    fun loadServers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val servers = ServerManager.getServers()
            _state.value = SourcesState(servers = servers, loading = false)
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            ServerManager.removeServer(id)
            loadServers()
        }
    }
}
