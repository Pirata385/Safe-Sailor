package com.safesailor.ui

import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.data.Repo
import com.safesailor.data.ThemeMode
import com.safesailor.ui.theme.SafeSailorTheme
import com.safesailor.vpn.DnsVpnService
import com.safesailor.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            val settings by Repo.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SafeSailorTheme(dark = dark, dynamicColor = settings.dynamicColor) {
                SafeSailorApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Se a proteção deveria estar ligada mas caiu (ex.: app encerrado), religa sozinho
        // quando a permissão de VPN ainda vale. Caso contrário a tela inicial oferece religar.
        val s = Repo.settings.value
        if (s.protectionEnabled && !VpnStatus.running.value && VpnService.prepare(this) == null) {
            try {
                DnsVpnService.start(this)
            } catch (_: Exception) {
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Repo.saveStats()
        // Saiu do app: o PIN digitado deixa de valer (exceto ao abrir uma tela do sistema pedida aqui).
        if (!isChangingConfigurations) Repo.onAppLeft()
    }
}
