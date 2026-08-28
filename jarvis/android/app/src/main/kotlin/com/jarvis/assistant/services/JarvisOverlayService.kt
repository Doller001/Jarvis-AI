package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.assistant.overlay.FloatingAssistantState
import com.jarvis.assistant.overlay.OverlayWindowManager
import com.jarvis.assistant.ui.floating.JarvisFloatingOverlay
import com.jarvis.assistant.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dedicated overlay service - separate from JarvisForegroundService.
 *
 * Responsibilities:
 *   - WindowManager.addView / removeView
 *   - Mount JarvisFloatingOverlay (Compose) onto the window
 *   - Observe JarvisForegroundService state callbacks and update FloatingAssistantState
 *
 * DOES NOT touch VoiceRuntime or any voice component directly.
 */
class JarvisOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        private const val TAG = "JarvisOverlayService"
        private const val NOTIF_CHANNEL = "jarvis_overlay"
        private const val NOTIF_ID = 1002

        const val ACTION_SHOW = "com.jarvis.assistant.OVERLAY_SHOW"
        const val ACTION_HIDE = "com.jarvis.assistant.OVERLAY_HIDE"
        const val ACTION_STOP = "com.jarvis.assistant.OVERLAY_STOP"

        var isRunning: Boolean = false
            private set
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val overlayWindowManager by lazy { OverlayWindowManager(this) }
    private var composeView: ComposeView? = null
    private var windowParams: android.view.WindowManager.LayoutParams? = null
    private var isViewAttached = false

    private val overlayState = mutableStateOf(FloatingAssistantState())
    private val coroutineScope = CoroutineScope(AndroidUiDispatcher.Main)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupVoiceCallbacks()
        Log.i(TAG, "JarvisOverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_STOP -> {
                hideOverlay()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun setupVoiceCallbacks() {
        JarvisForegroundService.onStateChanged = { state ->
            overlayState.value = overlayState.value.copy(
                voiceState = state,
                isThinking = state == VoiceState.PROCESSING
            )
        }
        JarvisForegroundService.onUtterance = { text ->
            overlayState.value = overlayState.value.copy(userQuery = text)
        }
    }

    private fun showOverlay() {
        if (isViewAttached) return
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val params = overlayWindowManager.createLayoutParams(x = 40, y = 200)
        windowParams = params

        val view = ComposeView(this).apply {
            setContent {
                com.jarvis.assistant.ui.theme.JarvisTheme {
                    JarvisFloatingOverlay(
                        state = overlayState.value,
                        onToggleExpand = { },
                        onClose = { hideOverlay() },
                        onMicTap = { handleMicTap() },
                        onConfirm = { handleConfirm() },
                        onEdit = { handleEdit() },
                        onDrag = { dx, dy -> handleDrag(dx, dy) }
                    )
                }
            }
        }

        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)

        val recomposer = Recomposer(coroutineScope.coroutineContext)
        view.compositionContext = recomposer
        coroutineScope.launch { recomposer.runRecomposeAndApplyChanges() }

        overlayWindowManager.windowManager.addView(view, params)
        composeView = view
        isViewAttached = true

        overlayState.value = overlayState.value.copy(visible = true)
        Log.i(TAG, "Overlay shown")
    }

    private fun hideOverlay() {
        if (!isViewAttached) return
        try {
            composeView?.let { overlayWindowManager.windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed: ${e.message}")
        }
        composeView = null
        isViewAttached = false
        overlayState.value = overlayState.value.copy(visible = false)
        Log.i(TAG, "Overlay hidden")
    }

    private fun handleMicTap() {
        when (overlayState.value.voiceState) {
            VoiceState.WAKE_LISTENING, VoiceState.WAKE, VoiceState.IDLE, VoiceState.DISABLED ->
                JarvisForegroundService.startCommandListening?.invoke()
            VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> {
                // Currently listening
            }
            VoiceState.SPEAKING -> {
                JarvisForegroundService.speak?.invoke("")
            }
            else -> {}
        }
    }

    private fun handleConfirm() {
        Log.i(TAG, "Overlay: user confirmed")
    }

    private fun handleEdit() {
        Log.i(TAG, "Overlay: user tapped Edit")
    }

    private fun handleDrag(dx: Int, dy: Int) {
        val params = windowParams ?: return
        val view = composeView ?: return
        overlayWindowManager.updatePosition(params, view, dx, dy)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL, "Jarvis Overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("JARVIS Overlay")
            .setContentText("Floating assistant active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        hideOverlay()
        coroutineScope.cancel()
        JarvisForegroundService.onStateChanged = null
        JarvisForegroundService.onUtterance = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        Log.i(TAG, "JarvisOverlayService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
