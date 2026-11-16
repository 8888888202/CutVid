package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AspectRatioPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.BadgeStickerType
import com.example.data.model.CanvasFillStyle
import com.example.data.model.ColorGradeConfig
import com.example.data.model.ColorPreset
import com.example.data.model.OverlayStickerItem
import com.example.data.model.SpeedCurvePreset
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoClip
import com.example.data.model.VideoProject
import com.example.data.model.VisualFxType
import com.example.media.SampleMediaGenerator
import com.example.media.TimecodeFormatter
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.EditorToolTab
import kotlin.math.roundToInt

@Composable
fun EditorToolDock(
    project: VideoProject,
    activeTool: EditorToolTab,
    selectedClip: VideoClip?,
    selectedOverlay: TextOverlayItem?,
    selectedSticker: OverlayStickerItem?,
    selectedAudio: AudioTrackItem?,
    histogramBins: List<Float>,
    isRecordingVoiceover: Boolean,
    voiceoverElapsedMs: Long,
    voiceoverAmplitude: Float,
    isExpandedInspector: Boolean = false,
    onSelectTool: (EditorToolTab) -> Unit,
    onSplitClick: () -> Unit,
    onFreezeFrameClick: () -> Unit,
    onDetachAudioClick: () -> Unit,
    onDuplicateClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onMoveClip: (Int) -> Unit,
    onTrimClip: (Long, Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedCurveChange: (SpeedCurvePreset) -> Unit,
    onRotateClip: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onUpdateMotion: (Float?, Float?, Float?, Float?) -> Unit,
    onUpdateFx: (VisualFxType, Float?) -> Unit,
    onVolumeChange: (Float, Boolean?) -> Unit,
    onApplyColorPreset: (ColorPreset) -> Unit,
    onUpdateColorGrade: ((ColorGradeConfig) -> ColorGradeConfig) -> Unit,
    onApplyGradeToAll: () -> Unit,
    onSetTransition: (TransitionType, Long) -> Unit,
    onAddTextOverlay: () -> Unit,
    onUpdateTextOverlay: ((TextOverlayItem) -> TextOverlayItem) -> Unit,
    onDeleteTextOverlay: () -> Unit,
    onPickPipPhoto: () -> Unit,
    onAddBadgeSticker: (BadgeStickerType) -> Unit,
    onUpdateSticker: ((OverlayStickerItem) -> OverlayStickerItem) -> Unit,
    onDeleteSticker: () -> Unit,
    onToggleVoiceover: () -> Unit,
    onPickAudioFile: () -> Unit,
    onAddOfflineAudioPreset: (SampleMediaGenerator.AudioPreset) -> Unit,
    onUpdateAudioTrack: ((AudioTrackItem) -> AudioTrackItem) -> Unit,
    onDeleteAudioTrack: () -> Unit,
    onSelectAspectRatio: (AspectRatioPreset) -> Unit,
    onSelectCanvasFill: (CanvasFillStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.studioSlate)
            .border(0.5.dp, palette.hairlineBorder)
            .then(if (isExpandedInspector) Modifier else Modifier.navigationBarsPadding())
    ) {
        val panelModifier = if (isExpandedInspector) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.panelSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(palette.panelSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        }

        Box(modifier = panelModifier) {
            when (activeTool) {
                EditorToolTab.EDIT -> EditClipPanel(
                    clip = selectedClip,
                    canMoveLeft = selectedClip != null && project.clips.indexOfFirst { it.id == selectedClip.id } > 0,
                    canMoveRight = selectedClip != null && project.clips.indexOfFirst { it.id == selectedClip.id } in 0 until (project.clips.lastIndex),
                    onSplitClick = onSplitClick,
                    onFreezeFrameClick = onFreezeFrameClick,
                    onDetachAudioClick = onDetachAudioClick,
                    onDuplicateClip = onDuplicateClip,
                    onDeleteClip = onDeleteClip,
                    onMoveClip = onMoveClip,
                    onTrimClip = onTrimClip,
                    onSpeedChange = onSpeedChange,
                    onSpeedCurveChange = onSpeedCurveChange,
                    onRotateClip = onRotateClip,
                    onFlipHorizontal = onFlipHorizontal,
                    onVolumeChange = onVolumeChange
                )

                EditorToolTab.MOTION -> MotionTransformPanel(
                    clip = selectedClip,
                    onRotateClip = onRotateClip,
                    onFlipHorizontal = onFlipHorizontal,
                    onFlipVertical = onFlipVertical,
                    onUpdateMotion = onUpdateMotion
                )

                EditorToolTab.FX -> VisualFxPanel(
                    clip = selectedClip,
                    onUpdateFx = onUpdateFx
                )

                EditorToolTab.COLOR -> ColorGradingPanel(
                    clip = selectedClip,
                    histogramBins = histogramBins,
                    onApplyPreset = onApplyColorPreset,
                    onUpdateGrade = onUpdateColorGrade,
                    onApplyToAll = onApplyGradeToAll
                )

                EditorToolTab.TRANSITION -> TransitionPanel(
                    clip = selectedClip,
                    isFirstClip = selectedClip != null && project.clips.firstOrNull()?.id == selectedClip.id,
                    onSetTransition = onSetTransition
                )

                EditorToolTab.TEXT -> TextOverlayPanel(
                    overlay = selectedOverlay,
                    onAddTextOverlay = onAddTextOverlay,
                    onUpdateTextOverlay = onUpdateTextOverlay,
                    onDeleteTextOverlay = onDeleteTextOverlay
                )

                EditorToolTab.OVERLAY -> PipOverlayPanel(
                    sticker = selectedSticker,
                    onPickPipPhoto = onPickPipPhoto,
                    onAddBadgeSticker = onAddBadgeSticker,
                    onUpdateSticker = onUpdateSticker,
                    onDeleteSticker = onDeleteSticker
                )

                EditorToolTab.AUDIO -> AudioMixerPanel(
                    audio = selectedAudio,
                    isRecordingVoiceover = isRecordingVoiceover,
                    voiceoverElapsedMs = voiceoverElapsedMs,
                    voiceoverAmplitude = voiceoverAmplitude,
                    onToggleVoiceover = onToggleVoiceover,
                    onPickAudioFile = onPickAudioFile,
                    onAddOfflineAudioPreset = onAddOfflineAudioPreset,
                    onUpdateAudioTrack = onUpdateAudioTrack,
                    onDeleteAudioTrack = onDeleteAudioTrack
                )

                EditorToolTab.CANVAS -> CanvasRatioPanel(
                    currentRatio = project.aspectRatio,
                    currentFill = project.canvasFillStyle,
                    onSelectRatio = onSelectAspectRatio,
                    onSelectFill = onSelectCanvasFill
                )
            }
        }

        // Scrollable Tool Dock Tab Strip
        val tabs = listOf(
            EditorToolTab.EDIT to Icons.Default.ContentCut,
            EditorToolTab.MOTION to Icons.Default.OpenWith,
            EditorToolTab.FX to Icons.Default.MovieFilter,
            EditorToolTab.COLOR to Icons.Default.Palette,
            EditorToolTab.TRANSITION to Icons.Default.AutoAwesome,
            EditorToolTab.TEXT to Icons.Default.Title,
            EditorToolTab.OVERLAY to Icons.Default.Layers,
            EditorToolTab.AUDIO to Icons.Default.GraphicEq,
            EditorToolTab.CANVAS to Icons.Default.AspectRatio
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.studioSlate)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (tab, icon) ->
                val selected = tab == activeTool
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) palette.cyanMuted else Color.Transparent)
                        .clickable { onSelectTool(tab) }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                        .testTag("tool_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (selected) palette.playheadCyan else palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = if (selected) palette.playheadCyan else palette.textSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EditClipPanel(
    clip: VideoClip?,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onSplitClick: () -> Unit,
    onFreezeFrameClick: () -> Unit,
    onDetachAudioClick: () -> Unit,
    onDuplicateClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onMoveClip: (Int) -> Unit,
    onTrimClip: (Long, Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedCurveChange: (SpeedCurvePreset) -> Unit,
    onRotateClip: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onVolumeChange: (Float, Boolean?) -> Unit
) {
    val palette = LocalStudioPalette.current
    if (clip == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Select or import a clip to edit trim, speed curves, freeze frames, and detached audio.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolChipButton("Split", Icons.Default.ContentCut, palette.playheadCyan, onClick = onSplitClick)
            ToolChipButton("Freeze", Icons.Default.CameraAlt, palette.audioAmber, onClick = onFreezeFrameClick)
            if (!clip.isImage) {
                ToolChipButton("Detach Audio", Icons.Default.MusicNote, palette.audioAmber, onClick = onDetachAudioClick)
            }
            ToolChipButton("Rotate 90°", Icons.Default.RotateRight, palette.textPrimary, onClick = onRotateClip)
            ToolChipButton("Mirror", Icons.Default.Flip, palette.textPrimary, onClick = onFlipHorizontal)
            ToolChipButton(
                label = if (clip.isMuted) "Unmute" else "Mute",
                icon = if (clip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                tint = if (clip.isMuted) palette.cutCrimson else palette.textPrimary,
                onClick = { onVolumeChange(clip.volume, !clip.isMuted) }
            )
            ToolChipButton("Duplicate", Icons.Default.ContentCopy, palette.textPrimary, onClick = onDuplicateClip)
            if (canMoveLeft) ToolChipButton("Move L", Icons.AutoMirrored.Filled.ArrowBack, palette.textSecondary) { onMoveClip(-1) }
            if (canMoveRight) ToolChipButton("Move R", Icons.AutoMirrored.Filled.ArrowForward, palette.textSecondary) { onMoveClip(1) }
            ToolChipButton("Delete", Icons.Default.Delete, palette.cutCrimson, onClick = onDeleteClip)
        }

        val maxDur = clip.sourceDurationMs.coerceAtLeast(1000L).toFloat()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Trim Window", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Text(
                text = "${TimecodeFormatter.formatDurationShort(clip.trimStartMs)} – ${TimecodeFormatter.formatDurationShort(clip.trimEndMs)}",
                style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp)
            )
        }
        RangeSlider(
            value = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),
            onValueChange = { range -> onTrimClip(range.start.toLong(), range.endInclusive.toLong()) },
            valueRange = 0f..maxDur,
            colors = SliderDefaults.colors(thumbColor = palette.playheadCyan, activeTrackColor = palette.playheadCyan),
            modifier = Modifier.height(26.dp)
        )

        // Speed Curves + Manual Speed
        Text("Speed Curve & Multiplier", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SpeedCurvePreset.entries) { curve ->
                FilterChip(
                    selected = clip.speedCurve == curve,
                    onClick = { onSpeedCurveChange(curve) },
                    label = { Text(curve.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.amberMuted,
                        selectedLabelColor = palette.audioAmber
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, tint = palette.audioAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Speed", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                    Text("${String.format("%.2f", clip.effectiveSpeed)}x", style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 11.sp))
                }
                Slider(
                    value = clip.speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.25f..4.0f,
                    colors = SliderDefaults.colors(thumbColor = palette.audioAmber, activeTrackColor = palette.audioAmber),
                    modifier = Modifier.height(26.dp)
                )
            }

            if (!clip.isImage) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Clip Gain", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        Text("${(clip.volume * 100).roundToInt()}%", style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp))
                    }
                    Slider(
                        value = clip.volume,
                        onValueChange = { onVolumeChange(it, false) },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = palette.playheadCyan, activeTrackColor = palette.playheadCyan),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionTransformPanel(
    clip: VideoClip?,
    onRotateClip: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onUpdateMotion: (Float?, Float?, Float?, Float?) -> Unit
) {
    val palette = LocalStudioPalette.current
    if (clip == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Select a clip to keyframe Zoom, Pan, and Ken Burns motion.", color = palette.textSecondary)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolChipButton("Rotate 90°", Icons.Default.RotateRight, palette.textPrimary, onRotateClip)
                ToolChipButton("Flip H", Icons.Default.Flip, palette.textPrimary, onFlipHorizontal)
                ToolChipButton("Flip V", Icons.Default.Flip, palette.textSecondary, onFlipVertical)
            }
            TextButton(
                onClick = { onUpdateMotion(1.0f, 0f, 0f, 1.0f) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("Reset", color = palette.textMuted, fontSize = 11.sp)
            }
        }

        MotionSlider("Zoom", clip.zoomScale, 1.0f..3.0f, "${String.format("%.2f", clip.zoomScale)}x") {
            onUpdateMotion(it, null, null, null)
        }
        MotionSlider("Pan X", clip.panX, -0.4f..0.4f, String.format("%+.2f", clip.panX)) {
            onUpdateMotion(null, it, null, null)
        }
        MotionSlider("Pan Y", clip.panY, -0.4f..0.4f, String.format("%+.2f", clip.panY)) {
            onUpdateMotion(null, null, it, null)
        }
        MotionSlider("Ken Burns", clip.kenBurnsEndScale, 0.85f..1.35f, "${String.format("%.2f", clip.kenBurnsEndScale)}x") {
            onUpdateMotion(null, null, null, it)
        }
    }
}

