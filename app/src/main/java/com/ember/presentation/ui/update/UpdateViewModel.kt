package com.ember.presentation.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.repository.UpdateInfo
import com.ember.data.repository.UpdateManager
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// OTA 状态
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class Downloaded(val file: File) : UpdateState()
}

class UpdateViewModel : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkUpdate() {
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            try {
                val info = withContext(Dispatchers.IO) { UpdateManager.checkUpdate() }
                if (info == null) {
                    _state.value = UpdateState.Error("检查更新失败，请检查网络")
                } else if (info.hasUpdate) {
                    _state.value = UpdateState.Available(info)
                } else {
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                AppLogger.e("checkUpdate vm exception", e)
                _state.value = UpdateState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    fun download(context: Context, url: String) {
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(0)
            try {
                val file = withContext(Dispatchers.IO) {
                    UpdateManager.downloadApk(context, url) { pct ->
                        _state.value = UpdateState.Downloading(pct)
                    }
                }
                if (file != null) {
                    _state.value = UpdateState.Downloaded(file)
                } else {
                    _state.value = UpdateState.Error("下载失败")
                }
            } catch (e: Exception) {
                AppLogger.e("download vm exception", e)
                _state.value = UpdateState.Error(ErrorTranslator.translate(e))
            }
        }
    }

    fun install(context: Context, file: File) {
        UpdateManager.installApk(context, file)
    }

    fun reset() { _state.value = UpdateState.Idle }
}
