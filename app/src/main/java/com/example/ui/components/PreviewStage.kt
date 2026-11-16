package com.example.ui.components

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.os.Build
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.BadgeStickerType
import com.example.data.model.OverlayStickerItem
import com.example.data.model.TextAnimation
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.model.VisualFxType
import com.example.media.Media3EffectFactory
import com.example.media.TimecodeFormatter
import com.example.media.TimelineOperations
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class StageGestureHudMode { NONE, BRIGHTNESS, VOLUME, SEEK, JUMP }

@Composable
fun PreviewStage(
    project: VideoProject,
    playheadMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean,
    monitorSpeed: Float,
    monitorVolume: Float,
    stageBrightness: Float,
    stageFillMode: Boolean,
    telemetryText: String,
    showSafeZoneGuides: Boolean,
    selectedOverlayId: String?,
    selectedStickerId: String?,
    isFullscreen: Boolean,
    onAttachTextureView: (TextureView) -> Unit,
    onDetachTextureView: (TextureView) -> Unit,
    onTogglePlay: () -> Unit,
    onJumpToStart: () -> Unit,
    onStepFrame: (Int) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onScrubTo: (Long) -> Unit,
    onCycleMonitorSpeed: () -> Unit,
    onSetMonitorVolume: (Float) -> Unit,
    onToggleMonitorMute: () -> Unit,
    onSetStageBrightness: (Float) -> Unit,
    onToggleStageFillMode: () -> Unit,
    onCaptureSnapshot: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectOverlay: (String) -> Unit,
    onDragOverlay: (String, Float, Float) -> Unit,
    onSelectSticker: (String) -> Unit,
    onDragSticker: (String, Float, Float) -> Unit,
    onAddClipsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val activeWindow = remember(project, playheadMs) {
        TimelineOperations.findWindowAt(project, playheadMs)
    }
    val activeClip = activeWindow?.clip

    val clipProgress = remember(activeWindow, playheadMs) {
        if (activeWindow == null) 0f
        else {
            val elapsed = (playheadMs - activeWindow.timelineStartMs).coerceAtLeast(0L)
            (elapsed.toFloat() / activeWindow.clip.effectiveDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        }
    }

    val transitionProgress = remember(activeWindow, playheadMs) {
        if (activeWindow == null || activeWindow.clip.transitionIn == TransitionType.NONE) {
            1f
        } else {
            val elapsedInClip = (playheadMs - activeWindow.timelineStartMs).coerceAtLeast(0L)
            val dur = activeWindow.clip.transitionDurationMs.coerceAtLeast(150L)
            (elapsedInClip.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        }
    }

    var gestureHudMode by remember { mutableStateOf(StageGestureHudMode.NONE) }
    var gestureHudLabel by remember { mutableStateOf("") }
    var gestureHudLevel by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(gestureHudMode, gestureHudLabel) {
        if (gestureHudMode != StageGestureHudMode.NONE) {
            delay(1100L)
            gestureHudMode = StageGestureHudMode.NONE
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.voidBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Broadcast Monitor Telemetry & Utility Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.studioSlate)
                .border(0.5.dp, palette.hairlineBorder)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (isPlaying) palette.readyEmerald else palette.audioAmber)
                )
                Text(
                    text = telemetryText,
                    style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Live compact VU activity meter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val activeLevel = if (isPlaying && monitorVolume > 0.05f) {
                        (0.35f + 0.55f * abs(sin(playheadMs * 0.018))).toFloat() * monitorVolume
                    } else 0.08f
                    for (bar in 0 until 5) {
                        val threshold = (bar + 1) / 5f
                        val lit = activeLevel >= threshold - 0.12f
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(8.dp)
                                .background(
                                    when {
                                        !lit -> palette.elevatedTrack
                                        bar >= 4 -> palette.cutCrimson
                                        bar >= 3 -> palette.audioAmber
                                        else -> palette.readyEmerald
                                    }
                                )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = onCycleMonitorSpeed,
                    shape = RoundedCornerShape(3.dp),
                    color = if (monitorSpeed != 1.0f) palette.amberMuted else palette.elevatedTrack,
                    modifier = Modifier
                        .border(0.5.dp, if (monitorSpeed != 1.0f) palette.audioAmber else palette.hairlineBorder, RoundedCornerShape(3.dp))
                        .testTag("monitor_speed_button")
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.2g", monitorSpeed)}x",
                        style = TimecodeTextStyle.copy(
                            color = if (monitorSpeed != 1.0f) palette.audioAmber else palette.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    onClick = onToggleStageFillMode,
                    shape = RoundedCornerShape(3.dp),
                    color = if (stageFillMode) palette.cyanMuted else palette.elevatedTrack,
                    modifier = Modifier
                        .border(0.5.dp, if (stageFillMode) palette.playheadCyan else palette.hairlineBorder, RoundedCornerShape(3.dp))
                        .testTag("stage_fit_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Toggle fit or fill",
                            tint = if (stageFillMode) palette.playheadCyan else palette.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (stageFillMode) "FILL" else "FIT",
                            style = TimecodeTextStyle.copy(
                                fontSize = 9.sp,
                                color = if (stageFillMode) palette.playheadCyan else palette.textSecondary
                            )
                        )
                    }
                }
                Surface(
                    onClick = onCaptureSnapshot,
                    shape = RoundedCornerShape(3.dp),
                    color = palette.elevatedTrack,
                    modifier = Modifier
                        .border(0.5.dp, palette.hairlineBorder, RoundedCornerShape(3.dp))
                        .testTag("stage_snapshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Save frame snapshot",
                        tint = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).size(13.dp)
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val stageRatio = project.aspectRatio.floatRatio
            val containerRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f

            val frameModifier = if (containerRatio > stageRatio) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(stageRatio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(stageRatio)
            }

            BoxWithConstraints(
                modifier = frameModifier
                    .clip(RoundedCornerShape(4.dp))
                    .clipToBounds()
                    .background(Color(project.canvasBgArgb))
                    .border(1.dp, palette.hairlineBorder, RoundedCornerShape(4.dp))
                    .pointerInput(activeClip?.id, project.totalDurationMs) {
                        detectTapGestures(
                            onTap = { if (activeClip != null) onTogglePlay() },
                            onDoubleTap = { offset ->
                                if (activeClip != null) {
                                    val isRight = offset.x > size.width / 2f
                                    val delta = if (isRight) 5000L else -5000L
                                    onSeekRelative(delta)
                                    gestureHudMode = StageGestureHudMode.JUMP
                                    gestureHudLabel = if (isRight) "+5.0s" else "-5.0s"
                                    gestureHudLevel = if (isRight) 1f else 0f
                                }
                            }
                        )
                    }
                    .pointerInput(activeClip?.id, monitorVolume, stageBrightness, playheadMs) {
                        var dragMode = StageGestureHudMode.NONE
                        var accX = 0f
                        var basePlayhead = playheadMs
                        var baseBright = stageBrightness
                        var baseVol = monitorVolume
                        detectDragGestures(
                            onDragStart = { start ->
                                accX = 0f
                                basePlayhead = playheadMs
                                baseBright = stageBrightness
                                baseVol = monitorVolume
                                dragMode = when {
                                    start.x < size.width * 0.28f -> StageGestureHudMode.BRIGHTNESS
                                    start.x > size.width * 0.72f -> StageGestureHudMode.VOLUME
                                    else -> StageGestureHudMode.SEEK
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (activeClip == null) return@detectDragGestures
                                change.consume()
                                // Re-classify horizontal swipe if user started near edge and moved horizontally
                                if (abs(dragAmount.x) > abs(dragAmount.y) * 1.4f && abs(accX) < 12f) {
                                    dragMode = StageGestureHudMode.SEEK
                                }
                                when (dragMode) {
                                    StageGestureHudMode.BRIGHTNESS -> {
                                        baseBright = (baseBright - dragAmount.y / size.height.coerceAtLeast(1)).coerceIn(0.2f, 1.0f)
                                        onSetStageBrightness(baseBright)
                                        gestureHudMode = StageGestureHudMode.BRIGHTNESS
                                        gestureHudLevel = baseBright
                                        gestureHudLabel = "LUMA ${(baseBright * 100).roundToInt()}%"
                                    }
                                    StageGestureHudMode.VOLUME -> {
                                        baseVol = (baseVol - dragAmount.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1.0f)
                                        onSetMonitorVolume(baseVol)
                                        gestureHudMode = StageGestureHudMode.VOLUME
                                        gestureHudLevel = baseVol
                                        gestureHudLabel = "MON ${(baseVol * 100).roundToInt()}%"
                                    }
                                    StageGestureHudMode.SEEK -> {
                                        accX += dragAmount.x
                                        val deltaMs = ((accX / size.width.coerceAtLeast(1)) * 12_000f).toLong()
                                        val target = (basePlayhead + deltaMs).coerceIn(0L, project.totalDurationMs.coerceAtLeast(0L))
                                        onScrubTo(target)
                                        gestureHudMode = StageGestureHudMode.SEEK
                                        gestureHudLabel = TimecodeFormatter.formatTimecode(target, project.exportSettings.fps)
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
            ) {
                val frameWpx = with(LocalDensity.current) { maxWidth.toPx() }
                val frameHpx = with(LocalDensity.current) { maxHeight.toPx() }

                val grade = activeClip?.colorGrade ?: com.example.data.model.ColorGradeConfig()
                val matrix = remember(grade) {
                    ColorMatrix(Media3EffectFactory.colorMatrixPreviewMultiplier(grade))
                }
                val animScale = when {
                    activeClip != null && activeClip.transitionIn == TransitionType.ZOOM_IN && transitionProgress < 1f ->
                        1.25f - 0.25f * transitionProgress
                    else -> 1f
                }
                val animTranslationX = when {
                    activeClip != null && activeClip.transitionIn == TransitionType.SLIDE_LEFT && transitionProgress < 1f ->
                        frameWpx * (1f - transitionProgress)
                    else -> 0f
                }
                val animAlpha = when {
                    activeClip != null && activeClip.transitionIn == TransitionType.CROSSFADE && transitionProgress < 1f ->
                        transitionProgress
                    else -> 1f
                }
                val kenScale = if (activeClip != null) {
                    activeClip.zoomScale + ((activeClip.zoomScale * activeClip.kenBurnsEndScale) - activeClip.zoomScale) * clipProgress
                } else 1f
                val fillZoom = if (stageFillMode) 1.24f else 1.0f
                val glitchShakeX = if (activeClip?.fxType == VisualFxType.GLITCH_PULSE) {
                    sin(playheadMs * 0.045).toFloat() * activeClip.fxIntensity * 18f
                } else 0f
                val glitchShakeY = if (activeClip?.fxType == VisualFxType.GLITCH_PULSE) {
                    sin(playheadMs * 0.061).toFloat() * activeClip.fxIntensity * 14f
                } else 0f

                val hardwareGradeEffect = remember(grade) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && grade.isModified) {
                        val nativeMatrix = android.graphics.ColorMatrix(Media3EffectFactory.colorMatrixPreviewMultiplier(grade))
                        AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(nativeMatrix)).asComposeRenderEffect()
                    } else null
                }

                val transformModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = activeClip?.rotationDegrees ?: 0f
                        scaleX = (if (activeClip?.flipHorizontal == true) -1f else 1f) * animScale * kenScale * fillZoom
                        scaleY = (if (activeClip?.flipVertical == true) -1f else 1f) * animScale * kenScale * fillZoom
                        translationX = animTranslationX + ((activeClip?.panX ?: 0f) * frameWpx) + glitchShakeX
                        translationY = ((activeClip?.panY ?: 0f) * frameHpx) + glitchShakeY
                        alpha = animAlpha
                    }

                val videoRenderModifier = if (hardwareGradeEffect != null) {
                    transformModifier.graphicsLayer { renderEffect = hardwareGradeEffect }
                } else {
                    transformModifier
                }

                // Persistent TextureView prevents black-frame dropouts across Video <-> Freeze Frame cuts
                var boundView: TextureView? = remember { null }
                Box(
                    modifier = videoRenderModifier.alpha(if (activeClip == null || activeClip.isImage) 0f else 1f)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                boundView = tv
                                onAttachTextureView(tv)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    DisposableEffect(Unit) {
                        onDispose { boundView?.let(onDetachTextureView) }
                    }
                }

                if (activeClip == null) {
                    EmptyStagePlaceholder(onAddClipsClick = onAddClipsClick)
                } else {
                    if (activeClip.isImage) {
                        AsyncImage(
                            model = activeClip.uriString,
                            contentDescription = activeClip.displayName,
                            contentScale = if (stageFillMode) ContentScale.Crop else ContentScale.Fit,
                            colorFilter = if (grade.isModified) ColorFilter.colorMatrix(matrix) else null,
                            modifier = transformModifier
                        )
                    } else if (grade.isModified && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        val tintColor = when {
                            grade.saturation <= -0.9f -> Color.Black.copy(alpha = 0.28f)
                            grade.warmth > 0.08f -> palette.audioAmber.copy(alpha = (grade.warmth * 0.26f).coerceIn(0f, 0.20f))
                            grade.warmth < -0.05f -> palette.playheadCyan.copy(alpha = (-grade.warmth * 0.24f).coerceIn(0f, 0.20f))
                            else -> Color.Transparent
                        }
                        if (tintColor != Color.Transparent) {
                            Box(modifier = Modifier.fillMaxSize().background(tintColor))
                        }
                    }

                    // Visual FX Overlay
                    if (activeClip.fxType != VisualFxType.NONE && activeClip.fxType != VisualFxType.GLITCH_PULSE) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val intensity = activeClip.fxIntensity
                            when (activeClip.fxType) {
                                VisualFxType.CINEMA_BARS -> {
                                    val barH = size.height * (0.09f + intensity * 0.07f)
                                    drawRect(Color.Black, Offset.Zero, Size(size.width, barH))
                                    drawRect(Color.Black, Offset(0f, size.height - barH), Size(size.width, barH))
                                }
                                VisualFxType.VINTAGE_SCAN -> {
                                    val c = Color.Black.copy(alpha = (intensity * 0.28f).coerceIn(0.05f, 0.35f))
                                    var y = 0f
                                    while (y < size.height) {
                                        drawLine(c, Offset(0f, y), Offset(size.width, y), 2f)
                                        y += 8f
                                    }
                                }
                                VisualFxType.FILM_GRAIN -> {
                                    val a = (intensity * 0.22f).coerceIn(0.04f, 0.28f)
                                    val seed = (playheadMs / 60L).toInt()
                                    for (i in 0 until 72) {
                                        val x = (((i * 73 + seed * 19) % 997) / 997f) * size.width
                                        val y = (((i * 137 + seed * 31) % 991) / 991f) * size.height
                                        drawRect(
                                            color = if (i % 2 == 0) Color.White.copy(alpha = a) else Color.Black.copy(alpha = a),
                                            topLeft = Offset(x, y),
                                            size = Size(3f, 3f)
                                        )
                                    }
                                }
                                VisualFxType.DREAM_GLOW -> {
                                    drawRect(
                                        color = Color(0xFFFFC382).copy(alpha = (intensity * 0.18f).coerceIn(0.03f, 0.22f)),
                                        topLeft = Offset.Zero,
                                        size = size
                                    )
                                }
                                else -> {}
                            }
                        }
                    }

                    // Vignette overlay
                    if (grade.vignette > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = (grade.vignette * 0.72f).coerceIn(0f, 0.8f))
                                        )
                                    )
                                )
                        )
                    }

                    // Stage Monitor Brightness Dimmer
                    if (stageBrightness < 0.99f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = (1f - stageBrightness).coerceIn(0f, 0.82f)))
                        )
                    }

                    // Transition Flash / Dip-Black overlay
                    if (transitionProgress < 1f) {
                        val transOverlayColor = when (activeClip.transitionIn) {
                            TransitionType.FLASH_WHITE -> Color.White.copy(alpha = 1f - transitionProgress)
                            TransitionType.DIP_BLACK -> Color.Black.copy(alpha = 1f - transitionProgress)
                            else -> Color.Transparent
                        }
                        if (transOverlayColor != Color.Transparent) {
                            Box(modifier = Modifier.fillMaxSize().background(transOverlayColor))
                        }
                    }

                    // Safe-Zone Rule-of-Thirds Guides
                    if (showSafeZoneGuides) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineColor = Color.White.copy(alpha = 0.14f)
                            drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1f)
                            drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1f)
                            drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1f)
                            drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1f)
                        }
                    }

                    // Interactive Draggable PiP / Badge Stickers
                    project.stickers.filter { playheadMs in it.startMs..it.endMs }.forEach { sticker ->
                        DraggableStickerNode(
                            sticker = sticker,
                            isSelected = sticker.id == selectedStickerId,
                            frameWpx = frameWpx,
                            frameHpx = frameHpx,
                            onSelect = { onSelectSticker(sticker.id) },
                            onDrag = { dx, dy ->
                                onDragSticker(
                                    sticker.id,
                                    sticker.normX + dx / frameWpx.coerceAtLeast(1f),
                                    sticker.normY + dy / frameHpx.coerceAtLeast(1f)
                                )
                            }
                        )
                    }

                    // Interactive Draggable Text Overlays
                    project.textOverlays.filter { playheadMs in it.startMs..it.endMs }.forEach { overlay ->
                        DraggableTextOverlayBadge(
                            overlay = overlay,
                            playheadMs = playheadMs,
                            isSelected = overlay.id == selectedOverlayId,
                            frameWpx = frameWpx,
                            frameHpx = frameHpx,
                            onSelect = { onSelectOverlay(overlay.id) },
                            onDrag = { dx, dy ->
                                onDragOverlay(
                                    overlay.id,
                                    overlay.normX + dx / frameWpx.coerceAtLeast(1f),
                                    overlay.normY + dy / frameHpx.coerceAtLeast(1f)
                                )
                            }
                        )
                    }

                    // Crisp Technical Gesture OSD
                    androidx.compose.animation.AnimatedVisibility(
                        visible = gestureHudMode != StageGestureHudMode.NONE,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            color = palette.voidBg.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.border(1.dp, palette.playheadCyan, RoundedCornerShape(4.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (gestureHudMode) {
                                    StageGestureHudMode.BRIGHTNESS -> Icon(Icons.Default.Brightness6, null, tint = palette.audioAmber, modifier = Modifier.size(16.dp))
                                    StageGestureHudMode.VOLUME -> Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = palette.playheadCyan, modifier = Modifier.size(16.dp))
                                    StageGestureHudMode.JUMP -> Icon(
                                        if (gestureHudLevel > 0.5f) Icons.Default.FastForward else Icons.Default.FastRewind,
                                        null,
                                        tint = palette.playheadCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> {}
                                }
                                Text(
                                    text = gestureHudLabel,
                                    style = TimecodeTextStyle.copy(color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hardware Transport Control Bar (zero-overflow responsive across 320dp+ phones)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.studioSlate)
                .border(0.5.dp, palette.hairlineBorder)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.widthIn(min = 60.dp, max = 72.dp)) {
                Text(
                    text = TimecodeFormatter.formatTimecode(playheadMs, project.exportSettings.fps),
                    style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    maxLines = 1
                )
                Text(
                    text = "/ ${TimecodeFormatter.formatTimecode(project.totalDurationMs, project.exportSettings.fps)}",
                    style = TimecodeTextStyle.copy(color = palette.textMuted, fontSize = 9.sp),
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onJumpToStart,
                    modifier = Modifier.size(40.dp).testTag("jump_start_button")
                ) {
                    Icon(Icons.Default.SkipPrevious, "Jump to start", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { onStepFrame(-1) },
                    modifier = Modifier.size(40.dp).testTag("step_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous frame", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                }
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.playheadCyan)
                        .clickable(onClick = onTogglePlay)
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = palette.voidBg,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onStepFrame(1) },
                    modifier = Modifier.size(40.dp).testTag("step_forward_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next frame", tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleMonitorMute,
                    modifier = Modifier.size(40.dp).testTag("monitor_mute_button")
                ) {
                    Icon(
                        imageVector = if (monitorVolume < 0.05f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Toggle monitor mute",
                        tint = if (monitorVolume < 0.05f) palette.cutCrimson else palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier.size(40.dp).testTag("loop_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Toggle loop playback",
                        tint = if (isLooping) palette.playheadCyan else palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(40.dp).testTag("fullscreen_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Toggle fullscreen preview",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableStickerNode(
    sticker: OverlayStickerItem,
    isSelected: Boolean,
    frameWpx: Float,
    frameHpx: Float,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val palette = LocalStudioPalette.current
    val xPx = (sticker.normX * frameWpx).roundToInt()
    val yPx = (sticker.normY * frameHpx).roundToInt()

    Box(modifier = Modifier.fillMaxSize()) {
        val baseModifier = Modifier
            .offset { IntOffset(xPx - 48, yPx - 28) }
            .graphicsLayer {
                scaleX = sticker.scale
                scaleY = sticker.scale
            }
            .then(
                if (isSelected) Modifier.border(1.5.dp, palette.playheadCyan, RoundedCornerShape(4.dp))
                else Modifier
            )
            .pointerInput(sticker.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable { onSelect() }

        if (sticker.type == BadgeStickerType.IMAGE_PIP && sticker.imageUriString != null) {
            AsyncImage(
                model = sticker.imageUriString,
                contentDescription = "Picture in picture overlay",
                contentScale = ContentScale.Crop,
                modifier = baseModifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            )
        } else {
            Surface(
                color = palette.voidBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = baseModifier
                    .border(1.dp, Color(sticker.type.accentArgb), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = sticker.type.badgeText,
                    style = TimecodeTextStyle.copy(
                        color = Color(sticker.type.accentArgb),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun DraggableTextOverlayBadge(
    overlay: TextOverlayItem,
    playheadMs: Long,
    isSelected: Boolean,
    frameWpx: Float,
    frameHpx: Float,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val palette = LocalStudioPalette.current
    val elapsed = (playheadMs - overlay.startMs).coerceAtLeast(0L)
    val enterFrac = (elapsed / 320f).coerceIn(0f, 1f)

    val displayedText = if (overlay.animation == TextAnimation.TYPEWRITER) {
        val count = (overlay.text.length * (elapsed / 700f).coerceIn(0.1f, 1f)).roundToInt()
            .coerceIn(1, overlay.text.length.coerceAtLeast(1))
        overlay.text.take(count)
    } else {
        overlay.text
    }

    val animAlpha = when (overlay.animation) {
        TextAnimation.FADE, TextAnimation.SLIDE_UP -> enterFrac
        else -> 1f
    }
    val animScale by animateFloatAsState(
        targetValue = if (overlay.animation == TextAnimation.POP && enterFrac < 1f) {
            0.7f + 0.3f * enterFrac
        } else 1f,
        label = "textPopScale"
    )
    val slideOffsetY = if (overlay.animation == TextAnimation.SLIDE_UP) {
        ((1f - enterFrac) * 28f).roundToInt()
    } else 0

    Box(modifier = Modifier.fillMaxSize()) {
        val xPx = (overlay.normX * frameWpx).roundToInt()
        val yPx = (overlay.normY * frameHpx).roundToInt() + slideOffsetY

        val boxWidthDp = with(LocalDensity.current) { (frameWpx * 0.56f).coerceAtLeast(80f).toDp() }
        Surface(
            color = if (overlay.hasBackground) Color(overlay.bgArgb) else Color.Transparent,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .offset { IntOffset(xPx - (frameWpx * 0.28f).roundToInt(), yPx - 36) }
                .width(boxWidthDp)
                .graphicsLayer {
                    alpha = animAlpha
                    scaleX = animScale
                    scaleY = animScale
                }
                .then(
                    if (isSelected) Modifier.border(1.5.dp, palette.playheadCyan, RoundedCornerShape(4.dp))
                    else Modifier
                )
                .pointerInput(overlay.id) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .clickable { onSelect() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayedText.ifBlank { "Title" },
                color = Color(overlay.colorArgb),
                fontSize = (overlay.fontSizeSp * 0.65f).sp,
                fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyStagePlaceholder(onAddClipsClick: () -> Unit) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAddClipsClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle hardware camera viewfinder corner brackets
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bracket = 16.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val c = palette.hairlineBorder
            // TL
            drawLine(c, Offset.Zero, Offset(bracket, 0f), stroke)
            drawLine(c, Offset.Zero, Offset(0f, bracket), stroke)
            // TR
            drawLine(c, Offset(size.width - bracket, 0f), Offset(size.width, 0f), stroke)
            drawLine(c, Offset(size.width, 0f), Offset(size.width, bracket), stroke)
            // BL
            drawLine(c, Offset(0f, size.height - bracket), Offset(0f, size.height), stroke)
            drawLine(c, Offset(0f, size.height), Offset(bracket, size.height), stroke)
            // BR
            drawLine(c, Offset(size.width - bracket, size.height), Offset(size.width, size.height), stroke)
            drawLine(c, Offset(size.width, size.height - bracket), Offset(size.width, size.height), stroke)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = "Add clips",
                tint = palette.playheadCyan,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("TIMELINE EMPTY", style = TimecodeTextStyle.copy(color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap stage to import clips or sample reel",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
