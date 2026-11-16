package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.data.model.clipWindows
import com.example.media.TimecodeFormatter
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

@Composable
fun MultiTrackTimeline(
    project: VideoProject,
    playheadMs: Long,
    isPlaying: Boolean,
    dpPerSec: Float,
    selectedClipId: String?,
    selectedOverlayId: String?,
    selectedStickerId: String?,
    selectedAudioId: String?,
    filmstrips: Map<String, List<Bitmap>>,
    onScrubTo: (Long, Boolean) -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectTransition: (String) -> Unit,
    onSelectOverlay: (String) -> Unit,
    onSelectSticker: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSplitClick: () -> Unit,
    onAddClipClick: () -> Unit,
    onDropBeatMarker: () -> Unit,
    onZoomChange: (Float) -> Unit,
    trackHeightDp: Dp = 176.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val pxPerSec = with(density) { dpPerSec.dp.toPx() }
    val totalSec = (project.totalDurationMs.coerceAtLeast(1000L) / 1000f)
    val contentWidthDp = (totalSec * dpPerSec).dp.coerceAtLeast(120.dp)

    LaunchedEffect(playheadMs, pxPerSec, isPlaying) {
        val targetPx = ((playheadMs / 1000f) * pxPerSec).roundToInt()
        if (!scrollState.isScrollInProgress || isPlaying) {
            if (kotlin.math.abs(scrollState.value - targetPx) > 2) {
                scrollState.scrollTo(targetPx)
            }
        }
    }

    LaunchedEffect(scrollState, pxPerSec, project.totalDurationMs) {
        snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (scrollPx, dragging) ->
                if (dragging && pxPerSec > 0f) {
                    val ms = ((scrollPx / pxPerSec) * 1000f).toLong()
                        .coerceIn(0L, project.totalDurationMs)
                    onScrubTo(ms, true)
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.studioSlate)
    ) {
        // Quick Action & Zoom Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panelSurface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onSplitClick,
                    shape = RoundedCornerShape(4.dp),
                    color = palette.elevatedTrack,
                    modifier = Modifier.border(0.5.dp, palette.hairlineBorder, RoundedCornerShape(4.dp)).testTag("timeline_split_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCut, "Split", tint = palette.playheadCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Split", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                    }
                }

                Surface(
                    onClick = onAddClipClick,
                    shape = RoundedCornerShape(4.dp),
                    color = palette.cyanMuted,
                    modifier = Modifier.border(0.5.dp, palette.playheadCyan.copy(alpha = 0.45f), RoundedCornerShape(4.dp)).testTag("timeline_add_clip_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Add clip", tint = palette.playheadCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium, color = palette.playheadCyan)
                    }
                }

                Surface(
                    onClick = onDropBeatMarker,
                    shape = RoundedCornerShape(4.dp),
                    color = palette.amberMuted,
                    modifier = Modifier.border(0.5.dp, palette.audioAmber.copy(alpha = 0.45f), RoundedCornerShape(4.dp)).testTag("timeline_beat_pin_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BookmarkAdd, "Beat marker", tint = palette.audioAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Pin", style = MaterialTheme.typography.labelMedium, color = palette.audioAmber)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(dpPerSec - 16f) },
                    modifier = Modifier.size(30.dp).testTag("timeline_zoom_out")
                ) {
                    Icon(Icons.Default.ZoomOut, "Zoom out", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${((dpPerSec / 56f) * 100).roundToInt()}%",
                    style = TimecodeTextStyle.copy(color = palette.textMuted, fontSize = 10.sp)
                )
                IconButton(
                    onClick = { onZoomChange(dpPerSec + 16f) },
                    modifier = Modifier.size(30.dp).testTag("timeline_zoom_in")
                ) {
                    Icon(Icons.Default.ZoomIn, "Zoom in", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        val isCompactTimeline = trackHeightDp < 160.dp
        val rulerH = if (isCompactTimeline) 20.dp else 22.dp
        val gap1 = if (isCompactTimeline) 3.dp else 4.dp
        val vTrackH = if (isCompactTimeline) 54.dp else 64.dp
        val gap2 = if (isCompactTimeline) 4.dp else 6.dp
        val oTrackH = if (isCompactTimeline) 28.dp else 32.dp
        val gap3 = if (isCompactTimeline) 4.dp else 6.dp
        val aTrackH = (trackHeightDp - rulerH - gap1 - vTrackH - gap2 - oTrackH - gap3).coerceAtLeast(32.dp)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeightDp)
                .background(palette.voidBg)
        ) {
            val halfViewportDp = maxWidth / 2f

            // Subtle horizontal track lane separator rules across the entire rack
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(rulerH))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(palette.hairlineBorder))
                Spacer(modifier = Modifier.height(gap1 + vTrackH + gap2 / 2))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(palette.hairlineBorder.copy(alpha = 0.55f)))
                Spacer(modifier = Modifier.height(gap2 / 2 + oTrackH + gap3 / 2))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(palette.hairlineBorder.copy(alpha = 0.55f)))
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.width(halfViewportDp))

                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TimelineRuler(
                            project = project,
                            totalDurationMs = project.totalDurationMs.coerceAtLeast(1000L),
                            dpPerSec = dpPerSec,
                            widthDp = contentWidthDp,
                            rulerHeightDp = rulerH,
                            onTapMs = { onScrubTo(it, false) }
                        )

                        Spacer(modifier = Modifier.height(gap1))

                        VideoClipTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedClipId = selectedClipId,
                            filmstrips = filmstrips,
                            trackHeightDp = vTrackH,
                            onSelectClip = onSelectClip,
                            onSelectTransition = onSelectTransition
                        )

                        Spacer(modifier = Modifier.height(gap2))

                        OverlayLayerTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedOverlayId = selectedOverlayId,
                            selectedStickerId = selectedStickerId,
                            trackHeightDp = oTrackH,
                            onSelectOverlay = onSelectOverlay,
                            onSelectSticker = onSelectSticker
                        )

                        Spacer(modifier = Modifier.height(gap3))

                        AudioWaveformTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedAudioId = selectedAudioId,
                            trackHeightDp = aTrackH,
                            onSelectAudio = onSelectAudio
                        )
                    }
                }

                Spacer(modifier = Modifier.width(halfViewportDp))
            }

            // Lane badges pinned exactly in sync with V1, O1, A1 row Y-offsets
            Box(modifier = Modifier.fillMaxSize()) {
                TrackBadge(
                    label = "V1",
                    color = palette.playheadCyan,
                    modifier = Modifier.offset(x = 6.dp, y = rulerH + gap1 + 4.dp)
                )
                TrackBadge(
                    label = "O1",
                    color = palette.gradeViolet,
                    modifier = Modifier.offset(x = 6.dp, y = rulerH + gap1 + vTrackH + gap2 + 5.dp)
                )
                TrackBadge(
                    label = "A1",
                    color = palette.audioAmber,
                    modifier = Modifier.offset(x = 6.dp, y = rulerH + gap1 + vTrackH + gap2 + oTrackH + gap3 + 6.dp)
                )
            }

            // Razor-sharp playhead reticle + pentagonal needle cap
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(14.dp)
                    .fillMaxHeight()
            ) {
                val cx = size.width / 2f
                val capPath = Path().apply {
                    moveTo(cx - 5.5.dp.toPx(), 0f)
                    lineTo(cx + 5.5.dp.toPx(), 0f)
                    lineTo(cx + 5.5.dp.toPx(), 7.dp.toPx())
                    lineTo(cx, 13.dp.toPx())
                    lineTo(cx - 5.5.dp.toPx(), 7.dp.toPx())
                    close()
                }
                drawPath(capPath, palette.playheadCyan)
                drawLine(
                    color = palette.playheadCyan,
                    start = Offset(cx, 12.dp.toPx()),
                    end = Offset(cx, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun TrackBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(palette.voidBg.copy(alpha = 0.88f))
            .border(0.5.dp, color.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = TimecodeTextStyle.copy(fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun TimelineRuler(
    project: VideoProject,
    totalDurationMs: Long,
    dpPerSec: Float,
    widthDp: Dp,
    rulerHeightDp: Dp,
    onTapMs: (Long) -> Unit
) {
    val palette = LocalStudioPalette.current
    val density = LocalDensity.current
    val pxPerSec = with(density) { dpPerSec.dp.toPx() }
    val totalSec = (totalDurationMs / 1000L).toInt().coerceAtLeast(1) + 1
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(rulerHeightDp)
            .background(palette.studioSlate)
            .pointerInput(pxPerSec, totalDurationMs) {
                detectTapGestures { offset ->
                    if (pxPerSec > 0f) {
                        val ms = ((offset.x / pxPerSec) * 1000f).toLong().coerceIn(0L, totalDurationMs)
                        onTapMs(ms)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val secPx = dpPerSec.dp.toPx()
            for (s in 0..totalSec) {
                val x = s * secPx
                drawLine(palette.textMuted, Offset(x, size.height * 0.45f), Offset(x, size.height), 1.5f)
                val halfX = x + secPx * 0.5f
                if (halfX < size.width) {
                    drawLine(palette.hairlineBorder, Offset(halfX, size.height * 0.7f), Offset(halfX, size.height), 1f)
                }
            }
        }
        for (s in 0..totalSec step if (dpPerSec < 45f) 2 else 1) {
            Text(
                text = TimecodeFormatter.formatDurationShort(s * 1000L),
                style = TimecodeTextStyle.copy(fontSize = 9.sp, color = palette.textMuted),
                modifier = Modifier.offset(x = (s * dpPerSec + 4f).dp, y = 2.dp)
            )
        }
        // Beat Marker Pins
        project.beatMarkers.forEach { marker ->
            val xDp = ((marker.timeMs / 1000f) * dpPerSec).dp
            Box(
                modifier = Modifier
                    .offset(x = xDp - 5.dp, y = 10.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(palette.audioAmber)
                    .clickable { onTapMs(marker.timeMs) }
            )
        }
    }
}

@Composable
private fun VideoClipTrack(
    project: VideoProject,
    dpPerSec: Float,
    selectedClipId: String?,
    filmstrips: Map<String, List<Bitmap>>,
    trackHeightDp: Dp,
    onSelectClip: (String) -> Unit,
    onSelectTransition: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    val windows = project.clipWindows()

    Box(modifier = Modifier.fillMaxWidth().height(trackHeightDp)) {
        windows.forEach { win ->
            val clip = win.clip
            val isSelected = clip.id == selectedClipId
            val leftDp = ((win.timelineStartMs / 1000f) * dpPerSec).dp
            val widthDp = ((clip.effectiveDurationMs / 1000f) * dpPerSec).dp.coerceAtLeast(28.dp)
            val frames = filmstrips[clip.id].orEmpty()

            Box(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.elevatedTrack)
                    .border(
                        width = if (isSelected) 1.75.dp else 0.75.dp,
                        color = if (isSelected) palette.playheadCyan else palette.hairlineBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .testTag("timeline_clip_${win.index}")
            ) {
                if (frames.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        frames.forEach { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isSelected) 0.16f else 0.36f))
                    )
                }

                if (isSelected) {
                    // Left & Right precision trim bracket handles with grip bars
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(7.dp)
                            .fillMaxHeight()
                            .background(palette.playheadCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(1.5.dp).height(16.dp).background(palette.voidBg))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(7.dp)
                            .fillMaxHeight()
                            .background(palette.playheadCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(1.5.dp).height(16.dp).background(palette.voidBg))
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = if (isSelected) 10.dp else 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = clip.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (clip.effectiveSpeed != 1.0f) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", clip.effectiveSpeed)}x",
                                style = TimecodeTextStyle.copy(fontSize = 8.sp, color = palette.audioAmber)
                            )
                        }
                        if (clip.colorGrade.isModified) {
                            Icon(Icons.Default.Tune, "Graded", tint = palette.gradeViolet, modifier = Modifier.size(10.dp))
                        }
                        if (clip.isMuted) {
                            Icon(Icons.AutoMirrored.Filled.VolumeOff, "Muted", tint = palette.cutCrimson, modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }

            if (win.index > 0) {
                val hasTransition = clip.transitionIn != TransitionType.NONE
                Box(
                    modifier = Modifier
                        .zIndex(2f)
                        .offset(x = leftDp - 10.dp, y = (trackHeightDp - 20.dp) / 2f)
                        .size(20.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (hasTransition) palette.playheadCyan else palette.panelSurface)
                        .border(1.dp, if (hasTransition) palette.voidBg else palette.hairlineBorder, RoundedCornerShape(3.dp))
                        .clickable { onSelectTransition(clip.id) }
                        .testTag("transition_node_${win.index}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasTransition) Icons.Default.SwapHoriz else Icons.Default.Add,
                        contentDescription = if (hasTransition) "Edit transition" else "Add transition",
                        tint = if (hasTransition) palette.voidBg else palette.textSecondary,
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer { rotationZ = -45f }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayLayerTrack(
    project: VideoProject,
    dpPerSec: Float,
    selectedOverlayId: String?,
    selectedStickerId: String?,
    trackHeightDp: Dp,
    onSelectOverlay: (String) -> Unit,
    onSelectSticker: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp)
            .background(palette.studioSlate.copy(alpha = 0.35f))
    ) {
        if (project.textOverlays.isEmpty() && project.stickers.isEmpty()) {
            Text(
                text = "Title & PiP Track",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = palette.textMuted.copy(alpha = 0.65f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 34.dp)
            )
        }

        project.textOverlays.forEach { overlay ->
            val leftDp = ((overlay.startMs / 1000f) * dpPerSec).dp
            val widthDp = ((overlay.durationMs / 1000f) * dpPerSec).dp.coerceAtLeast(36.dp)
            val isSelected = overlay.id == selectedOverlayId
            Row(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.gradeViolet.copy(alpha = if (isSelected) 0.45f else 0.25f))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) palette.playheadCyan else palette.gradeViolet,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectOverlay(overlay.id) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Title, null, tint = palette.textPrimary, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = overlay.text,
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        project.stickers.forEach { sticker ->
            val leftDp = ((sticker.startMs / 1000f) * dpPerSec).dp
            val widthDp = ((sticker.durationMs / 1000f) * dpPerSec).dp.coerceAtLeast(36.dp)
            val isSelected = sticker.id == selectedStickerId
            Row(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.cyanMuted.copy(alpha = if (isSelected) 0.85f else 0.55f))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) palette.playheadCyan else palette.hairlineBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectSticker(sticker.id) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Layers, null, tint = palette.playheadCyan, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = sticker.type.label,
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AudioWaveformTrack(
    project: VideoProject,
    dpPerSec: Float,
    selectedAudioId: String?,
    trackHeightDp: Dp,
    onSelectAudio: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    val totalW = ((project.totalDurationMs.coerceAtLeast(1000L) / 1000f) * dpPerSec).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeightDp)
            .background(palette.studioSlate.copy(alpha = 0.35f))
    ) {
        if (project.audioTracks.isEmpty()) {
            Text(
                text = "Music & Voiceover Track",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = palette.textMuted.copy(alpha = 0.65f)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 34.dp)
            )
        }

        project.audioTracks.forEach { track ->
            val effectiveDur = if (track.isLooping) {
                project.totalDurationMs.coerceAtLeast(track.durationMs)
            } else {
                track.durationMs
            }
            val leftDp = ((track.startMs / 1000f) * dpPerSec).dp
            val widthDp = ((effectiveDur / 1000f) * dpPerSec).dp.coerceAtLeast(48.dp).coerceAtMost(totalW)
            val isSelected = track.id == selectedAudioId
            val peaks = remember(track.id, track.waveformPeaks) {
                track.waveformPeaks.ifEmpty { List(32) { 0.4f } }
            }

            Box(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (track.isVoiceover) palette.crimsonMuted.copy(alpha = 0.55f)
                        else palette.amberMuted.copy(alpha = if (track.isMuted) 0.25f else 0.70f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) palette.playheadCyan else if (track.isVoiceover) palette.cutCrimson else palette.audioAmber.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelectAudio(track.id) }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = ((size.width / 5f).toInt()).coerceIn(12, 120)
                    val barWidth = (size.width / barCount) * 0.6f
                    val step = size.width / barCount
                    for (i in 0 until barCount) {
                        val peak = peaks[(i * peaks.size / barCount) % peaks.size]
                        val barH = (size.height * 0.76f * peak).coerceAtLeast(3f)
                        val x = i * step
                        val y = (size.height - barH) / 2f
                        drawRoundRect(
                            color = when {
                                track.isMuted -> palette.textMuted
                                track.isVoiceover -> palette.cutCrimson
                                else -> palette.audioAmber
                            },
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(1.5f, 1.5f)
                        )
                    }
                }

                Row(modifier = Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (track.isVoiceover) Icons.Default.Mic else Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
