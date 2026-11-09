package com.safesailor.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.safesailor.data.Repo
import com.safesailor.ui.MainActivity

/** Bloco das configurações rápidas para ligar/desligar a proteção. */
class ProtectionTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        Repo.init(this)
        if (VpnStatus.running.value) {
            if (Repo.settings.value.hasPin) {
                openApp() // com PIN definido, desligar sempre exige digitá-lo no app
                return
            }
            Repo.setProtectionEnabled(false)
            DnsVpnService.stop(this)
        } else {
            if (VpnService.prepare(this) != null) {
                openApp() // precisa da permissão de VPN
                return
            }
            Repo.setProtectionEnabled(true)
            try {
                DnsVpnService.start(this)
            } catch (e: Exception) {
                openApp()
                return
            }
        }
        refresh()
    }

    // A versão com Intent só é usada abaixo do Android 14, onde é a única disponível.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = VpnStatus.running.value
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Protegido" else "Desligado"
        tile.updateTile()
    }
}
