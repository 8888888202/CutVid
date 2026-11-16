package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.example.data.model.BadgeStickerType
import com.example.data.model.ClipTimelineWindow
import com.example.data.model.ColorGradeConfig
import com.example.data.model.ExportResolution
import com.example.data.model.OverlayStickerItem
import com.example.data.model.TextOverlayItem
import com.example.data.model.VideoClip
import com.example.data.model.VideoProject
import com.example.data.model.VisualFxType
import com.example.data.model.clipWindows
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
class GainAudioProcessor(
    private val gain: Float,
    private val fadeInMs: Long = 0L,
    private val fadeOutMs: Long = 0L,
    private val totalDurationMs: Long = 0L
) : BaseAudioProcessor() {

    private var processedSamples: Long = 0L
    private var sampleRateHz: Int = 44100
    private var channelCount: Int = 2

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT) {
            sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8000)
            channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
            processedSamples = 0L
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        out.order(ByteOrder.LITTLE_ENDIAN)
        val inBuf = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        val hasFades = (fadeInMs > 0L || fadeOutMs > 0L) && totalDurationMs > 0L
        while (inBuf.remaining() >= 2) {
            val sample = inBuf.short.toInt()
            var env = gain
            if (hasFades) {
                val frameIdx = processedSamples / channelCount
                val timeMs = (frameIdx * 1000L) / sampleRateHz
                if (fadeInMs > 0L && timeMs < fadeInMs) {
                    env *= (timeMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
                }
                if (fadeOutMs > 0L && totalDurationMs > fadeOutMs && timeMs > (totalDurationMs - fadeOutMs)) {
                    val rem = (totalDurationMs - timeMs).coerceAtLeast(0L)
                    env *= (rem.toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
                }
            }
            val scaled = (sample * env).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out.putShort(scaled.toShort())
            processedSamples++
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }
}

@OptIn(UnstableApi::class)
object Media3EffectFactory {

    fun buildClipVideoEffects(clip: VideoClip): List<Effect> {
        val effects = mutableListOf<Effect>()

        // Base Rotation & Flip
        if (clip.rotationDegrees != 0f || clip.flipHorizontal || clip.flipVertical) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees)
                    .setScale(
                        if (clip.flipHorizontal) -1f else 1f,
                        if (clip.flipVertical) -1f else 1f
                    )
                    .build()
            )
        }

        // Keyframed Zoom, Pan, Ken Burns, & Kinetic Glitch Shake via MatrixTransformation
        val hasMotion = clip.zoomScale != 1.0f ||
            clip.panX != 0f ||
            clip.panY != 0f ||
            clip.kenBurnsEndScale != 1.0f ||
            clip.fxType == VisualFxType.GLITCH_PULSE

        if (hasMotion) {
            val clipDurUs = (clip.effectiveDurationMs * 1000L).coerceAtLeast(100_000L).toFloat()
            val startScale = clip.zoomScale.coerceIn(1.0f, 3.0f)
            val endScale = (clip.zoomScale * clip.kenBurnsEndScale).coerceIn(0.8f, 3.2f)
            val panX = clip.panX.coerceIn(-0.5f, 0.5f)
            val panY = clip.panY.coerceIn(-0.5f, 0.5f)
            val glitchAmp = if (clip.fxType == VisualFxType.GLITCH_PULSE) clip.fxIntensity * 0.045f else 0f

            effects.add(MatrixTransformation { presentationTimeUs ->
                val prog = (presentationTimeUs / clipDurUs).coerceIn(0f, 1f)
                val s = startScale + (endScale - startScale) * prog
                val shakeX = if (glitchAmp > 0f) glitchAmp * sin(presentationTimeUs / 38_000.0).toFloat() else 0f
                val shakeY = if (glitchAmp > 0f) glitchAmp * sin(presentationTimeUs / 29_000.0).toFloat() else 0f
                Matrix().apply {
                    postScale(s, s)
                    postTranslate((panX + shakeX) * 2f, (-panY + shakeY) * 2f)
                }
            })
        }

        // Color Grade Pipeline
        val grade = clip.colorGrade
        if (grade.isModified) {
            if (grade.contrast != 0f) {
                effects.add(Contrast(grade.contrast.coerceIn(-0.9f, 0.9f)))
            }
            if (grade.saturation != 0f || grade.brightness != 0f) {
                effects.add(
                    HslAdjustment.Builder()
                        .adjustSaturation((grade.saturation * 100f).coerceIn(-100f, 100f))
                        .adjustLightness((grade.brightness * 60f).coerceIn(-50f, 50f))
                        .build()
                )
            }
            if (grade.warmth != 0f) {
                val r = (1f + grade.warmth * 0.45f).coerceIn(0.5f, 1.5f)
                val b = (1f - grade.warmth * 0.45f).coerceIn(0.5f, 1.5f)
                effects.add(
                    RgbAdjustment.Builder()
                        .setRedScale(r)
                        .setGreenScale(1f)
                        .setBlueScale(b)
                        .build()
                )
            }
        }

        return effects
    }

    fun buildComposition(
        context: android.content.Context,
        project: VideoProject,
        forExport: Boolean,
        exportWidth: Int? = null,
        exportHeight: Int? = null
    ): Composition? {
        if (project.clips.isEmpty()) return null

        val windows = project.clipWindows()
        val hasDuckingAudio = project.audioTracks.any { !it.isMuted && it.duckOriginalAudio }

        val videoEditedItems = windows.map { window ->
            buildEditedVideoClip(
                context = context,
                window = window,
                textOverlays = project.textOverlays,
                stickers = project.stickers,
                duckOriginal = hasDuckingAudio,
                forExport = forExport,
                canvasWidth = exportWidth ?: 1080,
                canvasHeight = exportHeight ?: 1920
            )
        }

        val videoSequence = EditedMediaItemSequence.Builder(videoEditedItems).build()
        val sequences = mutableListOf(videoSequence)

        val activeAudioTracks = project.audioTracks.filter { !it.isMuted }.take(4)
        activeAudioTracks.forEach { activeAudio ->
            val audioMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(activeAudio.uriString))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(activeAudio.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs((activeAudio.trimStartMs + activeAudio.durationMs).coerceAtLeast(500L))
                        .build()
                )
                .build()

            val audioProcessors = mutableListOf<AudioProcessor>()
            if (activeAudio.volume != 1.0f || activeAudio.fadeInMs > 0L || activeAudio.fadeOutMs > 0L) {
                audioProcessors.add(
                    GainAudioProcessor(
                        gain = activeAudio.volume.coerceIn(0f, 2f),
                        fadeInMs = activeAudio.fadeInMs,
                        fadeOutMs = activeAudio.fadeOutMs,
                        totalDurationMs = activeAudio.durationMs
                    )
                )
            }
            val editedAudio = EditedMediaItem.Builder(audioMediaItem)
                .setRemoveVideo(true)
                .setDurationUs((activeAudio.durationMs * 1000L).coerceAtLeast(500_000L))
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()

            val audioSeq = EditedMediaItemSequence.Builder(listOf(editedAudio))
                .setIsLooping(activeAudio.isLooping)
                .build()
            sequences.add(audioSeq)
        }

        val globalEffects = mutableListOf<Effect>()
        if (forExport && exportWidth != null && exportHeight != null) {
            globalEffects.add(
                Presentation.createForWidthAndHeight(
                    exportWidth,
                    exportHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        } else {
            val targetH = 720
            val targetW = ((targetH * project.aspectRatio.floatRatio).roundToInt() / 2) * 2
            globalEffects.add(
                Presentation.createForWidthAndHeight(
                    targetW.coerceAtLeast(360),
                    targetH,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        }

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), globalEffects))
            .build()
    }

    private fun buildEditedVideoClip(
        context: android.content.Context,
        window: ClipTimelineWindow,
        textOverlays: List<TextOverlayItem>,
        stickers: List<OverlayStickerItem>,
        duckOriginal: Boolean,
        forExport: Boolean,
        canvasWidth: Int,
        canvasHeight: Int
    ): EditedMediaItem {
        val clip = window.clip
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(clip.uriString))

        if (clip.isImage) {
            mediaItemBuilder.setImageDurationMs(clip.effectiveDurationMs)
        } else {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 250L))
                    .build()
            )
        }

        val videoEffects = buildClipVideoEffects(clip).toMutableList()

        // Burn in Visual FX overlays + Text + PiP/Badges on export
        if (forExport) {
            val overlayList = mutableListOf<TextureOverlay>()

            // Visual FX Overlay (Cinema Bars / Grain / Retro Scan / Halation)
            buildFxOverlayBitmap(clip.fxType, clip.fxIntensity, canvasWidth, canvasHeight)?.let { fxBmp ->
                val settings = StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0f, 0f)
                    .setOverlayFrameAnchor(0f, 0f)
                    .build()
                overlayList.add(BitmapOverlay.createStaticBitmapOverlay(fxBmp, settings))
            }

            // Sticker / PiP Overlays
            stickers.filter {
                it.endMs > window.timelineStartMs && it.startMs < window.timelineEndMs
            }.forEach { sticker ->
                val bmp = renderStickerBitmap(context, sticker, canvasWidth)
                if (bmp != null) {
                    val ndcX = (sticker.normX * 2f - 1f).coerceIn(-0.9f, 0.9f)
                    val ndcY = (1f - sticker.normY * 2f).coerceIn(-0.9f, 0.9f)
                    val settings = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(ndcX, ndcY)
                        .setOverlayFrameAnchor(0f, 0f)
                        .build()
                    overlayList.add(BitmapOverlay.createStaticBitmapOverlay(bmp, settings))
                }
            }

            // Text Overlays
            textOverlays.filter {
                it.endMs > window.timelineStartMs && it.startMs < window.timelineEndMs
            }.forEach { overlay ->
                val bmp = renderTextOverlayBitmap(overlay, canvasWidth)
                val ndcX = (overlay.normX * 2f - 1f).coerceIn(-0.9f, 0.9f)
                val ndcY = (1f - overlay.normY * 2f).coerceIn(-0.9f, 0.9f)
                val settings = StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(ndcX, ndcY)
                    .setOverlayFrameAnchor(0f, 0f)
                    .build()
                overlayList.add(BitmapOverlay.createStaticBitmapOverlay(bmp, settings))
            }

            if (overlayList.isNotEmpty()) {
                videoEffects.add(OverlayEffect(overlayList))
            }
        }

        val audioProcessors = mutableListOf<AudioProcessor>()
        val speed = clip.effectiveSpeed
        if (speed != 1.0f && !clip.isImage) {
            val sonic = SonicAudioProcessor().apply {
                setSpeed(speed)
            }
            audioProcessors.add(sonic)
        }
        val effectiveGain = (if (duckOriginal) clip.volume * 0.45f else clip.volume).coerceIn(0f, 2f)
        if (!clip.isMuted && effectiveGain != 1.0f && !clip.isImage) {
            audioProcessors.add(GainAudioProcessor(effectiveGain))
        }

        val builder = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(clip.isMuted || clip.isImage)
            .setDurationUs(clip.sourceDurationMs * 1000L)
            .setEffects(Effects(audioProcessors, videoEffects))

        if (clip.isImage) {
            builder.setFrameRate(30)
        }
        return builder.build()
    }

    private fun buildFxOverlayBitmap(
        fxType: VisualFxType,
        intensity: Float,
        w: Int,
        h: Int
    ): Bitmap? {
        if (fxType == VisualFxType.NONE || fxType == VisualFxType.GLITCH_PULSE) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (fxType) {
            VisualFxType.CINEMA_BARS -> {
                val barH = (h * (0.09f + intensity * 0.07f))
                paint.color = android.graphics.Color.BLACK
                canvas.drawRect(0f, 0f, w.toFloat(), barH, paint)
                canvas.drawRect(0f, h - barH, w.toFloat(), h.toFloat(), paint)
            }
            VisualFxType.VINTAGE_SCAN -> {
                paint.color = android.graphics.Color.argb((intensity * 70).toInt().coerceIn(10, 90), 0, 0, 0)
                paint.strokeWidth = 2f
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w.toFloat(), y, paint)
                    y += 8f
                }
            }
            VisualFxType.FILM_GRAIN -> {
                val alpha = (intensity * 55).toInt().coerceIn(8, 70)
                for (i in 0 until 450) {
                    val x = ((i * 73) % w).toFloat()
                    val y = ((i * 137) % h).toFloat()
                    paint.color = if (i % 2 == 0) {
                        android.graphics.Color.argb(alpha, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(alpha, 0, 0, 0)
                    }
                    canvas.drawRect(x, y, x + 3f, y + 3f, paint)
                }
            }
            VisualFxType.DREAM_GLOW -> {
                paint.color = android.graphics.Color.argb((intensity * 45).toInt().coerceIn(8, 60), 255, 195, 130)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            else -> {}
        }
        return bmp
    }

    private fun renderStickerBitmap(
        context: android.content.Context,
        sticker: OverlayStickerItem,
        canvasWidth: Int
    ): Bitmap? {
        if (sticker.type == BadgeStickerType.IMAGE_PIP && sticker.imageUriString != null) {
            return runCatching {
                val uri = Uri.parse(sticker.imageUriString)
                val targetSize = (canvasWidth * 0.28f * sticker.scale).roundToInt().coerceIn(80, canvasWidth)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream) ?: return null
                    val h = ((decoded.height.toFloat() / decoded.width.coerceAtLeast(1)) * targetSize)
                        .roundToInt().coerceIn(64, canvasWidth)
                    val scaled = Bitmap.createScaledBitmap(decoded, targetSize, h, true)
                    if (scaled != decoded) decoded.recycle()
                    scaled
                }
            }.getOrNull()
        }

        val scale = (canvasWidth / 1080f).coerceIn(0.6f, 2.2f) * sticker.scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sticker.type.accentArgb.toInt()
            textSize = 36f * scale
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val label = sticker.type.badgeText
        val fm = paint.fontMetrics
        val tw = paint.measureText(label)
        val bmpW = (tw + 48f * scale).roundToInt().coerceIn(64, canvasWidth)
        val bmpH = ((fm.descent - fm.ascent) + 32f * scale).roundToInt().coerceIn(48, 400)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(195, 11, 13, 18)
        }
        canvas.drawRoundRect(RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()), 16f * scale, 16f * scale, bgPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            color = sticker.type.accentArgb.toInt()
        }
        canvas.drawRoundRect(RectF(2f, 2f, bmpW - 2f, bmpH - 2f), 16f * scale, 16f * scale, borderPaint)
        canvas.drawText(label, bmpW / 2f, (bmpH - fm.ascent - fm.descent) / 2f, paint)
        return bmp
    }

    private fun renderTextOverlayBitmap(overlay: TextOverlayItem, canvasWidth: Int): Bitmap {
        val scale = (canvasWidth / 1080f).coerceIn(0.6f, 2.2f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.colorArgb.toInt()
            textSize = overlay.fontSizeSp * 2.2f * scale
            typeface = if (overlay.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val lines = overlay.text.ifBlank { "Title" }.split("\n")
        val fm = textPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 1.15f
        val maxLineW = lines.maxOfOrNull { textPaint.measureText(it) } ?: 120f
        val padH = 36f * scale
        val padV = 22f * scale
        val bmpW = (maxLineW + padH * 2f).roundToInt().coerceIn(64, canvasWidth)
        val bmpH = (lineHeight * lines.size + padV * 2f).roundToInt().coerceIn(48, 800)

        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (overlay.hasBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlay.bgArgb.toInt()
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat()),
                22f * scale,
                22f * scale,
                bgPaint
            )
        }
        lines.forEachIndexed { idx, line ->
            val y = padV - fm.ascent + idx * lineHeight
            canvas.drawText(line, bmpW / 2f, y, textPaint)
        }
        return bmp
    }

    fun computeExportDimensions(project: VideoProject): Pair<Int, Int> {
        val shortEdge = when (project.exportSettings.resolution) {
            ExportResolution.HD_720P -> 720
            ExportResolution.FHD_1080P -> 1080
            ExportResolution.UHD_4K -> 2160
        }
        val ratio = project.aspectRatio.floatRatio
        val rawW: Int
        val rawH: Int
        if (ratio >= 1f) {
            rawH = shortEdge
            rawW = (shortEdge * ratio).roundToInt()
        } else {
            rawW = shortEdge
            rawH = (shortEdge / ratio).roundToInt()
        }
        val evenW = (rawW / 2) * 2
        val evenH = (rawH / 2) * 2
        return evenW to evenH
    }

    fun colorMatrixPreviewMultiplier(grade: ColorGradeConfig): FloatArray {
        val b = grade.brightness * 255f
        val c = 1f + grade.contrast
        val t = (1f - c) * 128f + b
        val s = (1f + grade.saturation).coerceAtLeast(0f)
        val sr = (1f - s) * 0.213f
        val sg = (1f - s) * 0.715f
        val sb = (1f - s) * 0.072f

        val rGain = (1f + grade.warmth * 0.35f) * c
        val gGain = c
        val bGain = (1f - grade.warmth * 0.35f) * c

        return floatArrayOf(
            (sr + s) * rGain, sg * rGain, sb * rGain, 0f, t,
            sr * gGain, (sg + s) * gGain, sb * gGain, 0f, t,
            sr * bGain, sg * bGain, (sb + s) * bGain, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    }
}
