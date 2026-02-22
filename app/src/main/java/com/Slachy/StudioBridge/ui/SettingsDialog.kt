package com.Slachy.StudioBridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    twitchChannel: String,
    chatFontSize: Float,
    chatLineSpacing: Float,
    animatedEmotes: Boolean,
    enable7tv: Boolean,
    enableBttv: Boolean,
    enableFfz: Boolean,
    onSaveChannel: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onAnimatedEmotesChange: (Boolean) -> Unit,
    onEnable7tvChange: (Boolean) -> Unit,
    onEnableBttvChange: (Boolean) -> Unit,
    onEnableFfzChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var channelInput by remember(twitchChannel) { mutableStateOf(twitchChannel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // ── Twitch channel ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Twitch Channel", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            placeholder = { Text("channelname") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = channelInput.trim().lowercase()
                                if (trimmed.isNotBlank()) onSaveChannel(trimmed)
                            })
                        )
                        FilledTonalButton(
                            onClick = {
                                val trimmed = channelInput.trim().lowercase()
                                if (trimmed.isNotBlank()) onSaveChannel(trimmed)
                            },
                            enabled = channelInput.isNotBlank()
                        ) { Text("Save") }
                    }
                }

                HorizontalDivider()

                // ── Font size ──────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatFontSize.roundToInt()} sp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatFontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 10f..20f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Line spacing ───────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Line Spacing", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatLineSpacing.roundToInt()} dp",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = chatLineSpacing,
                        onValueChange = onLineSpacingChange,
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Reset chat display ─────────────────────────────────────
                TextButton(
                    onClick = {
                        onFontSizeChange(13f)
                        onLineSpacingChange(4f)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset font & spacing to defaults",
                        style = MaterialTheme.typography.labelSmall)
                }

                HorizontalDivider()

                // ── Animated emotes ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Animated Emotes", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Show GIFs as moving images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = animatedEmotes,
                        onCheckedChange = onAnimatedEmotesChange
                    )
                }

                HorizontalDivider()

                // ── Emote providers ────────────────────────────────────────
                Text("Emote Providers", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("7TV", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enable7tv, onCheckedChange = onEnable7tvChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BetterTTV", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enableBttv, onCheckedChange = onEnableBttvChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FrankerFaceZ", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enableFfz, onCheckedChange = onEnableFfzChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
