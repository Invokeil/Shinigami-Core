package com.invokeil.shinigami.service

import android.annotation.SuppressLint
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.invokeil.shinigami.MainActivity
import com.invokeil.shinigami.R

/**
 * Quick Settings tile (MASTER SPEC §60): "Ask Shini" — opens the assistant
 * and starts listening immediately.
 */
class ShinigamiTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_VOICE_LAUNCH, true)
        }
        startActivityAndCollapseCompat(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val pending = android.app.PendingIntent.getActivity(
                this,
                2001,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
