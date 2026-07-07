package com.ember.data.model

// 媒体项、媒体库、人员等数据模型，所有字段可空
data class EmbyItem(
    val Id: String? = null,
    val Name: String? = null,
    val Type: String? = null,
    val CollectionType: String? = null,
    val Overview: String? = null,
    val ProductionYear: Int? = null,
    val CommunityRating: Double? = null,
    val OfficialRating: String? = null,
    val RunTimeTicks: Long? = null,
    val ImageTags: ImageTags? = null,
    val Tags: List<String>? = null,
    val People: List<EmbyPerson>? = null,
    val MediaSources: List<MediaSource>? = null,
    val UserData: UserData? = null,
    val DateCreated: String? = null
)

data class ImageTags(
    val Primary: String? = null,
    val Backdrop: String? = null,
    val Logo: String? = null,
    val Thumb: String? = null
)

data class EmbyPerson(
    val Id: String? = null,
    val Name: String? = null,
    val Role: String? = null,
    val Type: String? = null,
    val PrimaryImageTag: String? = null
)

data class UserData(
    val Played: Boolean? = null,
    val PlayCount: Int? = null,
    val PlaybackPositionTicks: Long? = null
)

data class ItemsResponse(
    val Items: List<EmbyItem>? = null,
    val TotalRecordCount: Int? = null
)

data class ViewsResponse(
    val Items: List<EmbyItem>? = null,
    val TotalRecordCount: Int? = null
)

data class PlaybackInfoResponse(
    val MediaSources: List<MediaSource>? = null,
    val PlaySessionId: String? = null
)

// 搜索响应
data class SearchResponse(
    val SearchHints: List<EmbyItem>? = null,
    val TotalRecordCount: Int? = null
)