@Composable
private fun MotionSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatted: String,
    onChange: (Float) -> Unit
) {
    val palette = LocalStudioPalette.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(68.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = palette.playheadCyan, activeTrackColor = palette.playheadCyan),
            modifier = Modifier.weight(1f).height(26.dp)
        )
        Text(formatted, style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.playheadCyan), modifier = Modifier.width(46.dp))
    }
}

@Composable
private fun VisualFxPanel(
    clip: VideoClip?,
    onUpdateFx: (VisualFxType, Float?) -> Unit
) {
    val palette = LocalStudioPalette.current
    if (clip == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Select a clip to apply Cinema Bars, Film Grain, Scanlines, or Shake FX.", color = palette.textSecondary)
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hardware FX Rack", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VisualFxType.entries) { fx ->
                FilterChip(
                    selected = clip.fxType == fx,
                    onClick = { onUpdateFx(fx, null) },
                    label = { Text(fx.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.cyanMuted,
                        selectedLabelColor = palette.playheadCyan
                    )
                )
            }
        }
        if (clip.fxType != VisualFxType.NONE) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = clip.fxIntensity,
                    onValueChange = { onUpdateFx(clip.fxType, it) },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(clip.fxIntensity * 100).roundToInt()}%", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan))
            }
        }
    }
}

