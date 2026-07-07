package com.ember.presentation.ui.scrape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ember.data.local.AppPrefs
import com.ember.scrape.MediaMetadata
import com.ember.scrape.QueryCleaner
import com.ember.scrape.Scraper
import com.ember.scrape.TmdbScraper
import com.ember.utils.AppLogger

/**
 * 刮削预览对话框：
 *
 * - 用户在文件浏览器点击视频文件时，先弹此对话框
 * - 用 QueryCleaner 清洗文件名，调用 TmdbScraper 搜索元数据
 * - 有结果：显示元数据卡片（海报 + 标题 + 年份 + 评分 + 简介）+ "播放"按钮
 * - 无结果 / 无 Key / 出错：显示提示并提供"直接播放"按钮
 *
 * 调用方提供 onPlay()，由本组件触发实际播放跳转。
 */
@Composable
fun ScrapePreviewDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    // 状态：loading / empty / error / success(results)
    var loading by remember { mutableStateOf(true) }
    var results by remember { mutableStateOf<List<MediaMetadata>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasKey by remember { mutableStateOf(AppPrefs.hasTmdbKey()) }
    var cleanedQuery by remember { mutableStateOf("") }
    var cleanedYear by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileName) {
        hasKey = AppPrefs.hasTmdbKey()
        if (!hasKey) {
            loading = false
            errorMsg = "未配置 TMDB API Key，可在「设置」中填写后启用元数据刮削"
            return@LaunchedEffect
        }
        val (q, year) = QueryCleaner.clean(fileName)
        cleanedQuery = q
        cleanedYear = year
        if (q.isBlank()) {
            loading = false
            errorMsg = "无法从文件名提取有效搜索关键词"
            return@LaunchedEffect
        }
        loading = true
        try {
            val scraper: Scraper = TmdbScraper()
            val r = scraper.search(q)
            r.onSuccess { list ->
                results = list
                AppLogger.i("ScrapePreview: query=$q results=${list.size}")
            }.onFailure { e ->
                errorMsg = e.message ?: "刮削失败"
                AppLogger.w("ScrapePreview search failed: ${e.message}")
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "刮削异常"
            AppLogger.e("ScrapePreview error", e)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onDismiss()
                onPlay()
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("播放")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = {
            Text(
                text = "媒体信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在刮削…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    errorMsg != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                errorMsg!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cleanedQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "关键词: $cleanedQuery",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "可直接播放，不显示元数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    results.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("未找到匹配的元数据", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "关键词: $cleanedQuery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "可直接播放",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        // 取最匹配的第一条结果展示
                        val top = pickBest(results, cleanedYear)
                        MetadataCard(meta = top)
                    }
                }
            }
        }
    )
}

/**
 * 从搜索结果中挑选最佳匹配：
 * 优先选年份匹配的；否则取评分最高的（已按评分排序）。
 */
private fun pickBest(results: List<MediaMetadata>, year: String?): MediaMetadata {
    if (year.isNullOrEmpty()) return results.first()
    return results.firstOrNull { it.year == year } ?: results.first()
}

@Composable
private fun MetadataCard(meta: MediaMetadata) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 海报
            if (!meta.posterUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = meta.posterUrl,
                    contentDescription = meta.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 92.dp, height = 132.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 132.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文字信息
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 132.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (meta.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        meta.mediaTypeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    meta.title.ifEmpty { "未知标题" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.originalTitle.isNotEmpty() && meta.originalTitle != meta.title) {
                    Text(
                        meta.originalTitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.year.isNotEmpty()) {
                        Text(
                            meta.year,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFFFC107)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        meta.ratingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (meta.releaseDate.isNotEmpty()) {
                    Text(
                        meta.releaseDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 简介
        if (meta.overview.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                meta.overview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

private val MediaMetadata.mediaTypeLabel: String
    get() = if (mediaType == "tv") "剧集" else "电影"
