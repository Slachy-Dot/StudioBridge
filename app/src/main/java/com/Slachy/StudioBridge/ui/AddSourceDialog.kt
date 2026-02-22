package com.Slachy.StudioBridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class SourceType(val label: String, val kind: String)

private val SOURCE_TYPES = listOf(
    SourceType("Browser Source", "browser_source"),
    SourceType("Media File", "ffmpeg_source"),
    SourceType("Image", "image_source"),
    SourceType("Color Source", "color_source"),
    SourceType("Text (GDI+)", "text_gdiplus"),
    SourceType("Text (FreeType2)", "text_ft2_source"),
    SourceType("Window Capture", "window_capture"),
    SourceType("Display Capture", "monitor_capture"),
    SourceType("Audio Input Capture", "wasapi_input_capture"),
    SourceType("Audio Output Capture", "wasapi_output_capture"),
)

@Composable
fun AddSourceDialog(
    currentSceneName: String,
    onAdd: (inputName: String, inputKind: String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SOURCE_TYPES.first()) }
    var showTypePicker by remember { mutableStateOf(false) }

    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text("Select Source Type") },
            text = {
                LazyColumn {
                    items(SOURCE_TYPES) { type ->
                        TextButton(
                            onClick = {
                                selectedType = type
                                showTypePicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = type.label,
                                color = if (type == selectedType)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adding to: $currentSceneName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Source Name") },
                    placeholder = { Text("My Browser Source") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showTypePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Source Type", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedType.label)
                    }
                }

                Text(
                    "Note: source will be created with default settings. Configure it in OBS after adding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(inputName.trim(), selectedType.kind) },
                enabled = inputName.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
