package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.JarvisUiState
import com.jarvis.assistant.ui.components.ConnectionPill
import com.jarvis.assistant.ui.components.PrimaryButton
import com.jarvis.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onUpdateBackendUrl: (String) -> Unit,
    onPingBackend: (String) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onSelectSpeechRate: (Float) -> Unit,
    onSelectWakeSensitivity: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var urlInput by remember(uiState.backendUrl) { mutableStateOf(uiState.backendUrl) }
    var showClearDialog by remember { mutableStateOf(false) }

    val presets = listOf(
        "Render Cloud" to "https://and9-1.onrender.com",
        "Emulator (10.0.2.2)" to "http://10.0.2.2:8000",
        "Localhost (127.0.0.1)" to "http://127.0.0.1:8000"
    )

    val rates = listOf(0.8f to "0.8x", 1.0f to "1.0x", 1.2f to "1.2x", 1.5f to "1.5x")

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Conversation Memory?", color = Color.White) },
            text = { Text("This will remove all stored conversation turns from local memory.", color = JarvisTextSecondary) },
            containerColor = JarvisCard,
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = JarvisRed)
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
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
            }
            Text(
                "Settings & Config",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            ConnectionPill(uiState.connectionState)
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Backend Gateway Configuration Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = JarvisCard,
                border = BorderStroke(1.dp, JarvisGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Backend API Gateway",
                            color = JarvisCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Base URL", color = JarvisTextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisGlow,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = JarvisCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    Text("Quick Presets:", color = JarvisTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { (label, presetUrl) ->
                            val isSelected = urlInput == presetUrl
                            FilterChip(
                                selected = isSelected,
                                onClick = { urlInput = presetUrl },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = JarvisCyan,
                                    containerColor = JarvisCard,
                                    labelColor = JarvisTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) JarvisCyan else JarvisGlow
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Ping indicator
                    if (uiState.pingResult != null || uiState.isPinging) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisDark,
                            border = BorderStroke(1.dp, JarvisGlow),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.isPinging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = JarvisCyan
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Testing connection…", color = JarvisTextSecondary, fontSize = 13.sp)
                                } else {
                                    Icon(
                                        Icons.Filled.NetworkCheck,
                                        contentDescription = null,
                                        tint = if (uiState.pingResult?.contains("Online") == true) JarvisGreen else JarvisAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        uiState.pingResult ?: "",
                                        color = if (uiState.pingResult?.contains("Online") == true) JarvisGreen else JarvisAmber,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPingBackend(urlInput) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Test Ping", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { onUpdateBackendUrl(urlInput) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Save & Apply", color = JarvisDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Voice & Speech Synthesis Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = JarvisCard,
                border = BorderStroke(1.dp, JarvisGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Voice & Speech Engine",
                            color = JarvisCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Voice Response (TTS)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Speak assistant answers aloud", color = JarvisTextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = uiState.isTtsEnabled,
                            onCheckedChange = onToggleTts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisCyan,
                                checkedTrackColor = JarvisBlue.copy(alpha = 0.4f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisDark
                            )
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("Speech Rate:", color = JarvisTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rates.forEach { (rate, label) ->
                            val isSelected = uiState.speechRate == rate
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) JarvisBlue.copy(alpha = 0.25f) else JarvisDark,
                                border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisGlow),
                                onClick = { onSelectSpeechRate(rate) },
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        color = if (isSelected) JarvisCyan else JarvisTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("Wake Word Sensitivity:", color = JarvisTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val sensitivities = listOf("Low", "Balanced", "High")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sensitivities.forEach { sensitivity ->
                            val isSelected = uiState.wakeSensitivity == sensitivity
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) JarvisBlue.copy(alpha = 0.25f) else JarvisDark,
                                border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisGlow),
                                onClick = { onSelectWakeSensitivity(sensitivity) },
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        sensitivity,
                                        color = if (isSelected) JarvisCyan else JarvisTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // 3. Local Memory & History Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = JarvisCard,
                border = BorderStroke(1.dp, JarvisGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = JarvisRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Memory & Cache",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${uiState.messages.size} conversation turns stored in local cache.",
                        color = JarvisTextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisRed),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Clear Local History", fontSize = 13.sp)
                    }
                }
            }

            // 4. About & Version Info
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = JarvisCard.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, JarvisGlow),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("JARVIS Android Runtime", color = JarvisCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Version: 1.0.0 • Target SDK: 34 (Android 14)", color = JarvisTextSecondary, fontSize = 12.sp)
                    Text("Pipeline: On-Device Voice → Level-1 Deterministic Engine → Cloud Brain", color = JarvisTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
