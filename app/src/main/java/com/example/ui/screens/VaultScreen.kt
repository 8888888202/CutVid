package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.StudioSettings
import com.example.data.model.AspectRatioPreset
import com.example.data.model.ExportResolution
import com.example.data.model.VideoProject
import com.example.media.TimecodeFormatter
import com.example.ui.components.ExportsVaultPane
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle
import com.example.ui.viewmodel.StudioTemplate
import com.example.ui.viewmodel.VaultViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultTab(val label: String) {
    PROJECTS("Studio"),
    PLAYER("Player"),
    EXPORTS("Exports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onOpenProject: (String) -> Unit,
    onOpenPlayer: (String?) -> Unit,
    viewModel: VaultViewModel = viewModel()
) {
    val palette = LocalStudioPalette.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val exportedVideos by viewModel.exportedVideos.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(VaultTab.PROJECTS) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var pendingTitle by remember { mutableStateOf("Cut 01") }
    var pendingRatio by remember { mutableStateOf(AspectRatioPreset.RATIO_9_16) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<VideoProject?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab == VaultTab.EXPORTS) {
            viewModel.refreshExports()
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.createBlankProject(pendingTitle, pendingRatio, uris, onOpenProject)
        }
    }

    val directPlayPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onOpenPlayer(it.toString()) }
    }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.voidBg)
    ) {
        val useNavRail = maxWidth >= 600.dp
        val columns = when {
            maxWidth >= 980.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }

        Scaffold(
            containerColor = palette.voidBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.studioSlate)
                        .border(0.5.dp, palette.hairlineBorder)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.panelSurface)
                                .border(1.dp, palette.playheadCyan, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, null, tint = palette.playheadCyan, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                            Text(
                                text = stringResource(R.string.studio_tagline),
                                style = TimecodeTextStyle.copy(fontSize = 9.sp, color = palette.textMuted)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onOpenPlayer(null) },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("top_open_player_button")
                        ) {
                            Icon(Icons.Default.SmartDisplay, "Open media player", tint = palette.playheadCyan)
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("studio_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, "Studio settings", tint = palette.textSecondary)
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = palette.studioSlate,
                        contentColor = palette.textPrimary
                    ) {
                        VaultTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab == VaultTab.PLAYER) {
                                        onOpenPlayer(null)
                                    } else {
                                        activeTab = tab
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            VaultTab.PROJECTS -> Icons.Default.VideoLibrary
                                            VaultTab.PLAYER -> Icons.Default.SmartDisplay
                                            VaultTab.EXPORTS -> Icons.Default.FolderCopy
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = palette.playheadCyan,
                                    selectedTextColor = palette.playheadCyan,
                                    indicatorColor = palette.cyanMuted,
                                    unselectedIconColor = palette.textSecondary,
                                    unselectedTextColor = palette.textSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = palette.studioSlate,
                        contentColor = palette.textPrimary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        VaultTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab == VaultTab.PLAYER) {
                                        onOpenPlayer(null)
                                    } else {
                                        activeTab = tab
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            VaultTab.PROJECTS -> Icons.Default.VideoLibrary
                                            VaultTab.PLAYER -> Icons.Default.SmartDisplay
                                            VaultTab.EXPORTS -> Icons.Default.FolderCopy
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = palette.playheadCyan,
                                    selectedTextColor = palette.playheadCyan,
                                    indicatorColor = palette.cyanMuted,
                                    unselectedIconColor = palette.textSecondary,
                                    unselectedTextColor = palette.textSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (activeTab) {
                        VaultTab.EXPORTS -> {
                            ExportsVaultPane(
                                exports = exportedVideos,
                                columns = columns,
                                onPlayExport = { item -> onOpenPlayer(Uri.fromFile(item.file).toString()) },
                                onDeleteExport = viewModel::deleteExportFile
                            )
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item(span = { GridItemSpan(columns) }) {
                                    StudioConsoleDeck(
                                        isBusy = isBusy,
                                        projectCount = projects.size,
                                        exportCount = exportedVideos.size,
                                        onNewProject = { showNewProjectDialog = true },
                                        onOpenMediaFile = { directPlayPickerLauncher.launch(arrayOf("video/*", "audio/*")) },
                                        onTrySampleReel = { viewModel.createSampleReelProject(null, onOpenProject) }
                                    )
                                }

                                item(span = { GridItemSpan(columns) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Sequence Presets", style = MaterialTheme.typography.titleSmall, color = palette.textSecondary)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(StudioTemplate.entries) { template ->
                                                TemplateCard(
                                                    template = template,
                                                    enabled = !isBusy,
                                                    onClick = { viewModel.createSampleReelProject(template, onOpenProject) }
                                                )
                                            }
                                        }
                                    }
                                }

                                item(span = { GridItemSpan(columns) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Project Bin (${projects.size})", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                            Text("LOCAL DB", style = TimecodeTextStyle.copy(fontSize = 10.sp, color = palette.readyEmerald))
                                        }

                                        if (projects.isNotEmpty()) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                placeholder = { Text("Filter bin...") },
                                                leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.textMuted) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                if (filteredProjects.isEmpty()) {
                                    item(span = { GridItemSpan(columns) }) {
                                        EmptyVaultCard(
                                            hasProjects = projects.isNotEmpty(),
                                            onNewProject = { showNewProjectDialog = true },
                                            onTrySample = { viewModel.createSampleReelProject(null, onOpenProject) }
                                        )
                                    }
                                } else {
                                    items(filteredProjects, key = { it.id }) { project ->
                                        ProjectVaultCard(
                                            project = project,
                                            onClick = { onOpenProject(project.id) },
                                            onRename = {
                                                renameTarget = project
                                                renameDraft = project.title
                                            },
                                            onDuplicate = { viewModel.duplicateProject(project) },
                                            onDelete = { viewModel.deleteProject(project.id) }
                                        )
                                    }
                                }

                                item(span = { GridItemSpan(columns) }) { Spacer(Modifier.height(14.dp)) }
                            }
                        }
                    }

                    if (isBusy) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.panelSurface,
                                modifier = Modifier.border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = palette.playheadCyan)
                                    Text("Indexing Media...", style = TimecodeTextStyle.copy(color = palette.textPrimary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = palette.studioSlate,
            title = { Text("New Sequence", color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pendingTitle,
                        onValueChange = { pendingTitle = it },
                        label = { Text("Sequence Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AspectRatioPreset.entries) { ratio ->
                            FilterChip(
                                selected = pendingRatio == ratio,
                                onClick = { pendingRatio = ratio },
                                label = { Text("${ratio.label} (${ratio.subtitle})", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewProjectDialog = false
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("pick_media_confirm_button")
                ) {
                    Text("Select Media", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewProjectDialog = false
                        viewModel.createBlankProject(pendingTitle, pendingRatio, emptyList(), onOpenProject)
                    }
                ) {
                    Text("Empty Timeline", color = palette.textSecondary)
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = palette.studioSlate,
            title = { Text("Rename Sequence", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameProject(target.id, renameDraft)
                    renameTarget = null
                }, shape = RoundedCornerShape(4.dp)) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showSettingsSheet) {
        StudioSettingsSheet(
            settings = settings,
            onResolutionChange = viewModel::updateDefaultResolution,
            onFpsChange = viewModel::updateDefaultFps,
            onSafeZoneToggle = viewModel::toggleSafeZoneGuides,
            onMagneticSnapToggle = viewModel::toggleMagneticSnap,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun StudioConsoleDeck(
    isBusy: Boolean,
    projectCount: Int,
    exportCount: Int,
    onNewProject: () -> Unit,
    onOpenMediaFile: () -> Unit,
    onTrySampleReel: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.studioSlate)
            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.16f)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WORKSTATION // MEDIA ENGINE",
                    style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "$projectCount CUTS • $exportCount RENDERS",
                    style = TimecodeTextStyle.copy(color = palette.textMuted, fontSize = 10.sp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNewProject,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.playheadCyan, contentColor = palette.voidBg),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1.1f).height(46.dp).testTag("new_project_button")
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Cut", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenMediaFile,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("open_universal_player_button")
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play File", color = palette.textPrimary, fontSize = 12.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onTrySampleReel,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(0.95f).height(46.dp).testTag("try_sample_reel_button")
                ) {
                    Icon(Icons.Default.PlayCircleOutline, null, tint = palette.audioAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Demo Cut", color = palette.audioAmber, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: StudioTemplate,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalStudioPalette.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = palette.panelSurface,
        modifier = Modifier
            .width(150.dp)
            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
            .testTag("template_${template.name.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(template.ratio.label, style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 10.sp))
            Text(template.title, style = MaterialTheme.typography.titleSmall, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(template.subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectVaultCard(
    project: VideoProject,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalStudioPalette.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = palette.panelSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
            .testTag("project_card_${project.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(width = 86.dp, height = 58.dp).clip(RoundedCornerShape(4.dp)).background(palette.elevatedTrack),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = project.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(model = thumbFile, contentDescription = project.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.VideoLibrary, null, tint = palette.textMuted, modifier = Modifier.size(24.dp))
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(TimecodeFormatter.formatDurationShort(project.totalDurationMs), style = TimecodeTextStyle.copy(fontSize = 9.sp, color = Color.White))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(project.aspectRatio.label, style = TimecodeTextStyle.copy(color = palette.playheadCyan, fontSize = 10.sp))
                    Text("${project.clips.size} clips • ${project.textOverlays.size + project.stickers.size} layers", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                val dateStr = remember(project.updatedAt) { SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(project.updatedAt)) }
                Text("Modified $dateStr", style = TimecodeTextStyle.copy(color = palette.textMuted, fontSize = 9.sp))
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.MoreVert, "Project options", tint = palette.textSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete", color = palette.cutCrimson) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = palette.cutCrimson) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultCard(hasProjects: Boolean, onNewProject: () -> Unit, onTrySample: () -> Unit) {
    val palette = LocalStudioPalette.current
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = palette.panelSurface,
        modifier = Modifier.fillMaxWidth().border(1.dp, palette.hairlineBorder, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.VideoLibrary, null, tint = palette.playheadCyan, modifier = Modifier.size(32.dp))
            Text(if (hasProjects) "No Matching Projects" else stringResource(R.string.empty_vault_title), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(if (hasProjects) "Try another filter." else stringResource(R.string.empty_vault_subtitle), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            if (!hasProjects) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onNewProject) { Text("Import Media", color = palette.playheadCyan) }
                    TextButton(onClick = onTrySample) { Text("Load Demo Cut", color = palette.audioAmber) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioSettingsSheet(
    settings: StudioSettings,
    onResolutionChange: (ExportResolution) -> Unit,
    onFpsChange: (Int) -> Unit,
    onSafeZoneToggle: (Boolean) -> Unit,
    onMagneticSnapToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalStudioPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.studioSlate, contentColor = palette.textPrimary) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Studio Preferences", style = MaterialTheme.typography.headlineSmall)
            Text("Default Export Resolution", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportResolution.entries.forEach { res ->
                    FilterChip(
                        selected = settings.defaultResolution == res,
                        onClick = { onResolutionChange(res) },
                        label = { Text(res.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.cyanMuted, selectedLabelColor = palette.playheadCyan)
                    )
                }
            }
            Text("Default Frame Rate", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 30, 60).forEach { fps ->
                    FilterChip(selected = settings.defaultFps == fps, onClick = { onFpsChange(fps) }, label = { Text("${fps}fps") })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rule-of-Thirds Safe Guides", style = MaterialTheme.typography.titleSmall)
                    Text("Show framing grid on monitor stage", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                Switch(checked = settings.showSafeZoneGuides, onCheckedChange = onSafeZoneToggle)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Magnetic Timeline Snap", style = MaterialTheme.typography.titleSmall)
                    Text("Snap cuts and beat pins while scrubbing", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
                Switch(checked = settings.magneticSnapEnabled, onCheckedChange = onMagneticSnapToggle)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
