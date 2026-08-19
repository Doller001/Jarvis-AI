package com.jarvis.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.app.AppState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        setContent {
            JarvisMainApp()
        }
    }
}

@Composable
fun JarvisMainApp() {
    var appState by remember { mutableStateOf(AppState()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B0E14)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Branding
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "JARVIS",
                    fontSize = 36.sp,
                    color = Color(0xFF00D2FF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "● ${appState.lastResponse}",
                    fontSize = 14.sp,
                    color = Color(0xFF00D2FF).copy(alpha = 0.8f)
                )
            }

            // Connection & Model Status Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A00D2FF).copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Active Provider: ${appState.activeProvider}", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Active Model: ${appState.activeModel}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WebSocket: ${if (appState.isWebSocketConnected) "Connected" else "Offline"}",
                        color = if (appState.isWebSocketConnected) Color.Green else Color.Red,
                        fontSize = 12.sp
                    )
                }
            }

            // Action Button
            Button(
                onClick = {
                    appState = appState.copy(lastResponse = "Listening...")
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
            ) {
                Text(text = "Talk to Jarvis", color = Color(0xFF0B0E14), fontSize = 18.sp)
            }
        }
    }
}
