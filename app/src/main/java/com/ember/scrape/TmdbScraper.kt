package com.ember.scrape

import com.ember.data.local.AppPrefs
import com.ember.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * TMDB 刮削器实现
 *
 * API 文档：https://developers.themoviedb.org/3
 * - 搜索：/search/movie, /search/tv
 * - 详情：/movie/{id}, /tv/{id}
 * - 图片：https://image.tmdb.org/t/p/w500{poster_path}
 *
 * 需要 API Key（在设置页填写，存于 AppPrefs）
 */
class TmdbScraper : Scraper {

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
        private const val LANGUAGE = "zh-CN"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun apiKey(): String = AppPrefs.getCachedTmdbKey()

    override suspend fun search(query: String): Result<List<MediaMetadata>> = withContext(Dispatchers.IO) {
        if (apiKey().isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未配置 TMDB API Key，请在设置中填写"))
        }
        if (query.isBlank()) {
            return@withContext Result.success(emptyList())
        }
        try {
            // 同时搜电影和剧集，合并结果
            val movies = searchType(query, "movie")
            val tvs = searchType(query, "tv")
            val merged = (movies + tvs).sortedByDescending { it.rating }
            AppLogger.i("TMDB search query=$query results=${merged.size}")
            Result.success(merged)
        } catch (e: Exception) {
            AppLogger.e("TMDB search failed", e)
            Result.failure(e)
        }
    }

    private fun searchType(query: String, type: String): List<MediaMetadata> {
        val url = "$BASE_URL/search/$type?api_key=${apiKey()}&language=$LANGUAGE&query=${URLEncoder.encode(query, "UTF-8")}&page=1&include_adult=false"
        val resp = doRequest(url) ?: return emptyList()
        val results = resp.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<MediaMetadata>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            list.add(parseSearchResult(obj, type))
        }
        return list
    }

    override suspend fun getDetails(tmdbId: Int, mediaType: String): Result<MediaMetadata> = withContext(Dispatchers.IO) {
        if (apiKey().isEmpty()) {
            return@withContext Result.failure(IllegalStateException("未配置 TMDB API Key"))
        }
        try {
            val url = "$BASE_URL/$mediaType/$tmdbId?api_key=${apiKey()}&language=$LANGUAGE"
            val resp = doRequest(url) ?: return@withContext Result.failure(Exception("TMDB 返回空"))
            val meta = parseSearchResult(resp, mediaType).copy(
                overview = resp.optString("overview", "").ifEmpty { resp.optString("overview", "") }
            )
            Result.success(meta)
        } catch (e: Exception) {
            AppLogger.e("TMDB getDetails failed", e)
            Result.failure(e)
        }
    }

    private fun doRequest(url: String): JSONObject? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.w("TMDB request failed: HTTP ${response.code} url=${url.take(120)}")
                return null
            }
            val body = response.body?.string() ?: return null
            return JSONObject(body)
        }
    }

    private fun parseSearchResult(obj: JSONObject, type: String): MediaMetadata {
        val id = obj.optInt("id", 0)
        val title = when (type) {
            "movie" -> obj.optString("title", obj.optString("name", ""))
            else -> obj.optString("name", obj.optString("title", ""))
        }
        val originalTitle = obj.optString("original_title", obj.optString("original_name", ""))
        val dateField = if (type == "movie") "release_date" else "first_air_date"
        val releaseDate = obj.optString(dateField, "")
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
        val overview = obj.optString("overview", "")
        val posterPath = obj.optString("poster_path", "")
        val backdropPath = obj.optString("backdrop_path", "")
        val rating = obj.optDouble("vote_average", 0.0).toFloat()
        val genreIds = obj.optJSONArray("genre_ids")
        val genres = mutableListOf<String>()
        if (genreIds != null) {
            for (i in 0 until genreIds.length()) {
                genres.add(genreIds.optInt(i).toString())
            }
        }
        return MediaMetadata(
            tmdbId = id,
            mediaType = type,
            title = title,
            originalTitle = originalTitle,
            year = year,
            overview = overview,
            posterUrl = if (posterPath.isNotEmpty() && posterPath != "null") IMAGE_BASE + posterPath else null,
            backdropUrl = if (backdropPath.isNotEmpty() && backdropPath != "null") BACKDROP_BASE + backdropPath else null,
            rating = rating,
            genres = genres,
            releaseDate = releaseDate
        )
    }
}
