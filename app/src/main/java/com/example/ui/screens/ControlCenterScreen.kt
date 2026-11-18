package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ContactMatch
import com.example.data.model.DeviceTelemetry
import com.example.data.model.InstalledAppInfo
import com.example.data.model.NotificationDigestItem
import com.example.ui.components.GlassHudCard
import com.example.ui.theme.DeepCobalt
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudOutline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlCenterScreen(
    telemetry: DeviceTelemetry,
    installedApps: List<InstalledAppInfo>,
    contacts: List<ContactMatch>,
    notifications: List<NotificationDigestItem>,
    selectedPhotoUri: Uri?,
    onVolumeChange: (Int) -> Unit,
    onOpenWifi: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onOpenAppInfo: (InstalledAppInfo) -> Unit,
    onRequestContactsRefresh: (String) -> Unit,
    onCallContact: (ContactMatch) -> Unit,
    onSmsContact: (ContactMatch, String) -> Unit,
    onWhatsAppDraft: (String, String, String) -> Unit,
    onPickPhoto: () -> Unit,
    onCapturePhoto: () -> Unit,
    onOpenFacebook: () -> Unit,
    onPrepareFacebookPost: (String) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    notificationAccessEnabled: Boolean
) {
    var volumeSlider by remember(telemetry.mediaVolumePercent) { mutableFloatStateOf(telemetry.mediaVolumePercent.toFloat()) }
    var appQuery by remember { mutableStateOf("") }
    var contactQuery by remember { mutableStateOf("") }
    var quickMessageText by remember { mutableStateOf("Hello from Jarvis VIP") }
    var facebookCaption by remember { mutableStateOf("") }

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
                    text = "CONTROL & SOCIAL STUDIO",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ElectricCyan
                )

                // System Shortcuts & Volume
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text("SYSTEM CONTROLS", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = ElectricCyan)
                        Text("Media Volume (${volumeSlider.toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = volumeSlider,
                        onValueChange = { volumeSlider = it },
                        onValueChangeFinished = { onVolumeChange(volumeSlider.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onOpenWifi) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Wi-Fi")
                        }
                        OutlinedButton(onClick = onOpenBluetooth) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Bluetooth")
                        }
                        OutlinedButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Settings")
                        }
                        OutlinedButton(onClick = onCapturePhoto) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Camera")
                        }
                    }
                }

                // Facebook & Social Media Studio
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = ElectricCyan)
                            Text("FACEBOOK & SOCIAL STUDIO", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                        OutlinedButton(onClick = onOpenFacebook) {
                            Text("Open FB")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Selected post photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = facebookCaption,
                        onValueChange = { facebookCaption = it },
                        label = { Text("Post Caption / Comment Draft (EN or हिन्दी)") },
                        modifier = Modifier.fillMaxWidth().testTag("facebook_caption_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onPickPhoto,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Pick Photo")
                        }
                        Button(
                            onClick = { onPrepareFacebookPost(facebookCaption) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                            modifier = Modifier.weight(1f).testTag("prepare_facebook_post_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Review & Post")
                        }
                    }
                }

                // Contacts & Messaging
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Contacts, contentDescription = null, tint = ElectricCyan)
                            Text("CONTACTS • CALL • SMS • WHATSAPP", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                        OutlinedButton(onClick = { onRequestContactsRefresh(contactQuery) }) {
                            Text("Sync")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactQuery,
                        onValueChange = {
                            contactQuery = it
                            onRequestContactsRefresh(it)
                        },
                        label = { Text("Search contacts") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quickMessageText,
                        onValueChange = { quickMessageText = it },
                        label = { Text("Draft SMS / WhatsApp message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (contacts.isEmpty()) {
                        Text(
                            text = "Tap Sync to grant Contacts access and search contacts by name.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    } else {
                        contacts.take(5).forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DeepHudNavy,
                                border = BorderStroke(1.dp, HudOutline),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                                        Text(c.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                                    }
                                    IconButton(onClick = { onCallContact(c) }) {
                                        Icon(Icons.Default.Call, contentDescription = "Call ${c.name}", tint = EmeraldOnline)
                                    }
                                    IconButton(onClick = { onSmsContact(c, quickMessageText) }) {
                                        Icon(Icons.Default.Sms, contentDescription = "SMS ${c.name}", tint = ElectricCyan)
                                    }
                                    OutlinedButton(
                                        onClick = { onWhatsAppDraft(c.name, c.phoneNumber, quickMessageText) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("WA")
                                    }
                                }
                            }
                        }
                    }
                }

                // Installed Apps Launcher
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = ElectricCyan)
                        Text("APP LAUNCHER & CONTROL", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        label = { Text("Filter installed apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filteredApps = installedApps.filter {
                        appQuery.isBlank() || it.appName.contains(appQuery, ignoreCase = true)
                    }.take(10)
                    filteredApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onOpenAppInfo(app) }) {
                                    Text("Info")
                                }
                                Button(
                                    onClick = { onLaunchApp(app) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepCobalt, contentColor = IceCyan)
                                ) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }

                // Notification Reader Digest
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = ElectricCyan)
                            Text("NOTIFICATION READER", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                        }
                        if (!notificationAccessEnabled) {
                            OutlinedButton(onClick = onOpenNotificationAccess) {
                                Text("Enable")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!notificationAccessEnabled) {
                        Text(
                            text = "Notification access is off. Enable it only if you want Jarvis to read incoming notifications aloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    } else if (notifications.isEmpty()) {
                        Text(
                            text = "No recent notifications captured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    } else {
                        notifications.take(5).forEach { n ->
                            Text(
                                text = "${n.appName}: ${n.title} — ${n.body}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
