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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    onSourcesClick: () -> Unit = {},
    onEmbyLoginClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onLogsClick: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val tmdbKeySet by viewModel.tmdbKeySet.collectAsState()
    val context = LocalContext.current
    val isLoggedIn = TokenManager.isLoggedIn()

    // 退出登录后回到首页（不再强制跳登录页）
    LaunchedEffect(loggedOut) {
        if (loggedOut) {
            // 回到 MainActivity 首页
            val intent = Intent(context, com.ember.presentation.ui.main.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    var showEngineDialog by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ===== 分组1：媒体源 =====
                SettingsGroup(title = "媒体源") {
                    // 媒体源管理（抽屉式入口）
                    SettingsRow(
                        icon = Icons.Default.Hub,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "媒体源管理",
                        subtitle = "SMB / WebDAV / 本地 / 网盘",
                        onClick = onSourcesClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    // Emby 服务器
                    SettingsRow(
                        icon = Icons.Default.Dns,
                        iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        title = "Emby 服务器",
                        subtitle = if (isLoggedIn) viewModel.info() else "未连接，点击登录",
                        onClick = onEmbyLoginClick
                    )
                }

                // ===== 分组2：播放 =====
                SettingsGroup(title = "播放") {
                    SettingsRow(
                        icon = Icons.Default.PlayCircle,
                        iconBg = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = "播放器引擎",
                        subtitle = "${engineLabel(engine)}${if (engine != PlayerType.EXO) " · 待集成，回退 Exo" else ""}",
                        onClick = { showEngineDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.VpnKey,
                        iconBg = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.onErrorContainer,
                        title = "TMDB API Key",
                        subtitle = if (tmdbKeySet) "已配置 · 媒体刮削已启用" else "未配置 · 点击填写启用刮削",
                        onClick = { showTmdbDialog = true }
                    )
                }

                // ===== 分组3：其他 =====
                SettingsGroup(title = "其他") {
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "检查更新",
                        subtitle = "检查并安装最新版本",
                        onClick = onUpdateClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Article,
                        iconBg = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "系统日志",
                        subtitle = "查看应用运行日志",
                        onClick = onLogsClick
                    )
                }

                // ===== 分组4：关于 =====
                SettingsGroup(title = "关于") {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "Ember v${BuildConfig.VERSION_NAME}",
                        subtitle = "多媒体聚合播放器 · 多源 · 多引擎 · 媒体刮削"
                    )
                }

                // 退出登录（仅登录时显示）
                if (isLoggedIn) {
                    Surface(
                        onClick = { viewModel.logout() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "退出 Emby 登录",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
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

/**
 * 设置分组：带标题的圆角卡片容器，内部子项用 HorizontalDivider 分隔。
 * 借鉴 iOS 设置 / Material You 分组样式。
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(0.5.dp)
        ) {
            Column { content() }
        }
    }
}

/**
 * 设置项行：圆角图标 + 标题 + 副标题 + 右箭头
 * 借鉴 iOS Settings / Material 3 list item 样式
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆角方形图标背景（iOS 风格）
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = iconBg,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
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
