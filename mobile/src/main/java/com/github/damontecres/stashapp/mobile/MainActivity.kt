package com.github.damontecres.stashapp.mobile

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.ui.dashboard.OCountDashboardScreen
import com.github.damontecres.stashapp.mobile.ui.home.AppHomeScreen
import com.github.damontecres.stashapp.mobile.ui.home.PlaylistUiItem
import com.github.damontecres.stashapp.mobile.ui.home.SceneHomeScreen
import com.github.damontecres.stashapp.mobile.ui.home.SceneListScreen
import com.github.damontecres.stashapp.mobile.ui.onboarding.PinSetupScreen
import com.github.damontecres.stashapp.mobile.ui.onboarding.PinUnlockScreen
import com.github.damontecres.stashapp.mobile.ui.onboarding.ServerSetupScreen
import com.github.damontecres.stashapp.mobile.ui.player.ScenePlayerScreen
import com.github.damontecres.stashapp.mobile.ui.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private val isAppLockedState = mutableStateOf(false)
    private var pinCodeValue: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent app screen from being visible in recent apps list
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val sharedPrefs = getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE)
        pinCodeValue = sharedPrefs.getString("pin_code", null)

        // Lock on startup if PIN is set
        if (!pinCodeValue.isNullOrBlank()) {
            isAppLockedState.value = true
        }

        setContent {
            val systemInDark = isSystemInDarkTheme()
            var currentThemeMode by remember {
                mutableStateOf(sharedPrefs.getString("app_theme", "system") ?: "system")
            }

            val isDark = when (currentThemeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }

            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var serverUrl by remember {
                        mutableStateOf(sharedPrefs.getString("server_url", "") ?: "")
                    }
                    var apiKey by remember {
                        mutableStateOf(sharedPrefs.getString("api_key", "") ?: "")
                    }
                    var username by remember {
                        mutableStateOf(sharedPrefs.getString("username", "") ?: "")
                    }
                    var password by remember {
                        mutableStateOf(sharedPrefs.getString("password", "") ?: "")
                    }
                    var pinCode by remember {
                        mutableStateOf(pinCodeValue)
                    }
                    var isServerConfigured by remember {
                        mutableStateOf(serverUrl.isNotBlank())
                    }
                    var isPinConfigured by remember {
                        mutableStateOf(!pinCodeValue.isNullOrBlank())
                    }
                    var showPlaylists by remember {
                        mutableStateOf(false)
                    }
                    var selectedPlaylist by remember {
                        mutableStateOf<PlaylistUiItem?>(null)
                    }
                    var selectedScene by remember {
                        mutableStateOf<SlimSceneData?>(null)
                    }
                    var playQueue by remember {
                        mutableStateOf<List<SlimSceneData>>(emptyList())
                    }
                    var showOAnalytics by remember {
                        mutableStateOf(false)
                    }
                    var showSettings by remember {
                        mutableStateOf(false)
                    }
                    val isLocked by isAppLockedState

                    when {
                        !isServerConfigured -> {
                            ServerSetupScreen(
                                initialServerUrl = serverUrl,
                                initialApiKey = apiKey,
                                initialUsername = username,
                                initialPassword = password,
                                onServerConfigured = { newUrl, newKey, newUsername, newPassword ->
                                    serverUrl = newUrl
                                    apiKey = newKey
                                    username = newUsername
                                    password = newPassword
                                    sharedPrefs.edit()
                                        .putString("server_url", newUrl)
                                        .putString("api_key", newKey)
                                        .putString("username", newUsername)
                                        .putString("password", newPassword)
                                        .apply()
                                    isServerConfigured = true
                                    isPinConfigured = !pinCodeValue.isNullOrBlank()
                                }
                            )
                        }
                        !isPinConfigured -> {
                            PinSetupScreen(
                                onPinConfigured = { pin ->
                                    pinCode = pin
                                    pinCodeValue = pin
                                    sharedPrefs.edit()
                                        .putString("pin_code", pin)
                                        .putBoolean("pin_setup_done", true)
                                        .apply()
                                    isPinConfigured = true
                                    isAppLockedState.value = false
                                }
                            )
                        }
                        isLocked && !pinCode.isNullOrBlank() -> {
                            PinUnlockScreen(
                                correctPin = pinCode!!,
                                onPinUnlocked = {
                                    isAppLockedState.value = false
                                }
                            )
                        }
                        showOAnalytics -> {
                            BackHandler(enabled = true) {
                                if (selectedScene != null) {
                                    selectedScene = null
                                    playQueue = emptyList()
                                } else {
                                    showOAnalytics = false
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                OCountDashboardScreen(
                                    serverUrl = serverUrl,
                                    apiKey = apiKey,
                                    username = username,
                                    password = password,
                                    onNavigateBack = { showOAnalytics = false },
                                    onSceneClick = { scene ->
                                        playQueue = listOf(scene)
                                        selectedScene = scene
                                    }
                                )

                                if (selectedScene != null) {
                                    ScenePlayerScreen(
                                        serverUrl = serverUrl,
                                        apiKey = apiKey,
                                        username = username,
                                        password = password,
                                        scene = selectedScene!!,
                                        playQueue = playQueue,
                                        initialIndex = 0,
                                        onBack = {
                                            selectedScene = null
                                            playQueue = emptyList()
                                        }
                                    )
                                }
                            }
                        }
                        showPlaylists -> {
                            var lastPlayedSceneId by remember { mutableStateOf<String?>(null) }

                            BackHandler(enabled = true) {
                                when {
                                    selectedScene != null -> {
                                        lastPlayedSceneId = selectedScene?.id
                                        selectedScene = null
                                        playQueue = emptyList()
                                    }
                                    selectedPlaylist != null -> {
                                        selectedPlaylist = null
                                    }
                                    else -> {
                                        showPlaylists = false
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                SceneHomeScreen(
                                    serverUrl = serverUrl,
                                    apiKey = apiKey,
                                    username = username,
                                    password = password,
                                    onBack = { showPlaylists = false },
                                    onPlaylistSelected = { playlist ->
                                        selectedPlaylist = playlist
                                    }
                                )

                                if (selectedPlaylist != null) {
                                    SceneListScreen(
                                        serverUrl = serverUrl,
                                        apiKey = apiKey,
                                        username = username,
                                        password = password,
                                        playlist = selectedPlaylist!!,
                                        refreshSceneId = lastPlayedSceneId,
                                        onBack = { selectedPlaylist = null },
                                        onSceneClick = { scene ->
                                            playQueue = listOf(scene)
                                            selectedScene = scene
                                        },
                                        onPlayAll = { scenes ->
                                            playQueue = scenes
                                            scenes.firstOrNull()?.let { selectedScene = it }
                                        },
                                        onPlayRandom = { scenes ->
                                            val shuffled = scenes.shuffled()
                                            playQueue = shuffled
                                            shuffled.firstOrNull()?.let { selectedScene = it }
                                        }
                                    )
                                }

                                if (selectedScene != null) {
                                    ScenePlayerScreen(
                                        serverUrl = serverUrl,
                                        apiKey = apiKey,
                                        username = username,
                                        password = password,
                                        scene = selectedScene!!,
                                        playQueue = playQueue,
                                        initialIndex = playQueue.indexOf(selectedScene).coerceAtLeast(0),
                                        onBack = {
                                            lastPlayedSceneId = selectedScene?.id
                                            selectedScene = null
                                            playQueue = emptyList()
                                        }
                                    )
                                }
                            }
                        }
                        showSettings -> {
                            BackHandler(enabled = true) {
                                showSettings = false
                            }
                            SettingsScreen(
                                serverUrl = serverUrl,
                                apiKey = apiKey,
                                username = username,
                                password = password,
                                currentTheme = currentThemeMode,
                                onThemeSelected = { newMode ->
                                    currentThemeMode = newMode
                                },
                                onBack = { showSettings = false },
                                onServerReconfigured = { _, _, _, _ ->
                                    showSettings = false
                                    isServerConfigured = false
                                }
                            )
                        }
                        else -> {
                            AppHomeScreen(
                                serverUrl = serverUrl,
                                onNavigateToPlaylists = { showPlaylists = true },
                                onNavigateToOAnalytics = { showOAnalytics = true },
                                onNavigateToTags = { /* Tag Manager */ },
                                onNavigateToPerformers = { /* Performer Manager */ },
                                onNavigateToSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPrefs = getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE)
        pinCodeValue = sharedPrefs.getString("pin_code", null)
        if (!pinCodeValue.isNullOrBlank() && sharedPrefs.contains("pin_setup_done")) {
            isAppLockedState.value = true
        }
    }
}
