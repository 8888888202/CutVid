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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
                FilterChip(
                    selected = selected,
                    onClick = { onSetTransition(type, clip.transitionDurationMs) },
                    label = { Text(type.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.cyanMuted,
                        selectedLabelColor = palette.playheadCyan
                    )
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Spacer(Modifier.width(12.dp))
            Slider(
                value = clip.transitionDurationMs.toFloat(),
                onValueChange = { onSetTransition(clip.transitionIn, it.toLong()) },
                valueRange = 200f..1500f,
                colors = SliderDefaults.colors(
                    thumbColor = palette.playheadCyan,
                    activeTrackColor = palette.playheadCyan
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${String.format("%.1f", clip.transitionDurationMs / 1000f)}s",
                style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp)
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

        val swatches = listOf(0xFFFFFFFFL, 0xFF00E5FFL, 0xFFFFB300L, 0xFFFF4D6DL, 0xFF10B981L)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (overlay.colorArgb == argb) 2.dp else 1.dp,
                                color = if (overlay.colorArgb == argb) palette.playheadCyan else palette.hairlineBorder,
                                shape = CircleShape
                            )
                            .clickable { onUpdateTextOverlay { it.copy(colorArgb = argb) } }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Backdrop", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = overlay.hasBackground,
                    onCheckedChange = { checked -> onUpdateTextOverlay { it.copy(hasBackground = checked) } }
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimation.entries) { anim ->
                FilterChip(
                    selected = overlay.animation == anim,
                    onClick = { onUpdateTextOverlay { it.copy(animation = anim) } },
                    label = { Text(anim.label, fontSize = 11.sp) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Spacer(Modifier.width(10.dp))
            Slider(
                value = overlay.durationMs.toFloat(),
                onValueChange = { onUpdateTextOverlay { cur -> cur.copy(durationMs = it.toLong()) } },
                valueRange = 800f..12000f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = TimecodeFormatter.formatDurationShort(overlay.durationMs),
                style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 11.sp)
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
            Slider(
                value = sticker.scale,
                onValueChange = { v -> onUpdateSticker { it.copy(scale = v) } },
                valueRange = 0.5f..2.2f,
                modifier = Modifier.weight(1f).height(26.dp)
            )
            Text("${String.format("%.1f", sticker.scale)}x", style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(58.dp))
            Slider(
                value = sticker.durationMs.toFloat(),
                onValueChange = { v -> onUpdateSticker { it.copy(durationMs = v.toLong()) } },
                valueRange = 800f..12000f,
                modifier = Modifier.weight(1f).height(26.dp)
            )
            Text(TimecodeFormatter.formatDurationShort(sticker.durationMs), style = TimecodeTextStyle.copy(fontSize = 11.sp, color = palette.playheadCyan))
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
                    icon = if (audio.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
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
            Slider(
                value = audio.volume,
                onValueChange = { v -> onUpdateAudioTrack { it.copy(volume = v, isMuted = false) } },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(thumbColor = palette.audioAmber, activeTrackColor = palette.audioAmber),
                modifier = Modifier.weight(1f).height(26.dp)
            )
            Text("${(audio.volume * 100).roundToInt()}%", style = TimecodeTextStyle.copy(color = palette.audioAmber, fontSize = 11.sp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fade In", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.width(54.dp))
            Slider(
                value = audio.fadeInMs.toFloat(),
                onValueChange = { v -> onUpdateAudioTrack { it.copy(fadeInMs = v.toLong()) } },
                valueRange = 0f..3000f,
                modifier = Modifier.weight(1f).height(26.dp)
            )
            Text("${String.format("%.1f", audio.fadeInMs / 1000f)}s", style = TimecodeTextStyle.copy(color = palette.textSecondary, fontSize = 10.sp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Duck Video", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = audio.duckOriginalAudio,
                    onCheckedChange = { checked -> onUpdateAudioTrack { it.copy(duckOriginalAudio = checked) } }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loop", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = audio.isLooping,
                    onCheckedChange = { checked -> onUpdateAudioTrack { it.copy(isLooping = checked) } }
                )
            }
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AspectRatioPreset.entries) { preset ->
                val selected = preset == currentRatio
                Surface(
                    onClick = { onSelectRatio(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) palette.cyanMuted else palette.elevatedTrack,
                    modifier = Modifier
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) palette.playheadCyan else palette.hairlineBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("ratio_option_${preset.label}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(preset.label, style = MaterialTheme.typography.titleMedium, color = if (selected) palette.playheadCyan else palette.textPrimary)
                        Text(preset.subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = palette.textSecondary)
                    }
                }
            }
        }

        Text("Letterbox Backdrop", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CanvasFillStyle.entries) { style ->
                val selected = style == currentFill
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFill(style) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(style.argb))
                                .border(1.dp, palette.hairlineBorder, CircleShape)
                        )
                    },
                    label = { Text(style.label, fontSize = 11.sp) }
                )
            }
        }
    }
}
