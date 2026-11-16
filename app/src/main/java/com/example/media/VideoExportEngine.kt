package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.model.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(
        val progressPercent: Int,
        val elapsedMs: Long,
        val outputPath: String
    ) : ExportUiState

    data class Completed(
        val outputFile: File,
        val shareUri: Uri,
        val fileSizeBytes: Long,
        val durationMs: Long,
        val savedToGallery: Boolean
    ) : ExportUiState

    data class Failed(val message: String) : ExportUiState
}

@OptIn(UnstableApi::class)
class VideoExportEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    fun startExport(project: VideoProject) {
        if (project.clips.isEmpty()) {
            _exportState.value = ExportUiState.Failed("Add at least one clip before exporting.")
            return
        }

        val (width, height) = Media3EffectFactory.computeExportDimensions(project)
        val composition = Media3EffectFactory.buildComposition(
            context = context,
            project = project,
            forExport = true,
            exportWidth = width,
            exportHeight = height
        ) ?: run {
            _exportState.value = ExportUiState.Failed("Unable to build composition timeline.")
            return
        }

        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = project.title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(20).ifBlank { "CutForge" }
        val outputFile = File(exportDir, "${safeTitle}_$stamp.mp4")
        if (outputFile.exists()) outputFile.delete()

        val startTime = System.currentTimeMillis()
        _exportState.value = ExportUiState.Exporting(0, 0L, outputFile.absolutePath)

        mainHandler.post {
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopPolling()
                    val size = outputFile.length()
                    val savedGallery = saveToMoviesMediaStore(outputFile)
                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile
                    )
                    _exportState.value = ExportUiState.Completed(
                        outputFile = outputFile,
                        shareUri = shareUri,
                        fileSizeBytes = size,
                        durationMs = exportResult.durationMs.takeIf { it > 0 } ?: project.totalDurationMs,
                        savedToGallery = savedGallery
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopPolling()
                    _exportState.value = ExportUiState.Failed(
                        exportException.localizedMessage ?: "Hardware encoder failed during export."
                    )
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(project.exportSettings.codec.mimeType)
                .addListener(listener)
                .build()

            activeTransformer = transformer
            runCatching {
                transformer.start(composition, outputFile.absolutePath)
                startProgressPolling(transformer, outputFile.absolutePath, startTime)
            }.onFailure { err ->
                stopPolling()
                _exportState.value = ExportUiState.Failed(err.localizedMessage ?: "Failed to start export.")
            }
        }
    }

    fun cancelExport() {
        mainHandler.post {
            stopPolling()
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
            _exportState.value = ExportUiState.Idle
        }
    }

    fun resetState() {
        _exportState.value = ExportUiState.Idle
    }

    suspend fun saveFileToGallery(file: File): Boolean = withContext(Dispatchers.IO) {
        saveToMoviesMediaStore(file)
    }

    private fun startProgressPolling(transformer: Transformer, outputPath: String, startTime: Long) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportState.value = ExportUiState.Exporting(
                        progressPercent = holder.progress.coerceIn(0, 99),
                        elapsedMs = System.currentTimeMillis() - startTime,
                        outputPath = outputPath
                    )
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 200L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 150L)
    }

    private fun stopPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun saveToMoviesMediaStore(mp4File: File): Boolean {
        if (!mp4File.exists() || mp4File.length() == 0L) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, mp4File.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/CutForge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(mp4File).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }
}
