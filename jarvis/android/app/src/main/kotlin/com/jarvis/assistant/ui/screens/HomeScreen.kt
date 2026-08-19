package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.components.*
import com.jarvis.assistant.ui.theme.*
import com.jarvis.assistant.ui.JarvisUiState

@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    onOpenConversation: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    CosmicScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "JARVIS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = JarvisCyan,
                            blurRadius = 18f
                        )
                    )
                )
                ConnectionPill(uiState.connectionState)
            }

        Spacer(Modifier.height(24.dp))

        ListeningOrb(voiceState = uiState.voiceState)

        Spacer(Modifier.height(16.dp))
        Text(
            text = when (uiState.voiceState) {
                com.jarvis.assistant.voice.VoiceState.WAKE_DETECTED -> "Wake word detected"
                com.jarvis.assistant.voice.VoiceState.LISTENING -> "Listening…"
                com.jarvis.assistant.voice.VoiceState.PROCESSING -> "Thinking…"
                com.jarvis.assistant.voice.VoiceState.SPEAKING -> "Speaking…"
                com.jarvis.assistant.voice.VoiceState.ERROR -> "Error"
                else -> "Listening for \"Jarvis\""
            },
            fontSize = 16.sp,
            color = Color.White
        )
        Text(
            text = uiState.lastResponse,
            fontSize = 13.sp,
            color = JarvisTextSecondary,
            modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp)
        )

        Spacer(Modifier.height(20.dp))

        StatusCard(
            title = "Active Provider",
            lines = listOf(
                "Provider: ${uiState.activeProvider}" to Color.White,
                "Model: ${uiState.activeModel}" to Color.White.copy(alpha = 0.75f),
                "Accessibility: ${if (uiState.isAccessibilityEnabled) "On" else "Off"}" to
                        (if (uiState.isAccessibilityEnabled) JarvisGreen else JarvisAmber)
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickStat(
                icon = Icons.Filled.Memory,
                label = "Memory",
                value = uiState.messages.size.toString(),
                modifier = Modifier.weight(1f)
            )
            QuickStat(
                icon = Icons.Filled.ChatBubble,
                label = "Talk",
                value = "Open",
                modifier = Modifier.weight(1f),
                onClick = onOpenConversation
            )
            QuickStat(
                icon = Icons.Filled.Tune,
                label = "Providers",
                value = "Manage",
                modifier = Modifier.weight(1f),
                onClick = onOpenProviders
            )
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = "Talk to Jarvis", onClick = onOpenConversation)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
            Text("Review permissions", color = JarvisTextSecondary, fontSize = 13.sp)
        }
        }
    }
}

@Composable
fun QuickStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = JarvisCard,
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.height(78.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = JarvisBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(label, color = JarvisTextSecondary, fontSize = 11.sp)
        }
    }
}
