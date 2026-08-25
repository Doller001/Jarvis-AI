package com.jarvis.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.ui.components.CosmicScreen
import com.jarvis.assistant.ui.screens.ConversationScreen
import com.jarvis.assistant.ui.screens.HomeScreen
import com.jarvis.assistant.ui.screens.MemoryScreen
import com.jarvis.assistant.ui.screens.OnboardingScreen
import com.jarvis.assistant.ui.screens.ProvidersScreen
import com.jarvis.assistant.ui.screens.SettingsScreen
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JarvisAppRoot()
                }
            }
        }
    }
}

@Composable
fun JarvisAppRoot(viewModel: JarvisViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CosmicScreen {
        NavHost(navController = navController, startDestination = "home") {
        composable("onboarding") {
            OnboardingScreen(
                permissionState = uiState.permissionState,
                onRefresh = viewModel::refreshPermissions,
                onContinue = { navController.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
            )
        }
        composable("home") {
            HomeScreen(
                uiState = uiState,
                onOpenConversation = { navController.navigate("conversation") },
                onOpenProviders = { navController.navigate("providers") },
                onOpenPermissions = { navController.navigate("onboarding") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenMemory = { navController.navigate("memory") },
                onQuickAction = { viewModel.executeQuickAction(it) },
                onStartListening = viewModel::startListening,
                onToggleWakeListening = viewModel::toggleWakeListening,
                onToggleOverlay = viewModel::toggleOverlay
            )
        }
        composable("conversation") {
            ConversationScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSend = viewModel::sendUtterance,
                onStartListening = viewModel::startListening,
                onToggleWakeListening = viewModel::toggleWakeListening
            )
        }
        composable("providers") {
            LaunchedEffect(Unit) {
                viewModel.refreshProviders()
            }
            ProvidersScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSelectProvider = { viewModel.selectProvider(it) }
            )
        }
        composable("memory") {
            MemoryScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onDeleteMessage = { viewModel.deleteMemoryItem(it) },
                onClearHistory = viewModel::clearHistory
            )
        }
        composable("settings") {
            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onUpdateBackendUrl = viewModel::updateBackendUrl,
                onPingBackend = viewModel::pingBackend,
                onToggleTts = viewModel::setTtsEnabled,
                onSelectSpeechRate = viewModel::setSpeechRate,
                onSelectWakeSensitivity = viewModel::setWakeSensitivity,
                onClearHistory = viewModel::clearHistory
            )
        }
    }
        }
}