@Composable
private fun ColorGradingPanel(
    clip: VideoClip?,
    histogramBins: List<Float>,
    onApplyPreset: (ColorPreset) -> Unit,
    onUpdateGrade: ((ColorGradeConfig) -> ColorGradeConfig) -> Unit,
    onApplyToAll: () -> Unit
) {
    val palette = LocalStudioPalette.current
    if (clip == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Select a clip to apply LUT presets and color grading.", color = palette.textSecondary)
        }
        return
    }
    val grade = clip.colorGrade

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Color Lab & Luma Scope", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row {
                TextButton(onClick = { onApplyPreset(ColorPreset.NONE) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Reset", color = palette.textMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onApplyToAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Apply All", color = palette.playheadCyan, fontSize = 11.sp)
                }
            }
        }

        // Real Frame Luminance Histogram Scope
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.voidBg)
                .border(0.5.dp, palette.hairlineBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                val bins = histogramBins.ifEmpty { List(24) { 0.2f } }
                val barW = (size.width / bins.size) * 0.72f
                val step = size.width / bins.size
                bins.forEachIndexed { idx, v ->
                    val h = (size.height * v).coerceAtLeast(2f)
                    drawRoundRect(
                        color = palette.playheadCyan.copy(alpha = 0.78f),
                        topLeft = Offset(idx * step, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ColorPreset.entries) { preset ->
                FilterChip(
                    selected = grade.preset == preset,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.cyanMuted,
                        selectedLabelColor = palette.playheadCyan
                    )
                )
            }
        }

        GradeSlider("Contrast", grade.contrast, -0.5f..0.5f) { v -> onUpdateGrade { it.copy(contrast = v) } }
        GradeSlider("Saturation", grade.saturation, -1.0f..1.0f) { v -> onUpdateGrade { it.copy(saturation = v) } }
        GradeSlider("Brightness", grade.brightness, -0.4f..0.4f) { v -> onUpdateGrade { it.copy(brightness = v) } }
        GradeSlider("Warmth", grade.warmth, -0.5f..0.5f) { v -> onUpdateGrade { it.copy(warmth = v) } }
        GradeSlider("Vignette", grade.vignette, 0f..1f) { v -> onUpdateGrade { it.copy(vignette = v) } }
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalStudioPalette.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(74.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = palette.gradeViolet, activeTrackColor = palette.gradeViolet),
            modifier = Modifier.weight(1f).height(26.dp)
        )
        Text(String.format("%+.2f", value), style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.textMuted), modifier = Modifier.width(42.dp))
    }
}

@Composable
fun ToolChipButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = palette.elevatedTrack,
        modifier = Modifier.border(0.5.dp, palette.hairlineBorder, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
        }
    }
}
