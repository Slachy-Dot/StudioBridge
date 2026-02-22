package com.Slachy.StudioBridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obscontroller.OBSSceneItem

// Flat list entries for the LazyColumn
private sealed class UiEntry {
    data class TopLevel(val item: OBSSceneItem) : UiEntry()
    data class GroupHeader(val item: OBSSceneItem, val expanded: Boolean) : UiEntry()
    data class GroupChild(val item: OBSSceneItem, val groupName: String) : UiEntry()
    data class GroupLoading(val groupName: String) : UiEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneItemsSheet(
    sceneName: String,
    sceneItems: List<OBSSceneItem>,
    groupItems: Map<String, List<OBSSceneItem>> = emptyMap(),
    onToggleVisibility: (sceneItemId: Int, currentEnabled: Boolean) -> Unit,
    onDelete: (sceneItemId: Int) -> Unit,
    onToggleGroupItemVisibility: (groupName: String, sceneItemId: Int, currentEnabled: Boolean) -> Unit = { _, _, _ -> },
    onDeleteGroupItem: (groupName: String, sceneItemId: Int) -> Unit = { _, _ -> },
    onLoadGroupItems: (groupName: String) -> Unit = {},
    onOpenFilters: (sourceName: String) -> Unit = {},
    onOpenSettings: (sourceName: String, sourceKind: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    // Pair: item + optional groupName (null = top-level)
    var deleteTarget by remember { mutableStateOf<Pair<OBSSceneItem, String?>?>(null) }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    if (deleteTarget != null) {
        val (item, groupName) = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove source?") },
            text = { Text("\"${item.sourceName}\" will be removed from the scene.") },
            confirmButton = {
                TextButton(onClick = {
                    if (groupName != null) onDeleteGroupItem(groupName, item.sceneItemId)
                    else onDelete(item.sceneItemId)
                    deleteTarget = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = "Sources — $sceneName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (sceneItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val entries = remember(sceneItems, groupItems, expandedGroups) {
                    buildList {
                        for (item in sceneItems) {
                            if (item.inputKind == "group") {
                                val isExpanded = item.sourceName in expandedGroups
                                add(UiEntry.GroupHeader(item, isExpanded))
                                if (isExpanded) {
                                    val children = groupItems[item.sourceName]
                                    when {
                                        children == null -> add(UiEntry.GroupLoading(item.sourceName))
                                        children.isEmpty() -> { /* empty group, show nothing */ }
                                        else -> children.forEach { child ->
                                            add(UiEntry.GroupChild(child, item.sourceName))
                                        }
                                    }
                                }
                            } else {
                                add(UiEntry.TopLevel(item))
                            }
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(entries, key = { entry ->
                        when (entry) {
                            is UiEntry.TopLevel    -> "top_${entry.item.sceneItemId}"
                            is UiEntry.GroupHeader -> "grp_${entry.item.sceneItemId}"
                            is UiEntry.GroupChild  -> "child_${entry.groupName}_${entry.item.sceneItemId}"
                            is UiEntry.GroupLoading -> "loading_${entry.groupName}"
                        }
                    }) { entry ->
                        when (entry) {
                            is UiEntry.TopLevel -> SceneItemRow(
                                item = entry.item,
                                onToggleVisibility = { onToggleVisibility(entry.item.sceneItemId, entry.item.sceneItemEnabled) },
                                onOpenFilters = { onOpenFilters(entry.item.sourceName) },
                                onOpenSettings = { onOpenSettings(entry.item.sourceName, entry.item.inputKind) },
                                onDelete = { deleteTarget = entry.item to null }
                            )
                            is UiEntry.GroupHeader -> GroupHeaderRow(
                                item = entry.item,
                                expanded = entry.expanded,
                                onToggleExpand = {
                                    val name = entry.item.sourceName
                                    val nowExpanded = name !in expandedGroups
                                    expandedGroups = if (nowExpanded) expandedGroups + name else expandedGroups - name
                                    if (nowExpanded) onLoadGroupItems(name)
                                },
                                onToggleVisibility = { onToggleVisibility(entry.item.sceneItemId, entry.item.sceneItemEnabled) },
                                onOpenFilters = { onOpenFilters(entry.item.sourceName) },
                                onDelete = { deleteTarget = entry.item to null }
                            )
                            is UiEntry.GroupChild -> SceneItemRow(
                                item = entry.item,
                                indent = true,
                                onToggleVisibility = { onToggleGroupItemVisibility(entry.groupName, entry.item.sceneItemId, entry.item.sceneItemEnabled) },
                                onOpenFilters = { onOpenFilters(entry.item.sourceName) },
                                onOpenSettings = { onOpenSettings(entry.item.sourceName, entry.item.inputKind) },
                                onDelete = { deleteTarget = entry.item to entry.groupName }
                            )
                            is UiEntry.GroupLoading -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Group header row ──────────────────────────────────────────────────────────

@Composable
private fun GroupHeaderRow(
    item: OBSSceneItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleVisibility: () -> Unit,
    onOpenFilters: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = if (item.sceneItemEnabled) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.sceneItemEnabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Text(
                    text = "Group",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onOpenFilters) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Filters",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse group" else "Expand group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (item.sceneItemEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (item.sceneItemEnabled) "Hide group" else "Show group",
                    tint = if (item.sceneItemEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove group",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Regular item row (used for both top-level and group children) ─────────────

@Composable
private fun SceneItemRow(
    item: OBSSceneItem,
    indent: Boolean = false,
    onToggleVisibility: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = if (indent)
            Modifier.fillMaxWidth().padding(start = 20.dp)
        else
            Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            tonalElevation = if (item.sceneItemEnabled) 2.dp else 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (item.sceneItemEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = item.inputKind,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = onOpenFilters) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSourceConfigurable(item.inputKind)) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Source settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (item.sceneItemEnabled) Icons.Default.Visibility
                                      else Icons.Default.VisibilityOff,
                        contentDescription = if (item.sceneItemEnabled) "Hide source" else "Show source",
                        tint = if (item.sceneItemEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove source",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
