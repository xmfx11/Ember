package com.ember.data.model

// GitHub Release 响应模型（OTA 用，只取需要的字段）
data class GithubRelease(
    val tag_name: String? = null,
    val name: String? = null,
    val body: String? = null,
    val published_at: String? = null,
    val prerelease: Boolean? = null,
    val assets: List<GithubAsset>? = null
)

data class GithubAsset(
    val id: Long? = null,
    val name: String? = null,
    val size: Long? = null,
    val browser_download_url: String? = null,
    val content_type: String? = null
)
