package com.example.ui.viewmodel

import android.app.Application
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.model.AspectRatioPreset
import com.example.data.model.VideoProject
import com.example.media.MediaInspector
import com.example.media.MediaStreamSummary
import com.example.media.SampleMediaGenerator
import com.example.media.SrtSubtitleParser
import com.example.media.SubtitleCue
import com.example.media.TimecodeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerAspectMode(val label: String) {
    BEST_FIT("FIT"),
    FILL_CROP("FILL"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ORIGINAL("1:1")
}

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

data class PlayerQueueItem(
    val uriString: String,
    val title: String
)

class ProPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProjectRepository(AppDatabase.getInstance(application).projectDao())

    private val _queue = MutableStateFlow<List<PlayerQueueItem>>(emptyList())
    val queue: StateFlow<List<PlayerQueueItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioBoostPercent = MutableStateFlow(100) // 100..200
    val audioBoostPercent: StateFlow<Int> = _audioBoostPercent.asStateFlow()

    private val _aspectMode = MutableStateFlow(PlayerAspectMode.BEST_FIT)
    val aspectMode: StateFlow<PlayerAspectMode> = _aspectMode.asStateFlow()

    private val _repeatAll = MutableStateFlow(false)
    val repeatAll: StateFlow<Boolean> = _repeatAll.asStateFlow()

    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    private val _streamSummary = MutableStateFlow<MediaStreamSummary?>(null)
    val streamSummary: StateFlow<MediaStreamSummary?> = _streamSummary.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackOption>> = _audioTracks.asStateFlow()

    private val _activeSubtitleText = MutableStateFlow<String?>(null)
    val activeSubtitleText: StateFlow<String?> = _activeSubtitleText.asStateFlow()

    private val _hasExternalSrt = MutableStateFlow(false)
    val hasExternalSrt: StateFlow<Boolean> = _hasExternalSrt.asStateFlow()

    private val _subtitleOffsetMs = MutableStateFlow(0L)
    val subtitleOffsetMs: StateFlow<Long> = _subtitleOffsetMs.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var srtCues: List<SubtitleCue> = emptyList()
    private var embeddedCueText: String? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedTextureView: TextureView? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var lastAudioGroups: List<Tracks.Group> = emptyList()

    private val player: ExoPlayer = buildUniversalPlayer()

    init {
        startPositionTicker()
    }

    @OptIn(UnstableApi::class)
    private fun buildUniversalPlayer(): ExoPlayer {
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
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _durationMs.value = duration.coerceAtLeast(0L)
                            _errorBanner.value = null
                        } else if (playbackState == Player.STATE_ENDED) {
                            handleTrackEnded()
                        }
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        setupLoudnessEnhancer(audioSessionId)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractAudioTrackOptions(tracks)
                    }

                    override fun onCues(cueGroup: CueGroup) {
                        embeddedCueText = cueGroup.cues
                            .mapNotNull { it.text?.toString()?.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString("\n")
                            .ifBlank { null }
                        refreshDisplayedSubtitle(_positionMs.value)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _errorBanner.value = "Decoder fallback failed: ${error.errorCodeName}"
                        _isPlaying.value = false
                    }
                })
            }
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        if (sessionId <= 0) return
        runCatching {
            val enhancer = LoudnessEnhancer(sessionId)
            val boost = (_audioBoostPercent.value - 100).coerceIn(0, 100)
            enhancer.setTargetGain(boost * 12) // up to +1200 mB (200% boost)
            enhancer.enabled = boost > 0
            loudnessEnhancer = enhancer
        }
    }

    @OptIn(UnstableApi::class)
    private fun extractAudioTrackOptions(tracks: Tracks) {
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        lastAudioGroups = groups
        val options = mutableListOf<AudioTrackOption>()
        groups.forEachIndexed { gIdx, group ->
            for (tIdx in 0 until group.length) {
                val fmt = group.getTrackFormat(tIdx)
                val lang = fmt.language?.uppercase() ?: "TRK"
                val ch = if (fmt.channelCount > 0) "${fmt.channelCount}ch" else ""
                val rate = if (fmt.sampleRate > 0) "${fmt.sampleRate / 1000}kHz" else ""
                val label = listOf(lang, ch, rate).filter { it.isNotBlank() }.joinToString(" • ")
                options.add(
                    AudioTrackOption(
                        groupIndex = gIdx,
                        trackIndex = tIdx,
                        label = label.ifBlank { "Audio ${options.size + 1}" },
                        selected = group.isTrackSelected(tIdx)
                    )
                )
            }
        }
        _audioTracks.value = options
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        val group = lastAudioGroups.getOrNull(option.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        _toastMessage.value = "Audio: ${option.label}"
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(80L)
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)
                _positionMs.value = pos
                if (dur > 0L) _durationMs.value = dur

                val a = _abLoopStartMs.value
                val b = _abLoopEndMs.value
                if (a != null && b != null && b > a + 150L && pos >= b) {
                    player.seekTo(a)
                    _positionMs.value = a
                }
                refreshDisplayedSubtitle(pos)
            }
        }
    }

    private fun handleTrackEnded() {
        val list = _queue.value
        val next = _currentIndex.value + 1
        if (next in list.indices) {
            playQueueIndex(next)
        } else if (_repeatAll.value && list.isNotEmpty()) {
            playQueueIndex(0)
        } else {
            player.pause()
            _isPlaying.value = false
        }
    }

    fun attachTextureView(tv: TextureView) {
        if (attachedTextureView !== tv) {
            attachedTextureView = tv
            player.setVideoTextureView(tv)
        }
    }

    fun detachTextureView(tv: TextureView) {
        if (attachedTextureView === tv) {
            player.clearVideoTextureView(tv)
            attachedTextureView = null
        }
    }

    fun initializeWithUri(initialUriString: String?) {
        if (!initialUriString.isNullOrBlank()) {
            val decoded = Uri.decode(initialUriString)
            val currentList = _queue.value
            if (currentList.isNotEmpty() && currentList.getOrNull(_currentIndex.value)?.uriString == decoded) {
                return
            }
            viewModelScope.launch {
                val ctx = getApplication<Application>()
                val exports = MediaInspector.listExportedVideos(ctx)
                val targetUri = Uri.parse(decoded)
                val summary = MediaInspector.inspectStreamSummary(ctx, targetUri)
                val items = if (exports.any { Uri.fromFile(it.file).toString() == decoded }) {
                    exports.map { PlayerQueueItem(Uri.fromFile(it.file).toString(), it.name) }
                } else {
                    listOf(PlayerQueueItem(decoded, summary.displayName))
                }
                val startIdx = items.indexOfFirst { it.uriString == decoded }.coerceAtLeast(0)
                _queue.value = items
                playQueueIndex(startIdx)
            }
        } else if (_queue.value.isEmpty()) {
            viewModelScope.launch {
                val ctx = getApplication<Application>()
                val exports = MediaInspector.listExportedVideos(ctx)
                if (exports.isNotEmpty()) {
                    _queue.value = exports.map { PlayerQueueItem(Uri.fromFile(it.file).toString(), it.name) }
                    playQueueIndex(0)
                } else {
                    val samples = SampleMediaGenerator.ensureSampleClips(ctx).filter { !it.isImage }
                    if (samples.isNotEmpty()) {
                        _queue.value = samples.map { PlayerQueueItem(Uri.fromFile(it.file).toString(), it.title) }
                        playQueueIndex(0)
                    }
                }
            }
        }
    }

    fun loadPickedMediaUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val items = uris.map { uri ->
                val summary = MediaInspector.inspectStreamSummary(ctx, uri)
                PlayerQueueItem(uri.toString(), summary.displayName)
            }
            _queue.value = items
            playQueueIndex(0)
        }
    }

    fun playQueueIndex(index: Int) {
        val list = _queue.value
        if (index !in list.indices) return
        _currentIndex.value = index
        val item = list[index]
        _errorBanner.value = null
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
        srtCues = emptyList()
        _hasExternalSrt.value = false
        embeddedCueText = null
        _activeSubtitleText.value = null

        val uri = Uri.parse(item.uriString)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playbackParameters = PlaybackParameters(_playbackSpeed.value)
        attachedTextureView?.let { player.setVideoTextureView(it) }
        player.play()

        viewModelScope.launch {
            val summary = MediaInspector.inspectStreamSummary(getApplication(), uri)
            _streamSummary.value = summary
            if (summary.durationMs > 0L) _durationMs.value = summary.durationMs
        }
    }

    fun playNext() {
        val next = _currentIndex.value + 1
        if (next in _queue.value.indices) playQueueIndex(next)
    }

    fun playPrevious() {
        val pos = player.currentPosition
        if (pos > 3000L) {
            seekTo(0L)
        } else {
            val prev = _currentIndex.value - 1
            if (prev in _queue.value.indices) playQueueIndex(prev) else seekTo(0L)
        }
    }

    fun togglePlayPause() {
        if (_queue.value.isEmpty()) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        val dur = _durationMs.value.coerceAtLeast(0L)
        val clamped = if (dur > 0L) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
        player.seekTo(clamped)
        _positionMs.value = clamped
        refreshDisplayedSubtitle(clamped)
    }

    fun seekRelative(deltaMs: Long) {
        seekTo(player.currentPosition + deltaMs)
    }

    fun stepFrame(direction: Int) {
        if (player.isPlaying) player.pause()
        seekRelative(direction * 33L)
    }

    fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = safe
        player.playbackParameters = PlaybackParameters(safe)
    }

    fun setAudioBoostPercent(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        _audioBoostPercent.value = clamped
        val boost = clamped - 100
        runCatching {
            loudnessEnhancer?.apply {
                setTargetGain(boost * 12)
                enabled = boost > 0
            }
        }
    }

    fun cycleAspectMode() {
        val entries = PlayerAspectMode.entries
        val next = entries[(_aspectMode.value.ordinal + 1) % entries.size]
        _aspectMode.value = next
        _toastMessage.value = "Aspect: ${next.label}"
    }

    fun toggleRepeatAll() {
        _repeatAll.value = !_repeatAll.value
    }

    fun toggleAbLoopPoint() {
        val a = _abLoopStartMs.value
        val b = _abLoopEndMs.value
        val now = player.currentPosition.coerceAtLeast(0L)
        when {
            a == null -> {
                _abLoopStartMs.value = now
                _toastMessage.value = "Loop Point A set (${TimecodeFormatter.formatDurationShort(now)})"
            }
            b == null -> {
                if (now > a + 300L) {
                    _abLoopEndMs.value = now
                    player.seekTo(a)
                    _toastMessage.value = "A-B Loop Active"
                } else {
                    _abLoopStartMs.value = now
                }
            }
            else -> {
                _abLoopStartMs.value = null
                _abLoopEndMs.value = null
                _toastMessage.value = "A-B Loop Cleared"
            }
        }
    }

    fun loadExternalSubtitleUri(uri: Uri) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText()
                    }
                }.getOrNull()
            }
            if (content.isNullOrBlank()) {
                _toastMessage.value = "Unable to read subtitle file"
                return@launch
            }
            val parsed = SrtSubtitleParser.parse(content)
            if (parsed.isEmpty()) {
                _toastMessage.value = "No valid SRT/VTT cues found"
            } else {
                srtCues = parsed
                _hasExternalSrt.value = true
                _subtitleOffsetMs.value = 0L
                refreshDisplayedSubtitle(_positionMs.value)
                _toastMessage.value = "Loaded ${parsed.size} subtitles"
            }
        }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        _subtitleOffsetMs.value = (_subtitleOffsetMs.value + deltaMs).coerceIn(-5000L, 5000L)
        refreshDisplayedSubtitle(_positionMs.value)
    }

    fun clearExternalSubtitles() {
        srtCues = emptyList()
        _hasExternalSrt.value = false
        refreshDisplayedSubtitle(_positionMs.value)
    }

    private fun refreshDisplayedSubtitle(posMs: Long) {
        val external = SrtSubtitleParser.activeTextAt(srtCues, posMs, _subtitleOffsetMs.value)
        _activeSubtitleText.value = external ?: embeddedCueText
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                _sleepTimerMinutes.value = remaining
            }
            if (isActive) {
                player.pause()
                _toastMessage.value = "Sleep timer paused playback"
            }
        }
        _toastMessage.value = "Sleep timer: ${minutes}m"
    }

    fun captureSnapshot() {
        val current = _queue.value.getOrNull(_currentIndex.value) ?: return
        val tv = attachedTextureView
        viewModelScope.launch {
            val saved = if (tv != null && tv.isAvailable && _streamSummary.value?.isAudioOnly != true) {
                val bmp = tv.bitmap
                if (bmp != null) {
                    val f = MediaInspector.saveBitmapSnapshot(getApplication(), bmp, current.title)
                    bmp.recycle()
                    f
                } else null
            } else {
                MediaInspector.captureVideoSnapshot(getApplication(), Uri.parse(current.uriString), player.currentPosition)
            }
            _toastMessage.value = if (saved != null) "Snapshot saved (${saved.name})" else "Could not save snapshot"
        }
    }

    fun sendCurrentMediaToTimeline(onProjectReady: (String) -> Unit) {
        val current = _queue.value.getOrNull(_currentIndex.value) ?: return
        player.pause()
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val uri = Uri.parse(current.uriString)
            val summary = _streamSummary.value
            val ratio = if (summary != null && summary.width > summary.height && summary.height > 0) {
                AspectRatioPreset.RATIO_16_9
            } else {
                AspectRatioPreset.RATIO_9_16
            }
            val project = if (summary?.isAudioOnly == true) {
                val audio = MediaInspector.importAudioUri(ctx, uri)
                VideoProject(
                    title = current.title.take(24).ifBlank { "Audio Cut" },
                    aspectRatio = ratio,
                    audioTracks = listOfNotNull(audio)
                )
            } else {
                val clip = MediaInspector.importVisualUri(ctx, uri)
                val thumb = clip?.let { MediaInspector.saveProjectCoverThumbnail(ctx, "tmp_${System.currentTimeMillis()}", it) }
                VideoProject(
                    title = current.title.take(24).ifBlank { "Timeline Cut" },
                    aspectRatio = ratio,
                    clips = listOfNotNull(clip),
                    thumbnailPath = thumb
                )
            }
            repo.saveProject(project)
            onProjectReady(project.id)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        sleepJob?.cancel()
        runCatching { loudnessEnhancer?.release() }
        player.release()
    }
}
