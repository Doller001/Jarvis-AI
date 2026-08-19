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
import com.jarvis.assistant.ui.components.CosmicScreen
import com.jarvis.assistant.ui.theme.*
import com.jarvis.assistant.ui.JarvisUiState

@Composable
fun ConversationScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    CosmicScreen {
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
                    "Say \"Jarvis\" then speak, or type a command like\n\"open YouTube\" or \"call mom\".",
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
                placeholder = { Text("Type a command…", color = JarvisTextSecondary) },
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
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                modifier = Modifier
                    .size(48.dp)
                    .padding(0.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisBlue,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = JarvisDark)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { /* voice stub: in a full build this triggers WakeWordEngine */ },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisBlue),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = JarvisBlue)
            Spacer(Modifier.width(8.dp))
            Text("Hold to talk", fontSize = 14.sp)
        }
        }
    }
}
