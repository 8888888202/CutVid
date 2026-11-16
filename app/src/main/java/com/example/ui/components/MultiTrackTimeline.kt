package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
                    shape = RoundedCornerShape(8.dp),
                    color = palette.elevatedTrack,
                    modifier = Modifier.testTag("timeline_split_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCut, "Split", tint = palette.playheadCyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Split", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                    }
                }

                Surface(
                    onClick = onAddClipClick,
                    shape = RoundedCornerShape(8.dp),
                    color = palette.cyanMuted,
                    modifier = Modifier.testTag("timeline_add_clip_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Add clip", tint = palette.playheadCyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Clip", style = MaterialTheme.typography.labelMedium, color = palette.playheadCyan)
                    }
                }

                Surface(
                    onClick = onDropBeatMarker,
                    shape = RoundedCornerShape(8.dp),
                    color = palette.amberMuted,
                    modifier = Modifier.testTag("timeline_beat_pin_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BookmarkAdd, "Beat marker", tint = palette.audioAmber, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Beat Pin", style = MaterialTheme.typography.labelMedium, color = palette.audioAmber)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onZoomChange(dpPerSec - 16f) },
                    modifier = Modifier.size(32.dp).testTag("timeline_zoom_out")
                ) {
                    Icon(Icons.Default.ZoomOut, "Zoom out", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = "${((dpPerSec / 56f) * 100).roundToInt()}%",
                    style = TimecodeTextStyle.copy(color = palette.textMuted, fontSize = 10.sp)
                )
                IconButton(
                    onClick = { onZoomChange(dpPerSec + 16f) },
                    modifier = Modifier.size(32.dp).testTag("timeline_zoom_in")
                ) {
                    Icon(Icons.Default.ZoomIn, "Zoom in", tint = palette.textSecondary, modifier = Modifier.size(17.dp))
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .background(palette.voidBg)
        ) {
            val halfViewportDp = maxWidth / 2f

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
                            onTapMs = { onScrubTo(it, false) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        VideoClipTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedClipId = selectedClipId,
                            filmstrips = filmstrips,
                            onSelectClip = onSelectClip,
                            onSelectTransition = onSelectTransition
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OverlayLayerTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedOverlayId = selectedOverlayId,
                            selectedStickerId = selectedStickerId,
                            onSelectOverlay = onSelectOverlay,
                            onSelectSticker = onSelectSticker
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        AudioWaveformTrack(
                            project = project,
                            dpPerSec = dpPerSec,
                            selectedAudioId = selectedAudioId,
                            onSelectAudio = onSelectAudio
                        )
                    }
                }

                Spacer(modifier = Modifier.width(halfViewportDp))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 6.dp, top = 26.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                TrackBadge("V1", palette.playheadCyan)
                TrackBadge("O1", palette.gradeViolet)
                TrackBadge("A1", palette.audioAmber)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(palette.playheadCyan)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 12.dp, height = 14.dp)
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    .background(palette.playheadCyan)
            )
        }
    }
}

