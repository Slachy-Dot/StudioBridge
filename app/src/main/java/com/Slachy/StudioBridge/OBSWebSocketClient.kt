package com.Slachy.StudioBridge

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OBS WebSocket 5.x client (built into OBS 28+).
 *
 * Protocol overview:
 *  op 0 → Hello       (server sends on connect, may include auth challenge)
 *  op 1 → Identify    (client authenticates)
 *  op 2 → Identified  (server confirms, we're ready)
 *  op 5 → Event       (server pushes state changes)
 *  op 6 → Request     (client sends a command)
 *  op 7 → RequestResponse (server replies to a command)
 */
class OBSWebSocketClient {

    private val http = OkHttpClient()
    private val gson = Gson()

    private var socket: WebSocket? = null
    private var password: String = ""

    private val pending = ConcurrentHashMap<String, (JsonObject) -> Unit>()

    // ── Public state flows ────────────────────────────────────────────────────

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _scenes = MutableStateFlow<List<OBSScene>>(emptyList())
    val scenes: StateFlow<List<OBSScene>> = _scenes

    private val _currentScene = MutableStateFlow("")
    val currentScene: StateFlow<String> = _currentScene

    private val _inputs = MutableStateFlow<List<OBSInput>>(emptyList())
    val inputs: StateFlow<List<OBSInput>> = _inputs

    private val _streamActive = MutableStateFlow(false)
    val streamActive: StateFlow<Boolean> = _streamActive

    private val _recordActive = MutableStateFlow(false)
    val recordActive: StateFlow<Boolean> = _recordActive

    private val _studioModeEnabled = MutableStateFlow(false)
    val studioModeEnabled: StateFlow<Boolean> = _studioModeEnabled

    private val _previewScene = MutableStateFlow("")
    val previewScene: StateFlow<String> = _previewScene

    // Base64-encoded JPEG screenshots — null means not yet fetched
    private val _programScreenshot = MutableStateFlow<String?>(null)
    val programScreenshot: StateFlow<String?> = _programScreenshot

    private val _previewScreenshot = MutableStateFlow<String?>(null)
    val previewScreenshot: StateFlow<String?> = _previewScreenshot

    private val _sceneItems = MutableStateFlow<List<OBSSceneItem>>(emptyList())
    val sceneItems: StateFlow<List<OBSSceneItem>> = _sceneItems

    private val _filters = MutableStateFlow<List<OBSFilter>>(emptyList())
    val filters: StateFlow<List<OBSFilter>> = _filters

    private var filterSourceName = ""

    private val _inputSettings = MutableStateFlow<Map<String, Any>?>(null)
    val inputSettings: StateFlow<Map<String, Any>?> = _inputSettings

    private val _groupItems = MutableStateFlow<Map<String, List<OBSSceneItem>>>(emptyMap())
    val groupItems: StateFlow<Map<String, List<OBSSceneItem>>> = _groupItems

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(host: String, port: Int, password: String) {
        this.password = password
        _state.value = ConnectionState.Connecting

        val req = Request.Builder().url("ws://$host:$port").build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.Error(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.Disconnected
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "Disconnected by user")
        socket = null
        _state.value = ConnectionState.Disconnected
        _scenes.value = emptyList()
        _inputs.value = emptyList()
        _currentScene.value = ""
        _streamActive.value = false
        _recordActive.value = false
        _studioModeEnabled.value = false
        _previewScene.value = ""
        _programScreenshot.value = null
        _previewScreenshot.value = null
        _filters.value = emptyList()
        filterSourceName = ""
        _inputSettings.value = null
        _groupItems.value = emptyMap()
    }

    // ── Message handling ──────────────────────────────────────────────────────

    private fun handleMessage(text: String) {
        val msg = JsonParser.parseString(text).asJsonObject
        val op = msg.get("op")?.takeIf { !it.isJsonNull }?.asInt ?: return
        val d  = msg.get("d")?.takeIf { !it.isJsonNull }?.asJsonObject ?: return

        when (op) {
            0 -> onHello(d)
            2 -> onIdentified()
            5 -> onEvent(d)
            7 -> onResponse(d)
        }
    }

    private fun onHello(d: JsonObject) {
        val authSection = if (d.has("authentication")) d.getAsJsonObject("authentication") else null

        val authToken = if (authSection != null && password.isNotEmpty()) {
            computeAuth(
                password = password,
                challenge = authSection.get("challenge").asString,
                salt = authSection.get("salt").asString
            )
        } else null

        send(JsonObject().apply {
            addProperty("op", 1)
            add("d", JsonObject().apply {
                addProperty("rpcVersion", 1)
                // General(1) + Scenes(4) + Inputs(8) + Outputs(64) + SceneItems(128) + Filters(2) + Ui(1024) = 1231
                addProperty("eventSubscriptions", 1231)
                if (authToken != null) addProperty("authentication", authToken)
            })
        })
    }

    private fun onIdentified() {
        _state.value = ConnectionState.Connected
        fetchSceneList()
        fetchInputList()
        fetchStreamStatus()
        fetchRecordStatus()
        fetchStudioMode()
    }

    private fun onEvent(d: JsonObject) {
        val type = d.get("eventType").asString
        val data = if (d.has("eventData")) d.getAsJsonObject("eventData") else JsonObject()

        when (type) {
            "CurrentProgramSceneChanged" ->
                _currentScene.value = data.get("sceneName")?.asString ?: ""

            "CurrentPreviewSceneChanged" ->
                _previewScene.value = data.get("sceneName")?.asString ?: ""

            "SceneListChanged" -> fetchSceneList()

            "StreamStateChanged" ->
                _streamActive.value = data.get("outputActive")?.asBoolean ?: false

            "RecordStateChanged" ->
                _recordActive.value = data.get("outputActive")?.asBoolean ?: false

            "StudioModeStateChanged" -> {
                val enabled = data.get("studioModeEnabled")?.asBoolean ?: false
                _studioModeEnabled.value = enabled
                if (enabled) fetchPreviewScene()
            }

            "SceneItemEnableStateChanged" -> {
                val eventScene = data.get("sceneName")?.asString ?: return
                val itemId = data.get("sceneItemId")?.asInt ?: return
                val enabled = data.get("sceneItemEnabled")?.asBoolean ?: return
                _sceneItems.value = _sceneItems.value.map {
                    if (it.sceneItemId == itemId) it.copy(sceneItemEnabled = enabled) else it
                }
                val grp = _groupItems.value
                if (grp.containsKey(eventScene)) {
                    _groupItems.value = grp + (eventScene to grp[eventScene]!!.map {
                        if (it.sceneItemId == itemId) it.copy(sceneItemEnabled = enabled) else it
                    })
                }
            }

            "SceneItemCreated", "SceneItemRemoved" -> {
                val sn = data.get("sceneName")?.asString ?: return
                if (sn == _currentScene.value) fetchSceneItems(sn)
                if (_groupItems.value.containsKey(sn)) fetchGroupItems(sn)
            }

            "InputMuteStateChanged" -> {
                val name = data.get("inputName")?.asString ?: return
                val muted = data.get("inputMuted")?.asBoolean ?: return
                updateInput(name) { it.copy(muted = muted) }
            }

            "InputVolumeChanged" -> {
                val name = data.get("inputName")?.asString ?: return
                val db = data.get("inputVolumeDb")?.asFloat ?: return
                updateInput(name) { it.copy(volumeDb = db) }
            }

            "SourceFilterCreated", "SourceFilterRemoved" -> {
                val sn = data.get("sourceName")?.asString ?: return
                if (sn == filterSourceName) fetchFilters(sn)
            }

            "SourceFilterEnableStateChanged" -> {
                val sn = data.get("sourceName")?.asString ?: return
                val fn = data.get("filterName")?.asString ?: return
                val enabled = data.get("filterEnabled")?.asBoolean ?: return
                if (sn == filterSourceName) {
                    _filters.value = _filters.value.map {
                        if (it.filterName == fn) it.copy(filterEnabled = enabled) else it
                    }
                }
            }
        }
    }

    private fun onResponse(d: JsonObject) {
        val requestId = d.get("requestId").asString
        val success = d.getAsJsonObject("requestStatus").get("result").asBoolean
        val callback = pending.remove(requestId) ?: return
        if (!success) return
        val responseData = if (d.has("responseData")) d.getAsJsonObject("responseData") else JsonObject()
        callback(responseData)
    }

    // ── Scene controls ────────────────────────────────────────────────────────

    fun fetchSceneList() {
        request("GetSceneList") { data ->
            val arr = data.getAsJsonArray("scenes")
            _scenes.value = arr.map { OBSScene(it.asJsonObject.get("sceneName").asString) }.reversed()
            _currentScene.value = data.get("currentProgramSceneName")?.asString ?: ""
        }
    }

    fun createScene(sceneName: String) {
        request("CreateScene", JsonObject().apply { addProperty("sceneName", sceneName) })
    }

    fun setCurrentScene(name: String) {
        _currentScene.value = name
        request("SetCurrentProgramScene", JsonObject().apply { addProperty("sceneName", name) })
    }

    // ── Studio mode ───────────────────────────────────────────────────────────

    fun fetchStudioMode() {
        request("GetStudioModeEnabled") { data ->
            val enabled = data.get("studioModeEnabled")?.asBoolean ?: false
            _studioModeEnabled.value = enabled
            if (enabled) fetchPreviewScene()
        }
    }

    fun setStudioModeEnabled(enabled: Boolean) {
        request("SetStudioModeEnabled", JsonObject().apply {
            addProperty("studioModeEnabled", enabled)
        })
    }

    fun fetchPreviewScene() {
        request("GetCurrentPreviewScene") { data ->
            _previewScene.value = data.get("currentPreviewSceneName")?.asString ?: ""
        }
    }

    fun setPreviewScene(name: String) {
        _previewScene.value = name
        request("SetCurrentPreviewScene", JsonObject().apply { addProperty("sceneName", name) })
    }

    fun triggerTransition() = request("TriggerStudioModeTransition")

    // ── Stream controls ───────────────────────────────────────────────────────

    fun fetchStreamStatus() {
        request("GetStreamStatus") { data ->
            _streamActive.value = data.get("outputActive")?.asBoolean ?: false
        }
    }

    fun startStream() = request("StartStream")
    fun stopStream() = request("StopStream")

    fun fetchRecordStatus() {
        request("GetRecordStatus") { data ->
            _recordActive.value = data.get("outputActive")?.asBoolean ?: false
        }
    }

    fun startRecord() = request("StartRecord")
    fun stopRecord() = request("StopRecord")

    // ── Screenshots ───────────────────────────────────────────────────────────

    fun fetchScreenshot(sourceName: String, isProgram: Boolean) {
        request("GetSourceScreenshot", JsonObject().apply {
            addProperty("sourceName", sourceName)
            addProperty("imageFormat", "jpeg")
            addProperty("imageWidth", 320)
            addProperty("imageHeight", 180)
            addProperty("imageCompressionQuality", 60)
        }) { data ->
            val b64 = data.get("imageData")?.asString
            if (isProgram) _programScreenshot.value = b64
            else _previewScreenshot.value = b64
        }
    }

    // ── Scene items ───────────────────────────────────────────────────────────

    fun fetchSceneItems(sceneName: String) {
        _sceneItems.value = emptyList()
        request("GetSceneItemList", JsonObject().apply { addProperty("sceneName", sceneName) }) { data ->
            val arr = data.getAsJsonArray("sceneItems") ?: return@request
            _sceneItems.value = arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val sourceNameEl = obj.get("sourceName")
                    val sourceName = sourceNameEl
                        ?.takeIf { !it.isJsonNull }?.asString
                        ?: return@mapNotNull null
                    val isGroup = obj.get("isGroup")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                    OBSSceneItem(
                        sceneItemId = obj.get("sceneItemId").asInt,
                        sourceName = sourceName,
                        inputKind = obj.get("inputKind")?.takeIf { !it.isJsonNull }?.asString
                            ?: if (isGroup) "group" else "scene",
                        sceneItemEnabled = obj.get("sceneItemEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                    )
                } catch (_: Exception) { null }
            }.reversed()
        }
    }

    fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean) {
        _sceneItems.value = _sceneItems.value.map {
            if (it.sceneItemId == sceneItemId) it.copy(sceneItemEnabled = enabled) else it
        }
        request("SetSceneItemEnabled", JsonObject().apply {
            addProperty("sceneName", sceneName)
            addProperty("sceneItemId", sceneItemId)
            addProperty("sceneItemEnabled", enabled)
        })
    }

    fun removeSceneItem(sceneName: String, sceneItemId: Int) {
        request("RemoveSceneItem", JsonObject().apply {
            addProperty("sceneName", sceneName)
            addProperty("sceneItemId", sceneItemId)
        })
    }

    // ── Sources ───────────────────────────────────────────────────────────────

    fun createInput(sceneName: String, inputName: String, inputKind: String) {
        request("CreateInput", JsonObject().apply {
            addProperty("sceneName", sceneName)
            addProperty("inputName", inputName)
            addProperty("inputKind", inputKind)
            add("inputSettings", JsonObject())
            addProperty("sceneItemEnabled", true)
        })
    }

    // ── Input / audio controls ────────────────────────────────────────────────

    fun fetchInputList() {
        request("GetInputList") { data ->
            val arr = data.getAsJsonArray("inputs")
            val list = arr.map {
                val obj = it.asJsonObject
                OBSInput(
                    name = obj.get("inputName").asString,
                    kind = obj.get("inputKind").asString
                )
            }
            _inputs.value = list
            list.forEach { refreshInputAudio(it.name) }
        }
    }

    fun refreshInputAudio(inputName: String) {
        request("GetInputVolume", JsonObject().apply { addProperty("inputName", inputName) }) { data ->
            updateInput(inputName) { it.copy(volumeDb = data.get("inputVolumeDb")?.asFloat ?: -100f) }
        }
        request("GetInputMute", JsonObject().apply { addProperty("inputName", inputName) }) { data ->
            updateInput(inputName) { it.copy(muted = data.get("inputMuted")?.asBoolean ?: false) }
        }
    }

    fun toggleMute(inputName: String) {
        request("ToggleInputMute", JsonObject().apply { addProperty("inputName", inputName) })
    }

    fun setVolume(inputName: String, volumeDb: Float) {
        request("SetInputVolume", JsonObject().apply {
            addProperty("inputName", inputName)
            addProperty("inputVolumeDb", volumeDb)
        })
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    fun fetchFilters(sourceName: String) {
        filterSourceName = sourceName
        _filters.value = emptyList()
        request("GetSourceFilterList", JsonObject().apply { addProperty("sourceName", sourceName) }) { data ->
            val arr = data.getAsJsonArray("filters") ?: return@request
            _filters.value = arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    OBSFilter(
                        filterName = obj.get("filterName").asString,
                        filterKind = obj.get("filterKind").asString,
                        filterEnabled = obj.get("filterEnabled")?.asBoolean ?: true,
                        filterSettings = obj.getAsJsonObject("filterSettings")?.toSettingsMap() ?: emptyMap()
                    )
                } catch (_: Exception) { null }
            }
        }
    }

    fun createFilter(sourceName: String, filterName: String, filterKind: String) {
        request("CreateSourceFilter", JsonObject().apply {
            addProperty("sourceName", sourceName)
            addProperty("filterName", filterName)
            addProperty("filterKind", filterKind)
            add("filterSettings", JsonObject())
        }) { fetchFilters(sourceName) }
    }

    fun removeFilter(sourceName: String, filterName: String) {
        request("RemoveSourceFilter", JsonObject().apply {
            addProperty("sourceName", sourceName)
            addProperty("filterName", filterName)
        }) { _filters.value = _filters.value.filter { it.filterName != filterName } }
    }

    fun setFilterEnabled(sourceName: String, filterName: String, enabled: Boolean) {
        _filters.value = _filters.value.map {
            if (it.filterName == filterName) it.copy(filterEnabled = enabled) else it
        }
        request("SetSourceFilterEnabled", JsonObject().apply {
            addProperty("sourceName", sourceName)
            addProperty("filterName", filterName)
            addProperty("filterEnabled", enabled)
        })
    }

    fun setFilterSettings(sourceName: String, filterName: String, partialSettings: Map<String, Any>) {
        _filters.value = _filters.value.map {
            if (it.filterName == filterName) it.copy(filterSettings = it.filterSettings + partialSettings) else it
        }
        request("SetSourceFilterSettings", JsonObject().apply {
            addProperty("sourceName", sourceName)
            addProperty("filterName", filterName)
            add("filterSettings", partialSettings.toJsonObject())
            addProperty("overlay", true)
        })
    }

    // ── Group items ───────────────────────────────────────────────────────────

    fun fetchGroupItems(groupName: String) {
        request("GetGroupSceneItemList", JsonObject().apply { addProperty("sceneName", groupName) }) { data ->
            val arr = data.getAsJsonArray("sceneItems") ?: return@request
            val items = arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val sourceName = obj.get("sourceName")?.takeIf { !it.isJsonNull }?.asString
                        ?: return@mapNotNull null
                    OBSSceneItem(
                        sceneItemId = obj.get("sceneItemId").asInt,
                        sourceName = sourceName,
                        inputKind = obj.get("inputKind")?.takeIf { !it.isJsonNull }?.asString ?: "unknown",
                        sceneItemEnabled = obj.get("sceneItemEnabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                    )
                } catch (_: Exception) { null }
            }.reversed()
            _groupItems.value = _groupItems.value + (groupName to items)
        }
    }

    fun setGroupItemEnabled(groupName: String, sceneItemId: Int, enabled: Boolean) {
        _groupItems.value = _groupItems.value.run {
            val list = this[groupName] ?: return@run this
            this + (groupName to list.map {
                if (it.sceneItemId == sceneItemId) it.copy(sceneItemEnabled = enabled) else it
            })
        }
        request("SetSceneItemEnabled", JsonObject().apply {
            addProperty("sceneName", groupName)
            addProperty("sceneItemId", sceneItemId)
            addProperty("sceneItemEnabled", enabled)
        })
    }

    fun removeGroupItem(groupName: String, sceneItemId: Int) {
        request("RemoveSceneItem", JsonObject().apply {
            addProperty("sceneName", groupName)
            addProperty("sceneItemId", sceneItemId)
        }) {
            _groupItems.value = _groupItems.value.run {
                val list = this[groupName] ?: return@run this
                this + (groupName to list.filter { it.sceneItemId != sceneItemId })
            }
        }
    }

    // ── Input settings ────────────────────────────────────────────────────────

    fun fetchInputSettings(inputName: String) {
        _inputSettings.value = null
        request("GetInputSettings", JsonObject().apply { addProperty("inputName", inputName) }) { data ->
            _inputSettings.value = data.getAsJsonObject("inputSettings")?.toSettingsMap() ?: emptyMap()
        }
    }

    fun setInputSettings(inputName: String, partialSettings: Map<String, Any>) {
        _inputSettings.value = (_inputSettings.value ?: emptyMap()) + partialSettings
        request("SetInputSettings", JsonObject().apply {
            addProperty("inputName", inputName)
            add("inputSettings", partialSettings.toJsonObject())
            addProperty("overlay", true)
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun request(type: String, data: JsonObject? = null, callback: ((JsonObject) -> Unit)? = null) {
        val id = UUID.randomUUID().toString()
        if (callback != null) pending[id] = callback

        send(JsonObject().apply {
            addProperty("op", 6)
            add("d", JsonObject().apply {
                addProperty("requestType", type)
                addProperty("requestId", id)
                if (data != null) add("requestData", data)
            })
        })
    }

    private fun send(obj: JsonObject) { socket?.send(gson.toJson(obj)) }

    private fun updateInput(name: String, transform: (OBSInput) -> OBSInput) {
        _inputs.value = _inputs.value.map { if (it.name == name) transform(it) else it }
    }

    private fun computeAuth(password: String, challenge: String, salt: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val secret = Base64.encodeToString(
            sha256.digest((password + salt).toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
        )
        sha256.reset()
        return Base64.encodeToString(
            sha256.digest((secret + challenge).toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
        )
    }

    private fun JsonObject.toSettingsMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for ((key, value) in entrySet()) {
            if (value.isJsonPrimitive) {
                val prim = value.asJsonPrimitive
                when {
                    prim.isBoolean -> map[key] = prim.asBoolean
                    prim.isNumber  -> map[key] = prim.asDouble
                    else           -> map[key] = prim.asString
                }
            }
        }
        return map
    }

    private fun Map<String, Any>.toJsonObject(): JsonObject {
        val obj = JsonObject()
        for ((key, value) in this) {
            when (value) {
                is Boolean -> obj.addProperty(key, value)
                is Double  -> obj.addProperty(key, value)
                is Float   -> obj.addProperty(key, value)
                is Int     -> obj.addProperty(key, value)
                is Long    -> obj.addProperty(key, value)
                is String  -> obj.addProperty(key, value)
            }
        }
        return obj
    }
}
