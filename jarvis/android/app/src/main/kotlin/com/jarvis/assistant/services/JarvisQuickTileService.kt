package com.jarvis.assistant.services

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/**
 * Quick Settings Tile Service allowing users to toggle JARVIS background listening
 * directly from the Android status bar / notification shade.
 */
@RequiresApi(Build.VERSION_CODES.N)
class JarvisQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_ACTIVE) {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_STOP
            }
            startService(intent)
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Jarvis AI (Off)"
        } else {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Jarvis (Listening)"
        }
        tile.updateTile()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = JarvisForegroundService.isRunning
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRunning) "Jarvis (Active)" else "Jarvis AI"
        tile.updateTile()
    }
}
