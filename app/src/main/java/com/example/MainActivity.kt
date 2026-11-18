package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthManager
import com.example.data.local.SecurePreferencesStore
import com.example.data.repository.JarvisFirestoreRepository
import com.example.platform.DeviceControlManager
import com.example.platform.JarvisVoiceEngine
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.HudDashboardScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JarvisVipTheme
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.JarvisViewModel
import com.example.ui.viewmodel.UiEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object JarvisRoutes {
    const val HUD = "hud"
    const val CHAT = "chat"
    const val PLANNER = "planner"
    const val CONTROL = "control"
    const val SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(JarvisRoutes.HUD, R.string.nav_hud, Icons.Default.Hub),
    NavDestinationItem(JarvisRoutes.CHAT, R.string.nav_chat, Icons.Default.ChatBubble),
    NavDestinationItem(JarvisRoutes.PLANNER, R.string.nav_tasks, Icons.Default.EventNote),
    NavDestinationItem(JarvisRoutes.CONTROL, R.string.nav_control, Icons.Default.Tune),
    NavDestinationItem(JarvisRoutes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisVipTheme {
                JarvisRootApp()
            }
        }
    }
}

@Composable
fun JarvisRootApp() {
    val auth = Firebase.auth
    val currentUser by AuthManager.authStateFlow(auth).collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedJarvisShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "VIP User",
                email = user.email ?: ""
            )
        }
    }
}

