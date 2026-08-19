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
import com.jarvis.assistant.ui.screens.ConversationScreen
import com.jarvis.assistant.ui.screens.HomeScreen
import com.jarvis.assistant.ui.screens.OnboardingScreen
import com.jarvis.assistant.ui.screens.ProvidersScreen
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

    NavHost(navController = navController, startDestination = "onboarding") {
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
                onOpenPermissions = { navController.navigate("onboarding") }
            )
        }
        composable("conversation") {
            ConversationScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSend = viewModel::sendUtterance
            )
        }
        composable("providers") {
            ProvidersScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSelectProvider = { viewModel.selectProvider(it) }
            )
        }
    }
}
