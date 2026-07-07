package com.ember.presentation.ui.player

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.ember.data.local.AppPrefs
import com.ember.player.PlayerType
import com.ember.player.data.SmbDataSourceFactory
import com.ember.presentation.theme.EmberTheme
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator
import com.ember.utils.PlayerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
                Toast.makeText(
                    this,
                    "${engine.name} 引擎待集成，本次使用 ExoPlayer 播放",
                    Toast.LENGTH_SHORT
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

// 画面比例模式
enum class AspectMode(val label: String, val mode: Int) {
    FIT("适应", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("填充", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("缩放", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    SIXTEEN_NINE("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
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
    val exoPlayer = remember {
        try {
            val loadControl = com.google.android.exoplayer2.DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 30000, 400, 800)
                .setBackBuffer(30000, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
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
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }

    // 长按倍速状态
    var isLongPressSpeed by remember { mutableStateOf(false) }
    var speedBeforeLongPress by remember { mutableStateOf(1.0f) }

    // 睡眠定时（毫秒，0=关闭）
    var sleepTimerMs by remember { mutableLongStateOf(0L) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }

    // 截图反馈
    var screenshotMsg by remember { mutableStateOf<String?>(null) }

    // 手势控制状态
    var gestureVolume by remember { mutableStateOf(-1) }
    var gestureBrightness by remember { mutableStateOf(-1f) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var seekPreviewDelta by remember { mutableStateOf(0L) }
    var dragStartMs by remember { mutableLongStateOf(0L) }

    // 水平拖动灵敏度：满屏拖动 = 120秒
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
                                "MediaBrowser Client=\"Ember\", Device=\"Android\", DeviceId=\"Ember-Android-001\", Version=\"0.03\""
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
                player.playWhenReady = true
                AppLogger.i("Player prepared media url=${url.take(120)}... mime=$mime")
            } catch (e: Exception) {
                AppLogger.e("Player 准备媒体源失败", e)
                playerError = ErrorTranslator.translate(e)
            }
        }
    }

    // Player.Listener
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

    // 轮询进度 + 睡眠倒计时
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
            // 睡眠定时倒计时
            if (sleepTimerMs > 0) {
                sleepRemainingMs = (sleepTimerMs - System.currentTimeMillis()).coerceAtLeast(0)
                if (sleepRemainingMs == 0L) {
                    player.pause()
                    sleepTimerMs = 0L
                    AppLogger.i("Player 睡眠定时结束，已暂停")
                }
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

    // 截图反馈自动消失
    LaunchedEffect(screenshotMsg) {
        if (screenshotMsg != null) {
            delay(2000)
            screenshotMsg = null
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

    // 截图函数
    val takeScreenshot: () -> Unit = {
        try {
            val player = exoPlayer
            if (player == null) {
                screenshotMsg = "播放器未就绪"
            } else {
                // 用 PixelCopy 从 window 截取当前画面
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val bitmap = Bitmap.createBitmap(
                        activity?.window?.decorView?.width ?: 1920,
                        activity?.window?.decorView?.height ?: 1080,
                        Bitmap.Config.ARGB_8888
                    )
                    val hw = activity?.window
                    if (hw != null) {
                        android.view.PixelCopy.request(
                            hw,
                            bitmap,
                            { result ->
                                if (result == android.view.PixelCopy.SUCCESS) {
                                    saveBitmapToGallery(bitmap, context.applicationContext)
                                    screenshotMsg = "截图已保存到相册"
                                } else {
                                    screenshotMsg = "截图失败"
                                }
                            },
                            Handler(Looper.getMainLooper())
                        )
                    } else {
                        screenshotMsg = "无法获取窗口"
                    }
                } else {
                    screenshotMsg = "系统版本过低，不支持截图"
                }
            }
        } catch (e: Exception) {
            AppLogger.e("截图失败", e)
            screenshotMsg = "截图失败：${e.message}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked, exoPlayer, duration) {
                // ===== 主手势：拖动 + 长按倍速 =====
                // 水平拖动：进度（固定基准 + 120s/屏）
                // 左侧垂直拖动：亮度 / 右侧垂直拖动：音量
                // 长按不动 500ms → 临时 3x 倍速，松开恢复
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    val startX = first.position.x
                    val startY = first.position.y
                    val startTime = System.currentTimeMillis()
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()

                    var isDragging = false
                    var longPressTriggered = false

                    while (true) {
                        // 用 withTimeoutOrNull 让循环每 100ms 醒来一次，及时检测长按
                        // 手指不动时 awaitPointerEvent 会阻塞，需要超时唤醒
                        val event = withTimeoutOrNull(100L) {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        val now = System.currentTimeMillis()

                        // 无事件（超时）且未拖动 → 检查长按倍速
                        if (event == null) {
                            if (!isLocked && !longPressTriggered && !isDragging &&
                                now - startTime > 500 && exoPlayer != null) {
                                speedBeforeLongPress = playbackSpeed
                                exoPlayer?.setPlaybackSpeed(3.0f)
                                playbackSpeed = 3.0f
                                isLongPressSpeed = true
                                longPressTriggered = true
                                controlsVisible = false
                                AppLogger.i("Player 长按倍速 3x")
                            }
                            continue
                        }

                        val change = event.changes.firstOrNull() ?: break

                        if (change.changedToUp()) {
                            // 长按倍速结束 → 恢复原速
                            if (longPressTriggered && isLongPressSpeed) {
                                exoPlayer?.setPlaybackSpeed(speedBeforeLongPress)
                                playbackSpeed = speedBeforeLongPress
                                isLongPressSpeed = false
                                AppLogger.i("Player 长按倍速结束，恢复 ${speedBeforeLongPress}x")
                            }
                            // 拖动结束 → 执行 seek
                            if (isDragging && !isLocked && exoPlayer != null && seekPreviewMs != null) {
                                exoPlayer?.seekTo(seekPreviewMs!!)
                                seekPreviewMs = null
                                seekPreviewDelta = 0L
                            }
                            isDragging = false
                            longPressTriggered = false
                            change.consume()
                            break
                        }

                        if (!change.positionChanged()) continue

                        val dx = change.position.x - startX
                        val dy = change.position.y - startY

                        // 长按倍速中如果手指移动超过阈值，提前结束倍速（避免误操作）
                        if (longPressTriggered && (abs(dx) > 48 || abs(dy) > 48)) {
                            exoPlayer?.setPlaybackSpeed(speedBeforeLongPress)
                            playbackSpeed = speedBeforeLongPress
                            isLongPressSpeed = false
                            longPressTriggered = false
                        }

                        if (!isDragging && !longPressTriggered && (abs(dx) > 24 || abs(dy) > 24)) {
                            isDragging = true
                            if (!isLocked) {
                                dragStartMs = currentPosition
                                seekPreviewMs = currentPosition
                                seekPreviewDelta = 0L
                            }
                        }

                        if (isDragging && !isLocked && exoPlayer != null) {
                            if (abs(dx) > abs(dy)) {
                                val deltaMs = (dx / width * seekRangeMs).toLong()
                                val newPos = (dragStartMs + deltaMs).coerceIn(0, duration)
                                seekPreviewMs = newPos
                                seekPreviewDelta = newPos - dragStartMs
                            } else {
                                val isLeftHalf = startX < width / 2
                                val deltaRatio = -dy / height
                                if (isLeftHalf) {
                                    val cur = if (gestureBrightness >= 0) gestureBrightness
                                              else try {
                                                  val b = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                                  if (b < 0) 0.5f else b
                                              } catch (e: Exception) { 0.5f }
                                    gestureBrightness = (cur + deltaRatio * 1.5f).coerceIn(0f, 1f)
                                } else {
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
                                offset.x < width / 3 -> exoPlayer?.seekBack()
                                offset.x > width * 2 / 3 -> exoPlayer?.seekForward()
                                else -> if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
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
                        tag = "player_view"
                        player = exoPlayer
                        useController = false
                        resizeMode = aspectMode.mode
                    }
                },
                update = { it.resizeMode = aspectMode.mode },
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))) {
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

        // 长按倍速提示
        if (isLongPressSpeed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = Color(0xFFFFC107),
                            modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("3.0x 倍速播放中", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("松开恢复 ${speedBeforeLongPress}x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 进度拖动顶部大时间预览
        if (seekPreviewMs != null && !isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 110.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val sign = if (seekPreviewDelta >= 0) "+" else ""
                        Text(
                            "${sign}${PlayerUtils.msToTimeStr(seekPreviewDelta)}",
                            color = if (seekPreviewDelta >= 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            PlayerUtils.msToTimeStr(seekPreviewMs!!),
                            color = Color.White,
                            fontSize = 32.sp,
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

        // 截图反馈
        if (screenshotMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 120.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(screenshotMsg ?: "", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        // 睡眠定时剩余时间（顶部中央小气泡）
        if (sleepRemainingMs > 0) {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NightsStay, contentDescription = null, tint = Color(0xFFB39DDB), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "睡眠 ${PlayerUtils.msToTimeStr(sleepRemainingMs)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
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
                aspectMode = aspectMode,
                sleepRemainingMs = sleepRemainingMs,
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
                },
                onAspectChange = { aspectMode = it },
                onScreenshot = takeScreenshot,
                onSleepTimer = { minutes ->
                    if (minutes <= 0) {
                        sleepTimerMs = 0L
                        sleepRemainingMs = 0L
                    } else {
                        sleepTimerMs = System.currentTimeMillis() + minutes * 60_000L
                        sleepRemainingMs = minutes * 60_000L
                    }
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
            // 锁定状态下仍可单击切换锁按钮可见性
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isLocked) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    }
            )
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
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width((140 * value.coerceIn(0f, 1f)).dp)
                            .background(Color.White, RoundedCornerShape(3.dp))
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
    aspectMode: AspectMode,
    sleepRemainingMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onRotateToggle: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectChange: (AspectMode) -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: (Int) -> Unit
) {
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableLongStateOf(0L) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                // 倍速按钮（顶部快捷）
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
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speeds.forEach { sp ->
                            DropdownMenuItem(
                                text = { Text("${sp}x" + if (sp == 1.0f) " (正常)" else "") },
                                onClick = {
                                    onSpeedChange(sp)
                                    showSpeedMenu = false
                                }
                            )
                        }
                    }
                }
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
                // 更多菜单
                Box {
                    IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        // 画面比例
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                            text = { Text("画面比例: ${aspectMode.label}") },
                            onClick = { showMoreMenu = false; showAspectMenu = true }
                        )
                        // 字幕
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                            text = { Text("字幕/音轨") },
                            onClick = { showMoreMenu = false }
                        )
                        // 截图
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                            text = { Text("截图") },
                            onClick = { showMoreMenu = false; onScreenshot() }
                        )
                        // 睡眠定时
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.NightsStay, contentDescription = null) },
                            text = {
                                Text(if (sleepRemainingMs > 0)
                                    "睡眠定时: ${PlayerUtils.msToTimeStr(sleepRemainingMs)}"
                                    else "睡眠定时")
                            },
                            onClick = { showMoreMenu = false; showSleepMenu = true }
                        )
                    }
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
                // 画面比例快捷按钮
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    onClick = { showAspectMenu = !showAspectMenu }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(aspectMode.label, color = Color.White, fontSize = 12.sp)
                    }
                }
                DropdownMenu(
                    expanded = showAspectMenu,
                    onDismissRequest = { showAspectMenu = false }
                ) {
                    AspectMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text("${mode.label}" + if (mode == aspectMode) " ✓" else "") },
                            onClick = {
                                onAspectChange(mode)
                                showAspectMenu = false
                            }
                        )
                    }
                }
            }
        }
    }

    // 睡眠定时对话框
    if (showSleepMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSleepMenu = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSleepMenu = false }) {
                    Text("关闭")
                }
            },
            title = { Text("睡眠定时") },
            text = {
                Column {
                    val options = listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 45 to "45 分钟", 60 to "60 分钟", 90 to "90 分钟")
                    options.forEach { (min, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSleepTimer(min); showSleepMenu = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (min > 0 && sleepRemainingMs > 0) {
                                Text(
                                    "${PlayerUtils.msToTimeStr(sleepRemainingMs)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        )
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

    val animatedThumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbScale"
    )
    val animatedBarScale by animateFloatAsState(
        targetValue = if (isDragging) 1.75f else 1f,
        animationSpec = tween(200),
        label = "barScale"
    )

    val safeDuration = duration.coerceAtLeast(1L)
    val progress = (currentPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
    val buffered = (bufferedPercentage / 100f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    onSeekStart()
                    val width = size.width.toFloat()
                    val x = down.position.x.coerceIn(0f, width)
                    val pos = (x / width * duration).toLong().coerceIn(0, duration)
                    onSeekChange(pos)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            val newX = change.position.x.coerceIn(0f, width)
                            val newPos = (newX / width * duration).toLong().coerceIn(0, duration)
                            isDragging = false
                            onSeekEnd(newPos)
                            change.consume()
                            break
                        }
                        if (!change.positionChanged()) continue
                        val newX = change.position.x.coerceIn(0f, width)
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

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - barWidthPx / 2f),
                size = Size(canvasWidth, barWidthPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )

            if (buffered > 0) {
                drawRoundRect(
                    color = bufferedColor,
                    topLeft = Offset(0f, centerY - barWidthPx / 2f),
                    size = Size(canvasWidth * buffered, barWidthPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
            }

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, centerY - barWidthPx / 2f),
                size = Size(canvasWidth * progress, barWidthPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )

            if (isDragging || progress > 0f) {
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

// 保存 Bitmap 到相册（Pictures/Ember）
private fun saveBitmapToGallery(bitmap: Bitmap, context: android.content.Context) {
    try {
        val filename = "Ember_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ember")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            AppLogger.i("截图已保存: $filename")
        }
    } catch (e: Exception) {
        AppLogger.e("保存截图失败", e)
    }
}
