package com.Slachy.StudioBridge

// ── Connection ────────────────────────────────────────────────────────────────
internal const val DEFAULT_OBS_PORT = 4455

// ── OBS WebSocket protocol ────────────────────────────────────────────────────
// General(1) + Config(2) + Scenes(4) + Inputs(8) + Outputs(64) + SceneItems(128) + Ui(1024) + InputVolumeMeters(65536)
internal const val OBS_EVENT_SUBSCRIPTIONS = 66767
internal const val SCREENSHOT_WIDTH        = 320
internal const val SCREENSHOT_HEIGHT       = 180
internal const val SCREENSHOT_QUALITY      = 60

// ── Timing ────────────────────────────────────────────────────────────────────
internal const val RECONNECT_DELAY_MS  = 5_000L
internal const val SCREENSHOT_POLL_MS  = 1_500L

// ── Chat ──────────────────────────────────────────────────────────────────────
internal const val MAX_CHAT_MESSAGES = 300

// ── Chat display defaults ─────────────────────────────────────────────────────
internal const val DEFAULT_FONT_SIZE     = 13f
internal const val DEFAULT_USERNAME_SIZE = 13f
internal const val DEFAULT_LINE_SPACING  = 4f
internal const val DEFAULT_EMOTE_SIZE    = 22f
