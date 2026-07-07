package com.ember.player.data

import android.content.Context
import com.ember.data.source.ServerManager
import com.ember.data.source.SmbProvider
import com.ember.data.source.SourceType
import com.ember.utils.AppLogger
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import java.io.InputStream
import java.net.URLDecoder

/**
 * SMB DataSource：让 ExoPlayer 能直接读 smb:// URL
 *
 * URL 格式：smb://serverId/encodedPath
 * 通过 ServerManager 找到对应的 SmbProvider，调用 openInputStream 拉取数据
 *
 * 限制：不支持 seek（不支持 Range），仅顺序播放
 * 大文件 seek 需要重新建立连接，本期简化方案
 */
class SmbDataSource(
    private val context: Context
) : DataSource {

    private var currentStream: InputStream? = null
    private var uriString: String? = null
    private var bytesRemaining: Long = C.LENGTH_UNBOUNDED.toLong()
    private var bytesTransferred: Long = 0
    private var opened: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        // 简化：不维护多个 listener
    }

    override fun open(dataSpec: DataSpec): Long {
        uriString = dataSpec.uri.toString()
        AppLogger.i("SmbDataSource open: $uriString, position=${dataSpec.position}, length=${dataSpec.length}")

        try {
            val uri = dataSpec.uri
            // smb://serverId/encodedPath
            val serverId = uri.host ?: throw Exception("SMB URL 缺少 serverId")
            val encodedPath = uri.path?.trimStart('/') ?: ""
            val remotePath = URLDecoder.decode(encodedPath, "UTF-8")

            val config = ServerManager.getCachedServer(serverId)
                ?: throw Exception("找不到 SMB 服务器配置：$serverId")

            if (config.type != SourceType.SMB) {
                throw Exception("服务器 $serverId 不是 SMB 类型")
            }

            val provider = SmbProvider(config)
            currentStream = provider.openInputStream(remotePath)

            // 计算剩余字节
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                C.LENGTH_UNBOUNDED.toLong()  // 未知长度
            }

            opened = true
            return bytesRemaining
        } catch (e: Exception) {
            AppLogger.e("SmbDataSource open failed", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = currentStream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining == C.LENGTH_UNBOUNDED.toLong()) {
            length
        } else {
            minOf(length, bytesRemaining.toInt())
        }

        val read = stream.read(buffer, offset, toRead)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        bytesTransferred += read
        if (bytesRemaining != C.LENGTH_UNBOUNDED.toLong()) {
            bytesRemaining -= read
        }
        return read
    }

    override fun getUri(): android.net.Uri? {
        return uriString?.let { android.net.Uri.parse(it) }
    }

    override fun close() {
        try {
            currentStream?.close()
        } catch (e: Exception) {
            AppLogger.e("SmbDataSource close failed", e)
        }
        currentStream = null
        opened = false
    }

    // ExoPlayer C 常量（避免引用 com.google.android.exoplayer2.C 的复杂签名）
    private object C {
        const val LENGTH_UNSET: Long = -1
        const val RESULT_END_OF_INPUT: Int = -1
        val LENGTH_UNBOUNDED: Long = Long.MAX_VALUE
    }
}

/**
 * SMB DataSource Factory
 */
class SmbDataSourceFactory(
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): SmbDataSource {
        return SmbDataSource(context)
    }
}
