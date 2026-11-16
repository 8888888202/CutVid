package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatioPreset(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int,
    val subtitle: String,
    val targetWidth1080p: Int,
    val targetHeight1080p: Int
) {
    RATIO_9_16("9:16", 9, 16, "Reels / Shorts", 1080, 1920),
    RATIO_16_9("16:9", 16, 9, "Cinema", 1920, 1080),
    RATIO_1_1("1:1", 1, 1, "Square", 1080, 1080),
    RATIO_4_5("4:5", 4, 5, "Portrait", 1080, 1350),
    RATIO_21_9("21:9", 21, 9, "Anamorphic", 2520, 1080);

    val floatRatio: Float get() = widthRatio.toFloat() / heightRatio.toFloat()
}

@Serializable
enum class ColorPreset(
    val label: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val vignette: Float
) {
    NONE("Original", 0f, 0f, 0f, 0f, 0f),
    TEAL_ORANGE("Cinema Teal", -0.02f, 0.22f, 0.18f, -0.06f, 0.28f),
    KODAK_WARM("Kodak Gold", 0.04f, 0.14f, 0.12f, 0.24f, 0.18f),
    NOIR_BW("Silver Noir", 0.0f, 0.34f, -1.0f, 0.0f, 0.35f),
    CYBER_NEON("Cyber Neon", 0.02f, 0.28f, 0.42f, -0.18f, 0.20f),
    VIVID_POP("Vivid Pop", 0.05f, 0.16f, 0.34f, 0.04f, 0.0f),
    PASTEL_MATTE("Pastel Matte", 0.08f, -0.14f, -0.16f, 0.08f, 0.08f)
}

@Serializable
data class ColorGradeConfig(
    val preset: ColorPreset = ColorPreset.NONE,
    val brightness: Float = 0f,   // -0.5f .. 0.5f
    val contrast: Float = 0f,     // -0.5f .. 0.5f
    val saturation: Float = 0f,   // -1.0f .. 1.0f
    val warmth: Float = 0f,       // -0.5f .. 0.5f
    val vignette: Float = 0f      // 0.0f .. 1.0f
) {
    val isModified: Boolean
        get() = preset != ColorPreset.NONE ||
            brightness != 0f ||
            contrast != 0f ||
            saturation != 0f ||
            warmth != 0f ||
            vignette != 0f
}

@Serializable
enum class VisualFxType(val label: String) {
    NONE("Clean"),
    CINEMA_BARS("2.39:1 Bars"),
    FILM_GRAIN("Film Grain"),
    VINTAGE_SCAN("Retro Scan"),
    GLITCH_PULSE("Kinetic Shake"),
    DREAM_GLOW("Halation")
}

@Serializable
enum class SpeedCurvePreset(val label: String, val effectiveMultiplier: Float) {
    LINEAR("Linear", 1.0f),
    HERO_RAMP("Hero Ramp", 1.15f),
    BULLET_TIME("Bullet Slow", 0.70f),
    MONTAGE_SNAP("Montage Snap", 1.45f),
    JUMP_CUT("2.5x Surge", 2.50f)
}

@Serializable
enum class CanvasFillStyle(val label: String, val argb: Long) {
    OBSIDIAN("Obsidian", 0xFF0B0D12),
    PURE_BLACK("Pure Black", 0xFF000000),
    WARM_SLATE("Warm Charcoal", 0xFF1A1412),
    CYAN_STUDIO("Studio Navy", 0xFF0B1824),
    CREAM_MATTE("Gallery Cream", 0xFFECE9E2)
}

@Serializable
enum class TransitionType(val label: String) {
    NONE("Cut"),
    CROSSFADE("Dissolve"),
    FLASH_WHITE("Flash"),
    DIP_BLACK("Dip Black"),
    ZOOM_IN("Zoom Punch"),
    SLIDE_LEFT("Whip Slide")
}

@Serializable
enum class TextAnimation(val label: String) {
    NONE("Static"),
    FADE("Fade In"),
    POP("Kinetic Pop"),
    SLIDE_UP("Slide Up"),
    TYPEWRITER("Typewriter")
}

@Serializable
enum class BadgeStickerType(val label: String, val badgeText: String, val accentArgb: Long) {
    IMAGE_PIP("Photo PiP", "PIP", 0xFF00E5FF),
    REC_INDICATOR("REC ●", "● REC", 0xFFFF4D6D),
    RAW_4K("4K MASTER", "4K • 60FPS", 0xFFFFB300),
    CINEMA_TAG("PRO CUT", "CINEMA CUT", 0xFF00E5FF),
    FOCUS_FRAME("VIEWFINDER", "[ + ]", 0xFFFFFFFF),
    FOLLOW_CTA("FOLLOW", "FOLLOW +", 0xFF10B981)
}

