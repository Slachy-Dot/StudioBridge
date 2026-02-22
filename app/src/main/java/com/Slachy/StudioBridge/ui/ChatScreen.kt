package com.Slachy.StudioBridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import com.obscontroller.MessageSegment
import com.obscontroller.TwitchChatMessage
import com.obscontroller.parseMessageSegments

@Composable
fun ChatScreen(
    twitchChannel: String,
    chatMessages: List<TwitchChatMessage>,
    chatConnected: Boolean,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    emoteLoadReport: String,
    chatFontSize: Float,
    chatLineSpacing: Float,
    animatedEmotes: Boolean,
    onConnect: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-connect when a channel is configured
    LaunchedEffect(twitchChannel) {
        if (twitchChannel.isNotEmpty()) onConnect()
    }

    // Auto-scroll: consider "at bottom" if last visible item is within 1 of the end.
    // The ±1 buffer handles the race where totalItemsCount increases right as
    // a new message is added, before the LaunchedEffect can read the layout.
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || lastVisible >= total - 2
        }
    }
    LaunchedEffect(chatMessages.size) {
        if (isAtBottom && chatMessages.isNotEmpty()) {
            listState.scrollToItem(chatMessages.size - 1)
        }
    }

    val context = LocalContext.current
    val imageLoader: ImageLoader = remember(animatedEmotes) {
        if (animatedEmotes) {
            ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
        } else {
            ImageLoader.Builder(context).build()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status row ────────────────────────────────────────────────────────
        if (twitchChannel.isNotEmpty()) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (chatConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (chatConnected) "#$twitchChannel" else "Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = emoteLoadReport,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    if (!chatConnected) {
                        TextButton(onClick = onConnect) { Text("Reconnect") }
                    }
                }
            }
            HorizontalDivider()
        }

        // ── Messages / empty states ───────────────────────────────────────────
        if (twitchChannel.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap the StudioBridge title above to open Settings and enter your Twitch channel name.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (chatMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (chatConnected) "Waiting for chat messages…" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatMessageRow(
                        message = msg,
                        thirdPartyEmotes = thirdPartyEmotes,
                        twitchBadges = twitchBadges,
                        fontSize = chatFontSize,
                        lineSpacing = chatLineSpacing,
                        imageLoader = imageLoader
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: TwitchChatMessage,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    fontSize: Float,
    lineSpacing: Float,
    imageLoader: ImageLoader
) {
    val badgeSize = (fontSize * 1.1f).sp
    val emoteSize = (fontSize * 1.6f).sp

    val defaultColor = MaterialTheme.colorScheme.primary
    val nameColor: Color = remember(message.color, defaultColor) {
        if (message.color != null) {
            try { Color(android.graphics.Color.parseColor(message.color)) }
            catch (_: Exception) { defaultColor }
        } else defaultColor
    }

    // Resolve badge URLs, falling back to version "0" for channel-specific sets
    val badgeUrls: List<String> = remember(message.id, twitchBadges.size) {
        message.badgeTags.mapNotNull { tag ->
            twitchBadges[tag] ?: twitchBadges["${tag.substringBefore('/')}/0"]
        }
    }

    val segments: List<MessageSegment> = remember(message.id, thirdPartyEmotes.size) {
        parseMessageSegments(message.message, message.emotesTag, thirdPartyEmotes)
    }

    val inlineContent: Map<String, InlineTextContent> = remember(
        message.id, thirdPartyEmotes.size, twitchBadges.size, imageLoader, fontSize
    ) {
        buildMap {
            badgeUrls.forEach { url ->
                put(url, InlineTextContent(
                    Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.TextCenter)
                ) {
                    AsyncImage(model = url, contentDescription = null,
                        imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                })
            }
            segments.filterIsInstance<MessageSegment.EmotePart>()
                .distinctBy { it.url }
                .forEach { emote ->
                    put(emote.url, InlineTextContent(
                        Placeholder(emoteSize, emoteSize, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(model = emote.url, contentDescription = emote.name,
                            imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                    })
                }
        }
    }

    val annotatedText = remember(message.id, thirdPartyEmotes.size, twitchBadges.size, nameColor) {
        buildAnnotatedString {
            badgeUrls.forEachIndexed { i, url ->
                appendInlineContent(url, "[badge]")
                if (i < badgeUrls.lastIndex) append('\u2009') else append(' ')
            }
            withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold)) {
                append(message.username)
            }
            append(": ")
            segments.forEach { seg ->
                when (seg) {
                    is MessageSegment.TextPart  -> append(seg.text)
                    is MessageSegment.EmotePart -> appendInlineContent(seg.url, "[${seg.name}]")
                }
            }
        }
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize + lineSpacing).sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    )
}
