package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.components.ConnectionPill
import com.jarvis.assistant.ui.theme.*
import com.jarvis.assistant.ui.JarvisUiState

@Composable
fun ProvidersScreen(
    uiState: JarvisUiState,
    onBack: () -> Unit,
    onSelectProvider: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = JarvisCyan)
                }
                Text("LLM Providers", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
                ConnectionPill(uiState.connectionState)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Connected gateway: ${uiState.backendUrl}",
                color = JarvisTextSecondary,
                fontSize = 12.sp
            )
            if (uiState.providersLoading) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = JarvisBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Fetching providers…", color = JarvisTextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            items(uiState.providers) { provider ->
                val isActive = provider == uiState.activeProvider
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) JarvisBlue.copy(alpha = 0.12f) else JarvisCard,
                    onClick = { onSelectProvider(provider) },
                    border = if (isActive) BorderStroke(2.dp, JarvisBlue) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isActive) "Active — ${uiState.activeModel}" else "Tap to activate",
                                color = if (isActive) JarvisBlue else JarvisTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (isActive) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = JarvisBlue)
                        }
                    }
                }
            }
        }
    }
}
