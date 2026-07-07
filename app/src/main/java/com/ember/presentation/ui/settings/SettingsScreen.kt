package com.ember.presentation.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ember.BuildConfig
import com.ember.data.local.AppPrefs
import com.ember.data.local.TokenManager
import com.ember.player.PlayerType
import com.ember.presentation.ui.login.LoginActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _engine = MutableStateFlow(AppPrefs.getCachedEngine())
    val engine: StateFlow<PlayerType> = _engine.asStateFlow()

    private val _tmdbKeySet = MutableStateFlow(AppPrefs.hasTmdbKey())
    val tmdbKeySet: StateFlow<Boolean> = _tmdbKeySet.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            TokenManager.clear()
            _loggedOut.value = true
        }
    }

    fun setEngine(engine: PlayerType) {
        viewModelScope.launch {
            AppPrefs.setEngine(engine)
            _engine.value = engine
        }
    }

    fun setTmdbKey(key: String) {
        viewModelScope.launch {
            AppPrefs.setTmdbKey(key)
            _tmdbKeySet.value = key.isNotEmpty()
        }
    }

    fun info(): String {
        val server = TokenManager.getCachedServer() ?: "未连接"
        val userId = TokenManager.getCachedUserId() ?: "-"
        return "服务器: $server\n用户ID: $userId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onUpdateClick: () -> Unit = {},
    onLogsClick: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val tmdbKeySet by viewModel.tmdbKeySet.collectAsState()
    val context = LocalContext.current

    // 退出登录后跳回 LoginActivity
    LaunchedEffect(loggedOut) {
        if (loggedOut) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    // 播放器引擎选择对话框
    var showEngineDialog by remember { mutableStateOf(false) }
    // TMDB API Key 输入对话框
    var showTmdbDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 服务器信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Text("连接信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(viewModel.info(), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // ===== 播放器引擎切换 =====
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEngineDialog = true },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("播放器引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "当前：${engineLabel(engine)}${if (engine != PlayerType.EXO) "（待集成，回退 ExoPlayer）" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ===== TMDB API Key（媒体刮削） =====
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTmdbDialog = true },
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TMDB API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (tmdbKeySet) "已配置（媒体刮削已启用）"
                                else "未配置（填写后启用影片元数据刮削）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 检查更新入口
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUpdateClick),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                        Column {
                            Text("检查更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("检查并安装最新版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 系统日志入口
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLogsClick),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                        Column {
                            Text("系统日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("查看应用运行日志", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 关于卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Ember v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                        Text("多媒体聚合播放器 · 多源 · 多引擎 · 媒体刮削", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 退出登录按钮
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("退出登录")
                }
            }
        }
    }

    // 播放器引擎选择对话框
    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            confirmButton = {
                TextButton(onClick = { showEngineDialog = false }) { Text("关闭") }
            },
            title = { Text("选择播放器引擎") },
            text = {
                Column {
                    PlayerType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setEngine(type); showEngineDialog = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = type == engine,
                                onClick = { viewModel.setEngine(type); showEngineDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(engineLabel(type), fontWeight = FontWeight.Medium)
                                Text(engineDesc(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        )
    }

    // TMDB API Key 输入对话框
    if (showTmdbDialog) {
        var keyInput by remember { mutableStateOf(AppPrefs.getCachedTmdbKey()) }
        AlertDialog(
            onDismissRequest = { showTmdbDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTmdbKey(keyInput)
                    showTmdbDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showTmdbDialog = false }) { Text("取消") }
            },
            title = { Text("TMDB API Key") },
            text = {
                Column {
                    Text(
                        "在 themoviedb.org 注册后获取 API Key，用于影片元数据刮削（标题、简介、海报、评分）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Key (v3 auth)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
        )
    }
}

private fun engineLabel(type: PlayerType): String = when (type) {
    PlayerType.EXO -> "ExoPlayer"
    PlayerType.MPV -> "MPV"
    PlayerType.VLC -> "VLC"
}

private fun engineDesc(type: PlayerType): String = when (type) {
    PlayerType.EXO -> "默认内核，省电，支持 HLS/DASH 自适应码率"
    PlayerType.MPV -> "支持 ISO/BDMV 蓝光原盘（依赖待集成，当前回退 Exo）"
    PlayerType.VLC -> "兼容性强（依赖待集成，当前回退 Exo）"
}
