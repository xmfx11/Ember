package com.ember.scrape

/**
 * 媒体元数据（刮削结果）
 */
data class MediaMetadata(
    val tmdbId: Int,
    val mediaType: String,        // "movie" | "tv"
    val title: String,
    val originalTitle: String = "",
    val year: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Float = 0f,
    val genres: List<String> = emptyList(),
    val releaseDate: String = ""
) {
    /** 评分显示文本 */
    val ratingText: String get() = if (rating > 0) "%.1f".format(rating) else "暂无"
}

/**
 * 媒体刮削器接口
 *
 * 实现方：TMDB / 豆瓣 等
 * 用法：用文件名（去扩展名、去分辨率标记）作为 query 搜索
 */
interface Scraper {
    /** 搜索媒体，返回候选列表 */
    suspend fun search(query: String): Result<List<MediaMetadata>>

    /** 获取详情（含完整简介、海报） */
    suspend fun getDetails(tmdbId: Int, mediaType: String): Result<MediaMetadata>
}

/**
 * 文件名 → 搜索 query 的清洗工具
 *
 * 去除：扩展名、分辨率标记（720p/1080p/4K）、编码（x264/h264）、
 * 来源（BluRay/WEB-DL）、年份提取、替换分隔符为空格
 */
object QueryCleaner {

    private val NOISE_TOKENS = listOf(
        "720p", "1080p", "2160p", "4k", "480p", "360p",
        "x264", "x265", "h264", "h265", "hevc", "avc",
        "bluray", "web-dl", "webrip", "webdl", "hdrip", "bdrip", "dvdrip",
        "remux", "hdr", "sdr", "10bit", "6ch", "aac", "ac3", "dts",
        "hsbs", "hhou", "3d", "sbs",
        "multi", "dual", "subbed", "chs", "cht", "eng", "jpn", "chi",
        "x11", "x22"
    )

    private val YEAR_REGEX = Regex("\\((19|20)\\d{2}\\)")

    fun clean(fileName: String): Pair<String, String?> {
        // 去扩展名
        var name = fileName.substringBeforeLast('.', fileName)
        // 去年份括号
        val year = YEAR_REGEX.find(name)?.value?.let { it.substring(1, 5) }
        name = name.replace(YEAR_REGEX, " ")
        // 分隔符 -> 空格
        name = name.replace('.', ' ').replace('_', ' ').replace('-', ' ').replace('+', ' ')
        // 去方括号/中括号内容
        name = name.replace(Regex("[\\[\\]【】()]"), " ")
        // 小写化后逐 token 过滤噪音
        val tokens = name.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .filter { token -> NOISE_TOKENS.none { it.equals(token, ignoreCase = true) } }
            .joinToString(" ")
        return tokens.trim() to year
    }
}
