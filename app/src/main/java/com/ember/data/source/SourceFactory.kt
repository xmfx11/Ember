package com.ember.data.source

import android.content.Context
import com.ember.utils.AppLogger

/**
 * 媒体源工厂：根据 ServerConfig.type 创建对应的 MediaSource
 *
 * 已实现：WEBDAV / SMB / EMBY / LOCAL
 * 占位：FTP / SFTP / NFS / JELLYFIN / PLEX / 网盘系列（返回 NotImplementedSource）
 */
object SourceFactory {

    fun create(context: Context, config: ServerConfig): MediaSource = when (config.type) {
        SourceType.WEBDAV -> WebDavProvider(config)
        SourceType.SMB -> SmbProvider(config)
        SourceType.EMBY -> EmbyProxySource(config)
        SourceType.LOCAL -> LocalFileProvider(context)
        // 以下为预留类型，统一返回占位源
        SourceType.FTP,
        SourceType.SFTP,
        SourceType.NFS,
        SourceType.JELLYFIN,
        SourceType.PLEX,
        SourceType.ALIYUN_DRIVE,
        SourceType.BAIDU_NETDISK,
        SourceType.ONEDRIVE,
        SourceType.GOOGLE_DRIVE -> NotImplementedSource(config)
    }

    fun createLocal(context: Context): LocalFileProvider = LocalFileProvider(context)

    fun createWebDav(config: ServerConfig): WebDavProvider = WebDavProvider(config)

    fun createSmb(config: ServerConfig): SmbProvider = SmbProvider(config)
}

/**
 * Emby 代理源：包装原有 EmbyRepository，提供统一的 list 接口
 * 但 Emby 走原有 HomeViewModel 流程，这里仅作为接口占位
 */
class EmbyProxySource(
    private val config: ServerConfig
) : MediaSource {
    override val serverId: String = config.id
    override val type: SourceType = SourceType.EMBY

    override suspend fun list(path: String): Result<List<UnifiedItem>> {
        // Emby 走原有流程，这里不实现
        AppLogger.w("EmbyProxySource.list 未实现，请走原有 Emby 流程")
        return Result.success(emptyList())
    }

    override suspend fun resolvePlayUrl(item: UnifiedItem): Result<String> {
        return Result.success(item.path)
    }

    override suspend fun testConnection(): Result<Boolean> {
        return Result.success(true)
    }
}

/**
 * 占位源：用于尚未实现的协议/网盘类型
 *
 * 行为：
 * - list / resolvePlayUrl 返回失败（提示开发中）
 * - testConnection 返回失败
 *
 * 后续实现新类型时，替换 SourceFactory 中对应分支即可。
 */
class NotImplementedSource(
    private val config: ServerConfig
) : MediaSource {
    override val serverId: String = config.id
    override val type: SourceType = config.type

    private val notImplementedMsg: String
        get() = "${config.type.displayName} 暂未实现，敬请期待（该类型已纳入 v0.07 架构预留）"

    override suspend fun list(path: String): Result<List<UnifiedItem>> {
        AppLogger.w("NotImplementedSource.list type=${config.type}")
        return Result.failure(UnsupportedOperationException(notImplementedMsg))
    }

    override suspend fun resolvePlayUrl(item: UnifiedItem): Result<String> {
        return Result.failure(UnsupportedOperationException(notImplementedMsg))
    }

    override suspend fun testConnection(): Result<Boolean> {
        return Result.failure(UnsupportedOperationException(notImplementedMsg))
    }
}
