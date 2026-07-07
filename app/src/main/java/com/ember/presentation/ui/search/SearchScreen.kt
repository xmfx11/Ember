package com.ember.presentation.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ember.data.local.TokenManager
import com.ember.data.model.EmbyItem
import com.ember.presentation.ui.common.EmptyState
import com.ember.presentation.ui.common.MediaGridItem
import com.ember.presentation.ui.common.SkeletonGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onItemClick: (EmbyItem) -> Unit
) {
    val viewModel: SearchViewModel = viewModel()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("搜索") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                placeholder = { Text("搜索电影、剧集...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) })
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isSearching -> SkeletonGrid()
                    hasSearched && results.isEmpty() -> EmptyState("未找到结果")
                    results.isEmpty() -> EmptyState("输入关键词开始搜索")
                    else -> {
                        val server = TokenManager.getCachedServer() ?: ""
                        val token = TokenManager.getCachedToken()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            items(results, key = { it.Id ?: "" }) { item ->
                                MediaGridItem(item, server, token, onClick = { onItemClick(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}
