package com.Slachy.StudioBridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class TwitchChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val username: String,
    val message: String,
    val color: String? = null,                  // hex like "#FF4500" or null
    val emotesTag: String? = null,              // raw IRC emotes tag e.g. "25:0-4,6-10/1902:12-17"
    val badgeTags: List<String> = emptyList()   // e.g. ["broadcaster/1", "subscriber/0", "premium/1"]
)

class TwitchChatClient {

    private val http = OkHttpClient()
    private var socket: WebSocket? = null

    private val _messages = MutableStateFlow<List<TwitchChatMessage>>(emptyList())
    val messages: StateFlow<List<TwitchChatMessage>> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _roomId = MutableStateFlow("")
    val roomId: StateFlow<String> = _roomId

    fun connect(channel: String) {
        disconnect()
        _messages.value = emptyList()
        _roomId.value = ""

        val nick = "justinfan${(10000..99999).random()}"
        val req = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()

        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                ws.send("NICK $nick")
                ws.send("JOIN #${channel.lowercase()}")
                _connected.value = true
            }

            override fun onMessage(ws: WebSocket, text: String) {
                text.lines().forEach { handleLine(it.trim()) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connected.value = false
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
            }
        })
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return

        // Keep-alive
        if (line.startsWith("PING")) {
            socket?.send("PONG :tmi.twitch.tv")
            return
        }

        // Extract the channel's Twitch user ID from ROOMSTATE
        if (line.contains("ROOMSTATE")) {
            if (line.startsWith("@")) {
                val spaceIdx = line.indexOf(' ')
                if (spaceIdx > 0) {
                    line.substring(1, spaceIdx).split(";").forEach { tag ->
                        val eqIdx = tag.indexOf('=')
                        if (eqIdx >= 0 && tag.substring(0, eqIdx) == "room-id") {
                            val id = tag.substring(eqIdx + 1)
                            if (id.toLongOrNull() != null) _roomId.value = id
                        }
                    }
                }
            }
            return
        }

        if (!line.contains("PRIVMSG")) return

        try {
            var rest = line
            var color: String? = null
            var displayName: String? = null
            var emotesTag: String? = null
            var badgesStr: String? = null

            // Strip IRCv3 tags: @key=value;key=value ... <space> rest-of-line
            if (rest.startsWith("@")) {
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx < 0) return
                val tagsStr = rest.substring(1, spaceIdx)
                rest = rest.substring(spaceIdx + 1).trimStart()

                tagsStr.split(";").forEach { tag ->
                    val eqIdx = tag.indexOf('=')
                    if (eqIdx < 0) return@forEach
                    val key = tag.substring(0, eqIdx)
                    val value = tag.substring(eqIdx + 1)
                    when (key) {
                        "color"        -> if (value.isNotEmpty()) color = value
                        "display-name" -> if (value.isNotEmpty()) displayName = value
                        "emotes"       -> if (value.isNotEmpty()) emotesTag = value
                        "badges"       -> if (value.isNotEmpty()) badgesStr = value
                    }
                }
            }

            // rest: :nick!user@host PRIVMSG #channel :message text
            val prefix   = rest.removePrefix(":").substringBefore("!")
            val username = displayName ?: prefix.ifEmpty { return }

            // Message starts after the second ':'
            val msgIdx = rest.indexOf(':', 1)
            if (msgIdx < 0) return
            val message = rest.substring(msgIdx + 1)
            if (message.isBlank()) return

            val badges = badgesStr?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

            _messages.value = (_messages.value + TwitchChatMessage(
                username   = username,
                message    = message,
                color      = color,
                emotesTag  = emotesTag,
                badgeTags  = badges
            )).takeLast(MAX_CHAT_MESSAGES)

        } catch (_: Exception) { }
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        _roomId.value = ""
    }
}
