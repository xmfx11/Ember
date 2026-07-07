package com.ember.presentation.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ember.data.model.EmbyItem
import com.ember.presentation.ui.common.EmptyState
import com.ember.presentation.ui.common.ErrorState
import com.ember.presentation.ui.common.SkeletonGrid
import com.ember.presentation.ui.home.HomeViewModel
import com.ember.presentation.ui.home.ListState

// 媒体库 Tab：展示所有媒体库入口（与首页类似，独立 Tab）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onLibraryClick: (EmbyItem) -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.viewsState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadViews() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("媒体库") }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ListState.Loading -> SkeletonGrid(count = 6)
                is ListState.Error -> ErrorState(s.message)
                is ListState.Success -> {
                    if (s.data.isEmpty()) EmptyState("暂无媒体库")
                    else {
                        val server = com.ember.data.local.TokenManager.getCachedServer() ?: ""
                        val token = com.ember.data.local.TokenManager.getCachedToken()
                        com.ember.presentation.ui.common.MediaGrid(s.data, server, token, onLibraryClick)
                    }
                }
            }
        }
    }
}
