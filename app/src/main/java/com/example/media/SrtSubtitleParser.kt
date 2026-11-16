package com.example.media

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SrtSubtitleParser {

    fun parse(rawContent: String): List<SubtitleCue> {
        if (rawContent.isBlank()) return emptyList()
        val normalized = rawContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .removePrefix("\uFEFF")

        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()

        for (block in blocks) {
            val lines = block.lines()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            if (lines.first().startsWith("WEBVTT", ignoreCase = true) ||
                lines.first().startsWith("NOTE", ignoreCase = true)
            ) continue

            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex < 0) continue

            val parts = lines[timeLineIndex].split("-->")
            if (parts.size < 2) continue
            val startMs = parseTimestampMs(parts[0]) ?: continue
            val endMs = parseTimestampMs(parts[1].trim().substringBefore(' ')) ?: continue
            if (endMs <= startMs) continue

            val textLines = lines.drop(timeLineIndex + 1)
            val cleanText = textLines.joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (cleanText.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = cleanText))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun activeTextAt(
        cues: List<SubtitleCue>,
        positionMs: Long,
        offsetMs: Long = 0L
    ): String? {
        if (cues.isEmpty()) return null
        val adjusted = positionMs - offsetMs
        return cues.firstOrNull { adjusted in it.startMs..it.endMs }?.text
    }

    internal fun parseTimestampMs(raw: String): Long? {
        val cleaned = raw.trim().replace(',', '.')
        val parts = cleaned.split(':')
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val secParts = parts[2].split('.')
                    val s = secParts[0].toLong()
                    val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + ms
                }
                2 -> {
                    val m = parts[0].toLong()
                    val secParts = parts[1].split('.')
                    val s = secParts[0].toLong()
                    val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    (m * 60_000L) + (s * 1000L) + ms
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
