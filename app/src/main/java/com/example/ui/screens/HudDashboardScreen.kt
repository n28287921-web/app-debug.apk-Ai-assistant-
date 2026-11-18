package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActionLog
import com.example.data.model.AssistantCoreState
import com.example.data.model.DeviceTelemetry
import com.example.data.model.NewsHeadline
import com.example.data.model.UiState
import com.example.data.model.WeatherInfo
import com.example.ui.components.AnimatedVoiceWaveform
import com.example.ui.components.GlassHudCard
import com.example.ui.components.JarvisCoreVisualizer
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudOutline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudDashboardScreen(
    displayName: String,
    coreState: AssistantCoreState,
    rmsLevel: Float,
    partialTranscript: String,
    wakeWordActive: Boolean,
    telemetry: DeviceTelemetry,
    weather: WeatherInfo?,
    news: List<NewsHeadline>,
    actionLogsState: UiState<List<ActionLog>>,
    backendConfigured: Boolean,
    onMicClick: () -> Unit,
    onStopVoiceOrSpeech: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onQuickCommand: (String) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusText = when (coreState) {
        AssistantCoreState.STANDBY -> stringResource(R.string.status_standby)
        AssistantCoreState.LISTENING -> stringResource(R.string.status_listening)
        AssistantCoreState.WAKE_WORD_ACTIVE -> stringResource(R.string.status_wake_word)
        AssistantCoreState.THINKING -> stringResource(R.string.status_thinking)
        AssistantCoreState.SPEAKING -> stringResource(R.string.status_speaking)
    }
    val statusColor = when (coreState) {
        AssistantCoreState.LISTENING -> EmeraldOnline
        AssistantCoreState.WAKE_WORD_ACTIVE -> ElectricCyan
        AssistantCoreState.THINKING -> AmberWarning
        AssistantCoreState.SPEAKING -> IceCyan
        AssistantCoreState.STANDBY -> ArcBlue
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VoidNavy, DeepHudNavy, VoidNavy))),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "JARVIS VIP",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Welcome back, $displayName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                    StatusPill(label = statusText, accentColor = statusColor)
                }

                if (!backendConfigured) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AmberWarning.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSettings)
                            .testTag("configure_chatgpt_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Connect OpenAI ChatGPT Backend",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberWarning
                                )
                                Text(
                                    text = "Tap to add your secure proxy URL in Settings. Phone control & alarms work immediately.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HudTextSecondary
                                )
                            }
                        }
                    }
                }

                // Holographic Core & Waveform Card
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        JarvisCoreVisualizer(
                            coreState = coreState,
                            rmsLevel = rmsLevel,
                            size = 190.dp
                        )

                        AnimatedVoiceWaveform(
                            coreState = coreState,
                            rmsLevel = rmsLevel
                        )

                        if (partialTranscript.isNotBlank()) {
                            Text(
                                text = "\"$partialTranscript\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = IceCyan
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = {
                                    if (coreState == AssistantCoreState.LISTENING) onStopVoiceOrSpeech()
                                    else onMicClick()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (coreState == AssistantCoreState.LISTENING) EmeraldOnline else ElectricCyan,
                                    contentColor = VoidNavy
                                ),
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("hud_mic_button")
                            ) {
                                Icon(
                                    imageVector = if (coreState == AssistantCoreState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice Command Microphone",
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            if (coreState == AssistantCoreState.SPEAKING) {
                                FilledIconButton(
                                    onClick = onStopVoiceOrSpeech,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = CrimsonAlert,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("hud_stop_speech_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop Voice Output"
                                    )
                                }
                            }
                        }

                        // Hands-free wake word control
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = DeepHudNavy.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, HudOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = if (wakeWordActive) EmeraldOnline else ElectricCyan
                                    )
                                    Column {
                                        Text(
                                            text = "Hands-Free \"Hey JARVIS\"",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (wakeWordActive) "Foreground mic active • Pause anytime" else "Off • Tap to enable wake-word mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = HudTextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = wakeWordActive,
                                    onCheckedChange = onToggleWakeWord,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = VoidNavy,
                                        checkedTrackColor = EmeraldOnline
                                    ),
                                    modifier = Modifier.testTag("hud_wake_word_switch")
                                )
                            }
                        }
                    }
                }

                // Quick Bilingual Voice/Text Commands
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "QUICK COMMANDS (EN / हिन्दी)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            "Battery status" to "Battery & Storage",
                            "मौसम कैसा है" to "मौसम (Weather)",
                            "Daily news" to "Daily News",
                            "सुबह 7 बजे का अलार्म लगाओ" to "7 AM अलार्म",
                            "Open Facebook" to "Open Facebook",
                            "Read notifications" to "Notifications"
                        )
                        chips.forEach { (cmd, label) ->
                            AssistChip(
                                onClick = { onQuickCommand(cmd) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = DeepHudNavy,
                                    labelColor = IceCyan,
                                    leadingIconContentColor = ElectricCyan
                                ),
                                border = BorderStroke(1.dp, HudOutline)
                            )
                        }
                    }
                }

                // Telemetry Grid
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYSTEM TELEMETRY • ${telemetry.deviceModel.ifBlank { "ANDROID" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onRefreshTelemetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh telemetry",
                                tint = ElectricCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryTile(
                            icon = if (telemetry.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            title = "Battery",
                            value = "${telemetry.batteryPercent}%",
                            subtitle = if (telemetry.charging) "Charging • ${telemetry.batteryTempC}°C" else "${telemetry.batteryTempC}°C",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            icon = Icons.Default.SdStorage,
                            title = "Storage",
                            value = "${telemetry.freeStorageGb} GB",
                            subtitle = "Free / ${telemetry.totalStorageGb} GB",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryTile(
                            icon = Icons.Default.Memory,
                            title = "RAM Free",
                            value = "${telemetry.availableRamGb} GB",
                            subtitle = "Total ${telemetry.totalRamGb} GB",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            icon = Icons.Default.WbSunny,
                            title = weather?.locationName?.substringBefore(",") ?: "Weather",
                            value = weather?.let { "${it.temperatureC}°C" } ?: "--",
                            subtitle = weather?.conditionLabel ?: "Tap Weather chip",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Headlines / Recent Automation Logs
                if (news.isNotEmpty()) {
                    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Newspaper, contentDescription = null, tint = ElectricCyan)
                            Text("LIVE HEADLINES", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        news.take(3).forEach { item ->
                            Text(
                                text = "• ${item.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }

                if (actionLogsState is UiState.Success && actionLogsState.data.isNotEmpty()) {
                    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "RECENT AUTOMATION AUDIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        actionLogsState.data.take(4).forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.targetSummary.ifBlank { log.actionType },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = log.detailMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StatusPill(
                                    label = log.status.uppercase(),
                                    accentColor = when (log.status) {
                                        "executed", "confirmed" -> EmeraldOnline
                                        "cancelled" -> AmberWarning
                                        else -> CrimsonAlert
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = DeepHudNavy.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, HudOutline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = IceCyan)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
