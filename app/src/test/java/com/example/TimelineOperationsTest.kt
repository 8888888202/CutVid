package com.example

import com.example.data.model.AspectRatioPreset
import com.example.data.model.BadgeStickerType
import com.example.data.model.ColorPreset
import com.example.data.model.ExportResolution
import com.example.data.model.ExportSettings
import com.example.data.model.OverlayStickerItem
import com.example.data.model.SpeedCurvePreset
import com.example.data.model.VideoClip
import com.example.data.model.VideoProject
import com.example.media.Media3EffectFactory
import com.example.media.SrtSubtitleParser
import com.example.media.TimecodeFormatter
import com.example.media.TimelineOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOperationsTest {

    @Test
    fun splitClipAtPlayhead_dividesDurationAccurately() {
        val clip = VideoClip(
            id = "clip-1",
            uriString = "file:///tmp/test.mp4",
            displayName = "Intro",
            sourceDurationMs = 6000L,
            trimStartMs = 0L,
            trimEndMs = 6000L,
            speed = 2.0f // effective duration = 3000ms
        )
        val project = VideoProject(clips = listOf(clip))
        assertEquals(3000L, project.totalDurationMs)

        val (updated, secondId) = TimelineOperations.splitClipAtPlayhead(project, 1500L)
        assertNotNull(secondId)
        assertEquals(2, updated.clips.size)
        assertEquals(3000L, updated.totalDurationMs)
        assertEquals(3000L, updated.clips[0].trimEndMs)
        assertEquals(3000L, updated.clips[1].trimStartMs)
    }

    @Test
    fun speedCurvePreset_computesEffectiveDurationAndSplitsAccurately() {
        val clip = VideoClip(
            id = "curve-1",
            uriString = "file:///tmp/action.mp4",
            displayName = "Action",
            sourceDurationMs = 5000L,
            trimStartMs = 0L,
            trimEndMs = 5000L,
            speed = 1.0f,
            speedCurve = SpeedCurvePreset.JUMP_CUT // 2.5x -> effective 2000ms
        )
        assertEquals(2.5f, clip.effectiveSpeed, 0.001f)
        assertEquals(2000L, clip.effectiveDurationMs)

        val project = VideoProject(clips = listOf(clip))
        val (splitProj, secondId) = TimelineOperations.splitClipAtPlayhead(project, 1000L)
        assertNotNull(secondId)
        assertEquals(2500L, splitProj.clips[0].trimEndMs)
        assertEquals(2500L, splitProj.clips[1].trimStartMs)
    }

    @Test
    fun insertClipAfterCurrent_placesFreezeFrameAfterActiveClip() {
        val c1 = VideoClip(id = "c1", uriString = "file:///1.mp4", displayName = "First", sourceDurationMs = 3000L)
        val c2 = VideoClip(id = "c2", uriString = "file:///2.mp4", displayName = "Second", sourceDurationMs = 3000L)
        val freeze = VideoClip(
            id = "freeze",
            uriString = "file:///freeze.png",
            displayName = "Freeze",
            isImage = true,
            sourceDurationMs = 2500L
        )
        val project = VideoProject(clips = listOf(c1, c2))

        val updated = TimelineOperations.insertClipAfterCurrent(project, playheadMs = 1200L, newClip = freeze)
        assertEquals(listOf("c1", "freeze", "c2"), updated.clips.map { it.id })
    }

    @Test
    fun beatMarkers_addDeduplicatesAndSnapsWithinThreshold() {
        val c1 = VideoClip(id = "c1", uriString = "file:///1.mp4", displayName = "Clip", sourceDurationMs = 5000L)
        var project = VideoProject(clips = listOf(c1))

        project = TimelineOperations.addBeatMarker(project, 2200L)
        // Within 120ms threshold should be deduplicated
        project = TimelineOperations.addBeatMarker(project, 2260L)
        project = TimelineOperations.addBeatMarker(project, 900L)

        assertEquals(listOf(900L, 2200L), project.beatMarkers.map { it.timeMs })
        // Snapping within 180ms returns beat marker; outside returns rawMs
        assertEquals(2200L, TimelineOperations.snapToNearestBeatOrBoundary(project, 2320L))
        assertEquals(1500L, TimelineOperations.snapToNearestBeatOrBoundary(project, 1500L))
    }

    @Test
    fun updateSticker_modifiesTargetStickerInPlace() {
        val s1 = OverlayStickerItem(id = "st1", type = BadgeStickerType.REC_INDICATOR, normX = 0.2f, normY = 0.2f)
        val s2 = OverlayStickerItem(id = "st2", type = BadgeStickerType.RAW_4K, normX = 0.7f, normY = 0.2f)
        val project = VideoProject(stickers = listOf(s1, s2))

        val updated = TimelineOperations.updateSticker(project, "st1") {
            it.copy(normX = 0.5f, scale = 1.4f)
        }
        assertEquals(0.5f, updated.stickers[0].normX, 0.001f)
        assertEquals(1.4f, updated.stickers[0].scale, 0.001f)
        assertEquals(0.7f, updated.stickers[1].normX, 0.001f)
    }

    @Test
    fun applyColorPreset_andApplyToAll_updatesAllClips() {
        val c1 = VideoClip(uriString = "file:///a.mp4", displayName = "A", sourceDurationMs = 2000L)
        val c2 = VideoClip(uriString = "file:///b.mp4", displayName = "B", sourceDurationMs = 2000L)
        val project = VideoProject(clips = listOf(c1, c2))

        val grade = TimelineOperations.applyPresetToGrade(ColorPreset.TEAL_ORANGE)
        val updated = TimelineOperations.applyColorGradeToAll(project, grade)

        assertTrue(updated.clips.all { it.colorGrade.preset == ColorPreset.TEAL_ORANGE })
        assertTrue(updated.clips.all { it.colorGrade.isModified })
    }

    @Test
    fun computeExportDimensions_producesEvenEncoderSizes() {
        val project = VideoProject(
            aspectRatio = AspectRatioPreset.RATIO_4_5,
            exportSettings = ExportSettings(resolution = ExportResolution.FHD_1080P)
        )
        val (w, h) = Media3EffectFactory.computeExportDimensions(project)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
        assertEquals(1080, w)
        assertEquals(1350, h)
    }

    @Test
    fun timecodeFormatter_formatsTabularFrames() {
        assertEquals("00:05.15", TimecodeFormatter.formatTimecode(5500L, fps = 30))
        assertEquals("5.5s", TimecodeFormatter.formatDurationShort(5500L))
    }

    @Test
    fun srtSubtitleParser_parsesCuesAndAppliesOffset() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,200
            <i>CutForge Studio</i>

            2
            00:00:05.000 --> 00:00:07.500
            Universal Offline Player
        """.trimIndent()

        val cues = SrtSubtitleParser.parse(raw)
        assertEquals(2, cues.size)
        assertEquals("CutForge Studio", cues[0].text)
        assertEquals("CutForge Studio", SrtSubtitleParser.activeTextAt(cues, 2000L, 0L))
        assertNull(SrtSubtitleParser.activeTextAt(cues, 4000L, 0L))
        // +1000ms offset shifts cue 2 (5000..7500) to match 6000..8500
        assertEquals("Universal Offline Player", SrtSubtitleParser.activeTextAt(cues, 8000L, 1000L))
    }
}
