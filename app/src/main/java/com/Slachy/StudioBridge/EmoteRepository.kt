package com.Slachy.StudioBridge

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class EmoteRepository {

    // Single shared client with a browser-like UA so APIs don't reject us
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private val _thirdPartyEmotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val thirdPartyEmotes: StateFlow<Map<String, String>> = _thirdPartyEmotes

    private val _twitchBadges = MutableStateFlow<Map<String, String>>(emptyMap())
    val twitchBadges: StateFlow<Map<String, String>> = _twitchBadges

    private val _loadReport = MutableStateFlow("loading…")
    val loadReport: StateFlow<String> = _loadReport

    /**
     * Loads emote providers and global badges sequentially on the IO dispatcher.
     * Clears existing third-party emotes first so toggling a provider off takes effect.
     * loadReport is updated with per-source counts, "off", or exception class names.
     */
    suspend fun loadAll(
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ) = withContext(Dispatchers.IO) {
        _thirdPartyEmotes.value = emptyMap()

        fun loadProvider(enabled: Boolean, fetch: () -> Map<String, String>): String {
            if (!enabled) return "off"
            return runCatching { fetch() }
                .onSuccess { map -> _thirdPartyEmotes.update { current -> current + map } }
                .fold({ it.size.toString() }, { it.javaClass.simpleName })
        }

        val s7tv  = loadProvider(enable7tv,  ::fetch7TV)
        val sBttv = loadProvider(enableBttv, ::fetchBTTV)
        val sFfz  = loadProvider(enableFfz,  ::fetchFFZ)

        val sBdg = runCatching { fetchTwitchBadges() }
            .onSuccess { badges -> _twitchBadges.value = badges }
            .fold({ it.size.toString() }, { it.javaClass.simpleName })

        _loadReport.value = "7tv:$s7tv bttv:$sBttv ffz:$sFfz bdg:$sBdg"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun get(url: String): String? {
        val req = Request.Builder().url(url).build()
        val resp = http.newCall(req).execute()
        return if (resp.isSuccessful) resp.body?.string() else null
    }

    // ── 7TV global emotes ─────────────────────────────────────────────────────
    // GET https://7tv.io/v3/emote-sets/global
    // Response: { emotes: [{ name, data: { animated, host: { url, files:[{name}] } } }] }
    // Prefer 1x.gif for animated emotes (our GifDecoder handles it);
    // fall back to 1x.webp for static emotes.

    private fun fetch7TV(): Map<String, String> {
        val body = get("https://7tv.io/v3/emote-sets/global") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val arr  = root.getAsJsonArray("emotes") ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        arr.forEach { el ->
            runCatching {
                val obj      = el.asJsonObject
                val name     = obj.get("name").asString
                val data     = obj.getAsJsonObject("data")
                val hostObj  = data.getAsJsonObject("host")
                val hostUrl  = hostObj.get("url").asString
                val files    = hostObj.getAsJsonArray("files")
                val hasGif   = files?.any { it.asJsonObject.get("name")?.asString == "1x.gif" } == true
                val ext      = if (hasGif) "1x.gif" else "1x.webp"
                map[name] = "https:$hostUrl/$ext"
            }
        }
        return map
    }

    // ── BetterTTV global emotes ───────────────────────────────────────────────
    // GET https://api.betterttv.net/3/cached/emotes/global
    // Response: [{ id, code, imageType }]
    // Image URL: https://cdn.betterttv.net/emote/{id}/1x

    private fun fetchBTTV(): Map<String, String> {
        val body = get("https://api.betterttv.net/3/cached/emotes/global") ?: return emptyMap()
        val arr  = JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        arr.forEach { el ->
            runCatching {
                val obj  = el.asJsonObject
                val id   = obj.get("id").asString
                val code = obj.get("code").asString
                map[code] = "https://cdn.betterttv.net/emote/$id/1x"
            }
        }
        return map
    }

    // ── FrankerFaceZ global emotes ────────────────────────────────────────────
    // GET https://api.frankerfacez.com/v1/set/global
    // Response: { sets: { "3": { emoticons: [{ name, urls: { "1": "//cdn..." } }] } } }
    // Image URL: https:{urls["1"]}

    private fun fetchFFZ(): Map<String, String> {
        val body = get("https://api.frankerfacez.com/v1/set/global") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val sets = root.getAsJsonObject("sets") ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        sets.entrySet().forEach { (_, setEl) ->
            runCatching {
                val emoticons = setEl.asJsonObject.getAsJsonArray("emoticons") ?: return@runCatching
                emoticons.forEach { emEl ->
                    runCatching {
                        val emObj = emEl.asJsonObject
                        val name  = emObj.get("name").asString
                        val url1  = emObj.getAsJsonObject("urls").get("1")?.asString
                            ?: return@runCatching
                        map[name] = "https:$url1"
                    }
                }
            }
        }
        return map
    }

    // ── Twitch global badges ──────────────────────────────────────────────────
    // badges.twitch.tv no longer resolves (deprecated). Badge images are served
    // from static-cdn.jtvnw.net/badges/v1/{uuid}/1 with stable, well-known UUIDs.
    // Flat map key: "set/version"  e.g. "subscriber/0", "premium/1"

    private fun fetchTwitchBadges(): Map<String, String> {
        fun url(uuid: String) = "https://static-cdn.jtvnw.net/badges/v1/$uuid/1"
        return mapOf(
            "admin/1"       to url("9ef7e029-4cdf-4d4d-a0d5-e2b3fb2583fe"),
            "broadcaster/1" to url("5527c58c-fb7d-422d-b71b-f309dcb85cc1"),
            "global_mod/1"  to url("9384c9a7-f2f2-4f74-b6e5-7f78898a43fc"),
            "moderator/1"   to url("3267646d-33f0-4b17-b3df-f923a41db1d7"),
            "partner/1"     to url("d12a2e27-16f6-41d0-ab77-b780518f00a3"),
            "premium/1"     to url("bbbe0db0-a598-423e-86d0-f9fb98ca1933"),
            "staff/1"       to url("d97c37be-57b3-4f3d-b8db-6dcb904f84c8"),
            "subscriber/0"  to url("5d9f2208-5dd8-11e7-8513-2ff4adfae661"),
            "turbo/1"       to url("bd444ec6-8f34-4bf9-91f4-af1e3428d80f"),
            "vip/1"         to url("b817aba4-fad8-49e2-b88a-7cc744dfa6ec"),
        )
    }
}

// ── Top-level parsing function (pure, callable from composables) ──────────────

fun parseMessageSegments(
    message:          String,
    emotesTag:        String?,
    thirdPartyEmotes: Map<String, String>
): List<MessageSegment> {

    // Step 1 — resolve Twitch native emote positions from the IRC tag
    // Tag format: "emoteId:start-end,start-end/emoteId:start-end"
    val twitchRanges = mutableListOf<Triple<Int, Int, String>>()  // start, end (inclusive), url
    emotesTag?.split("/")?.forEach { entry ->
        if (entry.isBlank()) return@forEach
        val colonIdx = entry.indexOf(':')
        if (colonIdx < 0) return@forEach
        val emoteId = entry.substring(0, colonIdx)
        val url = "https://static-cdn.jtvnw.net/emoticons/v2/$emoteId/default/dark/1.0"
        entry.substring(colonIdx + 1).split(",").forEach { range ->
            val dashIdx = range.indexOf('-')
            if (dashIdx < 0) return@forEach
            val start = range.substring(0, dashIdx).toIntOrNull() ?: return@forEach
            val end   = range.substring(dashIdx + 1).toIntOrNull() ?: return@forEach
            if (start <= end && end < message.length) twitchRanges.add(Triple(start, end, url))
        }
    }
    twitchRanges.sortBy { it.first }

    // Step 2 — split message into text gaps and Twitch emotes
    val stage1 = mutableListOf<MessageSegment>()
    var cursor = 0
    for ((start, end, url) in twitchRanges) {
        if (start > cursor) {
            val text = message.substring(cursor, start).trim()
            if (text.isNotEmpty()) stage1.add(MessageSegment.TextPart(text))
        }
        val name = message.substring(start, end + 1)
        stage1.add(MessageSegment.EmotePart(name, url))
        cursor = end + 1
        if (cursor < message.length && message[cursor] == ' ') cursor++
    }
    if (cursor < message.length) {
        val tail = message.substring(cursor).trim()
        if (tail.isNotEmpty()) stage1.add(MessageSegment.TextPart(tail))
    }

    // Step 3 — scan TextParts for third-party emote words (BTTV / FFZ / 7TV)
    if (thirdPartyEmotes.isEmpty()) return stage1
    return stage1.flatMap { seg ->
        if (seg is MessageSegment.TextPart) scanThirdPartyWords(seg.text, thirdPartyEmotes)
        else listOf(seg)
    }
}

private fun scanThirdPartyWords(text: String, emotes: Map<String, String>): List<MessageSegment> {
    val result  = mutableListOf<MessageSegment>()
    val tokens  = text.split(" ")
    val pending = StringBuilder()

    tokens.forEachIndexed { idx, word ->
        val url = emotes[word]
        if (url != null) {
            val accumulated = pending.toString().trimEnd()
            if (accumulated.isNotEmpty()) result.add(MessageSegment.TextPart(accumulated))
            pending.clear()
            result.add(MessageSegment.EmotePart(word, url))
        } else {
            pending.append(word)
            if (idx < tokens.lastIndex) pending.append(' ')
        }
    }
    val remaining = pending.toString().trimEnd()
    if (remaining.isNotEmpty()) result.add(MessageSegment.TextPart(remaining))
    return result
}
