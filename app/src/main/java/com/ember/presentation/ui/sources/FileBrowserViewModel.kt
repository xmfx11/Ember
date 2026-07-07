package com.ember.presentation.ui.sources

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.source.MediaSource
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceFactory
import com.ember.data.source.UnifiedItem
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileBrowserState(
    val items: List<UnifiedItem> = emptyList(),
    val pathStack: List<String> = emptyList(),  // 路径栈，支持返回上级
    val currentPath: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val canGoBack: Boolean get() = pathStack.isNotEmpty()
}

class FileBrowserViewModel(
    private val context: Context,
    private val serverId: String
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private var source: MediaSource? = null

    init {
        loadSource()
    }

    private fun loadSource() {
        viewModelScope.launch {
            val config = ServerManager.getServer(serverId)
            if (config == null) {
                _state.value = _state.value.copy(error = "找不到服务器配置")
                return@launch
            }
            source = SourceFactory.create(context, config)
            // 自动加载根目录
            listDirectory("")
        }
    }

    fun listDirectory(path: String) {
        val mediaSource = source ?: run {
            _state.value = _state.value.copy(error = "源未初始化")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { mediaSource.list(path) }
                if (result.isSuccess) {
                    val items = result.getOrNull().orEmpty()
                    // 排序：目录在前，文件在后；同类按名称
                    val sorted = items.sortedWith(
                        compareByDescending<UnifiedItem> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    _state.value = FileBrowserState(
                        items = sorted,
                        currentPath = path,
                        loading = false
                    )
                    AppLogger.i("FileBrowser list ok path=$path count=${items.size}")
                } else {
                    val err = result.exceptionOrNull()?.message ?: "未知错误"
                    _state.value = _state.value.copy(loading = false, error = err)
                }
            } catch (e: Exception) {
                val msg = ErrorTranslator.translate(e)
                _state.value = _state.value.copy(loading = false, error = msg)
            }
        }
    }

    // 进入子目录
    fun enterDirectory(item: UnifiedItem) {
        if (!item.isDirectory) return
        val current = _state.value
        _state.value = current.copy(
            pathStack = current.pathStack + current.currentPath
        )
        listDirectory(item.path)
    }

    // 返回上级目录
    fun goBack(): Boolean {
        val current = _state.value
        if (current.pathStack.isEmpty()) return false
        val parentPath = current.pathStack.last()
        _state.value = current.copy(
            pathStack = current.pathStack.dropLast(1)
        )
        listDirectory(parentPath)
        return true
    }
}
