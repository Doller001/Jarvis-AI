package com.jarvis.assistant.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.components.ChatBubble
import com.jarvis.assistant.ui.components.ConnectionPill
import com.jarvis.assistant.ui.theme.*
import com.jarvis.assistant.ui.JarvisUiState

@Composable
fun ConversationScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onStartListening: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isListeningNow = uiState.voiceState == com.jarvis.assistant.voice.VoiceState.COMMAND_LISTENING

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
            }
            Text("Conversation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            ConnectionPill(uiState.connectionState)
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap the mic to speak or type a command like\n\"open YouTube\" or \"turn on torch\".",
                    color = JarvisTextSecondary,
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
                        if (isListeningNow) "Listening to you… speak now" else "Type a command…",
                        color = if (isListeningNow) JarvisCyan else JarvisTextSecondary
                    ) 
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisBlue,
                    unfocusedBorderColor = JarvisCard,
                    cursorColor = JarvisBlue,
                    focusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onStartListening,
                shape = RoundedCornerShape(12.dp),
                color = if (isListeningNow) JarvisCyan else JarvisCard,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Speak",
                        tint = if (isListeningNow) JarvisDark else JarvisCyan
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                shape = RoundedCornerShape(12.dp),
                color = JarvisBlue,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = JarvisDark)
                }
            }
        }
    }
}
