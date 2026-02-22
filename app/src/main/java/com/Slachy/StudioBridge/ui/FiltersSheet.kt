package com.Slachy.StudioBridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obscontroller.OBSFilter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    sourceName: String,
    filters: List<OBSFilter>,
    filterTypes: List<FilterTypeEntry> = AUDIO_FILTER_TYPES,
    onAddFilter: (filterName: String, filterKind: String) -> Unit,
    onRemoveFilter: (filterName: String) -> Unit,
    onToggleFilter: (filterName: String, currentEnabled: Boolean) -> Unit,
    onSetFilterSettings: (filterName: String, partialSettings: Map<String, Any>) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedFilter by remember { mutableStateOf<String?>(null) }
    var deleteConfirmFor by remember { mutableStateOf<String?>(null) }

    if (deleteConfirmFor != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmFor = null },
            title = { Text("Remove filter?") },
            text = { Text("\"$deleteConfirmFor\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFilter(deleteConfirmFor!!)
                    deleteConfirmFor = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmFor = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddDialog) {
        AddFilterDialog(
            title = if (filterTypes === VIDEO_FILTER_TYPES) "Add Video Filter" else "Add Audio Filter",
            filterTypes = filterTypes,
            onAdd = { name, kind ->
                onAddFilter(name, kind)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters — $sourceName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add filter",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No filters. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filters, key = { it.filterName }) { filter ->
                        FilterRow(
                            filter = filter,
                            expanded = expandedFilter == filter.filterName,
                            onExpand = {
                                expandedFilter =
                                    if (expandedFilter == filter.filterName) null
                                    else filter.filterName
                            },
                            onToggle = { onToggleFilter(filter.filterName, filter.filterEnabled) },
                            onDelete = { deleteConfirmFor = filter.filterName },
                            onSettingsChange = { partial ->
                                onSetFilterSettings(filter.filterName, partial)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterRow(
    filter: OBSFilter,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSettingsChange: (Map<String, Any>) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (filter.filterEnabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = filter.filterName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (filter.filterEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = filter.filterKind,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand settings",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Switch(
                    checked = filter.filterEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.height(32.dp)
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove filter",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    FilterSettingsContent(filter = filter, onSettingsChange = onSettingsChange)
                }
            }
        }
    }
}

@Composable
private fun FilterSettingsContent(
    filter: OBSFilter,
    onSettingsChange: (Map<String, Any>) -> Unit
) {
    val s = filter.filterSettings
    when (filter.filterKind) {

        // ── Audio filters ─────────────────────────────────────────────────────

        "gain_filter" -> {
            SettingSlider("Gain", s.dbl("db", 0.0), -30f..30f, " dB") { v ->
                onSettingsChange(mapOf("db" to v.toDouble()))
            }
        }
        "noise_gate_filter" -> {
            SettingSlider("Open Threshold", s.dbl("open_threshold", -26.0), -96f..0f, " dB") { v ->
                onSettingsChange(mapOf("open_threshold" to v.toDouble()))
            }
            SettingSlider("Close Threshold", s.dbl("close_threshold", -32.0), -96f..0f, " dB") { v ->
                onSettingsChange(mapOf("close_threshold" to v.toDouble()))
            }
            SettingSlider("Attack", s.dbl("attack_time", 25.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("attack_time" to v.toDouble()))
            }
            SettingSlider("Hold", s.dbl("hold_time", 200.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("hold_time" to v.toDouble()))
            }
            SettingSlider("Release", s.dbl("release_time", 150.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("release_time" to v.toDouble()))
            }
        }
        "compressor_filter" -> {
            SettingSlider("Threshold", s.dbl("threshold", -18.0), -60f..0f, " dB") { v ->
                onSettingsChange(mapOf("threshold" to v.toDouble()))
            }
            SettingSlider("Ratio", s.dbl("ratio", 10.0), 1f..32f, ":1") { v ->
                onSettingsChange(mapOf("ratio" to v.toDouble()))
            }
            SettingSlider("Attack", s.dbl("attack", 6.0), 1f..500f, " ms") { v ->
                onSettingsChange(mapOf("attack" to v.toDouble()))
            }
            SettingSlider("Release", s.dbl("release", 60.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("release" to v.toDouble()))
            }
            SettingSlider("Output Gain", s.dbl("output_gain", 0.0), -32f..32f, " dB") { v ->
                onSettingsChange(mapOf("output_gain" to v.toDouble()))
            }
        }
        "noise_suppress_filter", "noise_suppress_filter_v2" -> {
            SettingSlider("Suppression Level", s.dbl("suppress_level", -30.0), -60f..0f, " dB") { v ->
                onSettingsChange(mapOf("suppress_level" to v.toDouble()))
            }
        }
        "limiter_filter" -> {
            SettingSlider("Threshold", s.dbl("threshold", -6.0), -60f..0f, " dB") { v ->
                onSettingsChange(mapOf("threshold" to v.toDouble()))
            }
            SettingSlider("Release", s.dbl("release_time", 60.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("release_time" to v.toDouble()))
            }
        }
        "expander_filter" -> {
            SettingSlider("Threshold", s.dbl("threshold", -40.0), -60f..0f, " dB") { v ->
                onSettingsChange(mapOf("threshold" to v.toDouble()))
            }
            SettingSlider("Ratio", s.dbl("ratio", 2.0), 1f..20f, ":1") { v ->
                onSettingsChange(mapOf("ratio" to v.toDouble()))
            }
            SettingSlider("Attack", s.dbl("attack", 10.0), 1f..500f, " ms") { v ->
                onSettingsChange(mapOf("attack" to v.toDouble()))
            }
            SettingSlider("Release", s.dbl("release", 50.0), 1f..1000f, " ms") { v ->
                onSettingsChange(mapOf("release" to v.toDouble()))
            }
            SettingSlider("Output Gain", s.dbl("output_gain", 0.0), -32f..32f, " dB") { v ->
                onSettingsChange(mapOf("output_gain" to v.toDouble()))
            }
        }

        // ── Video filters ─────────────────────────────────────────────────────

        "color_correction_filter" -> {
            SettingSlider("Brightness", s.dbl("brightness", 0.0), -1f..1f, decimals = 2) { v ->
                onSettingsChange(mapOf("brightness" to v.toDouble()))
            }
            SettingSlider("Contrast", s.dbl("contrast", 0.0), -2f..2f, decimals = 2) { v ->
                onSettingsChange(mapOf("contrast" to v.toDouble()))
            }
            SettingSlider("Saturation", s.dbl("saturation", 0.0), -1f..5f, decimals = 2) { v ->
                onSettingsChange(mapOf("saturation" to v.toDouble()))
            }
            SettingSlider("Hue Shift", s.dbl("hue_shift", 0.0), -180f..180f, "°") { v ->
                onSettingsChange(mapOf("hue_shift" to v.toDouble()))
            }
            SettingSlider("Gamma", s.dbl("gamma", 0.0), -3f..3f, decimals = 2) { v ->
                onSettingsChange(mapOf("gamma" to v.toDouble()))
            }
            SettingSlider("Opacity", s.dbl("opacity", 1.0), 0f..1f, decimals = 2) { v ->
                onSettingsChange(mapOf("opacity" to v.toDouble()))
            }
        }
        "sharpness_filter" -> {
            SettingSlider("Sharpness", s.dbl("sharpness", 0.08), 0f..1f, decimals = 2) { v ->
                onSettingsChange(mapOf("sharpness" to v.toDouble()))
            }
        }
        "crop_filter" -> {
            SettingSlider("Top", s.dbl("top", 0.0), 0f..2000f, " px") { v ->
                onSettingsChange(mapOf("top" to v.toDouble()))
            }
            SettingSlider("Bottom", s.dbl("bottom", 0.0), 0f..2000f, " px") { v ->
                onSettingsChange(mapOf("bottom" to v.toDouble()))
            }
            SettingSlider("Left", s.dbl("left", 0.0), 0f..2000f, " px") { v ->
                onSettingsChange(mapOf("left" to v.toDouble()))
            }
            SettingSlider("Right", s.dbl("right", 0.0), 0f..2000f, " px") { v ->
                onSettingsChange(mapOf("right" to v.toDouble()))
            }
        }
        "gpu_delay" -> {
            SettingSlider("Delay", s.dbl("delay_ms", 0.0), 0f..5000f, " ms") { v ->
                onSettingsChange(mapOf("delay_ms" to v.toDouble()))
            }
        }
        "chroma_key_filter" -> {
            SettingSlider("Similarity", s.dbl("similarity", 80.0), 1f..1000f) { v ->
                onSettingsChange(mapOf("similarity" to v.toDouble()))
            }
            SettingSlider("Smoothness", s.dbl("smoothness", 50.0), 1f..1000f) { v ->
                onSettingsChange(mapOf("smoothness" to v.toDouble()))
            }
            SettingSlider("Spill Reduction", s.dbl("spill", 100.0), 1f..1000f) { v ->
                onSettingsChange(mapOf("spill" to v.toDouble()))
            }
            SettingSlider("Opacity", s.dbl("opacity", 1.0), 0f..1f, decimals = 2) { v ->
                onSettingsChange(mapOf("opacity" to v.toDouble()))
            }
        }
        "color_key_filter" -> {
            SettingSlider("Similarity", s.dbl("similarity", 80.0), 1f..1000f) { v ->
                onSettingsChange(mapOf("similarity" to v.toDouble()))
            }
            SettingSlider("Smoothness", s.dbl("smoothness", 50.0), 1f..1000f) { v ->
                onSettingsChange(mapOf("smoothness" to v.toDouble()))
            }
            SettingSlider("Opacity", s.dbl("opacity", 1.0), 0f..1f, decimals = 2) { v ->
                onSettingsChange(mapOf("opacity" to v.toDouble()))
            }
        }

        else -> {
            Text(
                "Settings for '${filter.filterKind}' are not configurable from this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    externalValue: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    decimals: Int = 0,
    onValueChangeFinished: (Float) -> Unit
) {
    var local by remember(externalValue) { mutableFloatStateOf(externalValue) }
    val displayText = if (decimals > 0) "%.${decimals}f$unit".format(local)
                      else "${local.roundToInt()}$unit"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                displayText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onValueChangeFinished(local) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Map<String, Any>.dbl(key: String, default: Double): Float =
    (this[key] as? Double)?.toFloat() ?: default.toFloat()
