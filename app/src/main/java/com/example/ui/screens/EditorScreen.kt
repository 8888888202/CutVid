package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.media.ExportUiState
import com.example.ui.components.EditorToolDock
import com.example.ui.components.ExportSheet
import com.example.ui.components.MultiTrackTimeline
import com.example.ui.components.PreviewStage
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val palette = LocalStudioPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val project by viewModel.project.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLooping by viewModel.isLooping.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val selectedOverlayId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()
    val selectedStickerId by viewModel.selectedStickerId.collectAsStateWithLifecycle()
    val selectedAudioId by viewModel.selectedAudioId.collectAsStateWithLifecycle()
    val dpPerSec by viewModel.timelineZoomDpPerSec.collectAsStateWithLifecycle()
    val filmstrips by viewModel.filmstrips.collectAsStateWithLifecycle()
    val histogramBins by viewModel.histogramBins.collectAsStateWithLifecycle()
    val monitorSpeed by viewModel.monitorSpeed.collectAsStateWithLifecycle()
    val monitorVolume by viewModel.monitorVolume.collectAsStateWithLifecycle()
    val stageBrightness by viewModel.stageBrightness.collectAsStateWithLifecycle()
    val stageFillMode by viewModel.stageFillMode.collectAsStateWithLifecycle()
    val activeClipTelemetry by viewModel.activeClipTelemetry.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val studioSettings by viewModel.studioSettings.collectAsStateWithLifecycle()
    val isRecordingVoiceover by viewModel.isRecordingVoiceover.collectAsStateWithLifecycle()
    val voiceoverElapsedMs by viewModel.voiceoverElapsedMs.collectAsStateWithLifecycle()
    val voiceoverAmplitude by viewModel.voiceoverAmplitude.collectAsStateWithLifecycle()

    var isFullscreenPreview by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showAddClipDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    BackHandler {
        if (isFullscreenPreview) {
            isFullscreenPreview = false
        } else {
            viewModel.pausePlayback()
            onNavigateBack()
        }
    }

    val visualPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importVisualUris(uris)
    }

    val pipPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addPipImageUri(it) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAudioUri(it) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleVoiceoverRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required to record voiceovers.")
            }
        }
    }

    val handleVoiceoverClick: () -> Unit = {
        if (isRecordingVoiceover) {
            viewModel.toggleVoiceoverRecording()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                viewModel.toggleVoiceoverRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val currentProject = project
    if (currentProject == null) {
        Box(modifier = Modifier.fillMaxSize().background(palette.voidBg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.playheadCyan)
        }
        return
    }

    val selectedClip = remember(currentProject, selectedClipId) {
        currentProject.clips.firstOrNull { it.id == selectedClipId } ?: currentProject.clips.firstOrNull()
    }
    val selectedOverlay = remember(currentProject, selectedOverlayId) {
        currentProject.textOverlays.firstOrNull { it.id == selectedOverlayId }
    }
    val selectedSticker = remember(currentProject, selectedStickerId) {
        currentProject.stickers.firstOrNull { it.id == selectedStickerId }
    }
    val selectedAudio = remember(currentProject, selectedAudioId) {
        currentProject.audioTracks.firstOrNull { it.id == selectedAudioId } ?: currentProject.audioTracks.firstOrNull()
    }

    Scaffold(
        containerColor = palette.voidBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullscreenPreview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.studioSlate)
                        .border(0.5.dp, palette.hairlineBorder)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = {
                                viewModel.pausePlayback()
                                onNavigateBack()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to vault", tint = palette.textPrimary)
                        }
                        Column(
                            modifier = Modifier
                                .clickable {
                                    renameDraft = currentProject.title
                                    showRenameDialog = true
                                }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = currentProject.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentProject.exportSettings.resolution.label} • ${currentProject.exportSettings.fps}fps • ${currentProject.aspectRatio.label}",
                                style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.textMuted)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = canUndo,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (canUndo) palette.textPrimary else palette.textMuted, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = viewModel::redo,
                            enabled = canRedo,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("redo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (canRedo) palette.textPrimary else palette.textMuted, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                viewModel.pausePlayback()
                                showExportSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("open_export_button")
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideStudio = maxWidth >= 760.dp && !isFullscreenPreview

            if (isFullscreenPreview) {
                PreviewStage(
                    project = currentProject,
                    playheadMs = playheadMs,
                    isPlaying = isPlaying,
                    isLooping = isLooping,
                    monitorSpeed = monitorSpeed,
                    monitorVolume = monitorVolume,
                    stageBrightness = stageBrightness,
                    stageFillMode = stageFillMode,
                    telemetryText = activeClipTelemetry,
                    showSafeZoneGuides = studioSettings.showSafeZoneGuides,
                    selectedOverlayId = selectedOverlayId,
                    selectedStickerId = selectedStickerId,
                    isFullscreen = true,
                    onAttachTextureView = viewModel::attachTextureView,
                    onDetachTextureView = viewModel::detachTextureView,
                    onTogglePlay = viewModel::togglePlayPause,
                    onJumpToStart = { viewModel.scrubTo(0L) },
                    onStepFrame = viewModel::stepFrame,
                    onSeekRelative = viewModel::seekRelativeMs,
                    onScrubTo = { viewModel.scrubTo(it, false) },
                    onCycleMonitorSpeed = viewModel::cycleMonitorSpeed,
                    onSetMonitorVolume = viewModel::setMonitorVolume,
                    onToggleMonitorMute = viewModel::toggleMonitorMute,
                    onSetStageBrightness = viewModel::setStageBrightness,
                    onToggleStageFillMode = viewModel::toggleStageFillMode,
                    onCaptureSnapshot = viewModel::captureCurrentStageSnapshot,
                    onToggleLoop = viewModel::toggleLoop,
                    onToggleFullscreen = { isFullscreenPreview = false },
                    onSelectOverlay = viewModel::selectOverlay,
                    onDragOverlay = viewModel::moveTextOverlayPosition,
                    onSelectSticker = viewModel::selectSticker,
                    onDragSticker = viewModel::moveStickerPosition,
                    onAddClipsClick = { showAddClipDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isWideStudio) {
                // Canonical Tablet / Foldable Two-Pane Studio + Full-Width Bottom Timeline
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PreviewStage(
                            project = currentProject,
                            playheadMs = playheadMs,
                            isPlaying = isPlaying,
                            isLooping = isLooping,
                            monitorSpeed = monitorSpeed,
                            monitorVolume = monitorVolume,
                            stageBrightness = stageBrightness,
                            stageFillMode = stageFillMode,
                            telemetryText = activeClipTelemetry,
                            showSafeZoneGuides = studioSettings.showSafeZoneGuides,
                            selectedOverlayId = selectedOverlayId,
                            selectedStickerId = selectedStickerId,
                            isFullscreen = false,
                            onAttachTextureView = viewModel::attachTextureView,
                            onDetachTextureView = viewModel::detachTextureView,
                            onTogglePlay = viewModel::togglePlayPause,
                            onJumpToStart = { viewModel.scrubTo(0L) },
                            onStepFrame = viewModel::stepFrame,
                            onSeekRelative = viewModel::seekRelativeMs,
                            onScrubTo = { viewModel.scrubTo(it, false) },
                            onCycleMonitorSpeed = viewModel::cycleMonitorSpeed,
                            onSetMonitorVolume = viewModel::setMonitorVolume,
                            onToggleMonitorMute = viewModel::toggleMonitorMute,
                            onSetStageBrightness = viewModel::setStageBrightness,
                            onToggleStageFillMode = viewModel::toggleStageFillMode,
                            onCaptureSnapshot = viewModel::captureCurrentStageSnapshot,
                            onToggleLoop = viewModel::toggleLoop,
                            onToggleFullscreen = { isFullscreenPreview = true },
                            onSelectOverlay = viewModel::selectOverlay,
                            onDragOverlay = viewModel::moveTextOverlayPosition,
                            onSelectSticker = viewModel::selectSticker,
                            onDragSticker = viewModel::moveStickerPosition,
                            onAddClipsClick = { showAddClipDialog = true },
                            modifier = Modifier.weight(1.15f).fillMaxHeight()
                        )
                        EditorToolDock(
                            project = currentProject,
                            activeTool = activeTool,
                            selectedClip = selectedClip,
                            selectedOverlay = selectedOverlay,
                            selectedSticker = selectedSticker,
                            selectedAudio = selectedAudio,
                            histogramBins = histogramBins,
                            isRecordingVoiceover = isRecordingVoiceover,
                            voiceoverElapsedMs = voiceoverElapsedMs,
                            voiceoverAmplitude = voiceoverAmplitude,
                            isExpandedInspector = true,
                            onSelectTool = viewModel::selectTool,
                            onSplitClick = viewModel::splitAtPlayhead,
                            onFreezeFrameClick = viewModel::captureFreezeFrame,
                            onDetachAudioClick = viewModel::detachAudioFromSelectedClip,
                            onDuplicateClip = viewModel::duplicateSelectedClip,
                            onDeleteClip = viewModel::deleteSelectedClip,
                            onMoveClip = viewModel::moveSelectedClip,
                            onTrimClip = viewModel::updateSelectedClipTrim,
                            onSpeedChange = viewModel::setSelectedClipSpeed,
                            onSpeedCurveChange = viewModel::setSelectedClipSpeedCurve,
                            onRotateClip = viewModel::rotateSelectedClip,
                            onFlipHorizontal = viewModel::toggleFlipHorizontal,
                            onFlipVertical = viewModel::toggleFlipVertical,
                            onUpdateMotion = viewModel::updateClipMotion,
                            onUpdateFx = viewModel::updateClipFx,
                            onVolumeChange = viewModel::setSelectedClipVolume,
                            onApplyColorPreset = viewModel::applyColorPreset,
                            onUpdateColorGrade = viewModel::updateColorGrade,
                            onApplyGradeToAll = viewModel::applyCurrentGradeToAllClips,
                            onSetTransition = viewModel::setSelectedClipTransition,
                            onAddTextOverlay = { viewModel.addTextOverlay() },
                            onUpdateTextOverlay = viewModel::updateSelectedTextOverlay,
                            onDeleteTextOverlay = viewModel::deleteSelectedTextOverlay,
                            onPickPipPhoto = {
                                pipPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onAddBadgeSticker = viewModel::addBadgeSticker,
                            onUpdateSticker = viewModel::updateSelectedSticker,
                            onDeleteSticker = viewModel::deleteSelectedSticker,
                            onToggleVoiceover = handleVoiceoverClick,
                            onPickAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            onAddOfflineAudioPreset = viewModel::addOfflineAudioPreset,
                            onUpdateAudioTrack = viewModel::updateSelectedAudioTrack,
                            onDeleteAudioTrack = viewModel::deleteSelectedAudioTrack,
                            onSelectAspectRatio = viewModel::setAspectRatio,
                            onSelectCanvasFill = viewModel::setCanvasFillStyle,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    MultiTrackTimeline(
                        project = currentProject,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        dpPerSec = dpPerSec,
                        selectedClipId = selectedClip?.id,
                        selectedOverlayId = selectedOverlayId,
                        selectedStickerId = selectedStickerId,
                        selectedAudioId = selectedAudio?.id,
                        filmstrips = filmstrips,
                        onScrubTo = viewModel::scrubTo,
                        onSelectClip = viewModel::selectClip,
                        onSelectTransition = { clipId ->
                            viewModel.selectClip(clipId)
                            viewModel.selectTool(EditorToolTab.TRANSITION)
                        },
                        onSelectOverlay = viewModel::selectOverlay,
                        onSelectSticker = viewModel::selectSticker,
                        onSelectAudio = viewModel::selectAudio,
                        onSplitClick = viewModel::splitAtPlayhead,
                        onAddClipClick = { showAddClipDialog = true },
                        onDropBeatMarker = viewModel::addBeatMarkerAtPlayhead,
                        onZoomChange = viewModel::setTimelineZoom
                    )
                }
            } else {
                // Handheld Phone Compact Vertical NLE Stack
                Column(modifier = Modifier.fillMaxSize()) {
                    PreviewStage(
                        project = currentProject,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        monitorSpeed = monitorSpeed,
                        monitorVolume = monitorVolume,
                        stageBrightness = stageBrightness,
                        stageFillMode = stageFillMode,
                        telemetryText = activeClipTelemetry,
                        showSafeZoneGuides = studioSettings.showSafeZoneGuides,
                        selectedOverlayId = selectedOverlayId,
                        selectedStickerId = selectedStickerId,
                        isFullscreen = false,
                        onAttachTextureView = viewModel::attachTextureView,
                        onDetachTextureView = viewModel::detachTextureView,
                        onTogglePlay = viewModel::togglePlayPause,
                        onJumpToStart = { viewModel.scrubTo(0L) },
                        onStepFrame = viewModel::stepFrame,
                        onSeekRelative = viewModel::seekRelativeMs,
                        onScrubTo = { viewModel.scrubTo(it, false) },
                        onCycleMonitorSpeed = viewModel::cycleMonitorSpeed,
                        onSetMonitorVolume = viewModel::setMonitorVolume,
                        onToggleMonitorMute = viewModel::toggleMonitorMute,
                        onSetStageBrightness = viewModel::setStageBrightness,
                        onToggleStageFillMode = viewModel::toggleStageFillMode,
                        onCaptureSnapshot = viewModel::captureCurrentStageSnapshot,
                        onToggleLoop = viewModel::toggleLoop,
                        onToggleFullscreen = { isFullscreenPreview = true },
                        onSelectOverlay = viewModel::selectOverlay,
                        onDragOverlay = viewModel::moveTextOverlayPosition,
                        onSelectSticker = viewModel::selectSticker,
                        onDragSticker = viewModel::moveStickerPosition,
                        onAddClipsClick = { showAddClipDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    MultiTrackTimeline(
                        project = currentProject,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying,
                        dpPerSec = dpPerSec,
                        selectedClipId = selectedClip?.id,
                        selectedOverlayId = selectedOverlayId,
                        selectedStickerId = selectedStickerId,
                        selectedAudioId = selectedAudio?.id,
                        filmstrips = filmstrips,
                        onScrubTo = viewModel::scrubTo,
                        onSelectClip = viewModel::selectClip,
                        onSelectTransition = { clipId ->
                            viewModel.selectClip(clipId)
                            viewModel.selectTool(EditorToolTab.TRANSITION)
                        },
                        onSelectOverlay = viewModel::selectOverlay,
                        onSelectSticker = viewModel::selectSticker,
                        onSelectAudio = viewModel::selectAudio,
                        onSplitClick = viewModel::splitAtPlayhead,
                        onAddClipClick = { showAddClipDialog = true },
                        onDropBeatMarker = viewModel::addBeatMarkerAtPlayhead,
                        onZoomChange = viewModel::setTimelineZoom
                    )

                    EditorToolDock(
                        project = currentProject,
                        activeTool = activeTool,
                        selectedClip = selectedClip,
                        selectedOverlay = selectedOverlay,
                        selectedSticker = selectedSticker,
                        selectedAudio = selectedAudio,
                        histogramBins = histogramBins,
                        isRecordingVoiceover = isRecordingVoiceover,
                        voiceoverElapsedMs = voiceoverElapsedMs,
                        voiceoverAmplitude = voiceoverAmplitude,
                        isExpandedInspector = false,
                        onSelectTool = viewModel::selectTool,
                        onSplitClick = viewModel::splitAtPlayhead,
                        onFreezeFrameClick = viewModel::captureFreezeFrame,
                        onDetachAudioClick = viewModel::detachAudioFromSelectedClip,
                        onDuplicateClip = viewModel::duplicateSelectedClip,
                        onDeleteClip = viewModel::deleteSelectedClip,
                        onMoveClip = viewModel::moveSelectedClip,
                        onTrimClip = viewModel::updateSelectedClipTrim,
                        onSpeedChange = viewModel::setSelectedClipSpeed,
                        onSpeedCurveChange = viewModel::setSelectedClipSpeedCurve,
                        onRotateClip = viewModel::rotateSelectedClip,
                        onFlipHorizontal = viewModel::toggleFlipHorizontal,
                        onFlipVertical = viewModel::toggleFlipVertical,
                        onUpdateMotion = viewModel::updateClipMotion,
                        onUpdateFx = viewModel::updateClipFx,
                        onVolumeChange = viewModel::setSelectedClipVolume,
                        onApplyColorPreset = viewModel::applyColorPreset,
                        onUpdateColorGrade = viewModel::updateColorGrade,
                        onApplyGradeToAll = viewModel::applyCurrentGradeToAllClips,
                        onSetTransition = viewModel::setSelectedClipTransition,
                        onAddTextOverlay = { viewModel.addTextOverlay() },
                        onUpdateTextOverlay = viewModel::updateSelectedTextOverlay,
                        onDeleteTextOverlay = viewModel::deleteSelectedTextOverlay,
                        onPickPipPhoto = {
                            pipPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onAddBadgeSticker = viewModel::addBadgeSticker,
                        onUpdateSticker = viewModel::updateSelectedSticker,
                        onDeleteSticker = viewModel::deleteSelectedSticker,
                        onToggleVoiceover = handleVoiceoverClick,
                        onPickAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        onAddOfflineAudioPreset = viewModel::addOfflineAudioPreset,
                        onUpdateAudioTrack = viewModel::updateSelectedAudioTrack,
                        onDeleteAudioTrack = viewModel::deleteSelectedAudioTrack,
                        onSelectAspectRatio = viewModel::setAspectRatio,
                        onSelectCanvasFill = viewModel::setCanvasFillStyle
                    )
                }
            }
        }
    }

    if (showAddClipDialog) {
        AlertDialog(
            onDismissRequest = { showAddClipDialog = false },
            containerColor = palette.studioSlate,
            title = { Text("Import Media", color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select local video or photo assets, or load built-in test footage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Button(
                        onClick = {
                            showAddClipDialog = false
                            visualPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose Videos / Photos", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showAddClipDialog = false
                            viewModel.addSampleClips()
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = palette.audioAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load Reference Clips", color = palette.audioAmber)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddClipDialog = false }) { Text("Close") } }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = palette.studioSlate,
            title = { Text("Rename Cut", color = palette.textPrimary) },
            text = {
                OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { viewModel.renameProject(renameDraft); showRenameDialog = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExportSheet) {
        ExportSheet(
            project = currentProject,
            exportState = exportState,
            onUpdateSettings = viewModel::updateExportSettings,
            onStartExport = viewModel::startExport,
            onCancelExport = viewModel::cancelExport,
            onDismiss = {
                if (exportState !is ExportUiState.Exporting) {
                    viewModel.resetExportState()
                    showExportSheet = false
                }
            }
        )
    }
}
