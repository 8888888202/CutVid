package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class GeneratedSampleClip(
    val file: File,
    val title: String,
    val isImage: Boolean,
    val durationMs: Long
)

data class GeneratedAudioAsset(
    val file: File,
    val title: String,
    val durationMs: Long
)

object SampleMediaGenerator {

    enum class VisualTheme {
        NEON_CITY,
        GOLDEN_HORIZON,
        KINETIC_PULSE
    }

    enum class AudioPreset(val fileName: String, val title: String, val durationMs: Long) {
        CYBER_BEAT("sfx_cyber_beat.wav", "Cyber Pulse 120BPM", 8000L),
        AMBIENT_PAD("sfx_ambient_pad.wav", "Midnight Synth Pad", 8000L),
        CINEMA_RISER("sfx_cinema_riser.wav", "Sub Boom & Riser", 3000L),
        WHOOSH_SFX("sfx_whoosh.wav", "Whip Whoosh SFX", 1400L),
        SHUTTER_CLICK("sfx_shutter.wav", "Camera Shutter SFX", 900L),
        LOFI_BEAT("sfx_lofi_groove.wav", "Lo-Fi Chill Groove", 8000L)
    }

    suspend fun ensureSampleClips(context: Context): List<GeneratedSampleClip> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "samples").apply { mkdirs() }
            val specs = listOf(
                Triple("sample_neon_city.mp4", "Neon Skyline", VisualTheme.NEON_CITY),
                Triple("sample_golden_horizon.mp4", "Golden Horizon", VisualTheme.GOLDEN_HORIZON),
                Triple("sample_kinetic_pulse.mp4", "Kinetic Beat", VisualTheme.KINETIC_PULSE)
            )
            specs.map { (fileName, title, theme) ->
                val mp4File = File(dir, fileName)
                if (mp4File.exists() && mp4File.length() > 8_000L) {
                    GeneratedSampleClip(mp4File, title, isImage = false, durationMs = 4000L)
                } else {
                    val encoded = runCatching {
                        encodeSyntheticMp4(mp4File, theme, width = 720, height = 1280, fps = 24, durationMs = 4000L)
                        mp4File.exists() && mp4File.length() > 4_000L
                    }.getOrDefault(false)

                    if (encoded) {
                        GeneratedSampleClip(mp4File, title, isImage = false, durationMs = 4000L)
                    } else {
                        mp4File.delete()
                        val pngFile = File(dir, fileName.replace(".mp4", ".png"))
                        renderFallbackPng(pngFile, theme, width = 720, height = 1280)
                        GeneratedSampleClip(pngFile, title, isImage = true, durationMs = 4000L)
                    }
                }
            }
        }

    suspend fun ensureAudioPreset(context: Context, preset: AudioPreset): GeneratedAudioAsset =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "samples").apply { mkdirs() }
            val wavFile = File(dir, preset.fileName)
            if (!wavFile.exists() || wavFile.length() < 1024L) {
                writeSynthesizedWav(wavFile, preset)
            }
            GeneratedAudioAsset(wavFile, preset.title, preset.durationMs)
        }

    private fun encodeSyntheticMp4(
        outputFile: File,
        theme: VisualTheme,
        width: Int,
        height: Int,
        fps: Int,
        durationMs: Long
    ) {
        val totalFrames = ((durationMs * fps) / 1000L).toInt().coerceAtLeast(24)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_800_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            for (frameIdx in 0 until totalFrames) {
                val progress = frameIdx.toFloat() / totalFrames.toFloat()
                val canvas = surface.lockCanvas(null)
                try {
                    drawThemeFrame(canvas, width.toFloat(), height.toFloat(), theme, progress)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(encoder, muxer, bufferInfo, endOfStream = false) { started, idx ->
                    muxerStarted = started
                    trackIndex = idx
                }.also { (started, idx) ->
                    muxerStarted = started
                    trackIndex = idx
                }
                // Small pace so surface timestamps stay monotonic
                Thread.sleep(8L)
            }

            encoder.signalEndOfInputStream()
            var drainTries = 0
            while (drainTries < 40) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    drainTries++
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (eos) break
                }
            }
            surface.release()
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onTrackState: (Boolean, Int) -> Unit
    ): Pair<Boolean, Int> {
        var muxerStarted = false
        var trackIndex = -1
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerStarted = true
                onTrackState(true, trackIndex)
            } else if (outIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outIndex)
                if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && trackIndex >= 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
        return muxerStarted to trackIndex
    }

    private fun renderFallbackPng(file: File, theme: VisualTheme, width: Int, height: Int) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawThemeFrame(canvas, width.toFloat(), height.toFloat(), theme, 0.45f)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bmp.recycle()
    }

    private fun drawThemeFrame(
        canvas: Canvas,
        w: Float,
        h: Float,
        theme: VisualTheme,
        t: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (theme) {
            VisualTheme.NEON_CITY -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(Color.rgb(9, 14, 34), Color.rgb(20, 12, 48), Color.rgb(6, 28, 44)),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                // Glowing horizon sun/orb
                val orbY = h * (0.36f + 0.04f * sin(t * 2f * PI).toFloat())
                paint.color = Color.argb(90, 0, 229, 255)
                canvas.drawCircle(w * 0.5f, orbY, w * 0.34f, paint)
                paint.color = Color.argb(200, 0, 229, 255)
                canvas.drawCircle(w * 0.5f, orbY, w * 0.21f, paint)

                // Stylized parallax skyline bars
                val barCount = 12
                val barW = w / barCount
                for (i in 0 until barCount) {
                    val shift = ((i + t * 3f) % barCount) / barCount
                    val bh = h * (0.16f + 0.24f * absSin(i * 1.3f + t * 4f))
                    paint.color = if (i % 3 == 0) Color.rgb(18, 28, 52) else Color.rgb(12, 18, 34)
                    val left = i * barW + 4f
                    canvas.drawRect(left, h * 0.72f - bh, left + barW - 8f, h * 0.72f, paint)
                    paint.color = Color.argb(180, 0, 229, 255)
                    canvas.drawRect(left + 6f, h * 0.72f - bh + 12f, left + barW - 14f, h * 0.72f - bh + 20f, paint)
                    if (shift > 0.5f) {
                        paint.color = Color.argb(130, 255, 179, 0)
                        canvas.drawCircle(left + barW * 0.5f, h * 0.72f - bh * 0.4f, 6f, paint)
                    }
                }

                // Perspective cyan floor grid
                paint.color = Color.argb(140, 0, 229, 255)
                paint.strokeWidth = 3f
                for (row in 0..8) {
                    val frac = ((row / 8f) + t * 2f) % 1f
                    val y = h * 0.72f + frac * frac * (h * 0.28f)
                    canvas.drawLine(0f, y, w, y, paint)
                }
            }

            VisualTheme.GOLDEN_HORIZON -> {
                paint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(Color.rgb(34, 16, 24), Color.rgb(120, 44, 28), Color.rgb(255, 168, 52)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                val sunY = h * (0.42f - 0.08f * sin(t * PI).toFloat())
                paint.color = Color.argb(230, 255, 206, 86)
                canvas.drawCircle(w * 0.5f, sunY, w * 0.24f, paint)

                // Layered mountain silhouettes
                for (layer in 0..2) {
                    val path = Path()
                    val baseY = h * (0.58f + layer * 0.12f)
                    path.moveTo(0f, h)
                    path.lineTo(0f, baseY)
                    val steps = 8
                    for (s in 0..steps) {
                        val px = (s.toFloat() / steps) * w
                        val py = baseY - h * 0.08f * sin((s * 0.9f) + layer * 1.4f + t * (layer + 1) * 2f).toFloat()
                        path.lineTo(px, py)
                    }
                    path.lineTo(w, h)
                    path.close()
                    paint.color = when (layer) {
                        0 -> Color.rgb(66, 28, 36)
                        1 -> Color.rgb(38, 18, 28)
                        else -> Color.rgb(16, 12, 20)
                    }
                    canvas.drawPath(path, paint)
                }
            }

            VisualTheme.KINETIC_PULSE -> {
                paint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(Color.rgb(11, 13, 22), Color.rgb(24, 18, 48), Color.rgb(11, 13, 22)),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                // Kinetic radial rings + equalizer bars
                val cx = w * 0.5f
                val cy = h * 0.45f
                paint.style = Paint.Style.STROKE
                for (ring in 1..4) {
                    val phase = (t * 2f + ring * 0.25f) % 1f
                    paint.strokeWidth = 6f * (1f - phase) + 2f
                    paint.color = if (ring % 2 == 0) {
                        Color.argb(((1f - phase) * 220).toInt(), 0, 229, 255)
                    } else {
                        Color.argb(((1f - phase) * 220).toInt(), 255, 77, 109)
                    }
                    canvas.drawCircle(cx, cy, w * (0.12f + phase * 0.36f), paint)
                }
                paint.style = Paint.Style.FILL
                val bars = 18
                val gap = w * 0.012f
                val bw = (w * 0.8f - gap * bars) / bars
                val startX = w * 0.1f
                for (b in 0 until bars) {
                    val amp = 0.2f + 0.8f * absSin(b * 0.65f + t * 10f)
                    val bh = h * 0.18f * amp
                    paint.color = if (b % 2 == 0) Color.rgb(0, 229, 255) else Color.rgb(255, 179, 0)
                    val left = startX + b * (bw + gap)
                    canvas.drawRoundRect(
                        RectF(left, h * 0.78f - bh, left + bw, h * 0.78f + bh * 0.25f),
                        bw * 0.5f,
                        bw * 0.5f,
                        paint
                    )
                }
            }
        }

        // Subtle studio frame counter tick at bottom corner
        paint.color = Color.argb(140, 244, 246, 250)
        paint.textSize = 24f
        canvas.drawText("CUTFORGE SAMPLE • ${(t * 100).toInt()}%", 36f, h - 42f, paint)
    }

    private fun absSin(v: Float): Float = kotlin.math.abs(sin(v.toDouble())).toFloat()

    private fun writeSynthesizedWav(file: File, preset: AudioPreset) {
        val sampleRate = 44_100
        val totalSamples = ((sampleRate * preset.durationMs) / 1000L).toInt()
        val dataSize = totalSamples * 2 // 16-bit mono
        val pcm = ByteArray(dataSize)
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val sample = when (preset) {
                AudioPreset.CYBER_BEAT -> {
                    val beatPhase = (t * 2.0) % 1.0 // 120 BPM
                    val kick = sin(2.0 * PI * (110.0 - 70.0 * beatPhase) * t) * kotlin.math.exp(-beatPhase * 9.0)
                    val arpFreq = when (((t * 4.0).toInt()) % 4) {
                        0 -> 220.0
                        1 -> 277.18
                        2 -> 329.63
                        else -> 440.0
                    }
                    val arp = sin(2.0 * PI * arpFreq * t) * 0.22 * (1.0 - ((t * 4.0) % 1.0) * 0.6)
                    (kick * 0.55 + arp).coerceIn(-0.95, 0.95)
                }
                AudioPreset.AMBIENT_PAD -> {
                    val chord = sin(2.0 * PI * 196.0 * t) * 0.25 +
                        sin(2.0 * PI * 246.94 * t) * 0.20 +
                        sin(2.0 * PI * 293.66 * t) * 0.18 +
                        sin(2.0 * PI * 392.0 * t) * 0.12
                    val lfo = 0.7 + 0.3 * sin(2.0 * PI * 0.35 * t)
                    (chord * lfo).coerceIn(-0.95, 0.95)
                }
                AudioPreset.CINEMA_RISER -> {
                    val prog = (t / (preset.durationMs / 1000.0)).coerceIn(0.0, 1.0)
                    val freq = 55.0 + 480.0 * prog * prog
                    val env = if (prog < 0.88) prog else (1.0 - (prog - 0.88) / 0.12).coerceAtLeast(0.0)
                    (sin(2.0 * PI * freq * t) * 0.65 * env + cos(2.0 * PI * (freq * 0.5) * t) * 0.25 * env)
                        .coerceIn(-0.95, 0.95)
                }
                AudioPreset.WHOOSH_SFX -> {
                    val prog = (t / (preset.durationMs / 1000.0)).coerceIn(0.0, 1.0)
                    val env = sin(PI * prog)
                    val sweep = sin(2.0 * PI * (120.0 + 520.0 * env) * t) * 0.4
                    val air = sin(i * 1.618) * cos(i * 0.73) * 0.5
                    ((sweep + air) * env).coerceIn(-0.95, 0.95)
                }
                AudioPreset.SHUTTER_CLICK -> {
                    val clickEnv = kotlin.math.exp(-t * 48.0) + if (t > 0.08) kotlin.math.exp(-(t - 0.08) * 55.0) * 0.7 else 0.0
                    val transient = sin(2.0 * PI * 920.0 * t) * 0.5 + sin(i * 2.13) * 0.4
                    (transient * clickEnv).coerceIn(-0.95, 0.95)
                }
                AudioPreset.LOFI_BEAT -> {
                    val beat = (t * 1.5) % 1.0 // 90 BPM
                    val kick = sin(2.0 * PI * (95.0 - 55.0 * beat) * t) * kotlin.math.exp(-beat * 11.0)
                    val snarePhase = ((t * 1.5) + 0.5) % 1.0
                    val snare = if (snarePhase < 0.15) sin(i * 1.91) * kotlin.math.exp(-snarePhase * 28.0) * 0.45 else 0.0
                    val warmPad = (sin(2.0 * PI * 261.63 * t) + sin(2.0 * PI * 329.63 * t) + sin(2.0 * PI * 392.0 * t)) * 0.11
                    (kick * 0.5 + snare + warmPad).coerceIn(-0.95, 0.95)
                }
            }
            buf.putShort((sample * Short.MAX_VALUE).toInt().toShort())
        }

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM chunk size
            header.putShort(1) // PCM format
            header.putShort(1) // Mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // Byte rate
            header.putShort(2) // Block align
            header.putShort(16) // Bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())
            out.write(pcm)
        }
    }
}
