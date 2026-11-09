package com.safesailor.vpn

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStatus {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun set(context: Context, running: Boolean) {
        _running.value = running
        try {
            TileService.requestListeningState(context, ComponentName(context, ProtectionTileService::class.java))
        } catch (_: Exception) {
        }
    }
}