@Composable
private fun TrackBadge(label: String, color: Color) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.studioSlate.copy(alpha = 0.85f))
            .border(0.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            style = TimecodeTextStyle.copy(fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun TimelineRuler(
    project: VideoProject,
    totalDurationMs: Long,
    dpPerSec: Float,
    widthDp: Dp,
    onTapMs: (Long) -> Unit
) {
    val palette = LocalStudioPalette.current
    val totalSec = (totalDurationMs / 1000L).toInt().coerceAtLeast(1) + 1
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(22.dp)
            .background(palette.studioSlate)
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
    onSelectClip: (String) -> Unit,
    onSelectTransition: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    val windows = project.clipWindows()

    Box(modifier = Modifier.fillMaxWidth().height(58.dp)) {
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.elevatedTrack)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) palette.playheadCyan else palette.hairlineBorder,
                        shape = RoundedCornerShape(8.dp)
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
                            .background(Color.Black.copy(alpha = if (isSelected) 0.18f else 0.34f))
                    )
                }

                if (isSelected) {
                    Box(Modifier.align(Alignment.CenterStart).width(7.dp).fillMaxHeight().background(palette.playheadCyan))
                    Box(Modifier.align(Alignment.CenterEnd).width(7.dp).fillMaxHeight().background(palette.playheadCyan))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp),
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
                                text = "${String.format("%.1f", clip.effectiveSpeed)}x",
                                style = TimecodeTextStyle.copy(fontSize = 9.sp, color = palette.audioAmber)
                            )
                        }
                        if (clip.colorGrade.isModified) {
                            Icon(Icons.Default.AutoFixHigh, "Graded", tint = palette.gradeViolet, modifier = Modifier.size(11.dp))
                        }
                        if (clip.isMuted) {
                            Icon(Icons.Default.VolumeOff, "Muted", tint = palette.cutCrimson, modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }

            if (win.index > 0) {
                val hasTransition = clip.transitionIn != TransitionType.NONE
                Box(
                    modifier = Modifier
                        .offset(x = leftDp - 12.dp, y = 17.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (hasTransition) palette.playheadCyan else palette.panelSurface)
                        .border(1.dp, palette.hairlineBorder, CircleShape)
                        .clickable { onSelectTransition(clip.id) }
                        .testTag("transition_node_${win.index}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasTransition) "✦" else "+",
                        fontSize = 11.sp,
                        color = if (hasTransition) palette.voidBg else palette.textSecondary,
                        fontWeight = FontWeight.Bold
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
    onSelectOverlay: (String) -> Unit,
    onSelectSticker: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(palette.studioSlate.copy(alpha = 0.45f))
    ) {
        project.textOverlays.forEach { overlay ->
            val leftDp = ((overlay.startMs / 1000f) * dpPerSec).dp
            val widthDp = ((overlay.durationMs / 1000f) * dpPerSec).dp.coerceAtLeast(36.dp)
            val isSelected = overlay.id == selectedOverlayId
            Row(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.gradeViolet.copy(alpha = if (isSelected) 0.48f else 0.28f))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.playheadCyan else palette.gradeViolet,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectOverlay(overlay.id) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Title, null, tint = palette.textPrimary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = overlay.text,
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 10.sp),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.cyanMuted.copy(alpha = if (isSelected) 0.85f else 0.55f))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.playheadCyan else palette.hairlineBorder,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectSticker(sticker.id) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Layers, null, tint = palette.playheadCyan, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text = sticker.type.label,
                    style = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary, fontSize = 10.sp),
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
    onSelectAudio: (String) -> Unit
) {
    val palette = LocalStudioPalette.current
    val totalW = ((project.totalDurationMs.coerceAtLeast(1000L) / 1000f) * dpPerSec).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(palette.studioSlate.copy(alpha = 0.45f))
    ) {
        project.audioTracks.forEach { track ->
            val effectiveDur = if (track.isLooping) {
                project.totalDurationMs.coerceAtLeast(track.durationMs)
            } else {
                track.durationMs
            }
            val leftDp = ((track.startMs / 1000f) * dpPerSec).dp
            val widthDp = ((effectiveDur / 1000f) * dpPerSec).dp.coerceAtLeast(48.dp).coerceAtMost(totalW)
            val isSelected = track.id == selectedAudioId

            Box(
                modifier = Modifier
                    .offset(x = leftDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (track.isVoiceover) palette.crimsonMuted.copy(alpha = 0.55f)
                        else palette.amberMuted.copy(alpha = if (track.isMuted) 0.25f else 0.72f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.playheadCyan else if (track.isVoiceover) palette.cutCrimson else palette.audioAmber.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectAudio(track.id) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                val peaks = track.waveformPeaks.ifEmpty { List(32) { 0.4f } }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = ((size.width / 5f).toInt()).coerceIn(12, 160)
                    val barWidth = (size.width / barCount) * 0.6f
                    val step = size.width / barCount
                    for (i in 0 until barCount) {
                        val peak = peaks[(i * peaks.size / barCount) % peaks.size]
                        val barH = (size.height * 0.78f * peak).coerceAtLeast(4f)
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
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }

                Row(modifier = Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (track.isVoiceover) Icons.Default.Mic else Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = palette.textPrimary,
                        modifier = Modifier.size(12.dp)
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
