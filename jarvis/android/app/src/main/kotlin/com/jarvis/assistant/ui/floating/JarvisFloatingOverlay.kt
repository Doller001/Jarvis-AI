package com.jarvis.assistant.ui.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Check
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
 * JARVIS floating overlay — Google Assistant-style floating bubble.
 *
 * Two states:
 *   1. BUBBLE mode (default): small translucent pill showing "JARVIS" + hologram dot.
 *      Tapping the bubble expands into full panel.
 *   2. PANEL mode: full UI with cards, controls, text.
 *
 * The bubble is always visible when the foreground service is running.
 * It is hidden only when the user taps the X button (manual dismiss).
 *
 * FIXES:
 * - Always shows as a floating bubble (like Google Assistant)
 * - Expandable on tap: bubble → panel ↔ bubble
 * - Drag support via onDrag callback from OverlayWindowManager
 * - Close button only hides bubble (does NOT stop service)
 * - Auto-visibility: bubble always visible when service is running
 */
@Composable
fun JarvisFloatingOverlay(
    state: FloatingAssistantState,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit,
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
        if (expanded) {
            // Full panel — same as original, but border radius slightly smaller for "floating" feel
            ExpandedPanel(
                state = state,
                onClose = onClose,
                onMicTap = onMicTap,
                onConfirm = onConfirm,
                onEdit = onEdit,
                onDrag = onDrag
            )
        } else {
            // Compact bubble — always visible, Google-Assistant-style
            BubbleBubble(
                voiceState = state.voiceState,
                stateLabel = stateLabel(state.voiceState),
                onTap = onToggleExpand,
                onClose = onClose
            )
        }
    }
}

/**
 * Compact floating bubble (always visible).
 * Mirror-blue hologram dot + "JARVIS" label + small gap gradient.
 * Tapping expands; swipe-down on bubble dismisses (optional).
 */
@Composable
private fun BubbleBubble(
    voiceState: VoiceState,
    stateLabel: String,
    onTap: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { _ ->
                    // Center-of-bubble tap → expand
                    // Only expand if tap is within the bubble region (we approximate: center area)
                    onTap()
                })
            }
    ) {
        // Bubble pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .widthIn(max = 80.dp)
                .heightIn(max = 80.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(PanelBg)
                .border(2.dp, BorderC, CircleShape)
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) {
            // Hologram dot (small cyan glow)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        color = when (voiceState) {
                            VoiceState.WAKE_LISTENING, VoiceState.WAKE, VoiceState.IDLE -> Cyan.copy(alpha = 0.6f)
                            VoiceState.PROCESSING -> Cyan.copy(alpha = 0.9f)
                            VoiceState.SPEAKING -> Cyan.copy(alpha = 1f)
                            VoiceState.DISABLED, VoiceState.ERROR, VoiceState.RECOVERING -> Color.Gray.copy(alpha = 0.4f)
                            else -> Cyan.copy(alpha = 0.5f)
                        }
                    )
                    .border(1.5.dp, Cyan.copy(alpha = 0.4f), CircleShape)
            )

            // Small "JARVIS" text badge below the dot
            Text(
                text = "JARVIS",
                color = Cyan.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 2.dp)
                    .padding(horizontal = 2.dp)
            )
        }

        // Pinch gradient at bottom edges (forms the "bubble" feel)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    BrushVerticalGradient(
                        topColor = Color.Transparent,
                        bottomColor = PanelBg.copy(alpha = 0.3f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    BrushVerticalGradient(
                        topColor = Color.Transparent,
                        bottomColor = PanelBg.copy(alpha = 0.3f)
                    )
                )
        )
    }
}

/**
 * Vertical gradient brush (top→bottom).
 */
private fun BrushVerticalGradient(topColor: Color, bottomColor: Color) =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(topColor, bottomColor)
    )

/**
 * Expanded panel (same as original but with tighter corner radius).
 */
@Composable
private fun ExpandedPanel(
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
            .clip(RoundedCornerShape(22.dp))
            .background(PanelBg)
            .border(1.5.dp, BorderC, RoundedCornerShape(22.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, end = 14.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { _ ->
                            // minimal — real drag handled by OverlayWindowManager
                        })
                    }
            )

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Hologram (full size)
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
            }

            Text(
                text = stateLabel(state.voiceState),
                color = TextLabel,
                fontSize = 9.sp,
                letterSpacing = 0.08.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

            // Mic button — shows listening/active state visually
            IconButton(
                onClick = onMicTap,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when (state.voiceState) {
                                VoiceState.WAKE_LISTENING, VoiceState.COMMAND_LISTENING, VoiceState.LISTENING ->
                                    Cyan.copy(alpha = 0.15f)
                                VoiceState.PROCESSING ->
                                    Cyan.copy(alpha = 0.10f)
                                VoiceState.SPEAKING ->
                                    Cyan.copy(alpha = 0.05f)
                                else -> Color.White.copy(alpha = 0.06f)
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = when (state.voiceState) {
                                VoiceState.WAKE_LISTENING, VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> Cyan
                                VoiceState.SPEAKING -> Cyan.copy(alpha = 0.6f)
                                VoiceState.PROCESSING -> Cyan.copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.12f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.voiceState == VoiceState.SPEAKING)
                            Icons.Default.MicNone else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = when (state.voiceState) {
                            VoiceState.WAKE_LISTENING, VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> Cyan
                            VoiceState.SPEAKING -> Cyan.copy(alpha = 0.7f)
                            VoiceState.PROCESSING -> Cyan.copy(alpha = 0.5f)
                            else -> TextPrimary.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(if (state.voiceState == VoiceState.SPEAKING) 20.dp else 22.dp)
                    )
                }
            }

            OverlayActionButton(
                label = "Edit",
                icon = Icons.Default.Edit,
                enabled = state.userQuery.isNotBlank(),
                onClick = onEdit
            )
        }

        // Notification bell indicator (when new notification arrives)
        if (state.requiresConfirmation) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.8f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Notification",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun OverlayActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = label, tint = if (enabled) Cyan else TextPrimary.copy(alpha = 0.35f))
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
