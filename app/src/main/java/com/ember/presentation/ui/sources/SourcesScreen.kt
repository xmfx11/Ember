package com.ember.presentation.ui.sources

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ember.data.source.ServerConfig
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceCategory
import com.ember.data.source.SourceType
import com.ember.presentation.ui.common.BackScaffold
import com.ember.presentation.ui.player.PlayerActivity
import com.ember.presentation.ui.scrape.ScrapePreviewDialog
import kotlinx.coroutines.launch

/**
 * 源列表页面（v0.07 重构）：
 *
 * 架构：
 * - 顶部：本地视频入口（SAAF 选文件）
 * - 添加媒体源：按分类（媒体服务器 / 网络协议 / 网盘）展示所有支持的类型
 *   - 已实现类型可点击进入添加表单
 *   - 未实现类型标记"开发中"，点击弹提示
 * - 已添加的源列表：按配置展示，点击进入文件浏览器
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(
    navController: NavController
) {
    val viewModel: SourcesViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 待刮削预览的本地视频：Pair<Uri, 显示名>
    var pendingLocalVideo by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }

    // SAF 选择视频文件
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 永久授权
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            // 取显示名，用于刮削
            var displayName = "本地视频"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) {
                        c.getString(idx)?.let { displayName = it }
                    }
                }
            }
            // 先弹刮削预览，确认后再跳播放器
            pendingLocalVideo = uri to displayName
        }
    }

    // 开发中提示对话框
    var pendingDevType by remember { mutableStateOf<SourceType?>(null) }

    BackScaffold(
        title = "媒体源",
        onBack = { navController.popBackStack() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== 本地视频入口 =====
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pickVideoLauncher.launch(arrayOf("video/*"))
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "本地视频",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "选择手机上的视频文件播放",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ===== 添加媒体源（按分类） =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "添加媒体源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 遍历分类，每类一个区块（LOCAL 跳过，已用顶部卡片）
            SourceType.byCategory().forEach { (category, types) ->
                if (category == SourceCategory.LOCAL) return@forEach

                item {
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        types.forEach { type ->
                            SourceTypeChip(
                                type = type,
                                onClick = {
                                    if (type.implemented) {
                                        // 已实现：进入添加表单（Emby 走登录页，这里也提供表单占位）
                                        navController.navigate(
                                            com.ember.presentation.navigation.Screen.AddServer.createRoute(type.name.lowercase())
                                        )
                                    } else {
                                        // 未实现：弹开发中提示
                                        pendingDevType = type
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ===== 已添加的源列表 =====
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "已添加的源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (state.servers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无已添加的源，点击上方类型添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        onClick = {
                            navController.navigate(
                                com.ember.presentation.navigation.Screen.FileBrowser.createRoute(server.id, server.name)
                            )
                        },
                        onDelete = { scope.launch { viewModel.removeServer(server.id) } }
                    )
                }
            }
        }
    }

    // 开发中提示对话框
    pendingDevType?.let { type ->
        AlertDialog(
            onDismissRequest = { pendingDevType = null },
            confirmButton = {
                TextButton(onClick = { pendingDevType = null }) { Text("知道了") }
            },
            title = { Text("${type.displayName} 即将支持") },
            text = {
                Text(
                    "${type.displayName} 已纳入 v0.07 媒体源架构，" +
                        if (type.requiresOAuth) "该网盘需要 OAuth 授权流程，正在开发中。"
                        else "该协议 Provider 正在开发中，敬请期待。"
                )
            }
        )
    }

    // 本地视频刮削预览：文件名 -> TMDB 搜索 -> 元数据卡片 -> 播放
    pendingLocalVideo?.let { (uri, name) ->
        ScrapePreviewDialog(
            fileName = name,
            onDismiss = { pendingLocalVideo = null },
            onPlay = {
                // PlayerActivity 是独立 Activity，必须用 Intent 启动
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("item_id", "local:$uri")
                }
                context.startActivity(intent)
            }
        )
    }
}

/**
 * 媒体源类型 chip：图标 + 名称 + 开发中标记
 */
@Composable
private fun SourceTypeChip(
    type: SourceType,
    onClick: () -> Unit
) {
    val icon = type.icon()
    val bg = if (type.implemented) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (type.implemented) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AssistChip(
        onClick = onClick,
        label = { Text(type.displayName, color = contentColor) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
        },
        trailingIcon = {
            if (!type.implemented) {
                Text(
                    "开发中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = bg)
    )
}

/** SourceType → 图标映射（UI 层） */
@Composable
private fun SourceType.icon(): ImageVector = when (this) {
    SourceType.LOCAL -> Icons.Default.PhoneAndroid
    SourceType.EMBY -> Icons.Default.Movie
    SourceType.JELLYFIN -> Icons.Default.VideoLibrary
    SourceType.PLEX -> Icons.Default.VideoLibrary
    SourceType.SMB -> Icons.Default.Computer
    SourceType.FTP -> Icons.Default.Storage
    SourceType.SFTP -> Icons.Default.Storage
    SourceType.NFS -> Icons.Default.Storage
    SourceType.WEBDAV -> Icons.Default.CloudQueue
    SourceType.ALIYUN_DRIVE -> Icons.Default.Cloud
    SourceType.BAIDU_NETDISK -> Icons.Default.Cloud
    SourceType.ONEDRIVE -> Icons.Default.Cloud
    SourceType.GOOGLE_DRIVE -> Icons.Default.Cloud
}

@Composable
private fun ServerCard(
    server: ServerConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = server.type.icon()
    val typeLabel = server.type.displayName

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, fontWeight = FontWeight.Medium)
                Text(
                    "$typeLabel · ${server.host.ifEmpty { "已配置" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
