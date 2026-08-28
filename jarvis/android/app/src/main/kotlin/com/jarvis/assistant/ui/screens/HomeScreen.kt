package com.jarvis.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import com.jarvis.assistant.ui.JarvisUiState
import com.jarvis.assistant.voice.VoiceState

// --- COLOR PALETTE ---
val BgColor = Color(0xFF040812) // Deep dark blue/black
val CyanGlow = Color(0xFF00E5FF) // Neon Cyan
val DarkCardBg = Color(0xFF071B26) // Slightly lighter dark blue for cards
val TextGray = Color(0xFFA0B4C4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: JarvisUiState,
    onOpenConversation: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenRoutines: () -> Unit = {},
    onQuickAction: (String) -> Unit,
    onStartListening: () -> Unit = {},
    onToggleWakeListening: () -> Unit = {},
    onToggleOverlay: () -> Unit = {}
) {
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

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            JarvisBottomNavigation(
                onHome = {},
                onData = onOpenMemory,
                onRoutines = onOpenRoutines,
                onCommunication = onOpenConversation,
                onSettings = onOpenSettings
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 1. HEADER SECTION
            Text(
                text = "JARVIS",
                color = CyanGlow,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                text = "Personal AI Assistant",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Setup Required Banner if permissions are missing
            if (!uiState.permissionState.allRequiredGranted) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF261907),
                    border = BorderStroke(1.dp, Color(0xFFFFB020)),
                    onClick = onOpenPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFFB020),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Setup Required",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tap to grant permissions (${uiState.permissionState.grantedCount}/9 active)",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFFB020),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. CENTRAL ORB / LISTENING SECTION
            val statusText = when (uiState.voiceState) {
                VoiceState.DISABLED       -> "WAKE WORD OFF"
                VoiceState.WAKE_LISTENING -> "SAY 'HEY JARVIS'"
                VoiceState.ACKNOWLEDGING  -> "YES BOSS..."
                VoiceState.COMMAND_LISTENING -> "LISTENING..."
                VoiceState.RECOVERING     -> "RECOVERING..."
                VoiceState.PROCESSING     -> "THINKING..."
                VoiceState.SPEAKING       -> "SPEAKING..."
                VoiceState.INTERRUPTING   -> "INTERRUPTING..."
            }

            Text(
                text = statusText,
                color = CyanGlow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GlowingMicOrb(
                voiceState = uiState.voiceState,
                onClick = handleMicClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Controls Row: Wake word toggle + Floating Overlay toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wake-word toggle (offline "Hey Jarvis" detection)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (uiState.wakeListening) CyanGlow.copy(alpha = 0.2f) else DarkCardBg,
                    border = BorderStroke(1.dp, if (uiState.wakeListening) CyanGlow else TextGray),
                    onClick = onToggleWakeListening,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (uiState.wakeListening) CyanGlow else TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (uiState.wakeListening) "Wake word ON" else "Wake word OFF",
                            color = if (uiState.wakeListening) CyanGlow else TextGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Floating UI Overlay Toggle
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (uiState.isOverlayActive) CyanGlow.copy(alpha = 0.2f) else DarkCardBg,
                    border = BorderStroke(1.dp, if (uiState.isOverlayActive) CyanGlow else TextGray),
                    onClick = onToggleOverlay,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = null,
                            tint = if (uiState.isOverlayActive) CyanGlow else TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (uiState.isOverlayActive) "Overlay ON" else "Floating UI",
                            color = if (uiState.isOverlayActive) CyanGlow else TextGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. GREETING MESSAGE BUBBLE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyanGlow, RoundedCornerShape(12.dp))
                    .background(DarkCardBg, RoundedCornerShape(12.dp))
                    .clickable { onOpenConversation() }
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.lastResponse.ifBlank { "Good Morning, user!\nWhat can I assist you with today?" },
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. ACTION BUTTONS GRID
            ActionGrid(
                onSystemsCheck = { onQuickAction("systems check") },
                onAnalyzeData = { onQuickAction("analyze data") },
                onVoiceCommand = onOpenConversation,
                onHomeControl = { onQuickAction("home control") },
                onSchedule = { onQuickAction("schedule") },
                onRoutines = onOpenRoutines
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun GlowingMicOrb(
        voiceState: VoiceState = VoiceState.DISABLED,
    onClick: () -> Unit = {}
) {
        val isListening = voiceState == VoiceState.COMMAND_LISTENING || voiceState == VoiceState.WAKE_LISTENING

    // Do not keep a 60fps infinite animation running while idle.
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.06f else 1f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "micScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .clickable(onClick = onClick)
    ) {
        // Outer glowing rings
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, CyanGlow.copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .border(4.dp, CyanGlow.copy(alpha = 0.8f), CircleShape)
        )
        // Inner Mic Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DarkCardBg)
                .border(2.dp, CyanGlow, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun ActionGrid(
    onSystemsCheck: () -> Unit,
    onAnalyzeData: () -> Unit,
    onVoiceCommand: () -> Unit,
    onHomeControl: () -> Unit,
    onSchedule: () -> Unit,
    onRoutines: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Default.SettingsSuggest,
                title = "Systems Check",
                modifier = Modifier.weight(1f),
                onClick = onSystemsCheck
            )
            ActionCard(
                icon = Icons.Default.Analytics,
                title = "Analyze Data",
                modifier = Modifier.weight(1f),
                onClick = onAnalyzeData
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Default.MicNone,
                title = "Voice Command",
                modifier = Modifier.weight(1f),
                onClick = onVoiceCommand
            )
            ActionCard(
                icon = Icons.Default.Home,
                title = "Home Control",
                modifier = Modifier.weight(1f),
                onClick = onHomeControl
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Default.CalendarMonth,
                title = "Schedule",
                modifier = Modifier.weight(1f),
                onClick = onSchedule
            )
            ActionCard(
                icon = Icons.Default.FlashOn,
                title = "Routines",
                modifier = Modifier.weight(1f),
                onClick = onRoutines
            )
        }
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(1.dp, CyanGlow, RoundedCornerShape(12.dp))
            .background(DarkCardBg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanGlow,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
fun JarvisBottomNavigation(
    onHome: () -> Unit,
    onData: () -> Unit,
    onRoutines: () -> Unit,
    onCommunication: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF020409))
            .border(width = 1.dp, color = CyanGlow.copy(alpha = 0.3f))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(icon = Icons.Default.Home, label = "Home", isSelected = true, onClick = onHome)
        BottomNavItem(icon = Icons.Default.BarChart, label = "Data", isSelected = false, onClick = onData)
        BottomNavItem(icon = Icons.Default.FlashOn, label = "Routines", isSelected = false, onClick = onRoutines)
        BottomNavItem(icon = Icons.Default.ChatBubbleOutline, label = "Chat", isSelected = false, onClick = onCommunication)
        BottomNavItem(icon = Icons.Default.Settings, label = "Settings", isSelected = false, onClick = onSettings)
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val color = if (isSelected) CyanGlow else TextGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp
        )
    }
}