@Serializable
data class OverlayStickerItem(
    val id: String = UUID.randomUUID().toString(),
    val type: BadgeStickerType,
    val imageUriString: String? = null,
    val startMs: Long = 0L,
    val durationMs: Long = 3000L,
    val normX: Float = 0.25f,
    val normY: Float = 0.22f,
    val scale: Float = 1.0f
) {
    val endMs: Long get() = startMs + durationMs
}

@Serializable
data class BeatMarker(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val label: String = "Beat"
)

@Serializable
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val isImage: Boolean = false,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val speed: Float = 1.0f,
    val speedCurve: SpeedCurvePreset = SpeedCurvePreset.LINEAR,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val zoomScale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val kenBurnsEndScale: Float = 1.0f,
    val colorGrade: ColorGradeConfig = ColorGradeConfig(),
    val fxType: VisualFxType = VisualFxType.NONE,
    val fxIntensity: Float = 0.65f,
    val transitionIn: TransitionType = TransitionType.NONE,
    val transitionDurationMs: Long = 500L,
    val waveformPeaks: List<Float> = emptyList()
) {
    val trimmedSourceDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(250L)

    val effectiveSpeed: Float
        get() = if (speedCurve == SpeedCurvePreset.LINEAR) {
            speed.coerceIn(0.25f, 4.0f)
        } else {
            (speed * speedCurve.effectiveMultiplier).coerceIn(0.25f, 4.0f)
        }

    val effectiveDurationMs: Long
        get() = ((trimmedSourceDurationMs / effectiveSpeed).toLong()).coerceAtLeast(200L)
}

@Serializable
data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long = 0L,
    val durationMs: Long = 3000L,
    val normX: Float = 0.5f,
    val normY: Float = 0.80f,
    val fontSizeSp: Float = 26f,
    val colorArgb: Long = 0xFFFFFFFF,
    val bgArgb: Long = 0xAA0B0D12,
    val hasBackground: Boolean = true,
    val isBold: Boolean = true,
    val animation: TextAnimation = TextAnimation.POP
) {
    val endMs: Long get() = startMs + durationMs
}

@Serializable
data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val title: String,
    val startMs: Long = 0L,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val durationMs: Long = sourceDurationMs,
    val volume: Float = 0.85f,
    val fadeInMs: Long = 300L,
    val fadeOutMs: Long = 600L,
    val duckOriginalAudio: Boolean = false,
    val isLooping: Boolean = true,
    val isMuted: Boolean = false,
    val isVoiceover: Boolean = false,
    val waveformPeaks: List<Float> = emptyList()
)

@Serializable
enum class ExportResolution(val label: String, val shortEdgePx: Int) {
    HD_720P("720p HD", 720),
    FHD_1080P("1080p FHD", 1080),
    UHD_4K("4K UHD", 2160)
}

@Serializable
enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 AVC", "video/avc"),
    H265("H.265 HEVC", "video/hevc")
}

@Serializable
enum class BitrateTier(val label: String, val mbpsLabel: String) {
    STANDARD("Balanced", "8 Mbps"),
    HIGH("High Quality", "16 Mbps"),
    PRO_MASTER("Pro Master", "32 Mbps")
}

@Serializable
data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val fps: Int = 30,
    val codec: ExportCodec = ExportCodec.H264,
    val bitrateTier: BitrateTier = BitrateTier.HIGH
)

@Serializable
data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Cut",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_9_16,
    val canvasFillStyle: CanvasFillStyle = CanvasFillStyle.OBSIDIAN,
    val canvasBgArgb: Long = 0xFF0B0D12,
    val clips: List<VideoClip> = emptyList(),
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickers: List<OverlayStickerItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val beatMarkers: List<BeatMarker> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
    val thumbnailPath: String? = null
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }
}

data class ClipTimelineWindow(
    val clip: VideoClip,
    val index: Int,
    val timelineStartMs: Long,
    val timelineEndMs: Long
)

fun VideoProject.clipWindows(): List<ClipTimelineWindow> {
    var cursor = 0L
    return clips.mapIndexed { idx, clip ->
        val start = cursor
        val end = cursor + clip.effectiveDurationMs
        cursor = end
        ClipTimelineWindow(clip, idx, start, end)
    }
}
