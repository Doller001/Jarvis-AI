package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.jarvis.assistant.ui.components.CosmicScreen
import com.jarvis.assistant.ui.components.PermissionCard
import com.jarvis.assistant.ui.components.PrimaryButton
import com.jarvis.assistant.ui.components.ScreenTopBar
import com.jarvis.assistant.ui.permissions.AllPermissions
import com.jarvis.assistant.ui.permissions.GrantKind
import com.jarvis.assistant.ui.permissions.JarvisPermission
import com.jarvis.assistant.ui.theme.*

@Composable
fun OnboardingScreen(
    permissionState: com.jarvis.assistant.permissions.PermissionState,
    onRefresh: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    // Map permission id -> current granted status
    val grantedById = mapOf(
        "microphone" to permissionState.isMicrophoneGranted,
        "notifications" to permissionState.isNotificationGranted,
        "accessibility" to permissionState.isAccessibilityGranted,
        "battery" to permissionState.isBatteryOptimizationIgnored,
        "camera" to permissionState.isCameraGranted,
        "call_phone" to permissionState.isCallPhoneGranted,
        "contacts" to permissionState.isContactsGranted,
        "sms" to permissionState.isSmsGranted
    )

    // Runtime permission launcher (batched request)
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onRefresh()
    }

    fun requestRuntime(perms: List<JarvisPermission>) {
        val androidPerms = perms.mapNotNull { it.androidPermission }
            .distinct()
            .filter { it.isNotBlank() }
        if (androidPerms.isNotEmpty()) {
            runtimeLauncher.launch(androidPerms.toTypedArray())
        }
    }

    fun openSettings(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
                    data = Uri.parse("package:${context.packageName}")
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun onGrant(permission: JarvisPermission) {
        if (permission.grant == GrantKind.RUNTIME) {
            requestRuntime(listOf(permission))
        } else {
            permission.settingsAction?.let { openSettings(it) }
        }
    }

    CosmicScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp)
        ) {
            ScreenTopBar(
                title = "Welcome to JARVIS",
                subtitle = "Grant the permissions below to activate your always-ready assistant."
            )

        val granted = permissionState.grantedCount
        val total = AllPermissions.list.size
        LinearProgressIndicator(
            progress = granted.toFloat() / total,
            color = JarvisBlue,
            trackColor = JarvisCard,
            modifier = Modifier.fillMaxWidth().height(6.dp).padding(vertical = 8.dp)
        )
        Text(
            "$granted of $total permissions granted",
            color = JarvisTextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AllPermissions.list) { perm ->
                PermissionCard(
                    permission = perm,
                    granted = grantedById[perm.id] == true,
                    onGrant = { onGrant(perm) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (permissionState.allRequiredGranted) "Continue" else "Grant required permissions ($granted/$total)",
            onClick = {
                onRefresh()
                if (permissionState.allRequiredGranted) onContinue()
            },
            modifier = Modifier.fillMaxWidth()
        )
        // Auto-advance once every required permission is actually granted.
        LaunchedEffect(permissionState.allRequiredGranted) {
            if (permissionState.allRequiredGranted) onContinue()
        }
        }
    }
}
