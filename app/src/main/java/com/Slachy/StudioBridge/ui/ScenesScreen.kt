package com.Slachy.StudioBridge.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import com.Slachy.StudioBridge.OBSFilter
import com.Slachy.StudioBridge.OBSSceneItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Slachy.StudioBridge.OBSScene

private const val TAG = "ScenesScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesScreen(
    scenes: List<OBSScene>,
    currentScene: String,
    previewScene: String,
    studioModeEnabled: Boolean,
    streamActive: Boolean,
    recordActive: Boolean,
    programScreenshot: String?,
    previewScreenshot: String?,
    onSceneClick: (String) -> Unit,
    onTransition: () -> Unit,
    onCut: () -> Unit,
    onToggleStudioMode: () -> Unit,
    onToggleStream: () -> Unit,
    onToggleRecord: () -> Unit,
    onCreateScene: (String) -> Unit = {},
    onAddSource: (inputName: String, inputKind: String) -> Unit,
    onPreviewVisibilityChange: (Boolean) -> Unit = {},
    sceneItems: List<OBSSceneItem> = emptyList(),
    groupItems: Map<String, List<OBSSceneItem>> = emptyMap(),
    onLoadSceneItems: (String) -> Unit = {},
    onToggleSceneItemVisibility: (sceneItemId: Int, currentEnabled: Boolean) -> Unit = { _, _ -> },
    onDeleteSceneItem: (sceneItemId: Int) -> Unit = {},
    onLoadGroupItems: (groupName: String) -> Unit = {},
    onToggleGroupItemVisibility: (groupName: String, sceneItemId: Int, currentEnabled: Boolean) -> Unit = { _, _, _ -> },
    onDeleteGroupItem: (groupName: String, sceneItemId: Int) -> Unit = { _, _ -> },
    filters: List<OBSFilter> = emptyList(),
    onLoadFilters: (String) -> Unit = {},
    onAddFilter: (filterName: String, filterKind: String) -> Unit = { _, _ -> },
    onRemoveFilter: (filterName: String) -> Unit = {},
    onToggleFilter: (filterName: String, currentEnabled: Boolean) -> Unit = { _, _ -> },
    onSetFilterSettings: (filterName: String, partialSettings: Map<String, Any>) -> Unit = { _, _ -> },
    inputSettings: Map<String, Any>? = null,
    onLoadInputSettings: (sourceName: String) -> Unit = {},
    onSetInputSettings: (sourceName: String, partialSettings: Map<String, Any>) -> Unit = { _, _ -> }
) {
    var showAddScene by remember { mutableStateOf(false) }
    var showAddSource by remember { mutableStateOf(false) }
    var sourcesSheetScene by remember { mutableStateOf<String?>(null) }
    var filterForSource by remember { mutableStateOf<String?>(null) }
    var settingsForSource by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmStream by remember { mutableStateOf(false) }
    var confirmRecord by remember { mutableStateOf(false) }

    if (confirmStream) {
        AlertDialog(
            onDismissRequest = { confirmStream = false },
            title = { Text(if (streamActive) "End Broadcast?" else "Go Live?") },
            text = {
                Text(if (streamActive) "This will stop your live stream."
                     else "This will start broadcasting to your stream.")
            },
            confirmButton = {
                Button(
                    onClick = { onToggleStream(); confirmStream = false },
                    colors = if (streamActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) { Text(if (streamActive) "End Broadcast" else "Go Live") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStream = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmRecord) {
        AlertDialog(
            onDismissRequest = { confirmRecord = false },
            title = { Text(if (recordActive) "End Recording?" else "Start Recording?") },
            text = {
                Text(if (recordActive) "This will stop the current recording."
                     else "This will start recording.")
            },
            confirmButton = {
                Button(
                    onClick = { onToggleRecord(); confirmRecord = false },
                    colors = if (recordActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) { Text(if (recordActive) "End Rec" else "Start Rec") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRecord = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(filterForSource) {
        if (filterForSource != null) onLoadFilters(filterForSource!!)
    }

    LaunchedEffect(settingsForSource) {
        if (settingsForSource != null) onLoadInputSettings(settingsForSource!!.first)
    }

    if (showAddScene) {
        AddSceneDialog(
            onAdd = { name ->
                onCreateScene(name)
                showAddScene = false
            },
            onDismiss = { showAddScene = false }
        )
    }

    if (showAddSource) {
        AddSourceDialog(
            currentSceneName = currentScene,
            onAdd = { name, kind ->
                onAddSource(name, kind)
                showAddSource = false
            },
            onDismiss = { showAddSource = false }
        )
    }

    if (sourcesSheetScene != null) {
        SceneItemsSheet(
            sceneName = sourcesSheetScene!!,
            sceneItems = sceneItems,
            groupItems = groupItems,
            onToggleVisibility = onToggleSceneItemVisibility,
            onDelete = onDeleteSceneItem,
            onToggleGroupItemVisibility = onToggleGroupItemVisibility,
            onDeleteGroupItem = onDeleteGroupItem,
            onLoadGroupItems = onLoadGroupItems,
            onOpenFilters = { sourceName -> filterForSource = sourceName },
            onOpenSettings = { sourceName, sourceKind -> settingsForSource = sourceName to sourceKind },
            onDismiss = { sourcesSheetScene = null }
        )
    }

    if (settingsForSource != null) {
        SourceSettingsSheet(
            sourceName = settingsForSource!!.first,
            sourceKind = settingsForSource!!.second,
            settings = inputSettings,
            onSetSettings = { partial -> onSetInputSettings(settingsForSource!!.first, partial) },
            onDismiss = { settingsForSource = null }
        )
    }

    if (filterForSource != null) {
        FiltersSheet(
            sourceName = filterForSource!!,
            filters = filters,
            filterTypes = VIDEO_FILTER_TYPES,
            onAddFilter = onAddFilter,
            onRemoveFilter = onRemoveFilter,
            onToggleFilter = onToggleFilter,
            onSetFilterSettings = onSetFilterSettings,
            onDismiss = { filterForSource = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(onClick = { showAddScene = true }) {
                    Icon(Icons.Default.Movie, contentDescription = "New Scene")
                }
                FloatingActionButton(onClick = { showAddSource = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Source")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Stream + Studio mode control bar ──────────────────────────────
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stream + Record buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (streamActive) {
                            Button(
                                onClick = { confirmStream = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop stream", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("End Broadcast")
                            }
                        } else {
                            Button(onClick = { confirmStream = true }) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = "Go live",
                                    modifier = Modifier.size(16.dp), tint = Color.Red)
                                Spacer(Modifier.width(4.dp))
                                Text("Go Live")
                            }
                        }

                        if (recordActive) {
                            Button(
                                onClick = { confirmRecord = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop recording", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("End Rec")
                            }
                        } else {
                            OutlinedButton(onClick = { confirmRecord = true }) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = "Start recording",
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(4.dp))
                                Text("Rec")
                            }
                        }
                    }

                    // Studio mode toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Studio", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = studioModeEnabled,
                            onCheckedChange = { onToggleStudioMode() }
                        )
                    }
                }
            }

            // ── Studio mode split view ────────────────────────────────────────
            if (studioModeEnabled) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Preview box
                        SceneBox(
                            label = "PREVIEW",
                            sceneName = previewScene,
                            screenshot = previewScreenshot,
                            labelColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )

                        // Program box
                        SceneBox(
                            label = "On Air",
                            sceneName = currentScene,
                            screenshot = programScreenshot,
                            labelColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onCut, modifier = Modifier.weight(1f)) {
                            Text("Cut")
                        }
                        Button(onClick = onTransition, modifier = Modifier.weight(1f)) {
                            Text("Transition")
                        }
                    }
                }

                HorizontalDivider()
            }

            // ── Scene cards ───────────────────────────────────────────────────
            if (scenes.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val activeScene = if (studioModeEnabled) previewScene else currentScene

                // Program thumbnail when studio mode OFF
                if (!studioModeEnabled) {
                    var previewVisible by remember { mutableStateOf(true) }
                    var previewPaused by remember { mutableStateOf(false) }
                    LaunchedEffect(previewVisible, previewPaused) {
                        onPreviewVisibilityChange(previewVisible && !previewPaused)
                    }

                    // Toggle bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { previewVisible = !previewVisible }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "LIVE: $currentScene",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (streamActive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { previewPaused = !previewPaused },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (previewPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (previewPaused) "Resume preview" else "Pause preview",
                                tint = if (previewPaused) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Icon(
                            imageVector = if (previewVisible) Icons.Default.KeyboardArrowUp
                                          else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (previewVisible) "Hide preview" else "Show preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = previewVisible,
                        enter = slideInVertically(initialOffsetY = { -it }),
                        exit = slideOutVertically(targetOffsetY = { -it })
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ScreenshotImage(
                                base64 = programScreenshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(scenes) { scene ->
                        val isActive = scene.name == activeScene
                        val isProgram = scene.name == currentScene
                        SceneCard(
                            scene = scene,
                            isActive = isActive,
                            isProgram = isProgram,
                            studioModeEnabled = studioModeEnabled,
                            onClick = { onSceneClick(scene.name) },
                            onShowSources = {
                                sourcesSheetScene = scene.name
                                onLoadSceneItems(scene.name)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneCard(
    scene: OBSScene,
    isActive: Boolean,
    isProgram: Boolean,
    studioModeEnabled: Boolean,
    onClick: () -> Unit,
    onShowSources: () -> Unit
) {
    val borderColor = when {
        isProgram && studioModeEnabled -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val label = when {
        isProgram && studioModeEnabled -> "On Air"
        isActive && studioModeEnabled -> "Preview"
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive || isProgram) 2.dp else 0.dp,
            color = borderColor
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (label != null) borderColor else Color.Transparent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = scene.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                color = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onShowSources,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "View sources",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneBox(
    label: String,
    sceneName: String,
    screenshot: String?,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )
        Spacer(Modifier.height(4.dp))
        ScreenshotImage(
            base64 = screenshot,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sceneName.ifEmpty { "—" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun ScreenshotImage(base64: String?, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(base64) {
        base64 ?: return@remember null
        try {
            val clean = if (',' in base64) base64.substringAfter(",") else base64
            val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode screenshot bitmap", e)
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (base64 == null) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
