package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cosmic particle network background — drifting cyan/green nodes with
 * proximity links, inspired by the JARVIS "Kinetic Cosmic Merge Core" design.
 *
 * Performance: 24 nodes, squared-distance linking (no sqrt), no per-node halo
 * circles, and the whole animation freezes while the app is backgrounded.
 */
@Composable
fun CosmicBackground(modifier: Modifier = Modifier) {
    val isResumed = LocalLifecycleOwner.current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    var resumed by remember { mutableStateOf(isResumed) }
    LaunchedEffect(isResumed) { resumed = isResumed }

    val transition = rememberInfiniteTransition(label = "cosmic")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(60000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "drift"
    )
    val value = if (resumed) drift else 0f

    val rng = remember { Random(42) }
    val nodes = remember {
        List(24) { i ->
            Node(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                speed = 0.015f + rng.nextFloat() * 0.03f,
                size = 1.5f + rng.nextFloat() * 2.5f,
                phase = rng.nextFloat() * 6.28f,
                cyan = rng.nextFloat() > 0.4f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val radiusSq = ((w * h).let { kotlin.math.sqrt(it) } * 0.05f).let { it * it }

        for (i in nodes.indices) {
            val n = nodes[i]
            val px = (n.x + sin((value * 0.01f + n.phase) * n.speed) * 0.08f) * w
            val py = (n.y + cos((value * 0.008f + n.phase) * n.speed) * 0.08f) * h
            val pulse = 0.6f + 0.4f * sin(value * 0.05f + n.phase)
            val color = if (n.cyan) Color(0xFF00FFCC) else Color(0xFF00CC88)

            for (j in i + 1 until nodes.size) {
                val m = nodes[j]
                val qx = (m.x + sin((value * 0.01f + m.phase) * m.speed) * 0.08f) * w
                val qy = (m.y + cos((value * 0.008f + m.phase) * m.speed) * 0.08f) * h
                val dx = px - qx
                val dy = py - qy
                if (dx * dx + dy * dy < radiusSq) {
                    drawLine(
                        color = Color(0x3300FFCC),
                        start = Offset(px, py),
                        end = Offset(qx, qy),
                        strokeWidth = 1f
                    )
                }
            }

            drawCircle(
                color = color.copy(alpha = pulse * 0.85f),
                radius = n.size * pulse,
                center = Offset(px, py)
            )
        }

        drawCircle(
            color = Color(0x0D00FFCC),
            radius = w * 0.5f,
            center = Offset(w / 2f, h * 0.9f)
        )
        drawCircle(
            color = Color(0x0800CC88),
            radius = w * 0.35f,
            center = Offset(w / 2f, h * 0.92f)
        )
    }
}

@Composable
fun CosmicScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        CosmicBackground()
        content()
    }
}

private data class Node(
    val x: Float,
    val y: Float,
    val speed: Float,
    val size: Float,
    val phase: Float,
    val cyan: Boolean
)