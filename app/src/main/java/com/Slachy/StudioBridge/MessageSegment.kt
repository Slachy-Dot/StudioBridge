package com.Slachy.StudioBridge

sealed class MessageSegment {
    data class TextPart(val text: String) : MessageSegment()
    data class EmotePart(val name: String, val url: String) : MessageSegment()
}
