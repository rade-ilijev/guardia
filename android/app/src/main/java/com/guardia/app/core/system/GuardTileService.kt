package com.guardia.app.core.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.guardia.app.core.guard.GuardController

/**
 * Quick Settings tile to start/stop guarding from the system shade.
 *
 * Scaffold note: stopping should require the real PIN (added in Phase 1). For now it
 * toggles state directly.
 */
class GuardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        GuardController.toggle(this)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (GuardController.isProtected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
