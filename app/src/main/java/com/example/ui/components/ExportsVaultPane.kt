package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.ExportedVideoMeta
import com.example.media.TimecodeFormatter
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportsVaultPane(
    exports: List<ExportedVideoMeta>,
    columns: Int,
    onPlayExport: (ExportedVideoMeta) -> Unit,
    onDeleteExport: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val context = LocalContext.current

    if (exports.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.panelSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Movie, null, tint = palette.playheadCyan, modifier = Modifier.size(34.dp))
                    Text("No Rendered Masters", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(
                        text = "Exported MP4 renders appear here for local playback and sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(exports, key = { it.file.absolutePath }) { item ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = palette.panelSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
                    .clickable { onPlayExport(item) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.cyanMuted)
                            .border(0.5.dp, palette.playheadCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = palette.playheadCyan, modifier = Modifier.size(24.dp))
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val mb = String.format(Locale.US, "%.2f MB", item.sizeBytes / (1024f * 1024f))
                        val date = remember(item.lastModifiedMs) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(item.lastModifiedMs))
                        }
                        Text(
                            text = "$mb • ${TimecodeFormatter.formatDurationShort(item.durationMs)} • $date",
                            style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.readyEmerald)
                        )
                    }

                    IconButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, item.shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share Export"))
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Share, "Share video", tint = palette.textSecondary)
                    }

                    IconButton(
                        onClick = { onDeleteExport(item.file) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Delete, "Delete export", tint = palette.cutCrimson)
                    }
                }
            }
        }
    }
}