@Composable
private fun AuthenticatedJarvisShell(
    currentUserId: String,
    displayName: String,
    email: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                JarvisViewModel(
                    appContext = app,
                    currentUserId = currentUserId,
                    userDisplayName = displayName,
                    userEmail = email,
                    repository = JarvisFirestoreRepository(app),
                    secretStore = SecurePreferencesStore(app),
                    deviceControl = DeviceControlManager(app),
                    voiceEngine = JarvisVoiceEngine(app)
                )
            }
        }
    )

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val secretConfig by viewModel.secretConfig.collectAsStateWithLifecycle()
    val coreState by viewModel.coreState.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val wakeWordRunning by viewModel.wakeWordServiceRunning.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val chatState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val actionLogsState by viewModel.actionLogsState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val selectedPhotoUri by viewModel.selectedSocialPhotoUri.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onPhotoPicked(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onPhotoCaptured(pendingCameraUri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshContactsIfPermitted()
            scope.launchSnackbar(snackbarHostState, "Permission granted.")
        } else {
            scope.launchSnackbar(snackbarHostState, "Permission denied. Feature limited without authorization.")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.RequestPhotoPicker -> {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                is UiEvent.RequestCameraCapture -> {
                    val uri = viewModel.createCameraCaptureUri()
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }
                is UiEvent.RequestRuntimePermission -> {
                    permissionLauncher.launch(event.permission)
                }
            }
        }
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: JarvisRoutes.HUD

    val backendConfigured = !userProfile?.backendBaseUrl.isNullOrBlank() ||
        secretConfig.backendBaseUrl.isNotBlank() ||
        secretConfig.encryptedRuntimeOpenAiKey.isNotBlank()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = VoidNavy,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = DeepHudNavy) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VoidNavy,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = HudTextSecondary,
                                    unselectedTextColor = HudTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = GlassNavySurface) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = VoidNavy,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = HudTextSecondary,
                                    unselectedTextColor = HudTextSecondary
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = JarvisRoutes.HUD,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable(JarvisRoutes.HUD) {
                        HudDashboardScreen(
                            displayName = userProfile?.displayName?.ifBlank { displayName } ?: displayName,
                            coreState = coreState,
                            rmsLevel = rmsLevel,
                            partialTranscript = partialTranscript,
                            wakeWordActive = wakeWordRunning,
                            telemetry = telemetry,
                            weather = weather,
                            news = news,
                            actionLogsState = actionLogsState,
                            backendConfigured = backendConfigured,
                            onMicClick = { viewModel.startVoiceInput() },
                            onStopVoiceOrSpeech = {
                                viewModel.stopVoiceInput()
                                viewModel.stopSpeaking()
                            },
                            onToggleWakeWord = { viewModel.toggleHandsFreeWakeWord(it) },
                            onQuickCommand = { cmd ->
                                viewModel.submitCommand(cmd)
                                navController.navigate(JarvisRoutes.CHAT)
                            },
                            onRefreshTelemetry = { viewModel.refreshDeviceDiagnostics() },
                            onOpenSettings = { navController.navigate(JarvisRoutes.SETTINGS) }
                        )
                    }

                    composable(JarvisRoutes.CHAT) {
                        ChatScreen(
                            chatState = chatState,
                            isThinking = isThinking,
                            isListening = isListening,
                            pendingConfirmation = pendingConfirmation,
                            attachedPhotoUri = selectedPhotoUri,
                            onSendCommand = { viewModel.submitCommand(it) },
                            onVoiceClick = { viewModel.startVoiceInput() },
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCapturePhoto = {
                                viewModel.submitCommand("capture photo")
                            },
                            onClearAttachment = { viewModel.onPhotoPicked(null) },
                            onConfirmAction = { viewModel.confirmPendingAction() },
                            onCancelAction = { viewModel.cancelPendingAction() }
                        )
                    }

                    composable(JarvisRoutes.PLANNER) {
                        PlannerScreen(
                            tasksState = tasksState,
                            onAddTask = { t, n, c, d, tm, p -> viewModel.createTaskItem(t, n, c, d, tm, p) },
                            onToggleTask = { viewModel.toggleTaskCompleted(it) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onSetSystemAlarm = { h, m, lbl -> viewModel.setSystemAlarmDirect(h, m, lbl) },
                            onExportToCalendar = { t, n -> viewModel.openCalendarEvent(t, n) }
                        )
                    }

                    composable(JarvisRoutes.CONTROL) {
                        ControlCenterScreen(
                            telemetry = telemetry,
                            installedApps = installedApps,
                            contacts = contacts,
                            notifications = notifications,
                            selectedPhotoUri = selectedPhotoUri,
                            onVolumeChange = { viewModel.setMediaVolume(it) },
                            onOpenWifi = { viewModel.openWifiSettings() },
                            onOpenBluetooth = { viewModel.openBluetoothSettings() },
                            onOpenSettings = { viewModel.openPhoneSettings() },
                            onLaunchApp = { viewModel.launchInstalledApp(it) },
                            onOpenAppInfo = { viewModel.openAppInfoForClose(it) },
                            onRequestContactsRefresh = { query ->
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.READ_CONTACTS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                else viewModel.refreshContactsIfPermitted(query)
                            },
                            onCallContact = { c ->
                                viewModel.queueCallConfirmation(c)
                                navController.navigate(JarvisRoutes.CHAT)
                            },
                            onSmsContact = { c, msg ->
                                viewModel.queueSmsConfirmation(c, msg)
                                navController.navigate(JarvisRoutes.CHAT)
                            },
                            onWhatsAppDraft = { name, num, msg ->
                                viewModel.queueWhatsAppConfirmation(name, num, msg)
                                navController.navigate(JarvisRoutes.CHAT)
                            },
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCapturePhoto = { viewModel.submitCommand("capture photo") },
                            onOpenFacebook = { viewModel.openFacebookDirect() },
                            onPrepareFacebookPost = { caption ->
                                viewModel.queueFacebookPostConfirmation(caption)
                                navController.navigate(JarvisRoutes.CHAT)
                            },
                            onOpenNotificationAccess = { viewModel.openNotificationListenerSettings() },
                            notificationAccessEnabled = viewModel.isNotificationListenerEnabled()
                        )
                    }

                    composable(JarvisRoutes.SETTINGS) {
                        SettingsScreen(
                            userProfile = userProfile,
                            secretConfig = secretConfig,
                            isBatteryOptimizationIgnored = viewModel.isBatteryOptimizationIgnored(),
                            isAccessibilityEnabled = viewModel.isAccessibilityEnabled(),
                            isNotificationListenerEnabled = viewModel.isNotificationListenerEnabled(),
                            onSaveLanguage = { viewModel.saveLanguageMode(it) },
                            onToggleVoice = { viewModel.saveVoiceEnabled(it) },
                            onToggleWakeWord = { viewModel.toggleHandsFreeWakeWord(it) },
                            onSaveVoiceTuning = { p, r -> viewModel.saveVoiceTuning(p, r) },
                            onSaveApprovedPreferences = { viewModel.saveApprovedPreferences(it) },
                            onSaveAiBackend = { url, model, key, token ->
                                viewModel.saveAiBackendSettings(url, model, key, token)
                            },
                            onOpenBatteryOptimization = { viewModel.openBatteryOptimizationSettings() },
                            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                            onOpenNotificationSettings = { viewModel.openNotificationListenerSettings() },
                            onGrantAccessibilityConsent = { viewModel.updateConsentFlags(accessibilityConsent = true) },
                            onSignOut = {
                                viewModel.toggleHandsFreeWakeWord(false)
                                AuthManager.signOut(credentialManager, scope)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchSnackbar(
    hostState: SnackbarHostState,
    message: String
) {
    launch {
        hostState.showSnackbar(message)
    }
}
