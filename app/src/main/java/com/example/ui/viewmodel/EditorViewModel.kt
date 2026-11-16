package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioPreferences
import com.example.data.local.StudioSettings
import com.example.data.model.AspectRatioPreset
import com.example.data.model.AudioTrackItem
import com.example.data.model.BadgeStickerType
import com.example.data.model.BitrateTier
import com.example.data.model.CanvasFillStyle
import com.example.data.model.ColorGradeConfig
import com.example.data.model.ColorPreset
import com.example.data.model.ExportCodec
import com.example.data.model.ExportResolution
import com.example.data.model.OverlayStickerItem
import com.example.data.model.SpeedCurvePreset
import com.example.data.model.TextAnimation
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoClip
import com.example.data.model.VideoProject
import com.example.data.model.VisualFxType
import com.example.data.model.clipWindows
import com.example.media.ExportUiState
import com.example.media.MediaInspector
import com.example.media.SampleMediaGenerator
import com.example.media.TimecodeFormatter
import com.example.media.TimelineOperations
import com.example.media.VideoExportEngine
import com.example.media.VoiceoverRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class EditorToolTab(val label: String) {
    EDIT("Cut"),
    MOTION("Motion"),
    FX("FX"),
    COLOR("Color"),
    TRANSITION("Trans"),
    TEXT("Text"),
    OVERLAY("PiP"),
    AUDIO("Audio"),
    CANVAS("Ratio")
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProjectRepository(AppDatabase.getInstance(application).projectDao())
    private val prefs = StudioPreferences(application)
    private val exportEngine = VideoExportEngine(application)
    private val voiceoverRecorder = VoiceoverRecorder(application)

    private val _project = MutableStateFlow<VideoProject?>(null)
    val project: StateFlow<VideoProject?> = _project.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _activeTool = MutableStateFlow(EditorToolTab.EDIT)
    val activeTool: StateFlow<EditorToolTab> = _activeTool.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedOverlayId = MutableStateFlow<String?>(null)
    val selectedOverlayId: StateFlow<String?> = _selectedOverlayId.asStateFlow()

    private val _selectedStickerId = MutableStateFlow<String?>(null)
    val selectedStickerId: StateFlow<String?> = _selectedStickerId.asStateFlow()

    private val _selectedAudioId = MutableStateFlow<String?>(null)
    val selectedAudioId: StateFlow<String?> = _selectedAudioId.asStateFlow()

    private val _timelineZoomDpPerSec = MutableStateFlow(56f)
    val timelineZoomDpPerSec: StateFlow<Float> = _timelineZoomDpPerSec.asStateFlow()

    private val _filmstrips = MutableStateFlow<Map<String, List<Bitmap>>>(emptyMap())
    val filmstrips: StateFlow<Map<String, List<Bitmap>>> = _filmstrips.asStateFlow()

    private val _histogramBins = MutableStateFlow<List<Float>>(List(24) { 0.25f })
    val histogramBins: StateFlow<List<Float>> = _histogramBins.asStateFlow()

    private val _monitorSpeed = MutableStateFlow(1.0f)
    val monitorSpeed: StateFlow<Float> = _monitorSpeed.asStateFlow()

    private val _monitorVolume = MutableStateFlow(1.0f)
    val monitorVolume: StateFlow<Float> = _monitorVolume.asStateFlow()

    private val _stageBrightness = MutableStateFlow(1.0f)
    val stageBrightness: StateFlow<Float> = _stageBrightness.asStateFlow()

    private val _stageFillMode = MutableStateFlow(false)
    val stageFillMode: StateFlow<Boolean> = _stageFillMode.asStateFlow()

    private val _activeClipTelemetry = MutableStateFlow("NO SIGNAL")
    val activeClipTelemetry: StateFlow<String> = _activeClipTelemetry.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val exportState: StateFlow<ExportUiState> = exportEngine.exportState
    val isRecordingVoiceover: StateFlow<Boolean> = voiceoverRecorder.isRecording
    val voiceoverElapsedMs: StateFlow<Long> = voiceoverRecorder.elapsedMs
    val voiceoverAmplitude: StateFlow<Float> = voiceoverRecorder.amplitude

    val studioSettings: StateFlow<StudioSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val undoStack = ArrayDeque<VideoProject>()
    private val redoStack = ArrayDeque<VideoProject>()

    private var videoPlayer: ExoPlayer? = null
    private val audioPlayers = mutableMapOf<String, ExoPlayer>()
    private val loadedAudioUris = mutableMapOf<String, String>()
    private var attachedTextureView: TextureView? = null
    private var loadedClipUri: String? = null
    private var playbackTickerJob: Job? = null
    private var saveDebounceJob: Job? = null

    init {
        videoPlayer = buildResilientExoPlayer(repeat = false).apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    _toastMessage.value = "Playback error: ${error.errorCodeName}"
                    pausePlayback()
                }
            })
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildResilientExoPlayer(repeat: Boolean): ExoPlayer {
        val ctx = getApplication<Application>()
        val renderersFactory = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(ctx, extractorsFactory)
        return ExoPlayer.Builder(ctx, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val loaded = repo.getProject(projectId)
            if (loaded != null) {
                undoStack.clear()
                redoStack.clear()
                updateUndoRedoFlags()
                _project.value = loaded
                _selectedClipId.value = loaded.clips.firstOrNull()?.id
                _selectedAudioId.value = loaded.audioTracks.firstOrNull()?.id
                _selectedStickerId.value = loaded.stickers.firstOrNull()?.id
                _playheadMs.value = 0L
                extractMissingFilmstrips(loaded.clips)
                syncPlayersToPlayhead(0L, shouldPlay = false)
                refreshHistogram()
                refreshActiveClipTelemetry(0L)
            }
        }
    }

    fun attachTextureView(textureView: TextureView) {
        if (attachedTextureView !== textureView) {
            attachedTextureView = textureView
            videoPlayer?.setVideoTextureView(textureView)
        }
    }

    fun detachTextureView(textureView: TextureView) {
        if (attachedTextureView === textureView) {
            videoPlayer?.clearVideoTextureView(textureView)
            attachedTextureView = null
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun selectTool(tab: EditorToolTab) {
        _activeTool.value = tab
        if (tab == EditorToolTab.COLOR) refreshHistogram()
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
        if (clipId != null) {
            _selectedOverlayId.value = null
            _selectedStickerId.value = null
        }
        if (_activeTool.value == EditorToolTab.COLOR) refreshHistogram()
    }

    fun selectOverlay(overlayId: String?) {
        _selectedOverlayId.value = overlayId
        if (overlayId != null) {
            _selectedStickerId.value = null
            _activeTool.value = EditorToolTab.TEXT
        }
    }

    fun selectSticker(stickerId: String?) {
        _selectedStickerId.value = stickerId
        if (stickerId != null) {
            _selectedOverlayId.value = null
            _activeTool.value = EditorToolTab.OVERLAY
        }
    }

    fun selectAudio(audioId: String?) {
        _selectedAudioId.value = audioId
        if (audioId != null) {
            _activeTool.value = EditorToolTab.AUDIO
        }
    }

    fun setTimelineZoom(dpPerSec: Float) {
        _timelineZoomDpPerSec.value = dpPerSec.coerceIn(28f, 140f)
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pausePlayback() else startPlayback()
    }

    fun cycleMonitorSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val cur = _monitorSpeed.value
        val next = speeds.firstOrNull { it > cur + 0.05f } ?: speeds.first()
        _monitorSpeed.value = next
        syncPlayersToPlayhead(_playheadMs.value, shouldPlay = _isPlaying.value)
    }

    fun setMonitorVolume(volume: Float) {
        _monitorVolume.value = volume.coerceIn(0f, 1f)
        syncPlayersToPlayhead(_playheadMs.value, shouldPlay = _isPlaying.value)
    }

    fun toggleMonitorMute() {
        _monitorVolume.value = if (_monitorVolume.value > 0.05f) 0f else 1.0f
        syncPlayersToPlayhead(_playheadMs.value, shouldPlay = _isPlaying.value)
    }

    fun setStageBrightness(brightness: Float) {
        _stageBrightness.value = brightness.coerceIn(0.2f, 1.0f)
    }

    fun toggleStageFillMode() {
        _stageFillMode.value = !_stageFillMode.value
    }

    fun seekRelativeMs(deltaMs: Long) {
        val proj = _project.value ?: return
        val wasPlaying = _isPlaying.value
        val target = (_playheadMs.value + deltaMs).coerceIn(0L, proj.totalDurationMs.coerceAtLeast(0L))
        _playheadMs.value = target
        syncPlayersToPlayhead(target, shouldPlay = wasPlaying)
        refreshActiveClipTelemetry(target)
    }

    fun captureCurrentStageSnapshot() {
        val proj = _project.value ?: return
        val win = TimelineOperations.findWindowAt(proj, _playheadMs.value) ?: return
        val tv = attachedTextureView
        viewModelScope.launch {
            val savedFile = if (!win.clip.isImage && tv != null && tv.isAvailable) {
                val bmp = tv.bitmap
                if (bmp != null) {
                    val file = MediaInspector.saveBitmapSnapshot(getApplication(), bmp, win.clip.displayName)
                    bmp.recycle()
                    file
                } else null
            } else {
                val localOffset = (_playheadMs.value - win.timelineStartMs).coerceAtLeast(0L)
                val sourceTime = win.clip.trimStartMs + (localOffset * win.clip.effectiveSpeed).toLong()
                MediaInspector.captureVideoSnapshot(getApplication(), Uri.parse(win.clip.uriString), sourceTime)
            }
            _toastMessage.value = if (savedFile != null) {
                "Snapshot saved (${savedFile.name})"
            } else {
                "Could not capture frame snapshot"
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackTickerJob?.cancel()
        videoPlayer?.pause()
        audioPlayers.values.forEach { it.pause() }
    }

    fun startPlayback() {
        val proj = _project.value ?: return
        if (proj.clips.isEmpty() || proj.totalDurationMs <= 0L) return
        if (_playheadMs.value >= proj.totalDurationMs - 40L) {
            _playheadMs.value = 0L
        }
        _isPlaying.value = true
        syncPlayersToPlayhead(_playheadMs.value, shouldPlay = true)

        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                delay(28L)
                val now = System.currentTimeMillis()
                val rawDt = (now - lastTick).coerceIn(1L, 100L)
                val dt = (rawDt * _monitorSpeed.value).toLong().coerceAtLeast(1L)
                lastTick = now
                val currentProj = _project.value ?: break
                val total = currentProj.totalDurationMs
                if (total <= 0L) {
                    pausePlayback()
                    break
                }
                val prevMs = _playheadMs.value
                val nextMs = prevMs + dt
                if (nextMs >= total) {
                    if (_isLooping.value) {
                        _playheadMs.value = 0L
                        syncPlayersToPlayhead(0L, shouldPlay = true)
                        refreshActiveClipTelemetry(0L)
                    } else {
                        _playheadMs.value = total
                        pausePlayback()
                        break
                    }
                } else {
                    _playheadMs.value = nextMs
                    val prevWin = TimelineOperations.findWindowAt(currentProj, prevMs)
                    val nextWin = TimelineOperations.findWindowAt(currentProj, nextMs)
                    if (prevWin?.clip?.id != nextWin?.clip?.id) {
                        syncPlayersToPlayhead(nextMs, shouldPlay = true)
                        refreshActiveClipTelemetry(nextMs)
                    } else {
                        updateAudioPlaybackDuringTick(currentProj, nextMs)
                    }
                }
            }
        }
    }

    fun scrubTo(ms: Long, applySnap: Boolean = false) {
        val proj = _project.value ?: return
        val raw = ms.coerceIn(0L, proj.totalDurationMs.coerceAtLeast(0L))
        val target = if (applySnap && studioSettings.value.magneticSnapEnabled) {
            TimelineOperations.snapToNearestBeatOrBoundary(proj, raw)
        } else {
            raw
        }
        if (_isPlaying.value) pausePlayback()
        _playheadMs.value = target
        syncPlayersToPlayhead(target, shouldPlay = false)
        refreshActiveClipTelemetry(target)
    }

    fun stepFrame(direction: Int) {
        val proj = _project.value ?: return
        val fps = proj.exportSettings.fps.coerceAtLeast(24)
        val stepMs = (1000L / fps).coerceAtLeast(16L)
        scrubTo(_playheadMs.value + direction * stepMs)
    }

    private fun refreshActiveClipTelemetry(playheadMs: Long) {
        val proj = _project.value ?: return
        val win = TimelineOperations.findWindowAt(proj, playheadMs)
        if (win == null) {
            _activeClipTelemetry.value = "NO SIGNAL"
            return
        }
        val clip = win.clip
        if (clip.isImage) {
            _activeClipTelemetry.value = "STILL • ${proj.aspectRatio.label}"
            return
        }
        viewModelScope.launch {
            val summary = MediaInspector.inspectStreamSummary(getApplication(), Uri.parse(clip.uriString))
            val parts = buildList {
                if (summary.width > 0 && summary.height > 0) add("${summary.width}x${summary.height}")
                summary.videoCodec?.let { add(it) }
                if (summary.sampleRateHz > 0) add("${summary.sampleRateHz / 1000}kHz")
                add("${String.format(Locale.US, "%.2f", clip.effectiveSpeed * _monitorSpeed.value)}x")
            }
            _activeClipTelemetry.value = parts.joinToString(" • ").ifBlank { clip.displayName }
        }
    }

    private fun syncPlayersToPlayhead(playheadMs: Long, shouldPlay: Boolean) {
        val proj = _project.value ?: return
        val window = TimelineOperations.findWindowAt(proj, playheadMs)
        val vp = videoPlayer
        val masterVol = _monitorVolume.value.coerceIn(0f, 1f)
        val mSpeed = _monitorSpeed.value.coerceIn(0.5f, 2.0f)

        if (window != null && vp != null) {
            val clip = window.clip
            if (clip.isImage) {
                vp.pause()
            } else {
                val localOffsetMs = (playheadMs - window.timelineStartMs).coerceAtLeast(0L)
                val sourcePosMs = (clip.trimStartMs + (localOffsetMs * clip.effectiveSpeed).toLong())
                    .coerceIn(clip.trimStartMs, clip.trimEndMs)

                if (loadedClipUri != clip.uriString) {
                    loadedClipUri = clip.uriString
                    vp.setMediaItem(MediaItem.fromUri(Uri.parse(clip.uriString)))
                    vp.prepare()
                }
                attachedTextureView?.let { vp.setVideoTextureView(it) }

                val hasDuckingAudio = proj.audioTracks.any {
                    !it.isMuted && it.duckOriginalAudio && (it.isLooping || playheadMs in it.startMs..(it.startMs + it.durationMs))
                }
                val vol = if (clip.isMuted) 0f else (if (hasDuckingAudio) clip.volume * 0.42f else clip.volume) * masterVol
                vp.volume = vol.coerceIn(0f, 1f)
                vp.playbackParameters = PlaybackParameters((clip.effectiveSpeed * mSpeed).coerceIn(0.25f, 4.0f))
                if (kotlin.math.abs(vp.currentPosition - sourcePosMs) > 85L || !shouldPlay) {
                    vp.seekTo(sourcePosMs)
                }
                if (shouldPlay) vp.play() else vp.pause()
            }
        } else {
            vp?.pause()
        }

        // Sync multi-track audio pool (up to 4 simultaneous tracks)
        val activeTracks = proj.audioTracks.take(4)
        val activeIds = activeTracks.map { it.id }.toSet()
        val staleIds = audioPlayers.keys.filterNot { it in activeIds }
        staleIds.forEach { staleId ->
            audioPlayers.remove(staleId)?.release()
            loadedAudioUris.remove(staleId)
        }

        activeTracks.forEach { track ->
            val player = audioPlayers.getOrPut(track.id) {
                buildResilientExoPlayer(repeat = track.isLooping)
            }
            player.repeatMode = if (track.isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            if (loadedAudioUris[track.id] != track.uriString) {
                loadedAudioUris[track.id] = track.uriString
                player.setMediaItem(MediaItem.fromUri(Uri.parse(track.uriString)))
                player.prepare()
            }
            val dur = track.durationMs.coerceAtLeast(500L)
            val inWindow = track.isLooping || playheadMs in track.startMs..(track.startMs + dur)
            val audioPos = if (track.isLooping) {
                (playheadMs % dur) + track.trimStartMs
            } else {
                (playheadMs - track.startMs + track.trimStartMs).coerceIn(0L, dur)
            }
            player.volume = if (track.isMuted) 0f else computeTrackEnvelopeGain(track, playheadMs) * masterVol
            player.playbackParameters = PlaybackParameters(mSpeed)
            if (kotlin.math.abs(player.currentPosition - audioPos) > 120L || !shouldPlay) {
                player.seekTo(audioPos)
            }
            if (shouldPlay && !track.isMuted && inWindow) {
                player.play()
            } else {
                player.pause()
            }
        }
    }

    private fun updateAudioPlaybackDuringTick(proj: VideoProject, playheadMs: Long) {
        val masterVol = _monitorVolume.value.coerceIn(0f, 1f)
        proj.audioTracks.take(4).forEach { track ->
            val player = audioPlayers[track.id] ?: return@forEach
            val dur = track.durationMs.coerceAtLeast(500L)
            val inWindow = track.isLooping || playheadMs in track.startMs..(track.startMs + dur)
            if (!track.isMuted && inWindow) {
                player.volume = computeTrackEnvelopeGain(track, playheadMs) * masterVol
                if (!player.isPlaying) {
                    val pos = if (track.isLooping) (playheadMs % dur) + track.trimStartMs else (playheadMs - track.startMs + track.trimStartMs)
                    player.seekTo(pos.coerceAtLeast(0L))
                    player.play()
                }
            } else if (player.isPlaying) {
                player.pause()
            }
        }
    }

    private fun computeTrackEnvelopeGain(track: com.example.data.model.AudioTrackItem, playheadMs: Long): Float {
        val dur = track.durationMs.coerceAtLeast(500L)
        val localMs = if (track.isLooping) playheadMs % dur else (playheadMs - track.startMs).coerceIn(0L, dur)
        var gain = track.volume.coerceIn(0f, 1.5f)
        if (track.fadeInMs > 0L && localMs < track.fadeInMs) {
            gain *= (localMs.toFloat() / track.fadeInMs.toFloat()).coerceIn(0f, 1f)
        }
        if (track.fadeOutMs > 0L && dur > track.fadeOutMs && localMs > (dur - track.fadeOutMs)) {
            val rem = (dur - localMs).coerceAtLeast(0L)
            gain *= (rem.toFloat() / track.fadeOutMs.toFloat()).coerceIn(0f, 1f)
        }
        return gain.coerceIn(0f, 1f)
    }

    fun undo() {
        val current = _project.value ?: return
        if (undoStack.isEmpty()) return
        pausePlayback()
        redoStack.addLast(current)
        applyProjectState(undoStack.removeLast())
    }

    fun redo() {
        val current = _project.value ?: return
        if (redoStack.isEmpty()) return
        pausePlayback()
        undoStack.addLast(current)
        applyProjectState(redoStack.removeLast())
    }

    private fun mutateProject(recordUndo: Boolean = true, transform: (VideoProject) -> VideoProject) {
        val current = _project.value ?: return
        val next = transform(current)
        if (next == current) return
        if (recordUndo) {
            undoStack.addLast(current)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
        }
        applyProjectState(next)
    }

    private fun applyProjectState(next: VideoProject) {
        _project.value = next
        if (_selectedClipId.value == null || next.clips.none { it.id == _selectedClipId.value }) {
            _selectedClipId.value = next.clips.firstOrNull()?.id
        }
        if (_playheadMs.value > next.totalDurationMs) {
            _playheadMs.value = next.totalDurationMs
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        pruneUnusedFilmstrips(next.clips)
        syncPlayersToPlayhead(_playheadMs.value, shouldPlay = _isPlaying.value)
        schedulePersist(next)
    }

    private fun pruneUnusedFilmstrips(activeClips: List<VideoClip>) {
        val activeIds = activeClips.mapTo(mutableSetOf()) { it.id }
        val currentMap = _filmstrips.value
        val staleIds = currentMap.keys.filterNot { it in activeIds }
        if (staleIds.isNotEmpty()) {
            val nextMap = currentMap.toMutableMap()
            val stillInUse = mutableSetOf<Bitmap>()
            activeIds.forEach { id -> currentMap[id]?.let { stillInUse.addAll(it) } }
            staleIds.forEach { staleId ->
                nextMap.remove(staleId)?.distinct()?.forEach { bmp ->
                    if (bmp !in stillInUse && !bmp.isRecycled) bmp.recycle()
                }
            }
            _filmstrips.value = nextMap
        }
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun schedulePersist(project: VideoProject) {
        saveDebounceJob?.cancel()
        saveDebounceJob = viewModelScope.launch {
            delay(350L)
            repo.saveProject(project)
        }
    }

    // --- Media Import ---
    fun importVisualUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val newClips = uris.mapNotNull { MediaInspector.importVisualUri(ctx, it) }
            if (newClips.isNotEmpty()) {
                mutateProject { it.copy(clips = it.clips + newClips) }
                _selectedClipId.value = newClips.last().id
                extractMissingFilmstrips(newClips)
                updateCoverThumbnailIfNeeded()
                _toastMessage.value = "Added ${newClips.size} clip(s)"
            }
        }
    }

    fun addSampleClips() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val samples = SampleMediaGenerator.ensureSampleClips(ctx)
            val newClips = samples.map { s ->
                MediaInspector.createClipFromLocalFile(ctx, s.file, s.title, s.isImage, s.durationMs)
            }
            mutateProject { it.copy(clips = it.clips + newClips) }
            _selectedClipId.value = newClips.firstOrNull()?.id
            extractMissingFilmstrips(newClips)
            updateCoverThumbnailIfNeeded()
            _toastMessage.value = "Added 3 sample clips"
        }
    }

    fun importAudioUri(uri: Uri) {
        viewModelScope.launch {
            val track = MediaInspector.importAudioUri(getApplication(), uri)
            if (track != null) {
                mutateProject { it.copy(audioTracks = it.audioTracks + track) }
                _selectedAudioId.value = track.id
                _toastMessage.value = "Added '${track.title}'"
            } else {
                _toastMessage.value = "Could not load audio file"
            }
        }
    }

    fun addOfflineAudioPreset(preset: SampleMediaGenerator.AudioPreset) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val asset = SampleMediaGenerator.ensureAudioPreset(ctx, preset)
            val track = MediaInspector.createAudioFromLocalFile(ctx, asset.file, asset.title, asset.durationMs)
            mutateProject { it.copy(audioTracks = it.audioTracks + track) }
            _selectedAudioId.value = track.id
            _toastMessage.value = "Added '${track.title}'"
        }
    }

    // --- Voiceover Recorder ---
    fun toggleVoiceoverRecording() {
        if (voiceoverRecorder.isRecording.value) {
            val file = voiceoverRecorder.stopRecording()
            if (file != null) {
                val startAt = _playheadMs.value
                viewModelScope.launch {
                    val track = MediaInspector.createAudioFromLocalFile(
                        context = getApplication(),
                        file = file,
                        title = "Voiceover ${TimecodeFormatter.formatDurationShort(startAt)}",
                        fallbackDurationMs = 1500L,
                        isVoiceover = true
                    ).copy(startMs = startAt, duckOriginalAudio = true, volume = 1.15f)
                    mutateProject { it.copy(audioTracks = it.audioTracks + track) }
                    _selectedAudioId.value = track.id
                    _toastMessage.value = "Voiceover recorded (${TimecodeFormatter.formatDurationShort(track.durationMs)})"
                }
            }
        } else {
            pausePlayback()
            val ok = voiceoverRecorder.startRecording(viewModelScope)
            if (!ok) {
                _toastMessage.value = "Unable to access microphone on this device"
            }
        }
    }

    // --- Pro Clip Operations ---
    fun splitAtPlayhead() {
        val current = _project.value ?: return
        val (updated, secondId) = TimelineOperations.splitClipAtPlayhead(current, _playheadMs.value)
        if (secondId == null) {
            _toastMessage.value = "Move playhead away from clip edges to split"
            return
        }
        pausePlayback()
        mutateProject { updated }
        _selectedClipId.value = secondId
        _toastMessage.value = "Split at ${TimecodeFormatter.formatTimecode(_playheadMs.value)}"
    }

    fun captureFreezeFrame() {
        val proj = _project.value ?: return
        val win = TimelineOperations.findWindowAt(proj, _playheadMs.value) ?: return
        val localOffset = (_playheadMs.value - win.timelineStartMs).coerceAtLeast(0L)
        val sourceTime = (win.clip.trimStartMs + (localOffset * win.clip.effectiveSpeed).toLong())
            .coerceIn(0L, win.clip.sourceDurationMs)
        pausePlayback()
        viewModelScope.launch {
            val freezeClip = MediaInspector.captureFreezeFrame(getApplication(), win.clip, sourceTime)
            if (freezeClip != null) {
                mutateProject { TimelineOperations.insertClipAfterCurrent(it, _playheadMs.value, freezeClip) }
                _selectedClipId.value = freezeClip.id
                extractMissingFilmstrips(listOf(freezeClip))
                _toastMessage.value = "Inserted 2.5s Freeze Frame"
            } else {
                _toastMessage.value = "Could not capture frame"
            }
        }
    }

    fun detachAudioFromSelectedClip() {
        val proj = _project.value ?: return
        val clipId = _selectedClipId.value ?: return
        val win = proj.clipWindows().firstOrNull { it.clip.id == clipId } ?: return
        if (win.clip.isImage) {
            _toastMessage.value = "Photo clips do not have an audio track"
            return
        }
        viewModelScope.launch {
            val detached = MediaInspector.detachAudioFromClip(getApplication(), win.clip, win.timelineStartMs)
            if (detached != null) {
                mutateProject { p ->
                    val mutedProj = TimelineOperations.updateClip(p, clipId) { it.copy(isMuted = true) }
                    mutedProj.copy(audioTracks = mutedProj.audioTracks + detached)
                }
                _selectedAudioId.value = detached.id
                _toastMessage.value = "Audio detached to A1 track"
            } else {
                _toastMessage.value = "No audio track found inside clip"
            }
        }
    }

    fun duplicateSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        val current = _project.value ?: return
        val (updated, copyId) = TimelineOperations.duplicateClip(current, clipId)
        if (copyId != null) {
            mutateProject { updated }
            _selectedClipId.value = copyId
        }
    }

    fun deleteSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        pausePlayback()
        mutateProject { TimelineOperations.deleteClip(it, clipId) }
    }

    fun moveSelectedClip(delta: Int) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { TimelineOperations.moveClip(it, clipId, delta) }
    }

    fun updateSelectedClipTrim(trimStartMs: Long, trimEndMs: Long) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { clip ->
                val maxEnd = clip.sourceDurationMs
                val safeStart = trimStartMs.coerceIn(0L, (maxEnd - 300L).coerceAtLeast(0L))
                val safeEnd = trimEndMs.coerceIn(safeStart + 300L, maxEnd)
                clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
            }
        }
    }

    fun setSelectedClipSpeed(speed: Float) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) {
                it.copy(speed = speed.coerceIn(0.25f, 4.0f), speedCurve = SpeedCurvePreset.LINEAR)
            }
        }
    }

    fun setSelectedClipSpeedCurve(curve: SpeedCurvePreset) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) {
                it.copy(speedCurve = curve, speed = 1.0f)
            }
        }
    }

    fun rotateSelectedClip() {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) {
                it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
            }
        }
    }

    fun toggleFlipHorizontal() {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { it.copy(flipHorizontal = !it.flipHorizontal) }
        }
    }

    fun toggleFlipVertical() {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { it.copy(flipVertical = !it.flipVertical) }
        }
    }

    fun updateClipMotion(
        zoomScale: Float? = null,
        panX: Float? = null,
        panY: Float? = null,
        kenBurnsEndScale: Float? = null
    ) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { clip ->
                clip.copy(
                    zoomScale = (zoomScale ?: clip.zoomScale).coerceIn(1.0f, 3.0f),
                    panX = (panX ?: clip.panX).coerceIn(-0.45f, 0.45f),
                    panY = (panY ?: clip.panY).coerceIn(-0.45f, 0.45f),
                    kenBurnsEndScale = (kenBurnsEndScale ?: clip.kenBurnsEndScale).coerceIn(0.8f, 1.4f)
                )
            }
        }
    }

    fun updateClipFx(fxType: VisualFxType, intensity: Float? = null) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { clip ->
                clip.copy(
                    fxType = fxType,
                    fxIntensity = (intensity ?: clip.fxIntensity).coerceIn(0.1f, 1.0f)
                )
            }
        }
    }

    fun setSelectedClipVolume(volume: Float, isMuted: Boolean? = null) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) {
                it.copy(volume = volume.coerceIn(0f, 2.0f), isMuted = isMuted ?: it.isMuted)
            }
        }
    }

    // --- Color Grading ---
    fun applyColorPreset(preset: ColorPreset) {
        val clipId = _selectedClipId.value ?: return
        val grade = TimelineOperations.applyPresetToGrade(preset)
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { it.copy(colorGrade = grade) }
        }
    }

    fun updateColorGrade(transform: (ColorGradeConfig) -> ColorGradeConfig) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) { clip ->
                clip.copy(colorGrade = transform(clip.colorGrade))
            }
        }
    }

    fun applyCurrentGradeToAllClips() {
        val clipId = _selectedClipId.value ?: return
        val proj = _project.value ?: return
        val grade = proj.clips.firstOrNull { it.id == clipId }?.colorGrade ?: return
        mutateProject { TimelineOperations.applyColorGradeToAll(it, grade) }
        _toastMessage.value = "Applied color grade to all clips"
    }

    fun refreshHistogram() {
        val proj = _project.value ?: return
        val win = TimelineOperations.findWindowAt(proj, _playheadMs.value) ?: return
        val localOffset = (_playheadMs.value - win.timelineStartMs).coerceAtLeast(0L)
        val sourceTime = win.clip.trimStartMs + (localOffset * win.clip.effectiveSpeed).toLong()
        viewModelScope.launch {
            _histogramBins.value = MediaInspector.computeLuminanceHistogram(
                getApplication(),
                win.clip,
                sourceTime
            )
        }
    }

    // --- Transitions ---
    fun setSelectedClipTransition(type: TransitionType, durationMs: Long) {
        val clipId = _selectedClipId.value ?: return
        mutateProject { proj ->
            TimelineOperations.updateClip(proj, clipId) {
                it.copy(transitionIn = type, transitionDurationMs = durationMs.coerceIn(200L, 1500L))
            }
        }
    }

    // --- Text Overlays ---
    fun addTextOverlay(text: String = "YOUR TITLE") {
        val proj = _project.value ?: return
        val start = _playheadMs.value.coerceIn(0L, (proj.totalDurationMs - 500L).coerceAtLeast(0L))
        val dur = 3000L.coerceAtMost((proj.totalDurationMs - start).coerceAtLeast(1500L))
        val overlay = TextOverlayItem(text = text, startMs = start, durationMs = dur, animation = TextAnimation.POP)
        mutateProject { it.copy(textOverlays = it.textOverlays + overlay) }
        _selectedOverlayId.value = overlay.id
        _activeTool.value = EditorToolTab.TEXT
    }

    fun updateSelectedTextOverlay(transform: (TextOverlayItem) -> TextOverlayItem) {
        val id = _selectedOverlayId.value ?: return
        mutateProject { TimelineOperations.updateTextOverlay(it, id, transform) }
    }

    fun moveTextOverlayPosition(overlayId: String, normX: Float, normY: Float) {
        mutateProject(recordUndo = false) { proj ->
            TimelineOperations.updateTextOverlay(proj, overlayId) {
                it.copy(normX = normX.coerceIn(0.08f, 0.92f), normY = normY.coerceIn(0.08f, 0.92f))
            }
        }
    }

    fun deleteSelectedTextOverlay() {
        val id = _selectedOverlayId.value ?: return
        mutateProject { proj -> proj.copy(textOverlays = proj.textOverlays.filterNot { it.id == id }) }
        _selectedOverlayId.value = null
    }

    // --- Stickers & PiP Overlays ---
    fun addBadgeSticker(type: BadgeStickerType) {
        val proj = _project.value ?: return
        val start = _playheadMs.value.coerceIn(0L, (proj.totalDurationMs - 500L).coerceAtLeast(0L))
        val dur = 3200L.coerceAtMost((proj.totalDurationMs - start).coerceAtLeast(1500L))
        val sticker = OverlayStickerItem(type = type, startMs = start, durationMs = dur)
        mutateProject { it.copy(stickers = it.stickers + sticker) }
        _selectedStickerId.value = sticker.id
        _activeTool.value = EditorToolTab.OVERLAY
    }

    fun addPipImageUri(uri: Uri) {
        viewModelScope.launch {
            val localUri = MediaInspector.copyImageUriLocal(getApplication(), uri) ?: return@launch
            val proj = _project.value ?: return@launch
            val start = _playheadMs.value.coerceIn(0L, (proj.totalDurationMs - 500L).coerceAtLeast(0L))
            val dur = 4000L.coerceAtMost((proj.totalDurationMs - start).coerceAtLeast(1500L))
            val sticker = OverlayStickerItem(
                type = BadgeStickerType.IMAGE_PIP,
                imageUriString = localUri,
                startMs = start,
                durationMs = dur,
                normX = 0.76f,
                normY = 0.22f
            )
            mutateProject { it.copy(stickers = it.stickers + sticker) }
            _selectedStickerId.value = sticker.id
            _activeTool.value = EditorToolTab.OVERLAY
        }
    }

    fun moveStickerPosition(stickerId: String, normX: Float, normY: Float) {
        mutateProject(recordUndo = false) { proj ->
            TimelineOperations.updateSticker(proj, stickerId) {
                it.copy(normX = normX.coerceIn(0.08f, 0.92f), normY = normY.coerceIn(0.08f, 0.92f))
            }
        }
    }

    fun updateSelectedSticker(transform: (OverlayStickerItem) -> OverlayStickerItem) {
        val id = _selectedStickerId.value ?: return
        mutateProject { TimelineOperations.updateSticker(it, id, transform) }
    }

    fun deleteSelectedSticker() {
        val id = _selectedStickerId.value ?: return
        mutateProject { proj -> proj.copy(stickers = proj.stickers.filterNot { it.id == id }) }
        _selectedStickerId.value = null
    }

    // --- Beat Markers ---
    fun addBeatMarkerAtPlayhead() {
        mutateProject { TimelineOperations.addBeatMarker(it, _playheadMs.value) }
        _toastMessage.value = "Beat pin dropped at ${TimecodeFormatter.formatDurationShort(_playheadMs.value)}"
    }

    fun clearBeatMarkers() {
        mutateProject { it.copy(beatMarkers = emptyList()) }
    }

    // --- Audio Tracks ---
    fun updateSelectedAudioTrack(transform: (AudioTrackItem) -> AudioTrackItem) {
        val id = _selectedAudioId.value ?: return
        mutateProject { proj ->
            proj.copy(audioTracks = proj.audioTracks.map { if (it.id == id) transform(it) else it })
        }
    }

    fun deleteSelectedAudioTrack() {
        val id = _selectedAudioId.value ?: return
        mutateProject { proj -> proj.copy(audioTracks = proj.audioTracks.filterNot { it.id == id }) }
        _selectedAudioId.value = _project.value?.audioTracks?.firstOrNull()?.id
    }

    // --- Canvas & Project Meta ---
    fun setAspectRatio(ratio: AspectRatioPreset) {
        mutateProject { it.copy(aspectRatio = ratio) }
    }

    fun setCanvasFillStyle(style: CanvasFillStyle) {
        mutateProject { it.copy(canvasFillStyle = style, canvasBgArgb = style.argb) }
    }

    fun renameProject(newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) mutateProject { it.copy(title = clean) }
    }

    fun updateExportSettings(
        resolution: ExportResolution? = null,
        fps: Int? = null,
        codec: ExportCodec? = null,
        bitrateTier: BitrateTier? = null
    ) {
        mutateProject(recordUndo = false) { proj ->
            val cur = proj.exportSettings
            proj.copy(
                exportSettings = cur.copy(
                    resolution = resolution ?: cur.resolution,
                    fps = fps ?: cur.fps,
                    codec = codec ?: cur.codec,
                    bitrateTier = bitrateTier ?: cur.bitrateTier
                )
            )
        }
    }

    fun startExport() {
        val proj = _project.value ?: return
        pausePlayback()
        viewModelScope.launch { repo.saveProject(proj) }
        exportEngine.startExport(proj)
    }

    fun cancelExport() = exportEngine.cancelExport()
    fun resetExportState() = exportEngine.resetState()

    private fun extractMissingFilmstrips(clips: List<VideoClip>) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val currentMap = _filmstrips.value.toMutableMap()
            var changed = false
            for (clip in clips) {
                if (!currentMap.containsKey(clip.id)) {
                    currentMap[clip.id] = MediaInspector.extractClipFilmstrip(ctx, clip, frameCount = 5)
                    changed = true
                }
            }
            if (changed) _filmstrips.value = currentMap
        }
    }

    private fun updateCoverThumbnailIfNeeded() {
        val proj = _project.value ?: return
        val firstClip = proj.clips.firstOrNull() ?: return
        if (proj.thumbnailPath != null) return
        viewModelScope.launch {
            val thumb = MediaInspector.saveProjectCoverThumbnail(getApplication(), proj.id, firstClip)
            if (thumb != null) mutateProject(recordUndo = false) { it.copy(thumbnailPath = thumb) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackTickerJob?.cancel()
        saveDebounceJob?.cancel()
        voiceoverRecorder.release()
        videoPlayer?.release()
        audioPlayers.values.forEach { it.release() }
        audioPlayers.clear()
        videoPlayer = null
        _filmstrips.value.values.flatten().distinct().forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        _filmstrips.value = emptyMap()
    }
}
