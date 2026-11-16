package com.example.media

import java.util.Locale

object TimecodeFormatter {
    fun formatTimecode(ms: Long, fps: Int = 30): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        val seconds = totalSeconds % 60L
        val frames = ((safeMs % 1000L) * fps / 1000L).coerceIn(0L, (fps - 1).toLong())
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, frames)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, frames)
        }
    }

    fun formatDurationShort(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        } else {
            val tenths = (safeMs % 1000L) / 100L
            String.format(Locale.US, "%d.%ds", seconds, tenths)
        }
    }
}
