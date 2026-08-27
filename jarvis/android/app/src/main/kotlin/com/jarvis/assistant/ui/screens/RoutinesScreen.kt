package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.*

/**
 * RoutinesScreen — displays all JARVIS preset routines as tappable cards.
 *
 * Users can activate any routine with a single tap, or use the voice shortcut
 * shown on each card. Activated routines are dispatched via JarvisViewModel.
 */

data class RoutineCard(
    val name: String,
    val label: String,
    val emoji: String,
    val description: String,
    val voiceShortcut: String,
    val color: Color,
    val icon: ImageVector,
)

private val ROUTINE_CARDS = listOf(
    RoutineCard(
        name = "morning",
        label = "Morning",
        emoji = "🌅",
        description = "Brightness 80%, Volume 50%, DND off, Ringer normal",
        voiceShortcut = "\"Morning routine\"",
        color = Color(0xFFFFB74D),
        icon = Icons.Filled.WbSunny,
    ),
    RoutineCard(
        name = "night",
        label = "Night",
        emoji = "🌙",
        description = "Brightness 15%, Volume 20%, DND on, Silent",
        voiceShortcut = "\"Night mode\"",
        color = Color(0xFF7986CB),
        icon = Icons.Filled.NightsStay,
    ),
    RoutineCard(
        name = "movie",
        label = "Movie",
        emoji = "🎬",
        description = "Brightness 100%, Volume 80%, DND on, Auto-rotate",
        voiceShortcut = "\"Movie mode\"",
        color = Color(0xFFEF5350),
        icon = Icons.Filled.Movie,
    ),
    RoutineCard(
        name = "meeting",
        label = "Meeting",
        emoji = "💼",
        description = "DND on, Vibrate, Brightness 60%, Muted",
        voiceShortcut = "\"Meeting mode\"",
        color = Color(0xFF42A5F5),
        icon = Icons.Filled.Work,
    ),
    RoutineCard(
        name = "driving",
        label = "Driving",
        emoji = "🚗",
        description = "Volume 100%, Brightness max, Maps opened",
        voiceShortcut = "\"Driving mode\"",
        color = Color(0xFF66BB6A),
        icon = Icons.Filled.DirectionsCar,
    ),
    RoutineCard(
        name = "gym",
        label = "Gym",
        emoji = "💪",
        description = "Volume max, DND on, Music started",
        voiceShortcut = "\"Gym mode\"",
        color = Color(0xFFFF7043),
        icon = Icons.Filled.FitnessCenter,
    ),
    RoutineCard(
        name = "reading",
        label = "Reading",
        emoji = "📖",
        description = "Brightness 50%, DND on, Silent, Portrait locked",
        voiceShortcut = "\"Reading mode\"",
        color = Color(0xFF26C6DA),
        icon = Icons.Filled.MenuBook,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onRunRoutine: (String) -> Unit,
) {
    var lastActivated by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = JarvisDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "⚡ JARVIS Routines",
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Text(
                            "Tap to activate · Voice: say the shortcut",
                            color = JarvisTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisDark),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {

            // Banner: last activated routine feedback
            lastActivated?.let { name ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisCyan.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Activated: ${name.replaceFirstChar { it.uppercase() }} routine",
                            color = JarvisCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(ROUTINE_CARDS) { card ->
                    RoutineCardItem(
                        card = card,
                        onClick = {
                            onRunRoutine(card.name)
                            lastActivated = card.name
                        },
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF000002)
@Composable
private fun RoutinesScreenPreview() {
    com.jarvis.assistant.ui.theme.JarvisTheme {
        RoutinesScreen(
            onBack = {},
            onRunRoutine = {},
        )
    }
}

@Composable
private fun RoutineCardItem(
    card: RoutineCard,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        card.color.copy(alpha = 0.25f),
                        card.color.copy(alpha = 0.08f),
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Icon + emoji
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = card.label,
                    tint = card.color,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(card.emoji, fontSize = 22.sp)
            }

            // Labels
            Column {
                Text(
                    card.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    card.description,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = card.color.copy(alpha = 0.2f),
                ) {
                    Text(
                        card.voiceShortcut,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = card.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
