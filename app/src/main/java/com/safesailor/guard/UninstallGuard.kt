package com.safesailor.guard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.safesailor.BuildConfig

/**
 * A proteção contra desinstalação (serviço de acessibilidade) só existe na edição "completa".
 * O Google Play Protect bloqueia a instalação, a partir da internet, de apps que declaram um
 * serviço de acessibilidade, por isso a edição "padrão" não o inclui.
 */
object UninstallGuard {
    /** Se esta edição do app traz a proteção contra desinstalação. */
    val available: Boolean get() = BuildConfig.UNINSTALL_GUARD

    private const val SERVICE_CLASS = "com.safesailor.guard.UninstallGuardService"

    /** Se o serviço está ligado nas configurações de acessibilidade. */
    fun isEnabled(context: Context): Boolean {
        if (!available) return false
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context.packageName, SERVICE_CLASS)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
