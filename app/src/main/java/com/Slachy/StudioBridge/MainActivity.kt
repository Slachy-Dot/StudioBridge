package com.Slachy.StudioBridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Slachy.StudioBridge.ui.AudioScreen
import com.Slachy.StudioBridge.ui.ChatScreen
import com.Slachy.StudioBridge.ui.ConnectScreen
import com.Slachy.StudioBridge.ui.ScenesScreen
import com.Slachy.StudioBridge.ui.SettingsDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OBSControllerTheme {
                OBSControllerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OBSControllerApp(vm: OBSViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val scenes by vm.scenes.collectAsState()
    val currentScene by vm.currentScene.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val streamActive by vm.streamActive.collectAsState()
    val recordActive by vm.recordActive.collectAsState()
    val studioModeEnabled by vm.studioModeEnabled.collectAsState()
    val previewScene by vm.previewScene.collectAsState()
    val programScreenshot by vm.programScreenshot.collectAsState()
    val previewScreenshot by vm.previewScreenshot.collectAsState()
    val sceneItems by vm.sceneItems.collectAsState()
    val groupItems by vm.groupItems.collectAsState()
    val filters by vm.filters.collectAsState()
    val inputSettings by vm.inputSettings.collectAsState()
    val autoConnect by vm.autoConnect.collectAsState()
    val reconnecting by vm.reconnecting.collectAsState()
    val twitchChannel by vm.twitchChannel.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val chatConnected by vm.chatConnected.collectAsState()
    val thirdPartyEmotes by vm.thirdPartyEmotes.collectAsState()
    val twitchBadges by vm.twitchBadges.collectAsState()
    val emoteLoadReport by vm.emoteLoadReport.collectAsState()
    val chatFontSize by vm.chatFontSize.collectAsState()
    val chatLineSpacing by vm.chatLineSpacing.collectAsState()
    val chatEmoteSize by vm.chatEmoteSize.collectAsState()
    val chatUsernameSize by vm.chatUsernameSize.collectAsState()
    val animatedEmotes by vm.animatedEmotes.collectAsState()
    val showDebugBar by vm.showDebugBar.collectAsState()
    val enable7tv by vm.enable7tv.collectAsState()
    val enableBttv by vm.enableBttv.collectAsState()
    val enableFfz by vm.enableFfz.collectAsState()
    val recordingTimeSec by vm.recordingTimeSec.collectAsState()
    val sceneCollections by vm.sceneCollections.collectAsState()
    val currentSceneCollection by vm.currentSceneCollection.collectAsState()
    val volumeMeters by vm.volumeMeters.collectAsState()
    val showMiniMixer by vm.showMiniMixer.collectAsState()
    val showCollectionChip by vm.showCollectionChip.collectAsState()

    val isConnected = state is ConnectionState.Connected
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(
            twitchChannel = twitchChannel,
            chatFontSize = chatFontSize,
            chatLineSpacing = chatLineSpacing,
            chatEmoteSize = chatEmoteSize,
            chatUsernameSize = chatUsernameSize,
            animatedEmotes = animatedEmotes,
            showDebugBar = showDebugBar,
            enable7tv = enable7tv,
            enableBttv = enableBttv,
            enableFfz = enableFfz,
            onSaveChannel = { vm.saveTwitchChannel(it) },
            onFontSizeChange = { vm.setChatFontSize(it) },
            onLineSpacingChange = { vm.setChatLineSpacing(it) },
            onEmoteSizeChange = { vm.setChatEmoteSize(it) },
            onUsernameSizeChange = { vm.setChatUsernameSize(it) },
            onAnimatedEmotesChange = { vm.setAnimatedEmotes(it) },
            onShowDebugBarChange = { vm.setShowDebugBar(it) },
            onEnable7tvChange = { vm.setEnable7tv(it) },
            onEnableBttvChange = { vm.setEnableBttv(it) },
            onEnableFfzChange = { vm.setEnableFfz(it) },
            showMiniMixer = showMiniMixer,
            showCollectionChip = showCollectionChip,
            onShowMiniMixerChange = { vm.setShowMiniMixer(it) },
            onShowCollectionChipChange = { vm.setShowCollectionChip(it) },
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isConnected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showSettings = true }
                        ) {
                            Text("StudioBridge", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Text("StudioBridge", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    if (isConnected) {
                        TextButton(onClick = { vm.disconnect() }) {
                            Icon(Icons.Default.WifiOff, contentDescription = "Disconnect")
                            Spacer(Modifier.width(4.dp))
                            Text("Disconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            if (isConnected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Scenes") },
                        label = { Text("Scenes") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio") },
                        label = { Text("Audio") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                        label = { Text("Chat") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!isConnected) {
                ConnectScreen(
                    profiles = profiles,
                    lastUsedProfileId = vm.lastUsedProfileId,
                    connectionState = state,
                    autoConnect = autoConnect,
                    reconnecting = reconnecting,
                    onConnect = { vm.connect(it) },
                    onSaveProfile = { vm.saveProfile(it) },
                    onDeleteProfile = { vm.deleteProfile(it) },
                    onAutoConnectChange = { vm.setAutoConnect(it) },
                    onCancelReconnect = { vm.disconnect() }
                )
            } else {
                when (selectedTab) {
                    0 -> ScenesScreen(
                        scenes = scenes,
                        currentScene = currentScene,
                        previewScene = previewScene,
                        studioModeEnabled = studioModeEnabled,
                        streamActive = streamActive,
                        recordActive = recordActive,
                        recordingTimeSec = recordingTimeSec,
                        programScreenshot = programScreenshot,
                        previewScreenshot = previewScreenshot,
                        onSceneClick = { vm.onSceneClick(it) },
                        onTransition = { vm.triggerTransition() },
                        onCut = { vm.cutToScene() },
                        onToggleStudioMode = { vm.toggleStudioMode() },
                        onToggleStream = { vm.toggleStream() },
                        onToggleRecord = { vm.toggleRecord() },
                        onCreateScene = { vm.createScene(it) },
                        onAddSource = { name, kind -> vm.addSource(name, kind) },
                        onPreviewVisibilityChange = { visible -> vm.setScreenshotsPaused(!visible) },
                        sceneItems = sceneItems,
                        groupItems = groupItems,
                        onLoadSceneItems = { vm.loadSceneItems(it) },
                        onToggleSceneItemVisibility = { id, enabled -> vm.toggleSceneItemVisibility(id, enabled) },
                        onDeleteSceneItem = { vm.deleteSceneItem(it) },
                        onLoadGroupItems = { vm.loadGroupItems(it) },
                        onToggleGroupItemVisibility = { grp, id, enabled -> vm.toggleGroupItemVisibility(grp, id, enabled) },
                        onDeleteGroupItem = { grp, id -> vm.deleteGroupItem(grp, id) },
                        filters = filters,
                        onLoadFilters = { vm.loadFilters(it) },
                        onAddFilter = { name, kind -> vm.addFilter(name, kind) },
                        onRemoveFilter = { vm.removeFilter(it) },
                        onToggleFilter = { name, enabled -> vm.toggleFilter(name, enabled) },
                        onSetFilterSettings = { name, settings -> vm.setFilterSettings(name, settings) },
                        inputSettings = inputSettings,
                        onLoadInputSettings = { vm.loadInputSettings(it) },
                        onSetInputSettings = { name, settings -> vm.setInputSettings(name, settings) },
                        onReorderSceneItem = { id, idx -> vm.reorderSceneItem(id, idx) },
                        sceneCollections = sceneCollections,
                        currentSceneCollection = currentSceneCollection,
                        onSetSceneCollection = { vm.setSceneCollection(it) },
                        inputs = inputs,
                        onToggleMute = { vm.toggleMute(it) },
                        onMiniVolumeChange = { name, db -> vm.setVolume(name, db) },
                        volumeMeters = volumeMeters,
                        showMiniMixer = showMiniMixer,
                        showCollectionChip = showCollectionChip
                    )
                    1 -> AudioScreen(
                        inputs = inputs,
                        filters = filters,
                        volumeMeters = volumeMeters,
                        onToggleMute = { vm.toggleMute(it) },
                        onVolumeChange = { name, db -> vm.setVolume(name, db) },
                        onLoadFilters = { vm.loadFilters(it) },
                        onAddFilter = { name, kind -> vm.addFilter(name, kind) },
                        onRemoveFilter = { vm.removeFilter(it) },
                        onToggleFilter = { name, enabled -> vm.toggleFilter(name, enabled) },
                        onSetFilterSettings = { name, settings -> vm.setFilterSettings(name, settings) }
                    )
                    2 -> ChatScreen(
                        twitchChannel = twitchChannel,
                        chatMessages = chatMessages,
                        chatConnected = chatConnected,
                        thirdPartyEmotes = thirdPartyEmotes,
                        twitchBadges = twitchBadges,
                        emoteLoadReport = emoteLoadReport,
                        chatFontSize = chatFontSize,
                        chatLineSpacing = chatLineSpacing,
                        chatEmoteSize = chatEmoteSize,
                        chatUsernameSize = chatUsernameSize,
                        animatedEmotes = animatedEmotes,
                        showDebugBar = showDebugBar,
                        onConnect = { vm.connectTwitchChat() }
                    )
                }
            }
        }
    }
}

@Composable
fun OBSControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
