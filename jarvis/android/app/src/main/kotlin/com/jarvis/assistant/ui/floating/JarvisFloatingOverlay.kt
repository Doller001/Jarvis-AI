package com.jarvis.assistant.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.overlay.FloatingAssistantState
import com.jarvis.assistant.voice.VoiceState

private val Cyan = Color(0xFF00F3FF)
private val PanelBg = Color(0x6B090E18) // rgba(9,14,24,0.42)
private val BorderC = Cyan.copy(alpha = 0.22f)
private val ResponseBg = Color(0x80001828)
private val TextPrimary = Color(0xFFE0F7FA)
private val TextSecondary = Color(0xFFB2EBF2)
private val TextLabel = Cyan.copy(alpha = 0.65f)

/**
 * Main composable for the JARVIS floating overlay.
 *
 * Structure mirrors the HTML design exactly:
 *   DragHandle + CloseButton
 *   Hologram (rings)
 *   UserQuery + StateLabel
 *   ResponseBox
 *   BottomControls (Confirm | Mic | Edit)
 */
@Composable
fun JarvisFloatingOverlay(
    state: FloatingAssistantState,
    onClose: () -> Unit,
    onMicTap: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDrag: (dx: Int, dy: Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        FloatingPanel(
            state = state,
            onClose = onClose,
            onMicTap = onMicTap,
            onConfirm = onConfirm,
            onEdit = onEdit,
            onDrag = onDrag
        )
    }
}

@Composable
private fun FloatingPanel(
    state: FloatingAssistantState,
    onClose: () -> Unit,
    onMicTap: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDrag: (dx: Int, dy: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(PanelBg)
            .border(1.dp, BorderC, RoundedCornerShape(28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle + close button row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, end = 14.dp)
        ) {
            // Drag handle (center)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
            )

            // Close button (top right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Hologram
        JarvisHologram(
            voiceState = state.voiceState,
            modifier = Modifier.weight(1f),
            size = 130.dp
        )

        // Text area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.userQuery.isNotBlank()) {
                Text(
                    text = state.userQuery,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
            }

            // State label
            Text(
                text = stateLabel(state.voiceState),
                color = TextLabel,
                fontSize = 9.sp,
                letterSpacing = 0.08.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(10.dp))

            // Response box
            val responseText = when {
                state.isThinking -> "Thinking…"
                state.response.isNotBlank() -> state.response
                else -> "JARVIS ready."
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(ResponseBg)
                    .border(1.dp, Cyan.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Text(
                    text = responseText,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OverlayActionButton(
                label = "Confirm",
                icon = Icons.Default.Check,
                enabled = state.requiresConfirmation,
                onClick = onConfirm
            )

            JarvisMicButton(
                voiceState = state.voiceState,
                onTap = onMicTap
            )

            OverlayActionButton(
                label = "Edit",
                icon = Icons.Default.Edit,
                enabled = state.userQuery.isNotBlank(),
                onClick = onEdit
            )
        }
    }
}

@Composable
private fun OverlayActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 0.85f else 0.38f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f * alpha))
                .border(1.dp, Color.White.copy(alpha = 0.12f * alpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TextPrimary.copy(alpha = alpha),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f * alpha),
            fontSize = 10.sp
        )
    }
}

private fun stateLabel(state: VoiceState): String = when (state) {
    VoiceState.DISABLED         -> "WAKE WORD OFF"
    VoiceState.IDLE             -> "READY"
    VoiceState.WAKE_LISTENING,
    VoiceState.WAKE             -> "LISTENING FOR HEY JARVIS"
    VoiceState.ACKNOWLEDGING    -> "ACKNOWLEDGING"
    VoiceState.COMMAND_LISTENING,
    VoiceState.LISTENING        -> "LISTENING…"
    VoiceState.PROCESSING       -> "THINKING…"
    VoiceState.SPEAKING         -> "SPEAKING…"
    VoiceState.RECOVERING,
    VoiceState.ERROR            -> "RECOVERING…"
}
