package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BitrateTier
import com.example.data.model.ExportCodec
import com.example.data.model.ExportResolution
import com.example.data.model.VideoProject
import com.example.media.ExportUiState
import com.example.media.TimecodeFormatter
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    project: VideoProject,
    exportState: ExportUiState,
    onUpdateSettings: (ExportResolution?, Int?, ExportCodec?, BitrateTier?) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportUiState.Exporting) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = palette.studioSlate,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware MP4 Export",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Media3 Hardware Encoder • Local Storage",
                        style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.readyEmerald)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = palette.panelSurface
                ) {
                    Text(
                        text = project.aspectRatio.label,
                        style = TimecodeTextStyle.copy(color = palette.playheadCyan),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            when (exportState) {
                is ExportUiState.Idle -> {
                    Text("Resolution", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.forEach { res ->
                            FilterChip(
                                selected = project.exportSettings.resolution == res,
                                onClick = { onUpdateSettings(res, null, null, null) },
                                label = { Text(res.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.cyanMuted,
                                    selectedLabelColor = palette.playheadCyan
                                )
                            )
                        }
                    }

                    Text("Frame Rate & Codec", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(24, 30, 60).forEach { fps ->
                            FilterChip(
                                selected = project.exportSettings.fps == fps,
                                onClick = { onUpdateSettings(null, fps, null, null) },
                                label = { Text("${fps}fps", fontSize = 12.sp) }
                            )
                        }
                        ExportCodec.entries.forEach { codec ->
                            FilterChip(
                                selected = project.exportSettings.codec == codec,
                                onClick = { onUpdateSettings(null, null, codec, null) },
                                label = { Text(codec.name, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Bitrate Master", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BitrateTier.entries.forEach { tier ->
                            FilterChip(
                                selected = project.exportSettings.bitrateTier == tier,
                                onClick = { onUpdateSettings(null, null, null, tier) },
                                label = { Text("${tier.label} (${tier.mbpsLabel})", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onStartExport,
                        enabled = project.clips.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.playheadCyan,
                            contentColor = palette.voidBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_export_button")
                    ) {
                        Icon(Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Render MP4 Offline", fontWeight = FontWeight.Bold)
                    }
                }

                is ExportUiState.Exporting -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.panelSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Encoding Hardware Stream...", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${exportState.progressPercent}%",
                                    style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 16.sp)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { exportState.progressPercent / 100f },
                                color = palette.playheadCyan,
                                trackColor = palette.elevatedTrack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "Elapsed: ${TimecodeFormatter.formatDurationShort(exportState.elapsedMs)}",
                                style = TimecodeTextStyle.copy(color = palette.textSecondary)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCancelExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Export", color = palette.cutCrimson)
                    }
                }

                is ExportUiState.Completed -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.panelSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.readyEmerald.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Export completed",
                                    tint = palette.readyEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Export Ready!", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                                    val mb = String.format("%.2f MB", exportState.fileSizeBytes / (1024f * 1024f))
                                    Text(
                                        text = "$mb • ${TimecodeFormatter.formatDurationShort(exportState.durationMs)}" +
                                            if (exportState.savedToGallery) " • Saved to Movies/CutForge" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.readyEmerald
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(exportState.shareUri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(viewIntent) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, null, tint = palette.playheadCyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Play MP4", color = palette.textPrimary)
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, exportState.shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share CutForge Video"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.playheadCyan,
                                contentColor = palette.voidBg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is ExportUiState.Failed -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.crimsonMuted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = palette.cutCrimson)
                            Spacer(Modifier.width(10.dp))
                            Text(exportState.message, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = onStartExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Export")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
