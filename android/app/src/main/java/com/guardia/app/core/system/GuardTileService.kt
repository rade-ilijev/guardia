package com.guardia.app.core.system

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.guardia.app.core.guard.GuardController
import com.guardia.app.core.guard.StopGuardActivity
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile to start/stop guarding from the system shade.
 *
 * Starting is one tap. Stopping requires the real PIN ([StopGuardActivity]) so an intruder
 * holding the unlocked phone can't disable protection from the shade.
 */
@AndroidEntryPoint
class GuardTileService : TileService() {

    @Inject lateinit var prefs: AppPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (GuardController.isProtected) {
            launchPinGate()
        } else {
            GuardController.start(this)
            // Persist so guarding re-arms after a reboot, matching the dashboard toggle.
            scope.launch { prefs.setGuardingEnabled(true) }
        }
        updateTile()
    }

    @Suppress("DEPRECATION") // Intent overload is the only option below API 34.
    private fun launchPinGate() {
        val intent = Intent(this, StopGuardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (GuardController.isProtected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
