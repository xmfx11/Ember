package com.ember.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ember.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地文件源：通过 SAF（Storage Access Framework）选择视频文件
 *
 * 设计：
 * - 不持久化目录配置（用户每次主动选择文件）
 * - list() 返回空（无目录概念，由 UI 层调用 SAF）
 * - resolvePlayUrl 直接返回 content:// URI 给 ExoPlayer
 *
 * ExoPlayer 原生支持 content:// URI，无需额外处理
 */
class LocalFileProvider(
    private val context: Context
) : MediaSource {

    override val serverId: String = "local"
    override val type: SourceType = SourceType.LOCAL

    // LOCAL 无目录概念，list 返回空，UI 应直接调用 SAF
    override suspend fun list(path: String): Result<List<UnifiedItem>> {
        return Result.success(emptyList())
    }

    // 从 SAF 返回的 Uri 构建 UnifiedItem
    suspend fun buildItemFromUri(uri: Uri): Result<UnifiedItem> = withContext(Dispatchers.IO) {
        try {
            var name = "本地视频"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            val mimeType = context.contentResolver.getType(uri)
            val item = UnifiedItem(
                id = uri.toString(),
                name = name,
                path = uri.toString(),
                isDirectory = false,
                size = size,
                mimeType = mimeType,
                serverId = "local",
                sourceType = SourceType.LOCAL
            )
            AppLogger.i("LocalFile buildItem: name=$name size=$size mime=$mimeType")
            Result.success(item)
        } catch (e: Exception) {
            AppLogger.e("LocalFile buildItem failed", e)
            Result.failure(e)
        }
    }

    // 直接返回 content:// URI
    override suspend fun resolvePlayUrl(item: UnifiedItem): Result<String> {
        return Result.success(item.path)
    }

    // 本地始终可用
    override suspend fun testConnection(): Result<Boolean> {
        return Result.success(true)
    }
}
