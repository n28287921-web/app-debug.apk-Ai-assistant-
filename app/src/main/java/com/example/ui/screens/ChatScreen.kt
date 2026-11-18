package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.PendingAction
import com.example.data.model.UiState
import com.example.ui.components.GlassHudCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepCobalt
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.HudOutline
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy

@Composable
fun ChatScreen(
    chatState: UiState<List<ChatMessage>>,
    isThinking: Boolean,
    isListening: Boolean,
    pendingConfirmation: PendingAction?,
    attachedPhotoUri: Uri?,
    onSendCommand: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onPickPhoto: () -> Unit,
    onCapturePhoto: () -> Unit,
    onClearAttachment: () -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = (chatState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VoidNavy, DeepHudNavy, VoidNavy)))
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            if (pendingConfirmation != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AmberWarning.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AmberWarning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("pending_action_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AUTHORIZATION REQUIRED • ${pendingConfirmation.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberWarning
                        )
                        Text(
                            text = pendingConfirmation.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onConfirmAction,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldOnline, contentColor = VoidNavy),
                                modifier = Modifier.testTag("confirm_action_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Confirm & Execute")
                            }
                            OutlinedButton(
                                onClick = onCancelAction,
                                border = BorderStroke(1.dp, CrimsonAlert),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                                modifier = Modifier.testTag("cancel_action_button")
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (chatState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    is UiState.Error -> {
                        Text(
                            text = chatState.message,
                            color = CrimsonAlert,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        )
                    }

                    is UiState.Success -> {
                        if (chatState.data.isEmpty()) {
                            GlassHudCard(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Jarvis VIP Neural Chat Ready",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = IceCyan
                                    )
                                    Text(
                                        text = "Speak or type in English or हिन्दी — try \"Call Mom\", \"सुबह 7 बजे का अलार्म लगाओ\", \"Weather in Mumbai\", or ask ChatGPT anything.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudTextSecondary
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(chatState.data, key = { it.id }) { msg ->
                                    ChatBubble(msg)
                                }
                                if (isThinking) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = ElectricCyan,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "Jarvis is reasoning...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = IceCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (attachedPhotoUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = attachedPhotoUri,
                        contentDescription = "Selected photo attachment",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "Photo attached for Facebook / Share Studio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearAttachment) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = CrimsonAlert)
                    }
                }
            }

            // Input bar
            Surface(
                color = GlassNavySurface,
                border = BorderStroke(1.dp, HudOutline),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onPickPhoto) {
                        Icon(Icons.Default.Image, contentDescription = "Select Photo", tint = ElectricCyan)
                    }
                    IconButton(onClick = onCapturePhoto) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Photo", tint = ElectricCyan)
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.hint_command_input),
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    onSendCommand(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = HudOutline,
                            focusedTextColor = HudTextPrimary,
                            unfocusedTextColor = HudTextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_command_input")
                    )
                    FilledIconButton(
                        onClick = onVoiceClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) EmeraldOnline else DeepCobalt,
                            contentColor = if (isListening) VoidNavy else ElectricCyan
                        ),
                        modifier = Modifier.testTag("chat_mic_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Speak command")
                    }
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendCommand(inputText)
                                inputText = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ElectricCyan,
                            contentColor = VoidNavy
                        ),
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send command")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) ArcBlue.copy(alpha = 0.28f) else GlassNavySurface,
            border = BorderStroke(1.dp, if (isUser) ElectricCyan.copy(alpha = 0.55f) else HudOutline),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HudTextPrimary
                )
                if (message.actionSummary.isNotBlank()) {
                    Text(
                        text = message.actionSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.actionStatus) {
                            "executed" -> EmeraldOnline
                            "pending_confirm" -> AmberWarning
                            "failed" -> CrimsonAlert
                            else -> ElectricCyan
                        }
                    )
                }
            }
        }
    }
}
