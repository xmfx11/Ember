package com.ember.presentation.ui.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ember.data.source.ServerConfig
import com.ember.data.source.ServerManager
import com.ember.data.source.SourceType
import com.ember.presentation.ui.common.BackScaffold

/**
 * 添加服务器页面
 * 根据 type 参数显示不同表单（SMB / WebDAV）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    type: String,
    navController: NavController
) {
    val viewModel: AddServerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    val sourceType = when (type.lowercase()) {
        "smb" -> SourceType.SMB
        "webdav" -> SourceType.WEBDAV
        "emby" -> SourceType.EMBY
        "jellyfin" -> SourceType.JELLYFIN
        "plex" -> SourceType.PLEX
        "ftp" -> SourceType.FTP
        "sftp" -> SourceType.SFTP
        "nfs" -> SourceType.NFS
        "aliyun_drive" -> SourceType.ALIYUN_DRIVE
        "baidu_netdisk" -> SourceType.BAIDU_NETDISK
        "onedrive" -> SourceType.ONEDRIVE
        "google_drive" -> SourceType.GOOGLE_DRIVE
        else -> SourceType.WEBDAV
    }
    val typeLabel = sourceType.displayName

    // 表单字段
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(if (sourceType == SourceType.SMB) "445" else "") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    BackScaffold(
        title = "添加 $typeLabel 服务器",
        onBack = { navController.popBackStack() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("显示名称") },
                placeholder = { Text("我的 NAS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.weight(2f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("端口") },
                    placeholder = { Text(if (sourceType == SourceType.SMB) "445" else "5005") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            if (sourceType == SourceType.SMB) {
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text("共享名称") },
                    placeholder = { Text("如 video、movies") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("域（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = useHttps, onCheckedChange = { useHttps = it })
                    Text("使用 HTTPS")
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 测试连接结果
            state.message?.let { msg ->
                Text(
                    text = msg,
                    color = if (state.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 测试连接按钮
            OutlinedButton(
                onClick = {
                    val fullHost = if (port.isNotEmpty()) "$host:$port" else host
                    val config = ServerConfig(
                        id = "test_${System.currentTimeMillis()}",
                        type = sourceType,
                        name = name,
                        host = fullHost,
                        username = username,
                        password = password,
                        share = share,
                        domain = domain,
                        useHttps = useHttps
                    )
                    viewModel.testConnection(config)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = host.isNotEmpty() && !state.loading
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Text("测试连接")
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    val fullHost = if (port.isNotEmpty()) "$host:$port" else host
                    val config = ServerConfig(
                        id = ServerManager.generateId(),
                        type = sourceType,
                        name = name.ifEmpty { "$typeLabel-$host" },
                        host = fullHost,
                        username = username,
                        password = password,
                        share = share,
                        domain = domain,
                        useHttps = useHttps
                    )
                    viewModel.save(config) { saved ->
                        if (saved) {
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = host.isNotEmpty() && !state.loading
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
