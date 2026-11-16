package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioPreferences
import com.example.data.local.StudioSettings
import com.example.data.model.AspectRatioPreset
import com.example.data.model.ColorPreset
import com.example.data.model.ExportResolution
import com.example.data.model.ExportSettings
import com.example.data.model.TextAnimation
import com.example.data.model.TextOverlayItem
import com.example.data.model.TransitionType
import com.example.data.model.VideoProject
import com.example.media.ExportedVideoMeta
import com.example.media.MediaInspector
import com.example.media.SampleMediaGenerator
import com.example.media.TimelineOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class StudioTemplate(
    val title: String,
    val subtitle: String,
    val ratio: AspectRatioPreset,
    val colorPreset: ColorPreset,
    val transition: TransitionType,
    val defaultTitleText: String
) {
    CINEMA_VLOG("Widescreen Vlog", "16:9 • Teal Grade", AspectRatioPreset.RATIO_16_9, ColorPreset.TEAL_ORANGE, TransitionType.CROSSFADE, "FIELD NOTES"),
    KINETIC_REEL("Vertical Edit 9:16", "9:16 • Punch Cuts", AspectRatioPreset.RATIO_9_16, ColorPreset.CYBER_NEON, TransitionType.ZOOM_IN, "HIGHLIGHT"),
    GOLDEN_TRAVEL("Editorial 4:5", "4:5 • Warm Film", AspectRatioPreset.RATIO_4_5, ColorPreset.KODAK_WARM, TransitionType.FLASH_WHITE, "GOLDEN HOUR"),
    CLEAN_SQUARE("Square Frame 1:1", "1:1 • Crisp Matte", AspectRatioPreset.RATIO_1_1, ColorPreset.VIVID_POP, TransitionType.DIP_BLACK, "CHAPTER 01")
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ProjectRepository(AppDatabase.getInstance(application).projectDao())
    private val prefs = StudioPreferences(application)

    val projects: StateFlow<List<VideoProject>> = repo.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val settings: StateFlow<StudioSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val _exportedVideos = MutableStateFlow<List<ExportedVideoMeta>>(emptyList())
    val exportedVideos: StateFlow<List<ExportedVideoMeta>> = _exportedVideos.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshExports()
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun refreshExports() {
        viewModelScope.launch {
            _exportedVideos.value = MediaInspector.listExportedVideos(getApplication())
        }
    }

    fun deleteExportFile(file: File) {
        viewModelScope.launch {
            if (file.exists()) file.delete()
            refreshExports()
            _statusMessage.value = "Removed exported video"
        }
    }

    fun createBlankProject(
        title: String,
        ratio: AspectRatioPreset,
        uris: List<Uri>,
        onCreated: (String) -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val ctx = getApplication<Application>()
                val curSettings = prefs.settingsFlow.first()
                val importedClips = uris.mapNotNull { MediaInspector.importVisualUri(ctx, it) }
                val project = VideoProject(
                    title = title.ifBlank { "Untitled Cut" },
                    aspectRatio = ratio,
                    clips = importedClips,
                    exportSettings = ExportSettings(
                        resolution = curSettings.defaultResolution,
                        fps = curSettings.defaultFps
                    )
                )
                val thumb = importedClips.firstOrNull()?.let {
                    MediaInspector.saveProjectCoverThumbnail(ctx, project.id, it)
                }
                repo.saveProject(project.copy(thumbnailPath = thumb))
                onCreated(project.id)
            } catch (t: Throwable) {
                _statusMessage.value = t.localizedMessage ?: "Failed to create project."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createSampleReelProject(
        template: StudioTemplate? = null,
        onCreated: (String) -> Unit
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val ctx = getApplication<Application>()
                val curSettings = prefs.settingsFlow.first()
                val generated = SampleMediaGenerator.ensureSampleClips(ctx)
                val grade = TimelineOperations.applyPresetToGrade(
                    template?.colorPreset ?: ColorPreset.TEAL_ORANGE
                )
                val trans = template?.transition ?: TransitionType.CROSSFADE

                val clips = generated.mapIndexed { idx, sample ->
                    val rawClip = MediaInspector.createClipFromLocalFile(
                        context = ctx,
                        file = sample.file,
                        title = sample.title,
                        isImage = sample.isImage,
                        fallbackDurationMs = sample.durationMs
                    )
                    rawClip.copy(
                        colorGrade = grade,
                        transitionIn = if (idx == 0) TransitionType.NONE else trans,
                        transitionDurationMs = 450L
                    )
                }

                val audioAsset = SampleMediaGenerator.ensureAudioPreset(
                    ctx,
                    SampleMediaGenerator.AudioPreset.CYBER_BEAT
                )
                val audioTrack = MediaInspector.createAudioFromLocalFile(
                    context = ctx,
                    file = audioAsset.file,
                    title = audioAsset.title,
                    fallbackDurationMs = audioAsset.durationMs
                ).copy(volume = 0.72f, isLooping = true)

                val projectTitle = template?.title ?: "Demo Edit 01"
                val projectRatio = template?.ratio ?: AspectRatioPreset.RATIO_9_16
                val overlayTitle = template?.defaultTitleText ?: "CUTFORGE EDIT"

                val overlay = TextOverlayItem(
                    text = overlayTitle,
                    startMs = 400L,
                    durationMs = 3400L,
                    normX = 0.5f,
                    normY = 0.78f,
                    fontSizeSp = 28f,
                    colorArgb = 0xFFFFFFFF,
                    bgArgb = 0xCC0B0D12,
                    hasBackground = true,
                    animation = TextAnimation.POP
                )

                val project = VideoProject(
                    title = projectTitle,
                    aspectRatio = projectRatio,
                    clips = clips,
                    textOverlays = listOf(overlay),
                    audioTracks = listOf(audioTrack),
                    exportSettings = ExportSettings(
                        resolution = curSettings.defaultResolution,
                        fps = curSettings.defaultFps
                    )
                )
                val thumb = clips.firstOrNull()?.let {
                    MediaInspector.saveProjectCoverThumbnail(ctx, project.id, it)
                }
                repo.saveProject(project.copy(thumbnailPath = thumb))
                onCreated(project.id)
            } catch (t: Throwable) {
                _statusMessage.value = t.localizedMessage ?: "Failed to generate sample reel."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch { repo.renameProject(projectId, newTitle) }
    }

    fun duplicateProject(project: VideoProject) {
        viewModelScope.launch {
            repo.duplicateProject(project)
            _statusMessage.value = "Duplicated '${project.title}'"
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { repo.deleteProject(projectId) }
    }

    fun updateDefaultResolution(resolution: ExportResolution) {
        viewModelScope.launch { prefs.updateDefaultResolution(resolution) }
    }

    fun updateDefaultFps(fps: Int) {
        viewModelScope.launch { prefs.updateDefaultFps(fps) }
    }

    fun toggleSafeZoneGuides(enabled: Boolean) {
        viewModelScope.launch { prefs.setSafeZoneGuides(enabled) }
    }

    fun toggleMagneticSnap(enabled: Boolean) {
        viewModelScope.launch { prefs.setMagneticSnap(enabled) }
    }
}
