package com.example.media

import com.example.data.model.BeatMarker
import com.example.data.model.ClipTimelineWindow
import com.example.data.model.ColorGradeConfig
import com.example.data.model.ColorPreset
import com.example.data.model.OverlayStickerItem
import com.example.data.model.TextOverlayItem
import com.example.data.model.VideoClip
import com.example.data.model.VideoProject
import com.example.data.model.clipWindows
import java.util.UUID

object TimelineOperations {

    fun findWindowAt(project: VideoProject, playheadMs: Long): ClipTimelineWindow? {
        val windows = project.clipWindows()
        if (windows.isEmpty()) return null
        val clamped = playheadMs.coerceIn(0L, project.totalDurationMs.coerceAtLeast(0L))
        return windows.firstOrNull { clamped >= it.timelineStartMs && clamped < it.timelineEndMs }
            ?: windows.last()
    }

    fun splitClipAtPlayhead(project: VideoProject, playheadMs: Long): Pair<VideoProject, String?> {
        val window = findWindowAt(project, playheadMs) ?: return project to null
        val clip = window.clip
        val localTimelineOffset = (playheadMs - window.timelineStartMs).coerceAtLeast(0L)
        val minPartMs = 300L
        if (localTimelineOffset < minPartMs || (clip.effectiveDurationMs - localTimelineOffset) < minPartMs) {
            return project to null
        }

        val sourceSplitOffset = (localTimelineOffset * clip.effectiveSpeed).toLong()
        val splitSourceMs = (clip.trimStartMs + sourceSplitOffset)
            .coerceIn(clip.trimStartMs + 250L, clip.trimEndMs - 250L)

        val firstHalf = clip.copy(trimEndMs = splitSourceMs)
        val secondHalf = clip.copy(
            id = UUID.randomUUID().toString(),
            trimStartMs = splitSourceMs,
            displayName = "${clip.displayName} (2)"
        )

        val newClips = project.clips.toMutableList()
        newClips.removeAt(window.index)
        newClips.add(window.index, secondHalf)
        newClips.add(window.index, firstHalf)

        return project.copy(clips = newClips) to secondHalf.id
    }

    fun insertClipAfterCurrent(
        project: VideoProject,
        playheadMs: Long,
        newClip: VideoClip
    ): VideoProject {
        val window = findWindowAt(project, playheadMs)
        val newClips = project.clips.toMutableList()
        if (window == null) {
            newClips.add(newClip)
        } else {
            newClips.add((window.index + 1).coerceIn(0, newClips.size), newClip)
        }
        return project.copy(clips = newClips)
    }

    fun duplicateClip(project: VideoProject, clipId: String): Pair<VideoProject, String?> {
        val idx = project.clips.indexOfFirst { it.id == clipId }
        if (idx < 0) return project to null
        val orig = project.clips[idx]
        val copy = orig.copy(
            id = UUID.randomUUID().toString(),
            displayName = "${orig.displayName} Copy"
        )
        val newClips = project.clips.toMutableList().apply { add(idx + 1, copy) }
        return project.copy(clips = newClips) to copy.id
    }

    fun deleteClip(project: VideoProject, clipId: String): VideoProject {
        val newClips = project.clips.filterNot { it.id == clipId }
        return project.copy(clips = newClips)
    }

    fun moveClip(project: VideoProject, clipId: String, delta: Int): VideoProject {
        val idx = project.clips.indexOfFirst { it.id == clipId }
        val target = idx + delta
        if (idx < 0 || target !in project.clips.indices) return project
        val list = project.clips.toMutableList()
        val item = list.removeAt(idx)
        list.add(target, item)
        return project.copy(clips = list)
    }

    fun updateClip(
        project: VideoProject,
        clipId: String,
        transform: (VideoClip) -> VideoClip
    ): VideoProject {
        return project.copy(
            clips = project.clips.map { if (it.id == clipId) transform(it) else it }
        )
    }

    fun applyColorGradeToAll(project: VideoProject, grade: ColorGradeConfig): VideoProject {
        return project.copy(
            clips = project.clips.map { it.copy(colorGrade = grade) }
        )
    }

    fun applyPresetToGrade(preset: ColorPreset): ColorGradeConfig {
        return ColorGradeConfig(
            preset = preset,
            brightness = preset.brightness,
            contrast = preset.contrast,
            saturation = preset.saturation,
            warmth = preset.warmth,
            vignette = preset.vignette
        )
    }

    fun updateTextOverlay(
        project: VideoProject,
        overlayId: String,
        transform: (TextOverlayItem) -> TextOverlayItem
    ): VideoProject {
        return project.copy(
            textOverlays = project.textOverlays.map { if (it.id == overlayId) transform(it) else it }
        )
    }

    fun updateSticker(
        project: VideoProject,
        stickerId: String,
        transform: (OverlayStickerItem) -> OverlayStickerItem
    ): VideoProject {
        return project.copy(
            stickers = project.stickers.map { if (it.id == stickerId) transform(it) else it }
        )
    }

    fun snapToNearestBeatOrBoundary(
        project: VideoProject,
        rawMs: Long,
        snapThresholdMs: Long = 180L
    ): Long {
        val candidates = buildList {
            add(0L)
            add(project.totalDurationMs)
            project.clipWindows().forEach {
                add(it.timelineStartMs)
                add(it.timelineEndMs)
            }
            project.beatMarkers.forEach { add(it.timeMs) }
        }
        val closest = candidates.minByOrNull { kotlin.math.abs(it - rawMs) } ?: return rawMs
        return if (kotlin.math.abs(closest - rawMs) <= snapThresholdMs) closest else rawMs
    }

    fun addBeatMarker(project: VideoProject, timeMs: Long): VideoProject {
        val clamped = timeMs.coerceIn(0L, project.totalDurationMs.coerceAtLeast(0L))
        if (project.beatMarkers.any { kotlin.math.abs(it.timeMs - clamped) < 120L }) {
            return project
        }
        val marker = BeatMarker(timeMs = clamped, label = "B${project.beatMarkers.size + 1}")
        return project.copy(
            beatMarkers = (project.beatMarkers + marker).sortedBy { it.timeMs }
        )
    }
}
