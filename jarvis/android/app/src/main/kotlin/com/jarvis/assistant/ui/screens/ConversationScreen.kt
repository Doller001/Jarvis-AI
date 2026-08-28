package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.jarvis.assistant.ui.components.ChatBubble
import com.jarvis.assistant.ui.components.ConnectionPill
import com.jarvis.assistant.ui.JarvisUiState

private val ConvCyan = Color(0xFF00E5FF)
private val ConvCardBg = Color(0xFF071B26)
private val ConvTextGray = Color(0xFFA0B4C4)
private val ConvDark = Color(0xFF040812)

@Composable
fun ConversationScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStartListening: () -> Unit = {},
    onToggleWakeListening: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isListeningNow = uiState.voiceState == com.jarvis.assistant.voice.VoiceState.COMMAND_LISTENING ||
            uiState.voiceState == com.jarvis.assistant.voice.VoiceState.WAKE_LISTENING

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartListening()
        }
    }

    val handleMicClick = {
        if (uiState.permissionState.isMicrophoneGranted) {
            onStartListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ConvCyan
                )
            }
            Text("Conversation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            ConnectionPill(uiState.connectionState)
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Tap the mic to speak or type a command like\n\"open YouTube\" or \"turn on torch\".",
                    color = ConvTextGray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(uiState.messages) { ChatBubble(it) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { 
                    Text(
                        text = if (isListeningNow) "Listening to you… speak now" else "Type a command…",
                        color = if (isListeningNow) ConvCyan else ConvTextGray
                    ) 
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ConvCyan,
                    unfocusedBorderColor = ConvCardBg,
                    cursorColor = ConvCyan,
                    focusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isListeningNow) ConvCyan else ConvCardBg)
                    .clickable { handleMicClick() }
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Speak",
                    tint = if (isListeningNow) ConvDark else ConvCyan
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ConvCyan)
                    .clickable { if (text.isNotBlank()) { onSend(text); text = "" } }
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = ConvDark
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onToggleWakeListening,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (uiState.wakeListening) ConvCyan else ConvTextGray),
            border = BorderStroke(
                1.dp,
                if (uiState.wakeListening) ConvCyan else ConvTextGray
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = if (uiState.wakeListening) ConvCyan else ConvTextGray
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (uiState.wakeListening) "Wake word on — say \"Jarvis\" or \"Hey Jarvis\"" else "Wake word paused",
                fontSize = 14.sp
            )
        }
    }
}
