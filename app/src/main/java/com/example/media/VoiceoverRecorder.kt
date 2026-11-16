package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class VoiceoverRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var meterJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    fun startRecording(scope: CoroutineScope): Boolean {
        if (_isRecording.value) return false
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val outFile = File(dir, "vo_${UUID.randomUUID()}.m4a")
        currentFile = outFile

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return runCatching {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _elapsedMs.value = 0L

            meterJob?.cancel()
            meterJob = scope.launch(Dispatchers.Main) {
                while (isActive && _isRecording.value) {
                    _elapsedMs.value = System.currentTimeMillis() - startTimeMs
                    val amp = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)
                    _amplitude.value = amp.coerceIn(0f, 1f)
                    delay(80L)
                }
            }
            true
        }.getOrElse {
            runCatching { mr.release() }
            recorder = null
            currentFile = null
            _isRecording.value = false
            false
        }
    }

    fun stopRecording(): File? {
        meterJob?.cancel()
        meterJob = null
        val file = currentFile
        val mr = recorder
        recorder = null
        currentFile = null
        _isRecording.value = false
        _amplitude.value = 0f

        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        return if (file != null && file.exists() && file.length() > 512L) file else null
    }

    fun release() {
        if (_isRecording.value) {
            stopRecording()
        }
    }
}
