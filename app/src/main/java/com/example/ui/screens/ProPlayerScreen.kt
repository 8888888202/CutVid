package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.media.MediaStreamSummary
import com.example.media.TimecodeFormatter
import com.example.ui.components.StudioOptionPill
import com.example.ui.components.StudioScrubSlider
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.AudioTrackOption
import com.example.ui.viewmodel.PlayerAspectMode
import com.example.ui.viewmodel.ProPlayerViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ProHudGesture { NONE, BRIGHTNESS, VOLUME, SEEK, JUMP }
private enum class ProPlayerSheet { NONE, SPEED_AUDIO, SUBTITLES, INFO_TRACKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPlayerScreen(
    initialUriString: String?,
    onNavigateBack: () -> Unit,
    onOpenInEditor: (String) -> Unit,
    viewModel: ProPlayerViewModel = viewModel()
) {
    val palette = LocalStudioPalette.current
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val audioBoostPercent by viewModel.audioBoostPercent.collectAsStateWithLifecycle()
    val aspectMode by viewModel.aspectMode.collectAsStateWithLifecycle()
    val repeatAll by viewModel.repeatAll.collectAsStateWithLifecycle()
    val abLoopStartMs by viewModel.abLoopStartMs.collectAsStateWithLifecycle()
    val abLoopEndMs by viewModel.abLoopEndMs.collectAsStateWithLifecycle()
    val streamSummary by viewModel.streamSummary.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val activeSubtitleText by viewModel.activeSubtitleText.collectAsStateWithLifecycle()
    val hasExternalSrt by viewModel.hasExternalSrt.collectAsStateWithLifecycle()
    val subtitleOffsetMs by viewModel.subtitleOffsetMs.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var isTouchLocked by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf(ProPlayerSheet.NONE) }

