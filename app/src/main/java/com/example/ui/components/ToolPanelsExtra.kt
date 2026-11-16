package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.data.model.AspectRatioPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.BadgeStickerType
import com.example.data.model.CanvasFillStyle
import com.example.data.model.OverlayStickerItem
import com.example.data.model.TextAnimation
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoClip
import com.example.media.SampleMediaGenerator
import com.example.media.TimecodeFormatter
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import kotlin.math.roundToInt

@Composable
fun TransitionPanel(
    clip: VideoClip?,
    isFirstClip: Boolean,
    onSetTransition: (TransitionType, Long) -> Unit
) {
    val palette = LocalStudioPalette.current
    if (clip == null || isFirstClip) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = if (clip == null) "Select a clip first." else "Transitions apply between clips. Select the 2nd+ clip or tap a '+' node on the timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Transition Into '${clip.displayName}'",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TransitionType.entries) { type ->
                val selected = clip.transitionIn == type
                StudioOptionPill(
                    label = type.label,
                    selected = selected,
                    onClick = { onSetTransition(type, clip.transitionDurationMs) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(62.dp))
            StudioScrubSlider(
                value = clip.transitionDurationMs.toFloat().coerceIn(200f, 1500f),
                onValueChange = { onSetTransition(clip.transitionIn, it.toLong()) },
                valueRange = 200f..1500f,
                defaultValue = 500f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${String.format(Locale.US, "%.1f", clip.transitionDurationMs / 1000f)}s",
                style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp),
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

@Composable
fun TextOverlayPanel(
    overlay: TextOverlayItem?,
    onAddTextOverlay: () -> Unit,
    onUpdateTextOverlay: ((TextOverlayItem) -> TextOverlayItem) -> Unit,
    onDeleteTextOverlay: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddTextOverlay,
                colors = ButtonDefaults.buttonColors(containerColor = palette.gradeViolet, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_text_overlay_button")
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Title", fontSize = 12.sp)
            }

            if (overlay != null) {
                OutlinedButton(
                    onClick = onDeleteTextOverlay,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, null, tint = palette.cutCrimson, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", color = palette.cutCrimson, fontSize = 12.sp)
                }
            }
        }

        if (overlay == null) {
            Text(
                text = "Tap '+ Add Title' to place draggable animated titles on your video.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            return@Column
        }

        OutlinedTextField(
            value = overlay.text,
            onValueChange = { txt -> onUpdateTextOverlay { it.copy(text = txt) } },
            label = { Text("Title Text") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val swatches = listOf(0xFFF0F2F5L, 0xFF3A9DF6L, 0xFFDE9235L, 0xFFDC4A54L, 0xFF2FB373L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(argb))
                            .border(
                                width = if (overlay.colorArgb == argb) 2.dp else 1.dp,
                                color = if (overlay.colorArgb == argb) palette.playheadCyan else palette.hairlineBorder,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onUpdateTextOverlay { it.copy(colorArgb = argb) } }
                    )
                }
            }
            StudioTogglePill(
                label = "Backdrop",
                checked = overlay.hasBackground,
                onCheckedChange = { checked -> onUpdateTextOverlay { it.copy(hasBackground = checked) } }
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimation.entries) { anim ->
                StudioOptionPill(
                    label = anim.label,
                    selected = overlay.animation == anim,
                    onClick = { onUpdateTextOverlay { it.copy(animation = anim) } },
                    accentColor = palette.gradeViolet,
                    accentContainer = palette.gradeViolet.copy(alpha = 0.22f)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(62.dp))
            StudioScrubSlider(
                value = overlay.durationMs.toFloat().coerceIn(800f, 12000f),
                onValueChange = { onUpdateTextOverlay { cur -> cur.copy(durationMs = it.toLong()) } },
                valueRange = 800f..12000f,
                defaultValue = 3000f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = TimecodeFormatter.formatDurationShort(overlay.durationMs),
                style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp),
                modifier = Modifier.width(42.dp)
            )
        }
    }
}

