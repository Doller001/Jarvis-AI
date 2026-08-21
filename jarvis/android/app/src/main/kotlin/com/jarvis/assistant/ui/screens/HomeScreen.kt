package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
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
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onQuickAction: (String) -> Unit,
    onStartListening: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionPill(uiState.connectionState)
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = JarvisCyan)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                ListeningOrb(voiceState = uiState.voiceState, onClick = onStartListening)

                Spacer(Modifier.height(16.dp))
                Text(
                    text = when (uiState.voiceState) {
                        com.jarvis.assistant.voice.VoiceState.WAKE_DETECTED,
                        com.jarvis.assistant.voice.VoiceState.COMMAND_LISTENING -> "Listening…"
                        com.jarvis.assistant.voice.VoiceState.PROCESSING -> "Thinking…"
                        com.jarvis.assistant.voice.VoiceState.SPEAKING -> "Speaking…"
                        com.jarvis.assistant.voice.VoiceState.ERROR -> "Recovering…"
                        com.jarvis.assistant.voice.VoiceState.STOPPED,
                        com.jarvis.assistant.voice.VoiceState.STARTING -> "Starting…"
                        else -> "Listening for \"Hey Jarvis\""
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

                Spacer(Modifier.height(18.dp))

                // Telemetry HUD Cards (From Generative UI Mockup)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryCard(
                        title = "ACTIVE LLM",
                        value = uiState.activeProvider,
                        subValue = uiState.activeModel.take(16),
                        modifier = Modifier.weight(1.3f),
                        onClick = onOpenProviders
                    )
                    TelemetryCard(
                        title = "LATENCY",
                        value = if (uiState.connectionState == com.jarvis.assistant.network.ConnectionState.CONNECTED) "180ms" else "Offline",
                        subValue = "Sub-second",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCard(
                        title = "MEMORY FACTS",
                        value = "${uiState.messages.size}",
                        subValue = "Supabase",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMemory
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Quick Action Sub-Second Hardware Pills
                Text(
                    "QUICK ACTIONS",
                    color = JarvisCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionPill(
                        icon = Icons.Filled.FlashlightOn,
                        label = "Torch",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("torch on") }
                    )
                    QuickActionPill(
                        icon = Icons.Filled.Wifi,
                        label = "WiFi",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("check wifi") }
                    )
                    QuickActionPill(
                        icon = Icons.Filled.Apps,
                        label = "Apps",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("apps list") }
                    )
                    QuickActionPill(
                        icon = Icons.Filled.VolumeUp,
                        label = "Volume",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("volume up") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickStat(
                        icon = Icons.Filled.Memory,
                        label = "Memory Core",
                        value = "${uiState.messages.size} Facts",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMemory
                    )
                    QuickStat(
                        icon = Icons.Filled.ChatBubble,
                        label = "Dialogue",
                        value = "Open Chat",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenConversation
                    )
                    QuickStat(
                        icon = Icons.Filled.Tune,
                        label = "LLM Gateway",
                        value = uiState.activeProvider,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenProviders
                    )
                }

                Spacer(Modifier.height(20.dp))

                PrimaryButton(text = "Talk to Jarvis", onClick = onOpenConversation)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                    Text("Review permissions", color = JarvisTextSecondary, fontSize = 13.sp)
                }
            }
        }
}

@Composable
fun TelemetryCard(
    title: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JarvisCard.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, JarvisGlow),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.height(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = JarvisTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subValue, color = JarvisCyan, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun QuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = JarvisCard,
        border = BorderStroke(1.dp, JarvisGlow),
        onClick = onClick,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
