package com.Slachy.StudioBridge

data class OBSScene(
    val name: String
)

data class OBSInput(
    val name: String,
    val kind: String,
    val muted: Boolean = false,
    val volumeDb: Float = 0f
) {
    // OBS audio source kinds across all platforms
    val isAudio: Boolean get() = kind in AUDIO_KINDS || AUDIO_KEYWORDS.any { kind.contains(it) }

    companion object {
        private val AUDIO_KINDS = setOf(
            "wasapi_input_capture",
            "wasapi_output_capture",
            "pulse_input_capture",
            "pulse_output_capture",
            "alsa_input_capture",
            "coreaudio_input_capture",
            "coreaudio_output_capture",
            "dshow_input",
        )
        private val AUDIO_KEYWORDS = listOf("audio", "mic", "sound", "capture")
    }
}

data class OBSSceneItem(
    val sceneItemId: Int,
    val sourceName: String,
    val inputKind: String,
    val sceneItemEnabled: Boolean
)

data class OBSFilter(
    val filterName: String,
    val filterKind: String,
    val filterEnabled: Boolean,
    val filterSettings: Map<String, Any> = emptyMap()
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
