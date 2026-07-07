package com.ember.data.source

import com.ember.utils.AppLogger
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URLEncoder
import java.util.EnumSet

/**
 * SMB 源：使用 smbj 访问 SMB/CIFS 共享
 *
 * 设计要点：
 * - 每次 list() 建立连接并关闭，避免长连接管理复杂度
 * - 路径格式：相对于 share 的子路径（如 "Movies/Action"）
 * - 播放 URL 用 smb:// 伪协议，由 SMB 拦截器在 ExoPlayer 中转 HTTP 流
 *   （但 ExoPlayer 不支持 smb://，这里改为通过本地 HTTP 代理转换）
 *
 * 简化方案：SMB 文件播放时，先下载到 cacheDir 再用 file:// 播放
 * （大文件流式播放需要本地 HTTP 代理，本期先简化为下载播放）
 */
class SmbProvider(
    private val config: ServerConfig
) : MediaSource {

    override val serverId: String = config.id
    override val type: SourceType = SourceType.SMB

    // 从配置解析主机和端口
    private val hostPort: Pair<String, Int> by lazy {
        val parts = config.host.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 445 else 445
        host to port
    }

    override suspend fun list(path: String): Result<List<UnifiedItem>> = withContext(Dispatchers.IO) {
        var share: DiskShare? = null
        var session: Session? = null
        var connection: Connection? = null
        try {
            val (host, port) = hostPort
            val client = SMBClient()
            connection = client.connect(host, port)
            val authContext = if (config.password.isNotEmpty()) {
                AuthenticationContext(
                    config.username.ifEmpty { "guest" },
                    config.password.toCharArray(),
                    config.domain.ifEmpty { null }
                )
            } else {
                AuthenticationContext.guest()
            }
            session = connection.authenticate(authContext)

            // 获取 share（用配置的 share 名）
            val shareName = config.share.trimStart('/').trimEnd('/')
            if (shareName.isEmpty()) {
                return@withContext Result.failure(Exception("SMB 共享名不能为空"))
            }
            share = session.connectShare(shareName) as DiskShare

            // 列出 path 目录内容
            val cleanPath = path.trim('/').replace('/', '\\')
            val result = mutableListOf<UnifiedItem>()

            // 获取目录列表
            val children = share.list(cleanPath.ifEmpty { "\\" })
            children.forEach { info ->
                val name = info.fileName
                // 跳过 . 和 ..
                if (name == "." || name == "..") return@forEach

                val isDir = (info.fileAttributes and 0x10L) != 0L  // FILE_ATTRIBUTE_DIRECTORY
                val childPath = if (cleanPath.isEmpty()) name else "$cleanPath\\$name"
                val unifiedPath = childPath.replace('\\', '/')

                result.add(UnifiedItem(
                    id = unifiedPath,
                    name = name,
                    path = unifiedPath,
                    isDirectory = isDir,
                    size = if (isDir) 0 else info.endOfFile,
                    lastModified = info.lastWriteTime.toEpochMillis(),
                    serverId = config.id,
                    sourceType = SourceType.SMB
                ))
            }

            AppLogger.i("SMB list ok, path=$path count=${result.size}")
            Result.success(result)
        } catch (e: TransportException) {
            AppLogger.e("SMB 连接失败", e)
            Result.failure(Exception("SMB 连接失败：无法连接到 ${config.host}（请检查地址、端口和防火墙）"))
        } catch (e: Exception) {
            AppLogger.e("SMB list failed", e)
            Result.failure(Exception("SMB 列目录失败：${e.message ?: e.javaClass.simpleName}"))
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * SMB 播放方案：将 SMB 文件流通过本地 HTTP 服务暴露给 ExoPlayer
     * 由于实现本地 HTTP 服务复杂，本期简化方案：将 SMB 文件路径编码为
     * 特殊 URL "smb://serverId/path"，由 PlayerActivity 在播放时通过
     * SmbProxyStreamDataSource 转换为 ExoPlayer 可读的 DataSource
     */
    override suspend fun resolvePlayUrl(item: UnifiedItem): Result<String> {
        // 编码为 smb:// 协议 URL，PlayerActivity 会用 SmbDataSource 处理
        val encodedPath = URLEncoder.encode(item.path, "UTF-8")
        val url = "smb://${config.id}/$encodedPath"
        return Result.success(url)
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        var share: DiskShare? = null
        var session: Session? = null
        var connection: Connection? = null
        try {
            val (host, port) = hostPort
            val client = SMBClient()
            connection = client.connect(host, port)
            val authContext = if (config.password.isNotEmpty()) {
                AuthenticationContext(
                    config.username.ifEmpty { "guest" },
                    config.password.toCharArray(),
                    config.domain.ifEmpty { null }
                )
            } else {
                AuthenticationContext.guest()
            }
            session = connection.authenticate(authContext)
            val shareName = config.share.trimStart('/').trimEnd('/')
            if (shareName.isNotEmpty()) {
                share = session.connectShare(shareName) as DiskShare
            }
            Result.success(true)
        } catch (e: TransportException) {
            Result.failure(Exception("SMB 连接失败：无法连接到 ${config.host}"))
        } catch (e: Exception) {
            Result.failure(Exception("SMB 连接失败：${e.message ?: e.javaClass.simpleName}"))
        } finally {
            try { share?.close() } catch (_: Exception) {}
            try { session?.close() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }

    /**
     * 打开 SMB 文件输入流（供 SmbDataSource 拉取数据）
     * 调用方负责关闭 InputStream
     */
    fun openInputStream(remotePath: String): InputStream {
        val (host, port) = hostPort
        val client = SMBClient()
        val connection = client.connect(host, port)
        val authContext = if (config.password.isNotEmpty()) {
            AuthenticationContext(
                config.username.ifEmpty { "guest" },
                config.password.toCharArray(),
                config.domain.ifEmpty { null }
            )
        } else {
            AuthenticationContext.guest()
        }
        val session = connection.authenticate(authContext)
        val shareName = config.share.trimStart('/').trimEnd('/')
        val share = session.connectShare(shareName) as DiskShare

        val smbPath = remotePath.replace('/', '\\')
        val file = share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        // 包装 InputStream，关闭时一并关闭 share/session/connection
        return SmbInputStream(file.inputStream, file, share, session, connection)
    }

    // 包装 InputStream，关闭时释放 SMB 资源
    private class SmbInputStream(
        private val delegate: InputStream,
        private val file: com.hierynomus.smbj.share.File,
        private val share: DiskShare,
        private val session: Session,
        private val connection: Connection
    ) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun available(): Int = delegate.available()
        override fun close() {
            try { delegate.close() } catch (_: Exception) {}
            try { file.close() } catch (_: Exception) {}
            try { share.close() } catch (_: Exception) {}
            try { session.close() } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}
        }
    }
}
