package com.Slachy.StudioBridge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OBSViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProfileStore(app).also { it.migrateFromLegacy(app) }
    private val client = OBSWebSocketClient()

    // ── OBS connection state ──────────────────────────────────────────────────

    val state = client.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)

    val scenes = client.scenes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentScene = client.currentScene
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val inputs = client.inputs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val streamActive = client.streamActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val recordActive = client.recordActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val studioModeEnabled = client.studioModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val previewScene = client.previewScene
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val programScreenshot = client.programScreenshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val previewScreenshot = client.previewScreenshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sceneItems = client.sceneItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filters = client.filters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val inputSettings = client.inputSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groupItems = client.groupItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val sceneCollections = client.sceneCollections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSceneCollection = client.currentSceneCollection
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val volumeMeters = client.volumeMeters
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Recording timer (seconds elapsed since record start)
    private val _recordingTimeSec = MutableStateFlow(0L)
    val recordingTimeSec: StateFlow<Long> = _recordingTimeSec
    private var recordTimerJob: Job? = null

    private var sceneItemsScene: String = ""

    fun loadSceneItems(sceneName: String) {
        sceneItemsScene = sceneName
        client.fetchSceneItems(sceneName)
    }

    fun toggleSceneItemVisibility(sceneItemId: Int, currentEnabled: Boolean) {
        if (sceneItemsScene.isNotEmpty())
            client.setSceneItemEnabled(sceneItemsScene, sceneItemId, !currentEnabled)
    }

    fun deleteSceneItem(sceneItemId: Int) {
        if (sceneItemsScene.isNotEmpty())
            client.removeSceneItem(sceneItemsScene, sceneItemId)
    }

    fun reorderSceneItem(sceneItemId: Int, newUiIndex: Int) {
        if (sceneItemsScene.isEmpty()) return
        val size = client.sceneItems.value.size
        val obsIndex = (size - 1 - newUiIndex).coerceIn(0, size - 1)
        client.setSceneItemIndex(sceneItemsScene, sceneItemId, obsIndex)
    }

    // ── Profile management ────────────────────────────────────────────────────

    private val _profiles = MutableStateFlow(store.getProfiles())
    val profiles: StateFlow<List<OBSProfile>> = _profiles

    val lastUsedProfileId: String? get() = store.getLastUsedId()

    private val _autoConnect = MutableStateFlow(store.getAutoConnect())
    val autoConnect: StateFlow<Boolean> = _autoConnect

    fun setAutoConnect(enabled: Boolean) {
        store.setAutoConnect(enabled)
        _autoConnect.value = enabled
    }

    fun saveProfile(profile: OBSProfile) {
        store.saveProfile(profile)
        _profiles.value = store.getProfiles()
    }

    fun deleteProfile(id: String) {
        store.deleteProfile(id)
        _profiles.value = store.getProfiles()
    }

    // ── Screenshot polling ────────────────────────────────────────────────────

    private var screenshotJob: Job? = null
    private var screenshotsPaused = false

    fun setScreenshotsPaused(paused: Boolean) {
        screenshotsPaused = paused
    }

    // ── Auto-reconnect ────────────────────────────────────────────────────────

    private var lastProfile: OBSProfile? = null
    private var manualDisconnect = false
    private var reconnectJob: Job? = null

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            _reconnecting.value = true
            delay(RECONNECT_DELAY_MS)
            _reconnecting.value = false
            val profile = lastProfile ?: return@launch
            if (!manualDisconnect) {
                client.connect(profile.host, profile.port, profile.password)
            }
        }
    }

    init {
        // Auto-connect on startup if enabled and a last-used profile exists
        if (store.getAutoConnect()) {
            val lastId = store.getLastUsedId()
            val profile = store.getProfiles().find { it.id == lastId }
            if (profile != null) connect(profile)
        }

        viewModelScope.launch {
            state.collect { s ->
                when (s) {
                    is ConnectionState.Connected -> {
                        reconnectJob?.cancel()
                        _reconnecting.value = false
                        startScreenshotPolling()
                    }
                    is ConnectionState.Disconnected, is ConnectionState.Error -> {
                        stopScreenshotPolling()
                        stopRecordTimer()
                        if (!manualDisconnect && lastProfile != null) {
                            scheduleReconnect()
                        }
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            recordActive.collect { active ->
                if (active) startRecordTimer() else stopRecordTimer()
            }
        }
    }

    private fun startRecordTimer() {
        recordTimerJob?.cancel()
        _recordingTimeSec.value = 0L
        recordTimerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _recordingTimeSec.value++
            }
        }
    }

    private fun stopRecordTimer() {
        recordTimerJob?.cancel()
        recordTimerJob = null
        _recordingTimeSec.value = 0L
    }

    private fun startScreenshotPolling() {
        if (screenshotJob?.isActive == true) return
        screenshotJob = viewModelScope.launch {
            while (true) {
                delay(SCREENSHOT_POLL_MS)
                if (!screenshotsPaused) {
                    val program = client.currentScene.value
                    if (program.isNotEmpty()) client.fetchScreenshot(program, isProgram = true)
                    if (client.studioModeEnabled.value) {
                        val preview = client.previewScene.value
                        if (preview.isNotEmpty() && preview != program)
                            client.fetchScreenshot(preview, isProgram = false)
                    }
                }
            }
        }
    }

    private fun stopScreenshotPolling() {
        screenshotJob?.cancel()
        screenshotJob = null
    }

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(profile: OBSProfile) {
        manualDisconnect = false
        lastProfile = profile
        store.setLastUsedId(profile.id)
        client.connect(profile.host, profile.port, profile.password)
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        _reconnecting.value = false
        client.disconnect()
    }

    // ── Scene / studio mode controls ──────────────────────────────────────────

    fun createScene(name: String) = client.createScene(name)

    fun onSceneClick(name: String) {
        if (client.studioModeEnabled.value) client.setPreviewScene(name)
        else client.setCurrentScene(name)
    }

    fun triggerTransition() = client.triggerTransition()
    fun cutToScene() {
        // Cut = instant switch from preview to program
        val preview = client.previewScene.value
        if (preview.isNotEmpty()) client.setCurrentScene(preview)
    }

    fun toggleStudioMode() = client.setStudioModeEnabled(!client.studioModeEnabled.value)

    /** Move a scene to a new position in the list.
     *  [uiIndex] is the 0-based index in our (reversed) scenes list;
     *  OBS stores scenes bottom-up, so we convert before sending. */
    fun reorderScene(sceneName: String, uiIndex: Int) {
        val obsIndex = (client.scenes.value.size - 1 - uiIndex).coerceAtLeast(0)
        client.setSceneIndex(sceneName, obsIndex)
    }

    fun setSceneCollection(name: String) = client.setCurrentSceneCollection(name)

    // ── Stream controls ───────────────────────────────────────────────────────

    fun toggleStream() {
        if (client.streamActive.value) client.stopStream() else client.startStream()
    }

    fun toggleRecord() {
        if (client.recordActive.value) client.stopRecord() else client.startRecord()
    }

    // ── Filter management ─────────────────────────────────────────────────────

    private var filterSourceName: String = ""

    fun loadFilters(sourceName: String) {
        filterSourceName = sourceName
        client.fetchFilters(sourceName)
    }

    fun addFilter(filterName: String, filterKind: String) {
        if (filterSourceName.isNotEmpty())
            client.createFilter(filterSourceName, filterName, filterKind)
    }

    fun removeFilter(filterName: String) {
        if (filterSourceName.isNotEmpty())
            client.removeFilter(filterSourceName, filterName)
    }

    fun toggleFilter(filterName: String, currentEnabled: Boolean) {
        if (filterSourceName.isNotEmpty())
            client.setFilterEnabled(filterSourceName, filterName, !currentEnabled)
    }

    fun setFilterSettings(filterName: String, partialSettings: Map<String, Any>) {
        if (filterSourceName.isNotEmpty())
            client.setFilterSettings(filterSourceName, filterName, partialSettings)
    }

    // ── Group item management ─────────────────────────────────────────────────

    fun loadGroupItems(groupName: String) = client.fetchGroupItems(groupName)

    fun toggleGroupItemVisibility(groupName: String, sceneItemId: Int, currentEnabled: Boolean) {
        client.setGroupItemEnabled(groupName, sceneItemId, !currentEnabled)
    }

    fun deleteGroupItem(groupName: String, sceneItemId: Int) {
        client.removeGroupItem(groupName, sceneItemId)
    }

    // ── Input settings ────────────────────────────────────────────────────────

    fun loadInputSettings(sourceName: String) = client.fetchInputSettings(sourceName)

    fun setInputSettings(sourceName: String, partialSettings: Map<String, Any>) =
        client.setInputSettings(sourceName, partialSettings)

    // ── Twitch chat ───────────────────────────────────────────────────────────

    private val _twitchChannel = MutableStateFlow(store.getTwitchChannel())
    val twitchChannel: StateFlow<String> = _twitchChannel

    private val twitchChatClient = TwitchChatClient()
    val emoteRepository = EmoteRepository()

    val chatMessages = twitchChatClient.messages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chatConnected = twitchChatClient.connected
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val thirdPartyEmotes = emoteRepository.thirdPartyEmotes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val twitchBadges = emoteRepository.twitchBadges
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val emoteLoadReport = emoteRepository.loadReport
        .stateIn(viewModelScope, SharingStarted.Eagerly, "loading…")

    private val _enable7tv  = MutableStateFlow(store.getEnable7tv())
    val enable7tv: StateFlow<Boolean> = _enable7tv

    private val _enableBttv = MutableStateFlow(store.getEnableBttv())
    val enableBttv: StateFlow<Boolean> = _enableBttv

    private val _enableFfz  = MutableStateFlow(store.getEnableFfz())
    val enableFfz: StateFlow<Boolean> = _enableFfz

    fun setEnable7tv(enabled: Boolean) {
        store.setEnable7tv(enabled); _enable7tv.value = enabled; reloadEmotes()
    }
    fun setEnableBttv(enabled: Boolean) {
        store.setEnableBttv(enabled); _enableBttv.value = enabled; reloadEmotes()
    }
    fun setEnableFfz(enabled: Boolean) {
        store.setEnableFfz(enabled); _enableFfz.value = enabled; reloadEmotes()
    }

    private fun reloadEmotes() {
        viewModelScope.launch {
            emoteRepository.loadAll(_enable7tv.value, _enableBttv.value, _enableFfz.value)
            val roomId = twitchChatClient.roomId.value
            if (roomId.isNotEmpty()) {
                emoteRepository.loadChannelEmotes(
                    roomId, _twitchChannel.value,
                    _enable7tv.value, _enableBttv.value, _enableFfz.value
                )
            }
        }
    }

    init {
        // Load global emotes at startup
        viewModelScope.launch {
            emoteRepository.loadAll(_enable7tv.value, _enableBttv.value, _enableFfz.value)
        }
        // Load channel emotes whenever the IRC room-id is received
        viewModelScope.launch {
            twitchChatClient.roomId.collect { roomId ->
                if (roomId.isNotEmpty()) {
                    emoteRepository.loadChannelEmotes(
                        roomId, _twitchChannel.value,
                        _enable7tv.value, _enableBttv.value, _enableFfz.value
                    )
                }
            }
        }
    }

    fun saveTwitchChannel(channel: String) {
        store.setTwitchChannel(channel)
        _twitchChannel.value = channel
        twitchChatClient.connect(channel)
    }

    fun connectTwitchChat() {
        val channel = _twitchChannel.value
        if (channel.isNotEmpty()) twitchChatClient.connect(channel)
    }

    // ── Chat display settings ─────────────────────────────────────────────────

    private val _chatFontSize = MutableStateFlow(store.getChatFontSize())
    val chatFontSize: StateFlow<Float> = _chatFontSize

    private val _chatLineSpacing = MutableStateFlow(store.getChatLineSpacing())
    val chatLineSpacing: StateFlow<Float> = _chatLineSpacing

    private val _chatEmoteSize = MutableStateFlow(store.getChatEmoteSize())
    val chatEmoteSize: StateFlow<Float> = _chatEmoteSize

    private val _chatUsernameSize = MutableStateFlow(store.getChatUsernameSize())
    val chatUsernameSize: StateFlow<Float> = _chatUsernameSize

    private val _animatedEmotes = MutableStateFlow(store.getAnimatedEmotes())
    val animatedEmotes: StateFlow<Boolean> = _animatedEmotes

    private val _showDebugBar = MutableStateFlow(store.getShowDebugBar())
    val showDebugBar: StateFlow<Boolean> = _showDebugBar

    fun setChatFontSize(sp: Float) { store.setChatFontSize(sp); _chatFontSize.value = sp }
    fun setChatLineSpacing(dp: Float) { store.setChatLineSpacing(dp); _chatLineSpacing.value = dp }
    fun setChatEmoteSize(sp: Float) { store.setChatEmoteSize(sp); _chatEmoteSize.value = sp }
    fun setChatUsernameSize(sp: Float) { store.setChatUsernameSize(sp); _chatUsernameSize.value = sp }
    fun setAnimatedEmotes(enabled: Boolean) { store.setAnimatedEmotes(enabled); _animatedEmotes.value = enabled }
    fun setShowDebugBar(enabled: Boolean) { store.setShowDebugBar(enabled); _showDebugBar.value = enabled }

    private val _showMiniMixer = MutableStateFlow(store.getShowMiniMixer())
    val showMiniMixer: StateFlow<Boolean> = _showMiniMixer
    fun setShowMiniMixer(enabled: Boolean) { store.setShowMiniMixer(enabled); _showMiniMixer.value = enabled }

    private val _showCollectionChip = MutableStateFlow(store.getShowCollectionChip())
    val showCollectionChip: StateFlow<Boolean> = _showCollectionChip
    fun setShowCollectionChip(enabled: Boolean) { store.setShowCollectionChip(enabled); _showCollectionChip.value = enabled }

    // ── Source management ─────────────────────────────────────────────────────

    fun addSource(inputName: String, inputKind: String) {
        val scene = client.currentScene.value
        if (scene.isNotEmpty()) client.createInput(scene, inputName, inputKind)
    }

    // ── Audio controls ────────────────────────────────────────────────────────

    fun toggleMute(inputName: String) = client.toggleMute(inputName)
    fun setVolume(inputName: String, db: Float) = client.setVolume(inputName, db)

    override fun onCleared() {
        super.onCleared()
        client.disconnect()
        twitchChatClient.disconnect()
    }
}
