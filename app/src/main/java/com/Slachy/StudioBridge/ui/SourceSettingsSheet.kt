package com.Slachy.StudioBridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

val CONFIGURABLE_SOURCE_KINDS = setOf("browser_source", "ffmpeg_source")

fun isSourceConfigurable(kind: String) = kind in CONFIGURABLE_SOURCE_KINDS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsSheet(
    sourceName: String,
    sourceKind: String,
    settings: Map<String, Any>?,   // null = still loading
    onSetSettings: (Map<String, Any>) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = "Settings — $sourceName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = sourceKind,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (settings == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    item {
                        when (sourceKind) {
                            "browser_source" ->
                                BrowserSourceSettings(settings = settings, onSetSettings = onSetSettings)
                            "ffmpeg_source" ->
                                MediaSourceSettings(settings = settings, onSetSettings = onSetSettings)
                            else -> Text(
                                "Settings for '$sourceKind' are not configurable from this app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Browser source ────────────────────────────────────────────────────────────

@Composable
private fun BrowserSourceSettings(
    settings: Map<String, Any>,
    onSetSettings: (Map<String, Any>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextFieldWithApply(
            label = "URL",
            initialValue = settings.str("url", "https://"),
            placeholder = "https://example.com",
            onApply = { onSetSettings(mapOf("url" to it)) }
        )

        SettingSliderInt(
            label = "Width",
            externalValue = settings.int("width", 800),
            range = 1..7680,
            unit = " px",
            onValueChangeFinished = { onSetSettings(mapOf("width" to it)) }
        )

        SettingSliderInt(
            label = "Height",
            externalValue = settings.int("height", 600),
            range = 1..4320,
            unit = " px",
            onValueChangeFinished = { onSetSettings(mapOf("height" to it)) }
        )

        SettingSliderInt(
            label = "FPS",
            externalValue = settings.int("fps", 30),
            range = 1..60,
            unit = " fps",
            onValueChangeFinished = { onSetSettings(mapOf("fps" to it)) }
        )

        TextFieldWithApply(
            label = "Custom CSS",
            initialValue = settings.str("css", ""),
            placeholder = "body { background-color: transparent; }",
            singleLine = false,
            onApply = { onSetSettings(mapOf("css" to it)) }
        )

        HorizontalDivider()

        SettingSwitch(
            label = "Shutdown when not visible",
            checked = settings.bool("shutdown", false),
            onCheckedChange = { onSetSettings(mapOf("shutdown" to it)) }
        )

        SettingSwitch(
            label = "Restart browser when activated",
            checked = settings.bool("restart_when_active", false),
            onCheckedChange = { onSetSettings(mapOf("restart_when_active" to it)) }
        )
    }
}

// ── Media source ──────────────────────────────────────────────────────────────

@Composable
private fun MediaSourceSettings(
    settings: Map<String, Any>,
    onSetSettings: (Map<String, Any>) -> Unit
) {
    val isLocal = settings.bool("is_local_file", true)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitch(
            label = "Local file",
            checked = isLocal,
            onCheckedChange = { onSetSettings(mapOf("is_local_file" to it)) }
        )

        if (isLocal) {
            TextFieldWithApply(
                label = "File path",
                initialValue = settings.str("local_file", ""),
                placeholder = "/path/to/video.mp4",
                onApply = { onSetSettings(mapOf("local_file" to it)) }
            )
        } else {
            TextFieldWithApply(
                label = "Network URL",
                initialValue = settings.str("input", ""),
                placeholder = "https://...",
                onApply = { onSetSettings(mapOf("input" to it)) }
            )
            SettingSliderInt(
                label = "Network buffering",
                externalValue = settings.int("buffering_mb", 2),
                range = 0..16,
                unit = " MB",
                onValueChangeFinished = { onSetSettings(mapOf("buffering_mb" to it)) }
            )
            SettingSliderInt(
                label = "Reconnect delay",
                externalValue = settings.int("reconnect_delay_sec", 10),
                range = 0..60,
                unit = " s",
                onValueChangeFinished = { onSetSettings(mapOf("reconnect_delay_sec" to it)) }
            )
        }

        HorizontalDivider()

        SettingSwitch(
            label = "Loop",
            checked = settings.bool("looping", false),
            onCheckedChange = { onSetSettings(mapOf("looping" to it)) }
        )

        SettingSwitch(
            label = "Restart when activated",
            checked = settings.bool("restart_on_activate", true),
            onCheckedChange = { onSetSettings(mapOf("restart_on_activate" to it)) }
        )

        SettingSwitch(
            label = "Close file when inactive",
            checked = settings.bool("close_when_inactive", false),
            onCheckedChange = { onSetSettings(mapOf("close_when_inactive" to it)) }
        )

        SettingSwitch(
            label = "Use hardware decoding",
            checked = settings.bool("hw_decode", false),
            onCheckedChange = { onSetSettings(mapOf("hw_decode" to it)) }
        )

        SettingSwitch(
            label = "Show nothing when playback ends",
            checked = settings.bool("clear_on_media_end", false),
            onCheckedChange = { onSetSettings(mapOf("clear_on_media_end" to it)) }
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun TextFieldWithApply(
    label: String,
    initialValue: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    onApply: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val isDirty = value != initialValue

    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                maxLines = if (singleLine) 1 else 5,
                keyboardOptions = if (singleLine)
                    KeyboardOptions(imeAction = ImeAction.Done)
                else
                    KeyboardOptions.Default,
                keyboardActions = if (singleLine)
                    KeyboardActions(onDone = { onApply(value) })
                else
                    KeyboardActions.Default,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = { onApply(value) },
                enabled = isDirty
            ) {
                Icon(Icons.Default.Check, contentDescription = "Apply")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun SettingSliderInt(
    label: String,
    externalValue: Int,
    range: IntRange,
    unit: String = "",
    onValueChangeFinished: (Int) -> Unit
) {
    var local by remember(externalValue) { mutableIntStateOf(externalValue) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "$local$unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = local.toFloat(),
            onValueChange = { local = it.roundToInt() },
            onValueChangeFinished = { onValueChangeFinished(local) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Map helpers ───────────────────────────────────────────────────────────────

private fun Map<String, Any>.str(key: String, default: String): String =
    this[key] as? String ?: default

private fun Map<String, Any>.bool(key: String, default: Boolean): Boolean =
    this[key] as? Boolean ?: default

private fun Map<String, Any>.int(key: String, default: Int): Int =
    (this[key] as? Double)?.toInt() ?: (this[key] as? Int) ?: default
