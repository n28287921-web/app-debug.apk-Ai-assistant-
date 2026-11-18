package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalSecretConfig
import com.example.data.model.LanguageMode
import com.example.data.model.UserProfile
import com.example.ui.components.GlassHudCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    secretConfig: LocalSecretConfig,
    isBatteryOptimizationIgnored: Boolean,
    isAccessibilityEnabled: Boolean,
    isNotificationListenerEnabled: Boolean,
    onSaveLanguage: (LanguageMode) -> Unit,
    onToggleVoice: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onSaveVoiceTuning: (Float, Float) -> Unit,
    onSaveApprovedPreferences: (String) -> Unit,
    onSaveAiBackend: (String, String, String?, String?) -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onGrantAccessibilityConsent: () -> Unit,
    onSignOut: () -> Unit
) {
    val activeLang = LanguageMode.fromRaw(userProfile?.preferredLanguage)
    var backendUrl by remember(userProfile?.backendBaseUrl, secretConfig.backendBaseUrl) {
        mutableStateOf(userProfile?.backendBaseUrl.orEmpty().ifBlank { secretConfig.backendBaseUrl })
    }
    var modelName by remember(userProfile?.openAiModel, secretConfig.openAiModel) {
        mutableStateOf(userProfile?.openAiModel.orEmpty().ifBlank { secretConfig.openAiModel })
    }
    var runtimeOpenAiKey by remember { mutableStateOf("") }
    var backendToken by remember { mutableStateOf("") }
    var approvedMemory by remember(userProfile?.approvedPreferencesSummary) {
        mutableStateOf(userProfile?.approvedPreferencesSummary.orEmpty())
    }
    var pitch by remember(secretConfig.ttsPitch) { mutableFloatStateOf(secretConfig.ttsPitch) }
    var rate by remember(secretConfig.ttsRate) { mutableFloatStateOf(secretConfig.ttsRate) }
    var showAccessibilityConsentDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VoidNavy, DeepHudNavy, VoidNavy))),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "JARVIS SETTINGS & SECURITY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ElectricCyan
                )

                // OpenAI ChatGPT Secure Backend Configuration
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = ElectricCyan)
                        Text("OPENAI CHATGPT SECURE BACKEND", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No OpenAI API keys are embedded inside this APK. Connect your deployed /backend proxy URL (recommended) or enter a temporary test key stored AES-GCM encrypted in Android Keystore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("Secure Backend Proxy URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_backend_url_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("OpenAI Model (e.g., gpt-4o-mini, gpt-4o)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendToken,
                        onValueChange = { backendToken = it },
                        label = { Text("Optional Backend Shared Token (Encrypted)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = runtimeOpenAiKey,
                        onValueChange = { runtimeOpenAiKey = it },
                        label = {
                            Text(
                                if (secretConfig.encryptedRuntimeOpenAiKey.isNotBlank())
                                    "Test OpenAI Key Saved (Enter to replace)"
                                else "Optional Prototype OpenAI Key (Keystore Encrypted)"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onSaveAiBackend(
                                backendUrl,
                                modelName,
                                runtimeOpenAiKey.takeIf { it.isNotBlank() },
                                backendToken.takeIf { it.isNotBlank() }
                            )
                            runtimeOpenAiKey = ""
                            backendToken = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                        modifier = Modifier.fillMaxWidth().testTag("save_backend_button")
                    ) {
                        Text("Save AI Brain Config")
                    }
                }

                // Language & Female Voice Controls
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ElectricCyan)
                        Text("LANGUAGE & FEMALE AI VOICE", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageMode.entries.forEach { mode ->
                            FilterChip(
                                selected = activeLang == mode,
                                onClick = { onSaveLanguage(mode) },
                                label = { Text(mode.displayLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = VoidNavy
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spoken Female Voice Replies", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = userProfile?.voiceEnabled != false,
                            onCheckedChange = onToggleVoice
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hands-Free \"Hey JARVIS\" Mic", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = secretConfig.wakeWordServiceEnabled,
                            onCheckedChange = onToggleWakeWord
                        )
                    }
                    Text("Voice Pitch (${String.format("%.2f", pitch)})", style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        onValueChangeFinished = { onSaveVoiceTuning(pitch, rate) },
                        valueRange = 0.85f..1.30f,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                    Text("Speech Speed (${String.format("%.2f", rate)})", style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                    Slider(
                        value = rate,
                        onValueChange = { rate = it },
                        onValueChangeFinished = { onSaveVoiceTuning(pitch, rate) },
                        valueRange = 0.80f..1.30f,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                }

                // Approved Preferences Memory
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("USER-APPROVED AI PREFERENCES", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = approvedMemory,
                        onValueChange = { approvedMemory = it },
                        label = { Text("Preferences Jarvis remembers (e.g. Call me Boss, prefer Hinglish)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onSaveApprovedPreferences(approvedMemory) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Approved Preferences")
                    }
                }

                // Opt-in Automation & Samsung Galaxy A51 Battery Guidance
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = ElectricCyan)
                        Text("PERMISSIONS & GALAXY A51 OPTIMIZATION", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = IceCyan, modifier = Modifier.size(18.dp))
                                Text("Accessibility Navigation", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Opt-in only for Back/Home/Recents voice control", style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                        }
                        StatusPill(
                            label = if (isAccessibilityEnabled) "ENABLED" else "OFF",
                            accentColor = if (isAccessibilityEnabled) EmeraldOnline else AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showAccessibilityConsentDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review Consent & Configure Accessibility")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = IceCyan, modifier = Modifier.size(18.dp))
                                Text("Notification Reader", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Lets Jarvis read unread notifications when asked", style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                        }
                        StatusPill(
                            label = if (isNotificationListenerEnabled) "ENABLED" else "OFF",
                            accentColor = if (isNotificationListenerEnabled) EmeraldOnline else AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configure Notification Access")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.BatterySaver, contentDescription = null, tint = IceCyan, modifier = Modifier.size(18.dp))
                                Text("Samsung Galaxy A51 Battery", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Set Battery to 'Unrestricted' & exclude from One UI Sleeping Apps for reliable Hey JARVIS mode.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary
                            )
                        }
                        StatusPill(
                            label = if (isBatteryOptimizationIgnored) "UNRESTRICTED" else "OPTIMIZED",
                            accentColor = if (isBatteryOptimizationIgnored) EmeraldOnline else AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onOpenBatteryOptimization,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Battery Optimization Settings")
                    }
                }

                // Account & Sign Out
                GlassHudCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CrimsonAlert.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "Signed in as ${userProfile?.displayName.orEmpty().ifBlank { "Jarvis User" }} (${userProfile?.email.orEmpty()})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = VoidNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    if (showAccessibilityConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityConsentDialog = false },
            containerColor = DeepHudNavy,
            title = { Text("Accessibility Consent", color = ElectricCyan) },
            text = {
                Text(
                    "Jarvis VIP uses Android's Accessibility Service ONLY when you trigger voice navigation commands (Go Back, Go Home, Open Recents). It never reads passwords or records screen content silently.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityConsentDialog = false
                        onGrantAccessibilityConsent()
                        onOpenAccessibilitySettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                ) { Text("I Consent • Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityConsentDialog = false }) { Text("Cancel") }
            }
        )
    }
}
