package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.AudioTrackItem
import com.example.data.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

data class ExportedVideoMeta(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val lastModifiedMs: Long,
    val shareUri: Uri
)

data class MediaStreamSummary(
    val displayName: String,
    val isAudioOnly: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Long,
    val videoCodec: String?,
    val audioCodec: String?,
    val sampleRateHz: Int,
    val channels: Int
)

object MediaInspector {

    suspend fun importVisualUri(context: Context, uri: Uri): VideoClip? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: ""
            val isImage = mime.startsWith("image/") ||
                uri.toString().lowercase().let {
                    it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
                }
            val displayName = queryDisplayName(context, uri) ?: if (isImage) "Photo Clip" else "Video Clip"
            val ext = when {
                isImage -> ".png"
                mime.contains("webm") -> ".webm"
                mime.contains("3gp") -> ".3gp"
                else -> ".mp4"
            }

            val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
            val destFile = File(mediaDir, "${UUID.randomUUID()}$ext")

            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
            }.getOrDefault(false)

            val finalUriString = if (copied && destFile.length() > 0L) {
                Uri.fromFile(destFile).toString()
            } else {
                uri.toString()
            }

            if (isImage) {
                return@withContext VideoClip(
                    uriString = finalUriString,
                    displayName = displayName,
                    isImage = true,
                    sourceDurationMs = 4000L,
                    trimStartMs = 0L,
                    trimEndMs = 4000L,
                    waveformPeaks = emptyList()
                )
            }

            val durationMs = extractDurationMs(context, Uri.parse(finalUriString)).coerceAtLeast(1000L)
            val peaks = extractWaveformPeaks(context, Uri.parse(finalUriString), targetCount = 48)
            VideoClip(
                uriString = finalUriString,
                displayName = displayName,
                isImage = false,
                sourceDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                waveformPeaks = peaks
            )
        }

    suspend fun copyImageUriLocal(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "overlays").apply { mkdirs() }
            val dest = File(dir, "pip_${UUID.randomUUID()}.png")
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                    true
                } ?: false
            }.getOrDefault(false)
            if (ok && dest.length() > 0L) Uri.fromFile(dest).toString() else uri.toString()
        }

    suspend fun importAudioUri(context: Context, uri: Uri): AudioTrackItem? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(context, uri) ?: "Audio Track"
            val ext = displayName.substringAfterLast('.', "m4a").lowercase()
            val mediaDir = File(context.filesDir, "audio").apply { mkdirs() }
            val destFile = File(mediaDir, "${UUID.randomUUID()}.$ext")

            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
            }.getOrDefault(false)

            val finalUri = if (copied && destFile.length() > 0L) {
                Uri.fromFile(destFile)
            } else {
                uri
            }

            val durationMs = extractDurationMs(context, finalUri).coerceAtLeast(2000L)
            val peaks = extractWaveformPeaks(context, finalUri, targetCount = 72)

            AudioTrackItem(
                uriString = finalUri.toString(),
                title = displayName.substringBeforeLast('.'),
                sourceDurationMs = durationMs,
                durationMs = durationMs,
                waveformPeaks = peaks
            )
        }

    suspend fun createClipFromLocalFile(
        context: Context,
        file: File,
        title: String,
        isImage: Boolean,
        fallbackDurationMs: Long
    ): VideoClip = withContext(Dispatchers.IO) {
        val uri = Uri.fromFile(file)
        val durationMs = if (isImage) {
            fallbackDurationMs
        } else {
            extractDurationMs(context, uri).coerceAtLeast(1000L)
        }
        val peaks = if (isImage) emptyList() else extractWaveformPeaks(context, uri, targetCount = 48)
        VideoClip(
            uriString = uri.toString(),
            displayName = title,
            isImage = isImage,
            sourceDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            waveformPeaks = peaks
        )
    }

    suspend fun createAudioFromLocalFile(
        context: Context,
        file: File,
        title: String,
        fallbackDurationMs: Long,
        isVoiceover: Boolean = false
    ): AudioTrackItem = withContext(Dispatchers.IO) {
        val uri = Uri.fromFile(file)
        val durationMs = extractDurationMs(context, uri).coerceAtLeast(fallbackDurationMs)
        val peaks = extractWaveformPeaks(context, uri, targetCount = 64)
        AudioTrackItem(
            uriString = uri.toString(),
            title = title,
            sourceDurationMs = durationMs,
            durationMs = durationMs,
            isVoiceover = isVoiceover,
            isLooping = !isVoiceover && durationMs >= 3000L,
            waveformPeaks = peaks
        )
    }

    suspend fun captureFreezeFrame(
        context: Context,
        clip: VideoClip,
        sourceTimeMs: Long
    ): VideoClip? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(clip.uriString)
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val outFile = File(mediaDir, "freeze_${UUID.randomUUID()}.png")

        val bmp: Bitmap = if (clip.isImage) {
            decodeScaledBitmap(context, uri, 1080) ?: return@withContext null
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                setDataSourceSafe(context, retriever, uri)
                retriever.getFrameAtTime(sourceTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L)
                    ?: return@withContext null
            } catch (_: Throwable) {
                return@withContext null
            } finally {
                runCatching { retriever.release() }
            }
        }

        val saved = runCatching {
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bmp.recycle()
            true
        }.getOrDefault(false)

        if (!saved) return@withContext null

        VideoClip(
            uriString = Uri.fromFile(outFile).toString(),
            displayName = "${clip.displayName} (Freeze)",
            isImage = true,
            sourceDurationMs = 2500L,
            trimStartMs = 0L,
            trimEndMs = 2500L,
            colorGrade = clip.colorGrade
        )
    }

    suspend fun detachAudioFromClip(
        context: Context,
        clip: VideoClip,
        timelineStartMs: Long
    ): AudioTrackItem? = withContext(Dispatchers.IO) {
        if (clip.isImage) return@withContext null
        val uri = Uri.parse(clip.uriString)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val outFile = File(audioDir, "detached_${UUID.randomUUID()}.m4a")

        try {
            if (uri.scheme == "file" && uri.path != null) {
                extractor.setDataSource(uri.path!!)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) return@withContext null

            extractor.selectTrack(audioTrackIdx)
            extractor.seekTo(clip.trimStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(128 * 1024)
            val info = MediaCodec.BufferInfo()
            val endUs = clip.trimEndMs * 1000L
            val baseUs = clip.trimStartMs * 1000L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = (sampleTimeUs - baseUs).coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
        } catch (_: Throwable) {
            outFile.delete()
            return@withContext null
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }

        if (!outFile.exists() || outFile.length() < 512L) {
            outFile.delete()
            return@withContext null
        }
        val detachedUri = Uri.fromFile(outFile)
        val dur = extractDurationMs(context, detachedUri).coerceAtLeast(clip.effectiveDurationMs)
        val peaks = extractWaveformPeaks(context, detachedUri, 64)
        AudioTrackItem(
            uriString = detachedUri.toString(),
            title = "${clip.displayName} Audio",
            startMs = timelineStartMs,
            sourceDurationMs = dur,
            durationMs = dur,
            volume = clip.volume,
            isLooping = false,
            waveformPeaks = peaks
        )
    }

    suspend fun computeLuminanceHistogram(
        context: Context,
        clip: VideoClip,
        sourceTimeMs: Long,
        bins: Int = 24
    ): List<Float> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(clip.uriString)
        val bmp: Bitmap? = if (clip.isImage) {
            decodeScaledBitmap(context, uri, 96)
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                setDataSourceSafe(context, retriever, uri)
                retriever.getFrameAtTime(sourceTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { raw ->
                        val scaled = Bitmap.createScaledBitmap(raw, 72, 72, false)
                        if (scaled != raw) raw.recycle()
                        scaled
                    }
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        if (bmp == null) return@withContext List(bins) { 0.25f }

        val counts = IntArray(bins)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()

        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val bin = (luma * (bins - 1)).toInt().coerceIn(0, bins - 1)
            counts[bin]++
        }
        val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        counts.map { (it.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1f) }
    }

    suspend fun listExportedVideos(context: Context): List<ExportedVideoMeta> =
        withContext(Dispatchers.IO) {
            val exportDir = File(context.filesDir, "exports")
            if (!exportDir.exists()) return@withContext emptyList()
            val files = exportDir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
                ?: return@withContext emptyList()

            files.sortedByDescending { it.lastModified() }.map { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val dur = extractDurationMs(context, Uri.fromFile(file))
                ExportedVideoMeta(
                    file = file,
                    name = file.name,
                    sizeBytes = file.length(),
                    durationMs = dur,
                    lastModifiedMs = file.lastModified(),
                    shareUri = uri
                )
            }
        }

    suspend fun extractClipFilmstrip(
        context: Context,
        clip: VideoClip,
        frameCount: Int = 5
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(clip.uriString)
        if (clip.isImage) {
            val bmp = decodeScaledBitmap(context, uri, maxWidth = 160, preferRgb565 = true)
            return@withContext if (bmp != null) List(frameCount.coerceAtLeast(1)) { bmp } else emptyList()
        }

        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            setDataSourceSafe(context, retriever, uri)
            val span = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(200L)
            for (i in 0 until frameCount) {
                val frac = if (frameCount <= 1) 0f else i.toFloat() / (frameCount - 1).toFloat()
                val timeUs = (clip.trimStartMs + (span * frac).toLong()) * 1000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null) {
                    val targetW = 140
                    val targetH = ((raw.height.toFloat() / raw.width.coerceAtLeast(1)) * targetW)
                        .toInt().coerceIn(64, 180)
                    val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                    if (scaled != raw) raw.recycle()
                    frames.add(scaled)
                }
            }
        } catch (_: Throwable) {
            // Gracefully return whatever frames succeeded
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    suspend fun saveProjectCoverThumbnail(context: Context, projectId: String, clip: VideoClip): String? =
        withContext(Dispatchers.IO) {
            val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val outFile = File(thumbDir, "$projectId.jpg")
            val uri = Uri.parse(clip.uriString)
            val bmp = if (clip.isImage) {
                decodeScaledBitmap(context, uri, 480, preferRgb565 = true)
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    setDataSourceSafe(context, retriever, uri)
                    val raw = retriever.getFrameAtTime(clip.trimStartMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    raw?.let {
                        val w = 480
                        val h = ((it.height.toFloat() / it.width.coerceAtLeast(1)) * w).toInt().coerceIn(240, 720)
                        val scaled = Bitmap.createScaledBitmap(it, w, h, true)
                        if (scaled != it) it.recycle()
                        scaled
                    }
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            } ?: return@withContext null

            runCatching {
                FileOutputStream(outFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
                }
                bmp.recycle()
                outFile.absolutePath
            }.getOrNull()
        }

    private fun extractDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            setDataSourceSafe(context, retriever, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 4000L
        } catch (_: Throwable) {
            4000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractWaveformPeaks(context: Context, uri: Uri, targetCount: Int): List<Float> {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null && path.endsWith(".wav", ignoreCase = true)) {
            val wavPeaks = runCatching { sampleWavPeaks(File(path), targetCount) }.getOrNull()
            if (!wavPeaks.isNullOrEmpty()) return wavPeaks
        }

        val extractor = MediaExtractor()
        try {
            if (path != null) {
                extractor.setDataSource(path)
            } else {
                extractor.setDataSource(context, uri, null)
            }
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack >= 0) {
                extractor.selectTrack(audioTrack)
                val sizes = mutableListOf<Int>()
                val buf = ByteBuffer.allocate(32 * 1024)
                while (sizes.size < 800) {
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0) break
                    sizes.add(read)
                    extractor.advance()
                }
                if (sizes.isNotEmpty()) {
                    val maxSize = sizes.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val bucketSize = (sizes.size.toFloat() / targetCount).coerceAtLeast(1f)
                    return List(targetCount) { idx ->
                        val start = (idx * bucketSize).toInt().coerceIn(0, sizes.lastIndex)
                        val end = ((idx + 1) * bucketSize).toInt().coerceIn(start + 1, sizes.size)
                        val avg = sizes.subList(start, end).average().toFloat()
                        (avg / maxSize.toFloat()).coerceIn(0.12f, 1.0f)
                    }
                }
            }
        } catch (_: Throwable) {
            // Fall through
        } finally {
            runCatching { extractor.release() }
        }

        return List(targetCount) { i ->
            (0.22f + 0.48f * abs(sin(i * 0.55f))).coerceIn(0.15f, 0.92f)
        }
    }

    private fun sampleWavPeaks(file: File, targetCount: Int): List<Float> {
        if (!file.exists() || file.length() <= 44L) return emptyList()
        val dataBytes = file.length() - 44L
        val totalSamples = (dataBytes / 2L).toInt().coerceAtLeast(1)
        val step = (totalSamples / targetCount).coerceAtLeast(1)
        val peaks = FloatArray(targetCount)
        FileInputStream(file).use { fis ->
            fis.skip(44L)
            val chunk = ByteArray(4096)
            var sampleIndex = 0
            while (true) {
                val read = fis.read(chunk)
                if (read <= 1) break
                val bb = ByteBuffer.wrap(chunk, 0, read - (read % 2)).order(ByteOrder.LITTLE_ENDIAN)
                while (bb.hasRemaining()) {
                    val amp = abs(bb.short.toInt()) / 32768f
                    val bucket = (sampleIndex / step).coerceIn(0, targetCount - 1)
                    if (amp > peaks[bucket]) peaks[bucket] = amp
                    sampleIndex++
                }
            }
        }
        return peaks.map { it.coerceIn(0.10f, 1.0f) }
    }

    private fun decodeScaledBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        preferRgb565: Boolean = false
    ): Bitmap? {
        return runCatching {
            val stream1 = context.contentResolver.openInputStream(uri) ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream1.use { BitmapFactory.decodeStream(it, null, opts) }
            val sampleSize = (opts.outWidth / maxWidth.coerceAtLeast(1)).coerceAtLeast(1)
            val stream2 = context.contentResolver.openInputStream(uri) ?: return null
            stream2.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        if (preferRgb565) inPreferredConfig = Bitmap.Config.RGB_565
                    }
                )
            }
        }.getOrNull()
    }

    private fun setDataSourceSafe(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "file" && uri.path != null) {
            retriever.setDataSource(uri.path)
        } else {
            retriever.setDataSource(context, uri)
        }
    }

    suspend fun inspectStreamSummary(context: Context, uri: Uri): MediaStreamSummary =
        withContext(Dispatchers.IO) {
            val name = queryDisplayName(context, uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "Media Stream"
            val retriever = MediaMetadataRetriever()
            var width = 0
            var height = 0
            var durationMs = 0L
            var bitrateBps = 0L
            var hasVideo = false
            try {
                setDataSourceSafe(context, retriever, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" || (width > 0 && height > 0)
            } catch (_: Throwable) {
            } finally {
                runCatching { retriever.release() }
            }

            var videoCodec: String? = null
            var audioCodec: String? = null
            var sampleRateHz = 0
            var channels = 0
            val extractor = MediaExtractor()
            try {
                val path = if (uri.scheme == "file") uri.path else null
                if (path != null) extractor.setDataSource(path) else extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") && videoCodec == null) {
                        videoCodec = mime.removePrefix("video/").uppercase()
                        hasVideo = true
                        if (width == 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                        if (height == 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    } else if (mime.startsWith("audio/") && audioCodec == null) {
                        audioCodec = mime.removePrefix("audio/").uppercase()
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRateHz = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { extractor.release() }
            }

            MediaStreamSummary(
                displayName = name,
                isAudioOnly = !hasVideo,
                durationMs = durationMs,
                width = width,
                height = height,
                bitrateBps = bitrateBps,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                sampleRateHz = sampleRateHz,
                channels = channels
            )
        }

    suspend fun saveBitmapSnapshot(
        context: Context,
        bitmap: Bitmap,
        namePrefix: String = "frame"
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val clean = namePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24).ifBlank { "frame" }
        val outFile = File(dir, "${clean}_${System.currentTimeMillis()}.png")
        val saved = runCatching {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
            }
            true
        }.getOrDefault(false)
        if (saved && outFile.length() > 0L) outFile else null
    }

    suspend fun captureVideoSnapshot(
        context: Context,
        uri: Uri,
        positionMs: Long
    ): File? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val bmp = try {
            setDataSourceSafe(context, retriever, uri)
            retriever.getFrameAtTime(positionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        } ?: return@withContext null

        val file = saveBitmapSnapshot(context, bmp, "snap")
        bmp.recycle()
        file
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }
}
