package com.ember.presentation.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ember.BuildConfig
import com.ember.presentation.ui.common.BackScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val viewModel: UpdateViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 进入页面自动检查
    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) viewModel.checkUpdate()
    }

    BackScaffold(title = "检查更新", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 当前版本
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("当前版本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                when (val s = state) {
                    is UpdateState.Idle -> {}

                    is UpdateState.Checking -> {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(24.dp))
                                Text("正在检查更新...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("已是最新版本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("当前版本 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    is UpdateState.Available -> {
                        val info = s.info
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("发现新版本", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("版本: v${info.latestVersion}", style = MaterialTheme.typography.bodyMedium)
                                if (info.publishedAt.isNotEmpty()) {
                                    Text("发布时间: ${info.publishedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (info.releaseNotes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("更新说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (info.downloadUrl.isNotEmpty()) {
                                    viewModel.download(context, info.downloadUrl)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = info.downloadUrl.isNotEmpty()
                        ) {
                            Text("下载并安装")
                        }
                    }

                    is UpdateState.Downloading -> {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("正在下载...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                LinearProgressIndicator(
                                    progress = s.progress / 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("${s.progress}%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    is UpdateState.Downloaded -> {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("下载完成", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("点击下方按钮安装更新", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = { viewModel.install(context, s.file) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("安装更新")
                        }
                    }

                    is UpdateState.Error -> {
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("检查失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(s.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 手动重试按钮
                if (state is UpdateState.Error || state is UpdateState.UpToDate) {
                    OutlinedButton(
                        onClick = { viewModel.checkUpdate() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重新检查")
                    }
                }
            }
        }
    }
}
