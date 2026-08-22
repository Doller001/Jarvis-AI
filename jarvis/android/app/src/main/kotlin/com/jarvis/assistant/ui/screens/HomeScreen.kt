package com.jarvis.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onQuickAction: (String) -> Unit,
    onStartListening: () -> Unit = {}
) {
    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            JarvisBottomNavigation(
                onHome = {},
                onData = onOpenMemory,
                onMap = onOpenProviders,
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
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // 2. CENTRAL ORB / LISTENING SECTION
            val statusText = when (uiState.voiceState) {
                VoiceState.COMMAND_LISTENING, VoiceState.WAKE_DETECTED -> "LISTENING..."
                VoiceState.PROCESSING -> "THINKING..."
                VoiceState.SPEAKING -> "SPEAKING..."
                VoiceState.STARTING -> "STARTING..."
                VoiceState.ERROR -> "RECOVERING..."
                else -> "LISTENING..."
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
                onClick = onStartListening
            )

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
                onSystemsCheck = { onQuickAction("check wifi") },
                onAnalyzeData = onOpenMemory,
                onVoiceCommand = onOpenConversation,
                onHomeControl = { onQuickAction("torch on") },
                onSchedule = onOpenProviders
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun GlowingMicOrb(
    voiceState: VoiceState = VoiceState.STOPPED,
    onClick: () -> Unit = {}
) {
    val isListening = voiceState == VoiceState.COMMAND_LISTENING ||
            voiceState == VoiceState.WAKE_DETECTED ||
            voiceState == VoiceState.WAKE_LISTENING

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
    onSchedule: () -> Unit
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
                modifier = Modifier.weight(0.5f),
                onClick = onSchedule
            )
            Spacer(modifier = Modifier.weight(0.5f)) // Empty space to match the layout
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
    onMap: () -> Unit,
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
        BottomNavItem(icon = Icons.Default.Place, label = "Map", isSelected = false, onClick = onMap)
        BottomNavItem(icon = Icons.Default.ChatBubbleOutline, label = "Communication", isSelected = false, onClick = onCommunication)
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
