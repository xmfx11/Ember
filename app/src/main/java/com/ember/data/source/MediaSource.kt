package com.ember.data.source

/**
 * 媒体源分类
 * - LOCAL: 手机本地存储
 * - MEDIA_SERVER: 媒体服务器（Emby / Jellyfin / Plex）
 * - NETWORK_PROTOCOL: 网络协议（SMB / FTP / SFTP / NFS / WebDAV）
 * - CLOUD_DRIVE: 网盘（阿里云盘 / 百度网盘 / OneDrive / Google Drive）
 */
enum class SourceCategory(val title: String) {
    LOCAL("本地媒体"),
    MEDIA_SERVER("媒体服务器"),
    NETWORK_PROTOCOL("网络协议"),
    CLOUD_DRIVE("网盘")
}

/**
 * 媒体源类型
 *
 * 架构设计：所有类型统一走 MediaSource 接口，由 SourceFactory 创建具体 Provider。
 * 已实现：LOCAL / EMBY / SMB / WEBDAV
 * 预留（占位 Provider，待实现）：JELLYFIN / PLEX / FTP / SFTP / NFS / 网盘系列
 */
enum class SourceType {
    // 本地
    LOCAL,

    // 媒体服务器
    EMBY, JELLYFIN, PLEX,

    // 网络协议
    SMB, FTP, SFTP, NFS, WEBDAV,

    // 网盘
    ALIYUN_DRIVE, BAIDU_NETDISK, ONEDRIVE, GOOGLE_DRIVE;

    /** 所属分类 */
    val category: SourceCategory get() = when (this) {
        LOCAL -> SourceCategory.LOCAL
        EMBY, JELLYFIN, PLEX -> SourceCategory.MEDIA_SERVER
        SMB, FTP, SFTP, NFS, WEBDAV -> SourceCategory.NETWORK_PROTOCOL
        ALIYUN_DRIVE, BAIDU_NETDISK, ONEDRIVE, GOOGLE_DRIVE -> SourceCategory.CLOUD_DRIVE
    }

    /** 中文显示名 */
    val displayName: String get() = when (this) {
        LOCAL -> "本地视频"
        EMBY -> "Emby"
        JELLYFIN -> "Jellyfin"
        PLEX -> "Plex"
        SMB -> "SMB"
        FTP -> "FTP"
        SFTP -> "SFTP"
        NFS -> "NFS"
        WEBDAV -> "WebDAV"
        ALIYUN_DRIVE -> "阿里云盘"
        BAIDU_NETDISK -> "百度网盘"
        ONEDRIVE -> "OneDrive"
        GOOGLE_DRIVE -> "Google Drive"
    }

    /** 是否需要 OAuth 授权（网盘类） */
    val requiresOAuth: Boolean get() = this in OAUTH_TYPES

    /** 是否已实现（用于 UI 显示“开发中”标记） */
    val implemented: Boolean get() = this in IMPLEMENTED_TYPES

    companion object {
        /** 需要 OAuth 的类型 */
        val OAUTH_TYPES = setOf(ALIYUN_DRIVE, BAIDU_NETDISK, ONEDRIVE, GOOGLE_DRIVE)

        /** 已完整实现的类型 */
        val IMPLEMENTED_TYPES = setOf(LOCAL, EMBY, SMB, WEBDAV)

        /** 按 category 分组（用于添加源 UI） */
        fun byCategory(): Map<SourceCategory, List<SourceType>> =
            values().groupBy { it.category }
    }
}

/**
 * 服务器配置（除 LOCAL 外都需要保存）
 *
 * 兼容字段：SMB/WebDAV/Emby 用 host/username/password/share/domain/useHttps
 * 扩展字段：网盘用 oauthToken/refreshToken/rootPath；其它自定义配置用 extras
 */
data class ServerConfig(
    val id: String,               // 唯一 id（uuid）
    val type: SourceType,
    val name: String,             // 显示名
    val host: String = "",        // 主机地址（含端口，如 192.168.1.100:8096）
    val username: String = "",
    val password: String = "",
    val share: String = "",       // SMB 共享名 / WebDAV 根路径
    val domain: String = "",      // SMB 域（可选）
    val useHttps: Boolean = false,// WebDAV/Emby/Jellyfin/Plex 是否 HTTPS
    // v0.07 网盘 / 扩展支持
    val oauthToken: String = "",   // 网盘 access token
    val refreshToken: String = "", // 网盘 refresh token
    val rootPath: String = "",     // 根路径（网盘文件夹 / FTP 初始目录）
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 构建访问基础 URL
     */
    fun baseUrl(): String = when (type) {
        SourceType.SMB -> "smb://$host/${share.trimStart('/')}"
        SourceType.FTP -> "ftp://$host"
        SourceType.SFTP -> "sftp://$host"
        SourceType.NFS -> "nfs://$host"
        SourceType.WEBDAV -> "${if (useHttps) "https" else "http"}://$host"
        SourceType.EMBY -> "${if (useHttps) "https" else "http"}://$host"
        SourceType.JELLYFIN -> "${if (useHttps) "https" else "http"}://$host"
        SourceType.PLEX -> "${if (useHttps) "https" else "http"}://$host"
        // 网盘走 API，无传统 baseUrl
        SourceType.ALIYUN_DRIVE,
        SourceType.BAIDU_NETDISK,
        SourceType.ONEDRIVE,
        SourceType.GOOGLE_DRIVE -> ""
        SourceType.LOCAL -> ""
    }
}

/**
 * 统一的媒体项（跨协议通用）
 * 用于文件浏览器展示和播放器跳转
 */
data class UnifiedItem(
    val id: String,               // 唯一 id（path 或 url）
    val name: String,             // 显示名
    val path: String,             // 完整路径/URL
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val mimeType: String? = null,
    val thumbnailUrl: String? = null,
    val serverId: String,         // 所属服务器 id（LOCAL 时为 "local"）
    val sourceType: SourceType
) {
    // 是否为视频文件
    fun isVideo(): Boolean {
        if (isDirectory) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    companion object {
        // 支持的视频扩展名
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "flv", "wmv", "ts", "m2ts", "mts",
            "webm", "m4v", "3gp", "mpg", "mpeg", "vob", "ogv", "rmvb", "rm"
        )
    }
}

/**
 * 媒体源统一接口
 * 所有协议实现此接口，文件浏览器通过此接口访问
 */
interface MediaSource {
    val serverId: String
    val type: SourceType

    /** 列出指定路径下的内容（path 为空表示根目录） */
    suspend fun list(path: String = ""): Result<List<UnifiedItem>>

    /** 获取可直接播放的 URL（本地返回 content:// uri，网络返回可访问 URL） */
    suspend fun resolvePlayUrl(item: UnifiedItem): Result<String>

    /** 测试连接是否可用 */
    suspend fun testConnection(): Result<Boolean>
}
