package com.jarvis.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.memory.MessageLog
import com.jarvis.assistant.ui.JarvisUiState
import com.jarvis.assistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onClearHistory: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredMessages = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) uiState.messages
        else uiState.messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Memories?", color = Color.White) },
            text = { Text("This will remove all stored conversation facts from local memory.", color = JarvisTextSecondary) },
            containerColor = JarvisCard,
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = JarvisRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
            }
            Text("Memory Core", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = JarvisGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, JarvisGreen.copy(alpha = 0.4f))
            ) {
                Text(
                    "● Supabase Synced",
                    color = JarvisGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Telemetry Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JarvisBlue.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, JarvisBlue.copy(alpha = 0.3f))
            ) {
                Text(
                    "${uiState.messages.size} Entity Nodes",
                    color = JarvisCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (uiState.messages.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear Memory", color = JarvisRed, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Holographic Knowledge Graph Visualizer Card
        KnowledgeGraphCard(uiState = uiState)

        Spacer(Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories and facts…", color = JarvisTextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisGlow,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = JarvisCyan
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Memory Items List
        if (filteredMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank()) "No memories stored yet. Talk to Jarvis to build context!" else "No matching memories found.",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredMessages.reversed(), key = { it.timestamp }) { msg ->
                    MemoryItemCard(message = msg, onDelete = { onDeleteMessage(msg.timestamp) })
                }
            }
        }
    }
}

@Composable
fun KnowledgeGraphCard(uiState: JarvisUiState) {
    val transition = rememberInfiniteTransition(label = "graph")
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ), label = "pulse"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = JarvisCard.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, JarvisGlow),
        modifier = Modifier.fillMaxWidth().height(165.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Neural network connection lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val node1 = Offset(w * 0.22f, h * 0.35f)
                val node2 = Offset(w * 0.50f, h * 0.20f)
                val node3 = Offset(w * 0.78f, h * 0.38f)
                val node4 = Offset(w * 0.35f, h * 0.75f)
                val node5 = Offset(w * 0.68f, h * 0.72f)

                val lineColor = JarvisCyan.copy(alpha = 0.35f * pulse)

                drawLine(lineColor, node1, node2, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node2, node3, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node1, node4, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node2, node4, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node2, node5, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node4, node5, strokeWidth = 1.5.dp.toPx())
                drawLine(lineColor, node3, node5, strokeWidth = 1.5.dp.toPx())
            }

            // Floating Node Badges
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                GraphNodeBadge(
                    label = "Owner: Minaty",
                    color = JarvisCyan,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 22.dp)
                )
                GraphNodeBadge(
                    label = "Assistant: JARVIS (AGI)",
                    color = JarvisBlue,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
                GraphNodeBadge(
                    label = "Audio: Bluetooth/Device",
                    color = Color(0xFFC084FC),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 26.dp)
                )
                GraphNodeBadge(
                    label = "Store: Supabase/Local (${uiState.messages.size} nodes)",
                    color = Color(0xFF34D399),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp)
                )
                GraphNodeBadge(
                    label = "Role: Cognitive Operator",
                    color = JarvisAmber,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
fun GraphNodeBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MemoryItemCard(message: MessageLog, onDelete: () -> Unit) {
    val isUser = message.role == "user"
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = JarvisCard,
        border = BorderStroke(1.dp, if (isUser) JarvisBlue.copy(alpha = 0.3f) else JarvisCyan.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (isUser) JarvisBlue else JarvisCyan).copy(alpha = 0.2f)
                    ) {
                        Text(
                            if (isUser) "USER" else "JARVIS",
                            color = if (isUser) JarvisBlue else JarvisCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(timeStr, color = JarvisTextSecondary, fontSize = 11.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = JarvisGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "99% Confidence",
                            color = JarvisGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = JarvisRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(message.text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
