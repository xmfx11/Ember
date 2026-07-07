package com.ember.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ember.data.local.TokenManager
import com.ember.data.model.EmbyItem
import com.ember.presentation.ui.common.EmptyState
import com.ember.presentation.ui.common.ErrorState
import com.ember.presentation.ui.common.SkeletonGrid
import com.ember.utils.ImageUrlBuilder

// 首页：媒体库入口
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLibraryClick: (EmbyItem) -> Unit,
    onSourcesClick: () -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.viewsState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadViews() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ember") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ListState.Loading -> SkeletonGrid(count = 6)
                is ListState.Error -> ErrorState(s.message)
                is ListState.Success -> {
                    if (s.data.isEmpty()) {
                        EmptyState("暂无媒体库，请到「媒体源」Tab 添加")
                    } else {
                        LibraryGrid(s.data, onLibraryClick)
                    }
                }
            }
        }
    }
}

// 媒体库入口网格（横版卡片）
@Composable
private fun LibraryGrid(
    items: List<EmbyItem>,
    onClick: (EmbyItem) -> Unit
) {
    val server = TokenManager.getCachedServer() ?: ""
    val token = TokenManager.getCachedToken()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.Id ?: "" }) { item ->
            LibraryCard(item, server, token) { onClick(item) }
        }
    }
}

@Composable
private fun LibraryCard(
    item: EmbyItem,
    server: String,
    token: String?,
    onClick: () -> Unit
) {
    val imageUrl = ImageUrlBuilder.buildImageUrl(server, item.Id, item.ImageTags?.Primary, token)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl,
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
                        text = item.Name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 底部渐变遮罩 + 名称
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            Text(
                text = item.Name ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

// 媒体库内容列表页
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemsScreen(
    parentId: String,
    parentName: String,
    onBack: () -> Unit,
    onItemClick: (EmbyItem) -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.itemsState.collectAsState()

    LaunchedEffect(parentId) { viewModel.loadItems(parentId) }

    com.ember.presentation.ui.common.BackScaffold(
        title = parentName,
        onBack = onBack
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ListState.Loading -> SkeletonGrid()
                is ListState.Error -> ErrorState(s.message)
                is ListState.Success -> {
                    val server = TokenManager.getCachedServer() ?: ""
                    val token = TokenManager.getCachedToken()
                    com.ember.presentation.ui.common.MediaGrid(s.data, server, token, onItemClick)
                }
            }
        }
    }
}

// 筛选列表页（标签 / 演员）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterItemsScreen(
    filterType: String,
    filterId: String,
    filterName: String,
    onBack: () -> Unit,
    onItemClick: (EmbyItem) -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.itemsState.collectAsState()

    LaunchedEffect(filterId) { viewModel.loadFiltered(filterType, filterId) }

    val title = if (filterType == "person") "演员: $filterName" else "标签: $filterName"
    com.ember.presentation.ui.common.BackScaffold(
        title = title,
        onBack = onBack
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ListState.Loading -> SkeletonGrid()
                is ListState.Error -> ErrorState(s.message)
                is ListState.Success -> {
                    val server = TokenManager.getCachedServer() ?: ""
                    val token = TokenManager.getCachedToken()
                    com.ember.presentation.ui.common.MediaGrid(s.data, server, token, onItemClick)
                }
            }
        }
    }
}
