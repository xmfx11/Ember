package com.ember.presentation.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ember.presentation.navigation.Screen
import com.ember.presentation.theme.EmberTheme
import com.ember.presentation.ui.detail.DetailScreen
import com.ember.presentation.ui.home.HomeScreen
import com.ember.presentation.ui.home.LibraryItemsScreen
import com.ember.presentation.ui.home.FilterItemsScreen
import com.ember.presentation.ui.library.LibraryScreen
import com.ember.presentation.ui.player.PlayerActivity
import com.ember.presentation.ui.search.SearchScreen
import com.ember.presentation.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // setContent 兜底，防止崩溃
        try {
            setContent { EmberTheme { MainScreen() } }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "setContent crash", e)
            setContentView(TextView(this).apply { text = "启动失败，请重试" })
        }
    }
}

// 底部导航 4 个 Tab
data class BottomTab(val screen: Screen, val label: String, val icon: @Composable () -> Unit)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val tabs = listOf(
        BottomTab(Screen.Home, "首页") { Icon(Icons.Default.Home, contentDescription = "首页") },
        BottomTab(Screen.Sources, "媒体源") { Icon(Icons.Default.Hub, contentDescription = "媒体源") },
        BottomTab(Screen.Search, "搜索") { Icon(Icons.Default.Search, contentDescription = "搜索") },
        BottomTab(Screen.Settings, "设置") { Icon(Icons.Default.Settings, contentDescription = "设置") }
    )

    // 当前是否在顶层 Tab（决定底部栏是否显示）
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.screen.route,
                            onClick = {
                                if (currentRoute != tab.screen.route) {
                                    navController.navigate(tab.screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { tab.icon() },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // 只应用底部 padding（底部导航栏 + 系统手势条），顶部不 padding
        // 让详情页 Backdrop 能沉浸式延伸到状态栏后；各 Tab 页自带 TopAppBar 处理状态栏
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // 首页
            composable(Screen.Home.route) {
                HomeScreen(
                    onLibraryClick = { view ->
                        navController.navigate(Screen.LibraryItems.createRoute(view.Id ?: "", view.Name ?: ""))
                    },
                    onSourcesClick = {
                        navController.navigate(Screen.Sources.route)
                    }
                )
            }

            // 搜索
            composable(Screen.Search.route) {
                SearchScreen(
                    onItemClick = { item ->
                        navController.navigate(Screen.Detail.createRoute(item.Id ?: ""))
                    }
                )
            }

            // 媒体库（分类浏览，复用首页数据但展示所有媒体库）
            composable(Screen.Library.route) {
                LibraryScreen(
                    onLibraryClick = { view ->
                        navController.navigate(Screen.LibraryItems.createRoute(view.Id ?: "", view.Name ?: ""))
                    }
                )
            }

            // 设置
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onUpdateClick = { navController.navigate(Screen.Update.route) },
                    onLogsClick = { navController.navigate(Screen.Logs.route) }
                )
            }

            // 检查更新
            composable(Screen.Update.route) {
                com.ember.presentation.ui.update.UpdateScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // 系统日志
            composable(Screen.Logs.route) {
                com.ember.presentation.ui.settings.LogViewerScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // 媒体库内容列表
            composable(
                Screen.LibraryItems.route,
                arguments = listOf(
                    navArgument("parentId") { type = NavType.StringType },
                    navArgument("parentName") { type = NavType.StringType }
                )
            ) { entry ->
                val parentId = entry.arguments?.getString("parentId") ?: ""
                val parentName = entry.arguments?.getString("parentName") ?: ""
                LibraryItemsScreen(
                    parentId = parentId,
                    parentName = parentName,
                    onBack = { navController.popBackStack() },
                    onItemClick = { item ->
                        navController.navigate(Screen.Detail.createRoute(item.Id ?: ""))
                    }
                )
            }

            // 通用筛选列表（标签 / 演员）
            composable(
                Screen.FilterItems.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType }
                )
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: "tag"
                val id = entry.arguments?.getString("id") ?: ""
                val name = entry.arguments?.getString("name") ?: ""
                FilterItemsScreen(
                    filterType = type,
                    filterId = id,
                    filterName = name,
                    onBack = { navController.popBackStack() },
                    onItemClick = { item ->
                        navController.navigate(Screen.Detail.createRoute(item.Id ?: ""))
                    }
                )
            }

            // 详情页
            composable(
                Screen.Detail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) { entry ->
                val itemId = entry.arguments?.getString("itemId") ?: ""
                DetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onTagClick = { tag ->
                        navController.navigate(Screen.FilterItems.createRoute("tag", tag, tag))
                    },
                    onPersonClick = { personId, personName ->
                        navController.navigate(Screen.FilterItems.createRoute("person", personId, personName))
                    },
                    onPlay = { id ->
                        // 启动独立播放 Activity
                        navController.context.let { ctx ->
                            val intent = Intent(ctx, PlayerActivity::class.java).apply {
                                putExtra("item_id", id)
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            }

            // v0.05 媒体源列表（SMB / WebDAV / 本地）
            composable(Screen.Sources.route) {
                com.ember.presentation.ui.sources.SourcesScreen(navController = navController)
            }

            // 添加服务器
            composable(
                Screen.AddServer.route,
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { entry ->
                val type = entry.arguments?.getString("type") ?: "webdav"
                com.ember.presentation.ui.sources.AddServerScreen(
                    type = type,
                    navController = navController
                )
            }

            // 文件浏览器
            composable(
                Screen.FileBrowser.route,
                arguments = listOf(
                    navArgument("serverId") { type = NavType.StringType },
                    navArgument("serverName") { type = NavType.StringType }
                )
            ) { entry ->
                val serverId = entry.arguments?.getString("serverId") ?: ""
                val serverName = entry.arguments?.getString("serverName") ?: ""
                com.ember.presentation.ui.sources.FileBrowserScreen(
                    serverId = serverId,
                    serverName = serverName,
                    navController = navController
                )
            }
        }
    }
}