@Composable
fun PipOverlayPanel(
    sticker: OverlayStickerItem?,
    onPickPipPhoto: () -> Unit,
    onAddBadgeSticker: (BadgeStickerType) -> Unit,
    onUpdateSticker: ((OverlayStickerItem) -> OverlayStickerItem) -> Unit,
    onDeleteSticker: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickPipPhoto,
                colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_pip_photo_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add Photo PiP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            BadgeStickerType.entries.filter { it != BadgeStickerType.IMAGE_PIP }.forEach { badge ->
                OutlinedButton(
                    onClick = { onAddBadgeSticker(badge) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ ${badge.label}", fontSize = 11.sp, color = Color(badge.accentArgb))
                }
            }
        }

        if (sticker == null) {
            Text(
                text = "Overlay a Photo PiP or Pro Studio Badge, drag it anywhere on the preview, and resize below.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sticker.type.label, style = MaterialTheme.typography.labelLarge, color = palette.playheadCyan)
            OutlinedButton(onClick = onDeleteSticker, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Delete, null, tint = palette.cutCrimson, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remove", color = palette.cutCrimson, fontSize = 11.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scale", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(58.dp))
            StudioScrubSlider(
                value = sticker.scale.coerceIn(0.5f, 2.2f),
                onValueChange = { v -> onUpdateSticker { it.copy(scale = v) } },
                valueRange = 0.5f..2.2f,
                defaultValue = 1.0f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${String.format(Locale.US, "%.1f", sticker.scale)}x", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan), modifier = Modifier.width(38.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(58.dp))
            StudioScrubSlider(
                value = sticker.durationMs.toFloat().coerceIn(800f, 12000f),
                onValueChange = { v -> onUpdateSticker { it.copy(durationMs = v.toLong()) } },
                valueRange = 800f..12000f,
                defaultValue = 3000f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(TimecodeFormatter.formatDurationShort(sticker.durationMs), style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan), modifier = Modifier.width(38.dp))
        }
    }
}

@Composable
fun AudioMixerPanel(
    audio: AudioTrackItem?,
    isRecordingVoiceover: Boolean,
    voiceoverElapsedMs: Long,
    voiceoverAmplitude: Float,
    onToggleVoiceover: () -> Unit,
    onPickAudioFile: () -> Unit,
    onAddOfflineAudioPreset: (SampleMediaGenerator.AudioPreset) -> Unit,
    onUpdateAudioTrack: ((AudioTrackItem) -> AudioTrackItem) -> Unit,
    onDeleteAudioTrack: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleVoiceover,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingVoiceover) palette.cutCrimson else palette.crimsonMuted,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("record_voiceover_button")
            ) {
                Icon(
                    imageVector = if (isRecordingVoiceover) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isRecordingVoiceover) "Stop (${TimecodeFormatter.formatDurationShort(voiceoverElapsedMs)})" else "Voiceover",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onPickAudioFile,
                colors = ButtonDefaults.buttonColors(containerColor = palette.audioAmber, contentColor = palette.voidBg),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("import_audio_button")
            ) {
                Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Import Audio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            SampleMediaGenerator.AudioPreset.entries.forEach { preset ->
                OutlinedButton(
                    onClick = { onAddOfflineAudioPreset(preset) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ ${preset.title}", fontSize = 11.sp, color = palette.textPrimary)
                }
            }
        }

        if (isRecordingVoiceover) {
            LinearProgressIndicator(
                progress = { voiceoverAmplitude },
                color = palette.cutCrimson,
                trackColor = palette.elevatedTrack,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            )
        }

        if (audio == null) {
            Text(
                text = "Record an offline Voiceover, import MP3/WAV/M4A music, or add built-in synth & SFX loops.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(audio.title, style = MaterialTheme.typography.labelLarge, color = palette.audioAmber)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolChipButton(
                    label = if (audio.isMuted) "Muted" else "Active",
                    icon = if (audio.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    tint = if (audio.isMuted) palette.cutCrimson else palette.audioAmber,
                    onClick = { onUpdateAudioTrack { it.copy(isMuted = !it.isMuted) } }
                )
                ToolChipButton(
                    label = "Remove",
                    icon = Icons.Default.Delete,
                    tint = palette.cutCrimson,
                    onClick = onDeleteAudioTrack
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gain", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(54.dp))
            StudioScrubSlider(
                value = audio.volume.coerceIn(0f, 2f),
                onValueChange = { v -> onUpdateAudioTrack { it.copy(volume = v, isMuted = false) } },
                valueRange = 0f..2f,
                accentColor = palette.audioAmber,
                defaultValue = 1.0f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${(audio.volume * 100).roundToInt()}%", style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 11.sp), modifier = Modifier.width(42.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fade In", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(54.dp))
            StudioScrubSlider(
                value = audio.fadeInMs.toFloat().coerceIn(0f, 3000f),
                onValueChange = { v -> onUpdateAudioTrack { it.copy(fadeInMs = v.toLong()) } },
                valueRange = 0f..3000f,
                defaultValue = 0f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${String.format(Locale.US, "%.1f", audio.fadeInMs / 1000f)}s", style = TimecodeTextStyle.copy(color = palette.textSecondary, fontSize = 10.sp), modifier = Modifier.width(42.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioTogglePill(
                label = "Auto-Duck Video",
                checked = audio.duckOriginalAudio,
                onCheckedChange = { checked -> onUpdateAudioTrack { it.copy(duckOriginalAudio = checked) } },
                activeColor = palette.audioAmber
            )
            StudioTogglePill(
                label = "Loop Track",
                checked = audio.isLooping,
                onCheckedChange = { checked -> onUpdateAudioTrack { it.copy(isLooping = checked) } },
                activeColor = palette.playheadCyan
            )
        }
    }
}

@Composable
fun CanvasRatioPanel(
    currentRatio: AspectRatioPreset,
    currentFill: CanvasFillStyle,
    onSelectRatio: (AspectRatioPreset) -> Unit,
    onSelectFill: (CanvasFillStyle) -> Unit
) {
    val palette = LocalStudioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Canvas Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AspectRatioPreset.entries) { preset ->
                val selected = preset == currentRatio
                Surface(
                    onClick = { onSelectRatio(preset) },
                    shape = RoundedCornerShape(4.dp),
                    color = if (selected) palette.cyanMuted else palette.elevatedTrack,
                    modifier = Modifier
                        .border(
                            width = if (selected) 1.25.dp else 0.75.dp,
                            color = if (selected) palette.playheadCyan else palette.hairlineBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .testTag("ratio_option_${preset.label}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AspectRatioWireframeIcon(
                            floatRatio = preset.floatRatio,
                            tint = if (selected) palette.playheadCyan else palette.textSecondary
                        )
                        Column {
                            Text(preset.label, style = MaterialTheme.typography.titleSmall, color = if (selected) palette.playheadCyan else palette.textPrimary)
                            Text(preset.subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = palette.textSecondary)
                        }
                    }
                }
            }
        }

        Text("Letterbox Backdrop", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CanvasFillStyle.entries) { style ->
                StudioOptionPill(
                    label = style.label,
                    selected = style == currentFill,
                    onClick = { onSelectFill(style) },
                    leadingDotColor = Color(style.argb)
                )
            }
        }
    }
}
