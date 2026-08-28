package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.memory.MessageLog
import com.jarvis.assistant.network.ConnectionState
import com.jarvis.assistant.ui.theme.*
import com.jarvis.assistant.ui.permissions.JarvisPermission

@Composable
fun ScreenTopBar(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 13.sp, color = JarvisTextSecondary)
        }
    }
}

@Composable
fun ConnectionPill(connectionState: ConnectionState) {
    val (label, color) = when (connectionState) {
        ConnectionState.CONNECTED -> "Connected" to JarvisGreen
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> "Connecting…" to JarvisAmber
        ConnectionState.DISCONNECTED -> "Offline" to JarvisRed
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Reflective "listening" orb for the home screen — animates with VoiceState. */
@Composable
fun ListeningOrb(
    voiceState: com.jarvis.assistant.voice.VoiceState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isActive = voiceState == com.jarvis.assistant.voice.VoiceState.COMMAND_LISTENING
    val isSpeaking = voiceState == com.jarvis.assistant.voice.VoiceState.SPEAKING
    val isProcessing = voiceState == com.jarvis.assistant.voice.VoiceState.PROCESSING

    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = if (isActive || isSpeaking) 0.85f else 1f,
        targetValue = if (isActive || isSpeaking) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(10000, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "rotation"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            RepeatMode.Restart
        ), label = "wavePhase"
    )

    val ringColor = when {
        isActive -> JarvisCyan
        isProcessing -> JarvisAmber
        isSpeaking -> JarvisGreen
        else -> JarvisTextSecondary
    }

    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radial Waveform Equalizer Spikes
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.70f
            val barCount = 36
            for (i in 0 until barCount) {
                val angleDeg = (i * (360f / barCount)) + (if (isActive || isSpeaking) rotation else 0f)
                val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                val ampMultiplier = if (isActive || isSpeaking) {
                    0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(i * 0.5f + wavePhase))
                } else 0.12f
                val barLen = 14.dp.toPx() * ampMultiplier
                val startX = center.x + baseRadius * kotlin.math.cos(angleRad)
                val startY = center.y + baseRadius * kotlin.math.sin(angleRad)
                val endX = center.x + (baseRadius + barLen) * kotlin.math.cos(angleRad)
                val endY = center.y + (baseRadius + barLen) * kotlin.math.sin(angleRad)

                drawLine(
                    color = ringColor.copy(alpha = if (isActive || isSpeaking) 0.85f else 0.3f),
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        // Outer pulse ring
        Box(
            modifier = Modifier
                .size(175.dp)
                .fillMaxSize(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ringColor.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        // Halo glow
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(ringColor.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )
        // Inner core
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFE6FFF8), ringColor, ringColor.copy(alpha = 0.35f))
                    )
                )
                .border(1.5.dp, ringColor.copy(alpha = 0.8f), CircleShape)
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    lines: List<Pair<String, Color>>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = JarvisCard.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisGlow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            lines.forEach { (text, color) ->
                Text(text, color = color, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    permission: JarvisPermission,
    granted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = JarvisCard,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = permission.icon,
                contentDescription = null,
                tint = if (granted) JarvisGreen else JarvisBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        permission.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (permission.required) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Required",
                            color = JarvisAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(permission.description, color = JarvisTextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Granted",
                    tint = JarvisGreen,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onGrant,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisBlue)
                ) {
                    Text("Grant", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: MessageLog) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isUser) JarvisBlue else JarvisCard,
            contentColor = if (isUser) JarvisDark else Color.White,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .shadow(2.dp, RoundedCornerShape(14.dp))
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text, color = JarvisDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