    var brightnessNorm by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0.05f..1f } ?: 0.72f
        )
    }
    var volumeNorm by remember {
        val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(curV.toFloat() / maxV.toFloat())
    }
    var pinchZoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    var hudGesture by remember { mutableStateOf(ProHudGesture.NONE) }
    var hudText by remember { mutableStateOf("") }
    var hudLevel by remember { mutableFloatStateOf(0f) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialUriString) {
        viewModel.initializeWithUri(initialUriString)
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(hudGesture, hudText) {
        if (hudGesture != ProHudGesture.NONE) {
            delay(1100L)
            hudGesture = ProHudGesture.NONE
        }
    }

    // Auto-hide HUD during playback after idle
    LaunchedEffect(showControls, isPlaying, isTouchLocked, activeSheet) {
        if (showControls && isPlaying && !isTouchLocked && activeSheet == ProPlayerSheet.NONE) {
            delay(4500L)
            showControls = false
        }
    }

    // Reset brightness attribute when leaving player
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { win ->
                val attrs = win.attributes
                attrs.screenBrightness = -1f
                win.attributes = attrs
            }
        }
    }

    BackHandler {
        if (isTouchLocked) {
            isTouchLocked = false
        } else {
            onNavigateBack()
        }
    }

    val mediaFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pinchZoom = 1f
            panX = 0f
            panY = 0f
            viewModel.loadPickedMediaUris(uris)
        }
    }

    val subtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadExternalSubtitleUri(it) }
    }

    val currentItem = queue.getOrNull(currentIndex)

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            val isTabletWide = maxWidth >= 760.dp
            val containerAspect = if (maxHeight > 0.dp) maxWidth / maxHeight else (16f / 9f)

            // Core Video / Audio Stage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(isTouchLocked, queue.size) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                if (isTouchLocked || queue.isEmpty()) return@detectTapGestures
                                val isRight = offset.x > size.width / 2f
                                val delta = if (isRight) 10_000L else -10_000L
                                viewModel.seekRelative(delta)
                                hudGesture = ProHudGesture.JUMP
                                hudLevel = if (isRight) 1f else 0f
                                hudText = if (isRight) "+10.0s" else "-10.0s"
                            }
                        )
                    }
                    .pointerInput(isTouchLocked) {
                        if (isTouchLocked) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (abs(zoom - 1f) > 0.015f || pinchZoom > 1.02f) {
                                pinchZoom = (pinchZoom * zoom).coerceIn(1f, 4f)
                                if (pinchZoom <= 1.02f) {
                                    pinchZoom = 1f
                                    panX = 0f
                                    panY = 0f
                                } else {
                                    panX = (panX + pan.x).coerceIn(-size.width * 0.45f, size.width * 0.45f)
                                    panY = (panY + pan.y).coerceIn(-size.height * 0.45f, size.height * 0.45f)
                                }
                            }
                        }
                    }
                    .pointerInput(isTouchLocked, positionMs, durationMs) {
                        var mode = ProHudGesture.NONE
                        var accX = 0f
                        var startPos = 0L
                        var startBright = brightnessNorm
                        var startVol = volumeNorm
                        detectDragGestures(
                            onDragStart = { start ->
                                if (isTouchLocked || pinchZoom > 1.05f) {
                                    mode = ProHudGesture.NONE
                                    return@detectDragGestures
                                }
                                accX = 0f
                                startPos = positionMs
                                startBright = brightnessNorm
                                startVol = volumeNorm
                                mode = when {
                                    start.x < size.width * 0.28f -> ProHudGesture.BRIGHTNESS
                                    start.x > size.width * 0.72f -> ProHudGesture.VOLUME
                                    else -> ProHudGesture.SEEK
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (mode == ProHudGesture.NONE || isTouchLocked || pinchZoom > 1.05f) return@detectDragGestures
                                change.consume()
                                if (abs(dragAmount.x) > abs(dragAmount.y) * 1.4f && abs(accX) < 12f) {
                                    mode = ProHudGesture.SEEK
                                }
                                when (mode) {
                                    ProHudGesture.BRIGHTNESS -> {
                                        startBright = (startBright - dragAmount.y / size.height.coerceAtLeast(1)).coerceIn(0.05f, 1.0f)
                                        brightnessNorm = startBright
                                        activity?.window?.let { win ->
                                            val attrs = win.attributes
                                            attrs.screenBrightness = startBright
                                            win.attributes = attrs
                                        }
                                        hudGesture = ProHudGesture.BRIGHTNESS
                                        hudLevel = startBright
                                        hudText = "BRIGHT ${(startBright * 100).roundToInt()}%"
                                    }
                                    ProHudGesture.VOLUME -> {
                                        startVol = (startVol - dragAmount.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        volumeNorm = startVol
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (startVol * maxVol).roundToInt().coerceIn(0, maxVol),
                                            0
                                        )
                                        hudGesture = ProHudGesture.VOLUME
                                        hudLevel = startVol
                                        hudText = "VOL ${(startVol * 100).roundToInt()}%"
                                    }
                                    ProHudGesture.SEEK -> {
                                        accX += dragAmount.x
                                        val span = durationMs.coerceAtLeast(15_000L).coerceAtMost(120_000L)
                                        val deltaMs = ((accX / size.width.coerceAtLeast(1)) * span).toLong()
                                        val target = (startPos + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
                                        viewModel.seekTo(target)
                                        hudGesture = ProHudGesture.SEEK
                                        val sign = if (deltaMs >= 0) "+" else "-"
                                        hudText = "${TimecodeFormatter.formatTimecode(target)}  ($sign${TimecodeFormatter.formatDurationShort(abs(deltaMs))})"
                                    }
                                    else -> {}
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val videoAspect = remember(streamSummary, aspectMode) {
                    val srcW = streamSummary?.width ?: 16
                    val srcH = streamSummary?.height ?: 9
                    val natural = if (srcW > 0 && srcH > 0) srcW.toFloat() / srcH.toFloat() else (16f / 9f)
                    when (aspectMode) {
                        PlayerAspectMode.BEST_FIT -> natural
                        PlayerAspectMode.FILL_CROP -> natural
                        PlayerAspectMode.RATIO_16_9 -> 16f / 9f
                        PlayerAspectMode.RATIO_4_3 -> 4f / 3f
                        PlayerAspectMode.ORIGINAL -> 1f
                    }
                }

                val isAudioOnly = streamSummary?.isAudioOnly == true
                val exactFillMul = remember(aspectMode, videoAspect, containerAspect) {
                    if (aspectMode != PlayerAspectMode.FILL_CROP) 1f
                    else {
                        val safeVid = videoAspect.coerceIn(0.3f, 3.2f)
                        val safeCon = containerAspect.coerceIn(0.3f, 3.2f)
                        maxOf(safeCon / safeVid, safeVid / safeCon).coerceIn(1f, 3.5f)
                    }
                }

                val videoBoxModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspect.coerceIn(0.3f, 3.2f))
                    .graphicsLayer {
                        scaleX = pinchZoom * exactFillMul
                        scaleY = pinchZoom * exactFillMul
                        translationX = panX
                        translationY = panY
                    }

                var boundView: TextureView? = remember { null }
                Box(modifier = videoBoxModifier.alpha(if (isAudioOnly) 0f else 1f)) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                boundView = tv
                                viewModel.attachTextureView(tv)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    DisposableEffect(Unit) {
                        onDispose { boundView?.let(viewModel::detachTextureView) }
                    }
                }

                if (isAudioOnly) {
                    AudioSpectrumConsole(
                        summary = streamSummary,
                        title = currentItem?.title ?: "Audio Stream",
                        isPlaying = isPlaying,
                        positionMs = positionMs
                    )
                }

                if (queue.isEmpty()) {
                    EmptyUniversalPlayerState(
                        onOpenFiles = { mediaFileLauncher.launch(arrayOf("video/*", "audio/*")) }
                    )
                }

                // Live Subtitles OSD
                activeSubtitleText?.let { subText ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (showControls) 160.dp else 32.dp, start = 24.dp, end = 24.dp)
                    ) {
                        Text(
                            text = subText,
                            color = Color.White,
                            fontSize = if (isTabletWide) 18.sp else 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Center Gesture HUD + Progress Bar Gauge
                AnimatedVisibility(
                    visible = hudGesture != ProHudGesture.NONE,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = palette.voidBg.copy(alpha = 0.90f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, palette.playheadCyan, RoundedCornerShape(4.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (hudGesture) {
                                    ProHudGesture.BRIGHTNESS -> Icon(Icons.Default.Brightness6, null, tint = palette.audioAmber, modifier = Modifier.size(18.dp))
                                    ProHudGesture.VOLUME -> Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = palette.playheadCyan, modifier = Modifier.size(18.dp))
                                    ProHudGesture.JUMP -> Icon(
                                        if (hudLevel > 0.5f) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        null,
                                        tint = palette.playheadCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    else -> {}
                                }
                                Text(
                                    text = hudText,
                                    style = TimecodeTextStyle.copy(color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                            if (hudGesture == ProHudGesture.BRIGHTNESS || hudGesture == ProHudGesture.VOLUME) {
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(palette.elevatedTrack)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(hudLevel.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(if (hudGesture == ProHudGesture.BRIGHTNESS) palette.audioAmber else palette.playheadCyan)
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Banner if codec unsupported
                errorBanner?.let { msg ->
                    Surface(
                        color = palette.crimsonMuted,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                            .border(1.dp, palette.cutCrimson, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Locked State Minimal Unlock Chip
            if (isTouchLocked && showControls) {
                Surface(
                    onClick = { isTouchLocked = false },
                    color = palette.voidBg.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .border(1.dp, palette.audioAmber, RoundedCornerShape(4.dp))
                        .testTag("unlock_player_button")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, "Unlock screen", tint = palette.audioAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("TAP TO UNLOCK", style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 11.sp))
                    }
                }
            }

            // Top Hardware HUD
            AnimatedVisibility(
                visible = showControls && !isTouchLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.voidBg.copy(alpha = 0.84f))
                        .border(0.5.dp, palette.hairlineBorder)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("pro_player_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.textPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentItem?.title ?: "CutForge Player",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaText = buildList {
                                streamSummary?.let { s ->
                                    if (s.width > 0 && s.height > 0) add("${s.width}x${s.height}")
                                    s.videoCodec?.let { add(it) }
                                    s.audioCodec?.let { add(it) }
                                }
                                if (audioBoostPercent > 100) add("BOOST $audioBoostPercent%")
                                if (sleepTimerMinutes > 0) add("SLEEP ${sleepTimerMinutes}m")
                            }.joinToString(" • ").ifBlank { "UNIVERSAL OFFLINE DECODER" }
                            Text(
                                text = metaText,
                                style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.playheadCyan),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { mediaFileLauncher.launch(arrayOf("video/*", "audio/*")) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("pro_player_open_file")
                        ) {
                            Icon(Icons.Default.FolderOpen, "Open local media", tint = palette.playheadCyan)
                        }
                        if (currentItem != null) {
                            OutlinedButton(
                                onClick = { viewModel.sendCurrentMediaToTimeline(onOpenInEditor) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("pro_player_edit_in_nle")
                            ) {
                                Icon(Icons.Default.ContentCut, null, tint = palette.audioAmber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", color = palette.audioAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        IconButton(
                            onClick = { activeSheet = ProPlayerSheet.INFO_TRACKS },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Info, "Stream info & tracks", tint = palette.textSecondary)
                        }
                    }
                }
            }

            // Bottom Hardware Transport & VLC Controls HUD
            AnimatedVisibility(
                visible = showControls && !isTouchLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.voidBg.copy(alpha = 0.88f))
                        .border(0.5.dp, palette.hairlineBorder)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Precision Scrub Bar + A-B Loop markers
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = TimecodeFormatter.formatTimecode(positionMs),
                            style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan),
                            modifier = Modifier.width(64.dp)
                        )
                        val durFloat = durationMs.coerceAtLeast(1000L).toFloat()
                        val markerANorm = abLoopStartMs?.let { (it / durFloat).coerceIn(0f, 1f) }
                        val markerBNorm = abLoopEndMs?.let { (it / durFloat).coerceIn(0f, 1f) }
                        StudioScrubSlider(
                            value = positionMs.toFloat().coerceIn(0f, durFloat),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..durFloat,
                            accentColor = palette.playheadCyan,
                            height = 28.dp,
                            markerNormA = markerANorm,
                            markerNormB = markerBNorm,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = TimecodeFormatter.formatTimecode(durationMs),
                            style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.textMuted),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(64.dp)
                        )
                    }

                    // Core Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { isTouchLocked = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("lock_player_button")
                        ) {
                            Icon(Icons.Default.LockOpen, "Lock touch", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = viewModel::playPrevious,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, "Previous", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { viewModel.stepFrame(-1) },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Prev frame", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 38.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.playheadCyan)
                                    .clickable(onClick = viewModel::togglePlayPause)
                                    .testTag("pro_player_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = palette.voidBg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.stepFrame(1) },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next frame", tint = palette.textSecondary, modifier = Modifier.size(22.dp))
                            }
                            IconButton(
                                onClick = viewModel::playNext,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, "Next", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                            }
                        }

                        IconButton(
                            onClick = viewModel::cycleAspectMode,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("pro_player_aspect_button")
                        ) {
                            Icon(Icons.Default.AspectRatio, "Aspect mode", tint = palette.playheadCyan, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Pro Utility Console Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlayerConsoleKey(
                            label = "${playbackSpeed}x",
                            icon = Icons.Default.Speed,
                            active = playbackSpeed != 1.0f || audioBoostPercent > 100,
                            onClick = { activeSheet = ProPlayerSheet.SPEED_AUDIO }
                        )
                        PlayerConsoleKey(
                            label = if (audioBoostPercent > 100) "VOL $audioBoostPercent%" else "Boost",
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            active = audioBoostPercent > 100,
                            onClick = { activeSheet = ProPlayerSheet.SPEED_AUDIO }
                        )
                        PlayerConsoleKey(
                            label = if (hasExternalSrt) "SRT ON" else "Subs",
                            icon = Icons.Default.ClosedCaption,
                            active = hasExternalSrt,
                            onClick = { activeSheet = ProPlayerSheet.SUBTITLES }
                        )
                        val abLabel = when {
                            abLoopStartMs != null && abLoopEndMs != null -> "A↔B ON"
                            abLoopStartMs != null -> "A SET"
                            else -> "A-B"
                        }
                        PlayerConsoleKey(
                            label = abLabel,
                            icon = Icons.Default.Repeat,
                            active = abLoopStartMs != null,
                            onClick = viewModel::toggleAbLoopPoint
                        )
                        PlayerConsoleKey(
                            label = aspectMode.label,
                            icon = Icons.Default.AspectRatio,
                            active = aspectMode != PlayerAspectMode.BEST_FIT,
                            onClick = viewModel::cycleAspectMode
                        )
                        PlayerConsoleKey(
                            label = "Snap",
                            icon = Icons.Default.CameraAlt,
                            active = false,
                            onClick = viewModel::captureSnapshot
                        )
                        PlayerConsoleKey(
                            label = if (repeatAll) "Loop All" else "Once",
                            icon = Icons.Default.Repeat,
                            active = repeatAll,
                            onClick = viewModel::toggleRepeatAll
                        )
                    }
                }
            }
        }
    }

    // Speed, 200% Audio Boost & Sleep Timer Bottom Sheet
    if (activeSheet == ProPlayerSheet.SPEED_AUDIO) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ProPlayerSheet.NONE },
            containerColor = palette.studioSlate,
            contentColor = palette.textPrimary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Playback Speed & Audio Boost", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { spd ->
                        StudioOptionPill(
                            label = "${spd}x",
                            selected = abs(playbackSpeed - spd) < 0.02f,
                            onClick = { viewModel.setSpeed(spd) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Software Preamp Boost", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Text("$audioBoostPercent%", style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 12.sp))
                }
                StudioScrubSlider(
                    value = audioBoostPercent.toFloat().coerceIn(100f, 200f),
                    onValueChange = { viewModel.setAudioBoostPercent(it.roundToInt()) },
                    valueRange = 100f..200f,
                    accentColor = palette.audioAmber,
                    defaultValue = 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Sleep Timer", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (mins, label) ->
                        StudioOptionPill(
                            label = label,
                            selected = sleepTimerMinutes == mins,
                            onClick = { viewModel.setSleepTimerMinutes(mins) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Subtitles Bottom Sheet
    if (activeSheet == ProPlayerSheet.SUBTITLES) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ProPlayerSheet.NONE },
            containerColor = palette.studioSlate,
            contentColor = palette.textPrimary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Offline Subtitles (.SRT / .VTT)", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            subtitleLauncher.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Load Subtitle File", fontWeight = FontWeight.Bold)
                    }
                    if (hasExternalSrt) {
                        OutlinedButton(onClick = viewModel::clearExternalSubtitles, shape = RoundedCornerShape(4.dp)) {
                            Text("Clear SRT", color = palette.cutCrimson)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync Offset", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.adjustSubtitleOffset(-250L) }, shape = RoundedCornerShape(4.dp)) { Text("-0.25s") }
                        Text("${String.format(Locale.US, "%+.2f", subtitleOffsetMs / 1000f)}s", style = TimecodeTextStyle.copy(color = palette.playheadCyan))
                        OutlinedButton(onClick = { viewModel.adjustSubtitleOffset(250L) }, shape = RoundedCornerShape(4.dp)) { Text("+0.25s") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Stream Telemetry & Audio Track Switcher Dialog
    if (activeSheet == ProPlayerSheet.INFO_TRACKS) {
        StreamTelemetryDialog(
            summary = streamSummary,
            audioTracks = audioTracks,
            onSelectAudioTrack = viewModel::selectAudioTrack,
            onDismiss = { activeSheet = ProPlayerSheet.NONE }
        )
    }
}

@Composable
private fun PlayerConsoleKey(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (active) palette.cyanMuted else palette.elevatedTrack,
        modifier = Modifier.border(
            0.5.dp,
            if (active) palette.playheadCyan else palette.hairlineBorder,
            RoundedCornerShape(4.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (active) palette.playheadCyan else palette.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = TimecodeTextStyle.copy(
                    color = if (active) palette.playheadCyan else palette.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun AudioSpectrumConsole(
    summary: MediaStreamSummary?,
    title: String,
    isPlaying: Boolean,
    positionMs: Long
) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth(0.84f)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.studioSlate)
            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.GraphicEq, null, tint = palette.audioAmber, modifier = Modifier.size(34.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(
                summary?.audioCodec,
                summary?.sampleRateHz?.takeIf { it > 0 }?.let { "${it / 1000} kHz" },
                summary?.channels?.takeIf { it > 0 }?.let { "${it}CH" }
            ).joinToString(" • ").ifBlank { "AUDIO STREAM" },
            style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 11.sp)
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val bars = 28
            val step = size.width / bars
            val barW = step * 0.65f
            for (i in 0 until bars) {
                val energy = if (isPlaying) {
                    (0.22f + 0.72f * abs(sin(positionMs * 0.012 + i * 0.45))).toFloat()
                } else {
                    (0.16f + 0.14f * abs(sin(i * 0.5))).toFloat()
                }
                val h = size.height * energy.coerceIn(0.1f, 1f)
                drawRoundRect(
                    color = if (i % 5 == 0) palette.audioAmber else palette.playheadCyan,
                    topLeft = Offset(i * step, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

@Composable
private fun EmptyUniversalPlayerState(onOpenFiles: () -> Unit) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.studioSlate)
            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("NO STREAM LOADED", style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontWeight = FontWeight.Bold))
        Text(
            text = "Open any local MP4, MKV, WebM, MOV, MP3, FLAC, or WAV file for hardware-accelerated playback.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onOpenFiles,
            colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open Local Media", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StreamTelemetryDialog(
    summary: MediaStreamSummary?,
    audioTracks: List<AudioTrackOption>,
    onSelectAudioTrack: (AudioTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.studioSlate,
        title = { Text("Stream Telemetry & Audio", color = palette.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (summary != null) {
                    Text("FILE: ${summary.displayName}", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.textPrimary))
                    Text("VIDEO: ${summary.videoCodec ?: "None"} (${summary.width}x${summary.height})", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan))
                    Text("AUDIO: ${summary.audioCodec ?: "None"} • ${summary.sampleRateHz}Hz • ${summary.channels}ch", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.audioAmber))
                    if (summary.bitrateBps > 0) {
                        Text("BITRATE: ${summary.bitrateBps / 1000} kbps", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.textSecondary))
                    }
                }
                if (audioTracks.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Audio Streams", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    audioTracks.forEach { track ->
                        StudioOptionPill(
                            label = track.label,
                            selected = track.selected,
                            onClick = { onSelectAudioTrack(track) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = palette.playheadCyan) }
        }
    )
}
