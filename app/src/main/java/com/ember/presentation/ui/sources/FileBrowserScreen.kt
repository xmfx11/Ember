package com.ember.presentation.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ember.data.source.UnifiedItem
import com.ember.presentation.navigation.Screen
import com.ember.presentation.ui.common.BackScaffold
import com.ember.presentation.ui.player.PlayerActivity
import com.ember.presentation.ui.scrape.ScrapePreviewDialog

/**
 * 文件浏览器：列出 SMB/WebDAV 服务器上的目录和文件
 * 点击目录进入，点击视频文件跳转播放器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    serverId: String,
    serverName: String,
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: FileBrowserViewModel = viewModel(
        factory = FileBrowserViewModelFactory(context, serverId)
    )
    val state by viewModel.state.collectAsState()

    // 待播放的视频文件（点击后先弹刮削预览，确认后再跳播放器）
    var pendingPlayItem by remember { mutableStateOf<UnifiedItem?>(null) }

    BackScaffold(
        title = serverName,
        onBack = {
            if (!viewModel.goBack()) {
                navController.popBackStack()
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 当前路径面包屑
            if (state.currentPath.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "路径: /${state.currentPath}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when {
                state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                            Button(onClick = { viewModel.listDirectory(state.currentPath) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("空目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            FileItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.enterDirectory(item)
                                    } else if (item.isVideo()) {
                                        // 点击视频：先弹刮削预览（TMDB 元数据卡片），
                                        // 用户确认播放后再跳 PlayerActivity
                                        pendingPlayItem = item
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 刮削预览对话框：文件名 -> TMDB 搜索 -> 展示元数据卡片 -> 播放
    pendingPlayItem?.let { item ->
        ScrapePreviewDialog(
            fileName = item.name,
            onDismiss = { pendingPlayItem = null },
            onPlay = {
                // 跳转播放器，用 serverId:path 标记
                // PlayerActivity 是独立 Activity，必须用 Intent 启动（path 含 / 会破坏 NavHost 路由）
                val itemId = "${item.serverId}:${item.path}"
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("item_id", itemId)
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun FileItemRow(
    item: UnifiedItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            item.isDirectory -> Icons.Default.Folder
            item.isVideo() -> Icons.Default.Movie
            else -> Icons.Default.Article
        }
        val iconTint = when {
            item.isDirectory -> MaterialTheme.colorScheme.primary
            item.isVideo() -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.isDirectory && item.size > 0) {
                Text(
                    formatSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1fKB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1fMB".format(mb)
    val gb = mb / 1024.0
    return "%.2fGB".format(gb)
}

// 简化的 ViewModel 工厂
class FileBrowserViewModelFactory(
    private val context: android.content.Context,
    private val serverId: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return FileBrowserViewModel(context, serverId) as T
    }
}
