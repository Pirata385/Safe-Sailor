package com.safesailor.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.safesailor.data.Repo

/** Religa a proteção ao iniciar o aparelho ou após atualizar o app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Repo.init(context)
        val s = Repo.settings.value
        val wanted = s.protectionEnabled && (s.startOnBoot || action == Intent.ACTION_MY_PACKAGE_REPLACED)
        if (wanted && VpnService.prepare(context) == null) {
            try {
                DnsVpnService.start(context)
            } catch (e: Exception) {
                Log.w("BootReceiver", "Não foi possível iniciar", e)
            }
        }
    }
}
