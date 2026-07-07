package com.ember.presentation.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ember.data.local.TokenManager
import com.ember.data.model.EmbyItem
import com.ember.presentation.ui.common.ErrorState
import com.ember.presentation.ui.common.LoadingState
import com.ember.utils.ImageUrlBuilder

@Composable
fun DetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onPlay: (String) -> Unit
) {
    val viewModel: DetailViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(itemId) { viewModel.loadItem(itemId) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            is DetailState.Loading -> LoadingState()
            is DetailState.Error -> ErrorState(s.message)
            is DetailState.Success -> DetailContent(
                item = s.item,
                onBack = onBack,
                onTagClick = onTagClick,
                onPersonClick = onPersonClick,
                onPlay = { onPlay(s.item.Id ?: "") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    item: EmbyItem,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onPlay: () -> Unit
) {
    val server = TokenManager.getCachedServer() ?: ""
    val token = TokenManager.getCachedToken()
    val backdropUrl = ImageUrlBuilder.buildBackdropImageUrl(server, item.Id, item.ImageTags?.Backdrop, token)
    val primaryUrl = ImageUrlBuilder.buildImageUrl(server, item.Id, item.ImageTags?.Primary, token)

    var overviewExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 顶部：Backdrop 大图 + 渐变遮罩 + 透明返回按钮 + 海报 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            // 背景图
            if (!backdropUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = item.Name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!primaryUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = primaryUrl,
                    contentDescription = item.Name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }

            // 顶部渐变（让状态栏和返回按钮清晰）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
            // 底部渐变（过渡到背景色）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )

            // 透明返回按钮（叠加在顶部）
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 海报（浮在底部左侧，超出 Backdrop 一半）
            val posterWidth = 120.dp
            val posterHeight = 180.dp
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 0.dp)
                    .size(width = posterWidth, height = posterHeight)
            ) {
                if (!primaryUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = primaryUrl,
                        contentDescription = item.Name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.Name?.take(2) ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ===== 标题 + 元信息 + 播放按钮 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
        ) {
            Text(
                text = item.Name ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // 元信息行：年份 · 评分 · 分级 · 时长
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.ProductionYear?.let {
                    MetaChip(it.toString(), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.CommunityRating?.let { rating ->
                    MetaChip(
                        text = "%.1f".format(rating),
                        bgColor = Color(0xFFFFC107).copy(alpha = 0.2f),
                        textColor = Color(0xFFFFC107),
                        leadingIcon = {
                            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFC107))
                        }
                    )
                }
                item.OfficialRating?.let {
                    MetaChip(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                item.RunTimeTicks?.let {
                    if (it > 0) {
                        val mins = it / 600000000  // ticks per minute
                        if (mins > 0) MetaChip("${mins}分钟", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 播放按钮（大胶囊）
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(56.dp)
                    .clickable(onClick = onPlay)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "播放",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // ===== 简介（可展开收起）=====
        if (!item.Overview.isNullOrEmpty()) {
            DetailCard(title = "简介") {
                val maxLines = if (overviewExpanded) Int.MAX_VALUE else 4
                Text(
                    text = item.Overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
                // 超过 4 行才显示展开按钮（粗略判断：内容较长时显示）
                if (item.Overview.length > 120) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (overviewExpanded) "收起" else "展开",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { overviewExpanded = !overviewExpanded }
                    )
                }
            }
        }

        // ===== 标签 =====
        if (!item.Tags.isNullOrEmpty()) {
            DetailCard(title = "标签") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.Tags.take(20).forEach { tag ->
                        AssistChip(
                            onClick = { onTagClick(tag) },
                            label = { Text(tag, fontSize = 13.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // ===== 演员（横向滚动）=====
        val actors = item.People?.filter { it.Type == "Actor" || it.Role == "Actor" }
        if (!actors.isNullOrEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "演员",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(actors.take(20), key = { it.Id ?: it.Name ?: "" }) { person ->
                        PersonCard(
                            server = server,
                            token = token,
                            personId = person.Id,
                            personName = person.Name,
                            personRole = person.Role,
                            primaryImageTag = person.PrimaryImageTag,
                            onClick = {
                                val pid = person.Id ?: return@PersonCard
                                onPersonClick(pid, person.Name ?: "")
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// 元信息小徽章
@Composable
private fun MetaChip(
    text: String,
    bgColor: Color,
    textColor: Color,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingIcon?.invoke()
            Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// 详情页卡片容器（圆角 + 表面色 + 内边距）
@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

// 演员卡片（头像 + 名字 + 角色）
@Composable
private fun PersonCard(
    server: String,
    token: String?,
    personId: String?,
    personName: String?,
    personRole: String?,
    primaryImageTag: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val personImg = ImageUrlBuilder.buildPersonImageUrl(server, personId, primaryImageTag, token)
        Card(
            shape = CircleShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(72.dp)
        ) {
            if (!personImg.isNullOrEmpty()) {
                AsyncImage(
                    model = personImg,
                    contentDescription = personName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = personName?.take(1) ?: "?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = personName ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!personRole.isNullOrEmpty() && personRole != "Actor") {
            Text(
                text = "饰 $personRole",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
