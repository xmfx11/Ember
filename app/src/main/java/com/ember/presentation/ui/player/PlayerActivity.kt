package com.ember.presentation.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.ember.player.data.SmbDataSourceFactory
import com.ember.data.local.AppPrefs
import com.ember.player.PlayerType
import com.ember.presentation.theme.EmberTheme
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import com.ember.utils.PlayerUtils
import kotlinx.coroutines.delay
import kotlin.math.abs

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = intent.getStringExtra("item_id") ?: ""
        if (itemId.isEmpty()) {
            AppLogger.w("PlayerActivity itemId 为空，直接退出")
            finish()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (e: Exception) {
            AppLogger.e("PlayerActivity 沉浸式设置失败", e)
        }

        try {
            // 多播放器引擎：读用户选择，MPV/VLC 待集成时提示回退 ExoPlayer
            val engine = AppPrefs.getCachedEngine()
            if (engine != PlayerType.EXO) {
                android.widget.Toast.makeText(
                    this,
                    "${engine.name} 引擎待集成，本次使用 ExoPlayer 播放",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                AppLogger.w("Player engine=${engine.name} 未集成，回退 ExoPlayer")
            }
            setContent {
                EmberTheme(darkTheme = true) {
                    PlayerScreen(itemId = itemId, onBack = { finish() })
                }
            }
        } catch (e: Exception) {
            AppLogger.e("PlayerActivity setContent crash", e)
            finish()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun PlayerScreen(itemId: String, onBack: () -> Unit) {
    val viewModel: PlayerViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val ready = state as? PlayerState.Ready
    val errorMsg = (state as? PlayerState.Error)?.message

    var initError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        viewModel.loadAndPrepare(itemId)
    }

    // ===== 起播速度优化 =====
    // LoadControl: minBuffer=1s 加快起播；maxBuffer=30s 保证流畅；rebuffer=800ms 减少卡顿恢复时间
    // seekBack/Forward 增量 10s
    val exoPlayer = remember {
        try {
            val loadControl = com.google.android.exoplayer2.DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 30000, 400, 800)
                // minBuffer=1s（加快起播）, maxBuffer=30s, 播放需0.4s即可起, 重缓冲需0.8s
                .setBackBuffer(30000, true)
                .setPrioritizeTimeOverSizeThresholds(true)  // 优先时间阈值，加快起播
                .build()
            ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10_000)   // 后退 10s
                .setSeekForwardIncrementMs(10_000) // 前进 10s
                .build()
        } catch (e: Exception) {
            AppLogger.e("ExoPlayer 创建失败", e)
            initError = "播放器初始化失败：${ErrorTranslator.translate(e)}"
            null
        } catch (e: Error) {
            AppLogger.e("ExoPlayer 创建失败(Error)", e)
            initError = "播放器初始化失败：${e.javaClass.simpleName}"
            null
        }
    }

    // 播放状态
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPercentage by remember { mutableStateOf(0) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // 控制 UI 状态
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(true) }
    var showDiagnostic by remember { mutableStateOf(false) }

    // 手势控制状态
    var gestureVolume by remember { mutableStateOf(-1) }       // 手势调节后的音量（-1 表示未调节）
    var gestureBrightness by remember { mutableStateOf(-1f) }  // 手势调节后的亮度
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }  // 拖动进度预览
    var seekPreviewDelta by remember { mutableStateOf(0L) }    // 拖动偏移量（正负）
    var dragStartMs by remember { mutableLongStateOf(0L) }     // 拖动开始时的播放位置（固定基准，避免漂移）

    // 水平拖动灵敏度：满屏拖动 = SEEK_RANGE_MS（120秒），更自然
    // 不再映射整个 duration（拖满屏跳整片太激进）
    val seekRangeMs = 120_000L

    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    // 缓冲超时检测
    LaunchedEffect(isBuffering, ready?.url) {
        if (isBuffering && ready != null && playerError == null) {
            delay(30_000)
            if (isBuffering && playerError == null) {
                playerError = "缓冲超时（30秒未开始播放），请检查网络或服务器是否支持该媒体格式"
                AppLogger.w("Player buffering timeout 30s")
            }
        }
    }

    // 监听播放 URL 准备媒体源
    LaunchedEffect(ready?.url) {
        val url = ready?.url
        val player = exoPlayer
        if (!url.isNullOrEmpty() && player != null) {
            playerError = null
            isBuffering = true
            try {
                val dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory = when {
                    url.startsWith("smb://") -> {
                        AppLogger.i("Player 使用 SMB DataSource")
                        SmbDataSourceFactory(context)
                    }
                    url.startsWith("content://") || url.startsWith("file://") -> {
                        AppLogger.i("Player 使用本地 DataSource")
                        DefaultDataSource.Factory(context)
                    }
                    else -> {
                        val token = com.ember.data.local.TokenManager.getCachedToken()
                        val headers = mutableMapOf(
                            "User-Agent" to "Ember",
                            "Accept" to "*/*"
                        )
                        if (!token.isNullOrEmpty()) {
                            headers["X-Emby-Token"] = token
                            headers["X-Emby-Authorization"] =
                                "MediaBrowser Client=\"Ember\", Device=\"Android\", DeviceId=\"Ember-Android-001\", Version=\"0.02\""
                        }
                        val httpFactory = DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(15_000)
                            .setReadTimeoutMs(30_000)
                            .setUserAgent("Ember")
                            .setDefaultRequestProperties(headers)
                        DefaultDataSource.Factory(context, httpFactory)
                    }
                }
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                val mime = PlayerUtils.containerToMime(ready?.mediaSource?.Container)
                val mediaItemBuilder = MediaItem.Builder().setUri(url)
                if (mime != null) mediaItemBuilder.setMimeType(mime)
                val mediaItem = mediaItemBuilder.build()
                val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true  // 起播速度：立即准备播放
                AppLogger.i("Player prepared media url=${url.take(120)}... mime=$mime")
            } catch (e: Exception) {
                AppLogger.e("Player 准备媒体源失败", e)
                playerError = ErrorTranslator.translate(e)
            }
        }
    }

    // Player.Listener 监听状态和错误
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                try {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        val d = player.duration
                        if (d > 0) duration = d
                        playerError = null
                        AppLogger.i("Player STATE_READY duration=$duration")
                    } else if (playbackState == Player.STATE_ENDED) {
                        AppLogger.i("Player STATE_ENDED")
                    }
                } catch (e: Exception) {
                    AppLogger.e("Player onPlaybackStateChanged 异常", e)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                AppLogger.d("Player isPlaying=$playing")
            }

            override fun onPlayerError(error: PlaybackException) {
                try {
                    val msg = ErrorTranslator.translateExoError(error.errorCodeName, error.message)
                    AppLogger.e("Player onPlayerError", error)
                    playerError = msg
                } catch (e: Exception) {
                    AppLogger.e("Player onPlayerError 回调异常", e)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            try {
                player.removeListener(listener)
                player.stop()
            } catch (e: Exception) {
                AppLogger.e("Player removeListener/stop 异常", e)
            }
        }
    }

    // 轮询当前进度（250ms 提升流畅度）+ 缓冲进度
    LaunchedEffect(ready?.url) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            try {
                val d = player.duration
                if (d > 0) {
                    currentPosition = player.currentPosition.coerceIn(0, d)
                    duration = d
                }
                bufferedPercentage = player.bufferedPercentage
            } catch (e: Exception) {
            }
            delay(250)
        }
    }

    // 控制层自动隐藏
    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    // 应用手势调节的亮度
    DisposableEffect(gestureBrightness) {
        if (gestureBrightness >= 0 && activity != null) {
            try {
                val attrs = activity.window.attributes
                attrs.screenBrightness = gestureBrightness.coerceIn(0f, 1f)
                activity.window.attributes = attrs
            } catch (e: Exception) {
                AppLogger.e("设置亮度失败", e)
            }
        }
        onDispose {}
    }

    // 应用手势调节的音量
    LaunchedEffect(gestureVolume) {
        if (gestureVolume >= 0) {
            try {
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC, gestureVolume, 0
                )
            } catch (e: Exception) {
                AppLogger.e("设置音量失败", e)
            }
        }
    }

    // 释放
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer?.release()
                AppLogger.i("Player released")
            } catch (e: Exception) {
                AppLogger.e("Player release 异常", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked, exoPlayer, duration) {
                // ===== 手势控制核心 =====
                // 单击：切换控制层
                // 双击左 1/3：快退 10s
                // 双击右 1/3：快进 10s
                // 双击中间：播放/暂停
                // 水平拖动：进度拖动（显示预览）
                // 左侧垂直拖动：亮度
                // 右侧垂直拖动：音量
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val startX = first.position.x
                    val startY = first.position.y
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()

                    var totalDeltaX = 0f
                    var totalDeltaY = 0f
                    var isDragging = false
                    var lastTapTime = 0L
                    var lastTapX = 0f

                    // 等待后续事件
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break

                        // 抬起时结束拖动并执行 seek
                        if (change.changedToUp()) {
                            if (isDragging && !isLocked && exoPlayer != null && seekPreviewMs != null) {
                                exoPlayer?.seekTo(seekPreviewMs!!)
                                seekPreviewMs = null
                                seekPreviewDelta = 0L
                            }
                            isDragging = false
                            change.consume()
                            break
                        }

                        if (!change.positionChanged()) continue

                        val dx = change.position.x - startX
                        val dy = change.position.y - startY

                        // 判断是否进入拖动模式
                        if (!isDragging && (abs(dx) > 24 || abs(dy) > 24)) {
                            isDragging = true
                            if (!isLocked) {
                                // 记录拖动开始的播放位置作为固定基准，避免轮询更新导致漂移
                                dragStartMs = currentPosition
                                seekPreviewMs = currentPosition
                                seekPreviewDelta = 0L
                            }
                        }

                        if (isDragging && !isLocked && exoPlayer != null) {
                            // 水平拖动 → 进度（基于固定基准 + 120s/屏灵敏度，更自然）
                            if (abs(dx) > abs(dy)) {
                                val deltaMs = (dx / width * seekRangeMs).toLong()
                                val newPos = (dragStartMs + deltaMs).coerceIn(0, duration)
                                seekPreviewMs = newPos
                                seekPreviewDelta = newPos - dragStartMs
                            } else {
                                // 垂直拖动
                                val isLeftHalf = startX < width / 2
                                val deltaRatio = -dy / height  // 上滑增加
                                if (isLeftHalf) {
                                    // 亮度
                                    val cur = if (gestureBrightness >= 0) gestureBrightness
                                              else try {
                                                  val b = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                                  if (b < 0) 0.5f else b
                                              } catch (e: Exception) { 0.5f }
                                    gestureBrightness = (cur + deltaRatio * 1.5f).coerceIn(0f, 1f)
                                } else {
                                    // 音量
                                    val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val curVol = if (gestureVolume >= 0) gestureVolume
                                                 else audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                    val newVol = (curVol + deltaRatio * maxVol).toInt().coerceIn(0, maxVol)
                                    gestureVolume = newVol
                                }
                            }
                        }

                        change.consume()
                    }
                }
            }
            .pointerInput(isLocked, exoPlayer) {
                // 单击 / 双击检测（独立 pointerInput，避免与拖动冲突）
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && exoPlayer != null) {
                            val width = size.width.toFloat()
                            when {
                                offset.x < width / 3 -> {
                                    exoPlayer?.seekBack()
                                }
                                offset.x > width * 2 / 3 -> {
                                    exoPlayer?.seekForward()
                                }
                                else -> {
                                    if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                }
                            }
                            controlsVisible = false
                        }
                    }
                )
            }
    ) {
        // 视频画面
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    StyledPlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 缓冲圈
        if (isBuffering && playerError == null && ready != null && exoPlayer != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // 初始化错误
        if (initError != null && exoPlayer == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("播放器初始化失败", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(initError ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        // 错误层
        if (playerError != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("播放失败", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(playerError ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                playerError = null
                                exoPlayer?.prepare()
                                exoPlayer?.playWhenReady = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重试", color = Color.White)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            onClick = { showDiagnostic = !showDiagnostic }
                        ) {
                            Text(if (showDiagnostic) "隐藏详情" else "查看详情", color = Color.White, fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                    if (showDiagnostic && ready != null) {
                        Spacer(modifier = Modifier.size(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = PlayerUtils.buildDiagnosticInfo(ready.itemId, ready.mediaSource, ready.url),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 加载中
        if (state is PlayerState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("加载中...", color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        // 数据层错误
        if (errorMsg != null && playerError == null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("无法播放", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(errorMsg, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.size(16.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.loadAndPrepare(itemId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
        }

        // 手势提示：亮度/音量/进度拖动
        if (seekPreviewMs != null && !isLocked) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val sign = if (seekPreviewDelta >= 0) "+" else ""
                        Text(
                            "${sign}${PlayerUtils.msToTimeStr(seekPreviewDelta)}",
                            color = if (seekPreviewDelta >= 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            PlayerUtils.msToTimeStr(seekPreviewMs!!),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 手势提示：音量
        if (gestureVolume >= 0 && seekPreviewMs == null) {
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val pct = (gestureVolume.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
            GestureIndicator(
                icon = Icons.Default.VolumeUp,
                value = pct,
                label = "${(pct * 100).toInt()}%"
            )
        }

        // 手势提示：亮度
        if (gestureBrightness >= 0 && seekPreviewMs == null) {
            GestureIndicator(
                icon = Icons.Default.Brightness6,
                value = gestureBrightness,
                label = "${(gestureBrightness * 100).toInt()}%"
            )
        }

        // 控制层
        AnimatedVisibility(
            visible = controlsVisible && playerError == null && exoPlayer != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControls(
                title = ready?.title ?: "Ember 播放器",
                isPlaying = isPlaying,
                isLocked = isLocked,
                isLandscape = isLandscape,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPercentage = bufferedPercentage,
                seekPreviewMs = seekPreviewMs,
                playbackSpeed = playbackSpeed,
                onBack = onBack,
                onPlayPause = { if (isPlaying) exoPlayer?.pause() else exoPlayer?.play() },
                onSeekBack = { exoPlayer?.seekBack() },
                onSeekForward = { exoPlayer?.seekForward() },
                onSeekTo = { pos ->
                    exoPlayer?.seekTo(pos)
                    seekPreviewMs = null
                    seekPreviewDelta = 0L
                },
                onSeekPreviewChange = { pos ->
                    seekPreviewMs = pos
                    seekPreviewDelta = pos - currentPosition
                },
                onSeekPreviewEnd = {
                    seekPreviewMs?.let { exoPlayer?.seekTo(it) }
                    seekPreviewMs = null
                    seekPreviewDelta = 0L
                },
                onLockToggle = {
                    isLocked = !isLocked
                    if (isLocked) controlsVisible = true
                },
                onRotateToggle = {
                    isLandscape = !isLandscape
                    activity?.requestedOrientation =
                        if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                },
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    exoPlayer?.setPlaybackSpeed(speed)
                }
            )
        }

        // 锁定状态
        if (isLocked) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            onClick = {
                                isLocked = false
                                controlsVisible = true
                            }
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "解锁", tint = Color.White,
                                modifier = Modifier.padding(16.dp))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("已锁定，点击解锁", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// 音量/亮度手势指示器
@Composable
private fun GestureIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    label: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.75f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.size(8.dp))
                // 进度条
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width((120 * value.coerceIn(0f, 1f)).dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    isLocked: Boolean,
    isLandscape: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    seekPreviewMs: Long?,
    playbackSpeed: Float,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long) -> Unit,
    onSeekPreviewEnd: () -> Unit,
    onLockToggle: () -> Unit,
    onRotateToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableLongStateOf(0L) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val displayPos = when {
        sliderDragging -> sliderPos
        seekPreviewMs != null -> seekPreviewMs
        else -> currentPosition
    }
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

    Box(modifier = Modifier.fillMaxSize()) {
        // 顶部渐变条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onRotateToggle) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "旋转", tint = Color.White)
                }
                IconButton(onClick = onLockToggle) {
                    Icon(
                        if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "锁定",
                        tint = Color.White
                    )
                }
            }
        }

        // 中央播放控制
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = "后退10秒", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                shadowElevation = 8.dp,
                onClick = onPlayPause,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = "前进10秒", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        // 底部进度条 + 时间 + 倍速
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 自定义进度条（带缓冲进度 + 拖动预览）
            CustomProgressBar(
                currentPosition = displayPos,
                duration = duration,
                bufferedPercentage = bufferedPercentage,
                onSeekStart = {
                    sliderDragging = true
                    sliderPos = currentPosition
                },
                onSeekChange = { pos ->
                    sliderPos = pos
                    onSeekPreviewChange(pos)
                },
                onSeekEnd = { pos ->
                    sliderDragging = false
                    onSeekTo(pos)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 时间 + 倍速行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = PlayerUtils.msToTimeStr(displayPos),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = " / ${PlayerUtils.msToTimeStr(duration)}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                // 倍速按钮
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        onClick = { showSpeedMenu = !showSpeedMenu }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speeds.forEach { sp ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${sp}x" + if (sp == 1.0f) " (正常)" else "") },
                                onClick = {
                                    onSpeedChange(sp)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 自定义 Canvas 进度条：含缓冲进度、播放进度、拖动 thumb（带动画放大）
@Composable
private fun CustomProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit
) {
    val barHeight = 4.dp
    val thumbRadius = 6.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    val bufferedColor = Color.White.copy(alpha = 0.3f)
    val trackColor = Color.White.copy(alpha = 0.15f)

    var isDragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }

    // thumb 半径动画：拖动时放大，有平滑过渡
    val animatedThumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.5f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "thumbScale"
    )
    // 进度条高度动画：拖动时变粗，更醒目
    val animatedBarScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.75f else 1f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "barScale"
    )

    val safeDuration = duration.coerceAtLeast(1L)
    val progress = (currentPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
    val buffered = (bufferedPercentage / 100f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)  // 增大触摸区域
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    onSeekStart()
                    val width = size.width.toFloat()
                    val x = down.position.x.coerceIn(0f, width)
                    dragX = x
                    val pos = (x / width * duration).toLong().coerceIn(0, duration)
                    onSeekChange(pos)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            // 抬起
                            val newX = change.position.x.coerceIn(0f, width)
                            val newPos = (newX / width * duration).toLong().coerceIn(0, duration)
                            isDragging = false
                            onSeekEnd(newPos)
                            change.consume()
                            break
                        }
                        if (!change.positionChanged()) continue
                        val newX = change.position.x.coerceIn(0f, width)
                        dragX = newX
                        val newPos = (newX / width * duration).toLong().coerceIn(0, duration)
                        onSeekChange(newPos)
                        change.consume()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f
            val barWidthPx = barHeight.value * density * animatedBarScale
            val thumbRadiusPx = thumbRadius.value * density * animatedThumbScale

            // 轨道（背景）
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - barWidthPx / 2f),
                size = Size(canvasWidth, barWidthPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )

            // 缓冲进度
            if (buffered > 0) {
                drawRoundRect(
                    color = bufferedColor,
                    topLeft = Offset(0f, centerY - barWidthPx / 2f),
                    size = Size(canvasWidth * buffered, barWidthPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
            }

            // 播放进度
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, centerY - barWidthPx / 2f),
                size = Size(canvasWidth * progress, barWidthPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )

            // thumb（拖动时显示，带光晕）
            if (isDragging || progress > 0f) {
                // 外圈光晕（拖动时）
                if (isDragging) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.25f),
                        radius = thumbRadiusPx * 2f,
                        center = Offset(canvasWidth * progress, centerY)
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = thumbRadiusPx,
                    center = Offset(canvasWidth * progress, centerY)
                )
            }
        }
    }
}
