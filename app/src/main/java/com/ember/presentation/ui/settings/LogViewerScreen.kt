package com.ember.presentation.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ember.presentation.ui.common.BackScaffold
import com.ember.utils.AppLogger

@Composable
fun LogViewerScreen(
    onBack: () -> Unit
) {
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    // 进入时加载一次
    LaunchedEffect(Unit) { logs = AppLogger.getBuffer().reversed() }

    BackScaffold(title = "系统日志", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 操作按钮
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                OutlinedButton(
                    onClick = { logs = AppLogger.getBuffer().reversed() },
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("刷新") }
                OutlinedButton(onClick = {
                    AppLogger.clear()
                    logs = emptyList()
                }) { Text("清空") }
            }
            // 日志列表
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
