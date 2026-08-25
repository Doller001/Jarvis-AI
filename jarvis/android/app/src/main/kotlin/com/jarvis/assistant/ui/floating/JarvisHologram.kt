package com.jarvis.assistant.ui.floating

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.voice.VoiceState

/**
 * Hologram widget: 3 animated cyan rings drawn on a single Canvas.
 *
 * Phase 5 performance rule: one Canvas node, not 100+ Compose nodes.
 * Three animations: rotation1, rotation2-reversed, pulse scale.
 *
 * Ring speed/pulse adapts to VoiceState:
 *   IDLE / DISABLED  -> slow, dim
 *   WAKE_LISTENING   -> medium, bright
 *   ACKNOWLEDGING    -> fast, bright
 *   COMMAND_LISTENING-> faster pulse
 *   PROCESSING       -> fast rotation
 *   SPEAKING         -> strong pulse
 */
@Composable
fun JarvisHologram(
    voiceState: VoiceState,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val rotateDuration = when (voiceState) {
        VoiceState.PROCESSING -> 3000
        VoiceState.ACKNOWLEDGING, VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> 5000
        VoiceState.SPEAKING -> 4000
        VoiceState.WAKE_LISTENING, VoiceState.WAKE -> 6500
        else -> 9000
    }
    val pulseDuration = when (voiceState) {
        VoiceState.SPEAKING -> 700
        VoiceState.PROCESSING -> 900
        VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> 1100
        VoiceState.WAKE_LISTENING, VoiceState.WAKE -> 1600
        else -> 2200
    }
    val ringAlpha = when (voiceState) {
        VoiceState.DISABLED, VoiceState.IDLE -> 0.45f
        else -> 0.90f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hologram")

    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotateDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((rotateDuration * 0.75f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val cyan = Color(0xFF00F3FF)
    val cyanGlow = cyan.copy(alpha = ringAlpha * 0.35f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = size.toPx() / 2f
            val cy = size.toPx() / 2f

            // Ring 3 - innermost pulsing filled glow
            val r3 = size.toPx() * 0.24f * pulseScale
            drawCircle(
                color = cyanGlow,
                radius = r3,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = cyan.copy(alpha = ringAlpha * 0.55f),
                radius = r3,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Ring 1 - outer solid spinning
            val r1 = size.toPx() * 0.46f
            drawArc(
                color = cyan.copy(alpha = ringAlpha),
                startAngle = rotation1,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - r1, cy - r1),
                size = Size(r1 * 2, r1 * 2),
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = cyan.copy(alpha = ringAlpha),
                startAngle = rotation1 + 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - r1, cy - r1),
                size = Size(r1 * 2, r1 * 2),
                style = Stroke(width = 2.dp.toPx())
            )

            // Ring 2 - middle dashed reverse-spinning
            val r2 = size.toPx() * 0.35f
            drawArc(
                color = cyan.copy(alpha = ringAlpha * 0.75f),
                startAngle = rotation2,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r2, cy - r2),
                size = Size(r2 * 2, r2 * 2),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )
            drawArc(
                color = cyan.copy(alpha = ringAlpha * 0.75f),
                startAngle = rotation2 + 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r2, cy - r2),
                size = Size(r2 * 2, r2 * 2),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )
        }
    }
}
