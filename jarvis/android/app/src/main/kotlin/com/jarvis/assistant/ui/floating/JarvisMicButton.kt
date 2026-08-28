package com.jarvis.assistant.ui.floating

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.voice.VoiceState

@Composable
fun JarvisMicButton(
    voiceState: VoiceState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyan = Color(0xFF00F3FF)
    val isActive = voiceState == VoiceState.COMMAND_LISTENING ||
                   voiceState == VoiceState.COMMAND_LISTENING ||
                    voiceState == VoiceState.WAKE_LISTENING ||
                   voiceState == VoiceState.ACKNOWLEDGING

    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micGlow"
    )

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(glowScale)
                    .clip(CircleShape)
                    .background(cyan.copy(alpha = 0.18f))
            )
        }

        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(
                    elevation = if (isActive) 16.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (isActive) cyan else Color.Transparent,
                    spotColor = if (isActive) cyan else Color.Transparent
                )
                .clip(CircleShape)
                .background(cyan)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone",
                tint = Color(0xFF0A0E18),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
