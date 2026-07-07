package com.ember.presentation.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ember.data.local.TokenManager
import com.ember.data.repository.EmbyRepository
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import com.ember.utils.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 登录状态
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // 登录
    fun login(server: String, username: String, password: String) {
        if (server.isBlank() || username.isBlank()) {
            _loginState.value = LoginState.Error("请填写服务器地址和用户名")
            return
        }
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                // 关键：登录前清空旧凭据，避免旧 token 干扰新服务器认证
                TokenManager.clear()
                val apiService = NetworkModule.createApiService(server)
                val repository = EmbyRepository(apiService)
                val result = withContext(Dispatchers.IO) {
                    repository.login(username, password)
                }
                result.onSuccess { response ->
                    val token = response.AccessToken
                    val user = response.User
                    val userId = user?.Id
                    if (!token.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                        TokenManager.saveCredentials(server, token, userId)
                        _loginState.value = LoginState.Success
                    } else {
                        _loginState.value = LoginState.Error("服务器返回无效的响应")
                    }
                }.onFailure { e ->
                    _loginState.value = LoginState.Error(mapError(e))
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(mapError(e))
            }
        }
    }

    // 自动登录：检查已保存 token 是否有效
    // 关键改进：网络异常不清空 token（保留登录态，下次再试）；只有 401 才清空
    fun checkAutoLogin() {
        viewModelScope.launch {
            val server = TokenManager.getServer()
            val token = TokenManager.getToken()
            val userId = TokenManager.getUserId()
            if (server.isNullOrEmpty() || token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                _loginState.value = LoginState.Idle
                return@launch
            }
            _loginState.value = LoginState.Loading
            try {
                val apiService = NetworkModule.createApiService(server)
                val repository = EmbyRepository(apiService)
                val result = withContext(Dispatchers.IO) { repository.getUser(userId) }
                if (result.isSuccess) {
                    _loginState.value = LoginState.Success
                } else {
                    // 区分 token 真失效（401）和 网络问题
                    val errMsg = result.exceptionOrNull()?.message ?: ""
                    if (errMsg.contains("401") || errMsg.contains("认证失败")) {
                        // token 真失效，清空让用户重新登录
                        TokenManager.clear()
                        _loginState.value = LoginState.Idle
                    } else {
                        // 网络问题或服务器临时不可用，保留 token，直接进入 App
                        // 用户在 App 内访问数据时如果仍失败会看到错误提示
                        AppLogger.w("自动登录验证失败但保留登录态：$errMsg")
                        _loginState.value = LoginState.Success
                    }
                }
            } catch (e: Exception) {
                // 异常（网络问题等），保留登录态，让用户进 App 后再处理
                AppLogger.w("自动登录异常但保留登录态：${e.message}")
                _loginState.value = LoginState.Success
            }
        }
    }

    // 错误信息映射（统一走 ErrorTranslator，保留服务器原始错误便于排查）
    private fun mapError(e: Throwable): String {
        val msg = e.message ?: ""
        // 401 特殊处理：可能是密码错误，也可能是旧 token 干扰，提取服务器返回的具体原因
        if (msg.contains("401")) {
            val serverErr = msg.substringAfter(":", "").trim()
            return if (serverErr.isNotEmpty()) "认证失败（401）：$serverErr"
            else "认证失败（401）：用户名或密码错误，请重试"
        }
        // 登录失败的特殊场景：repository 包装了"登录失败 xxx"前缀
        if (msg.startsWith("登录失败")) {
            return msg
        }
        return ErrorTranslator.translate(e)
    }

    fun resetState() { _loginState.value = LoginState.Idle }
}
