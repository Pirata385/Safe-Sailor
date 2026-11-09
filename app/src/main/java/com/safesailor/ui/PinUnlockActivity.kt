package com.safesailor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.data.Repo
import com.safesailor.data.ThemeMode
import com.safesailor.ui.components.PinPromptDialog
import com.safesailor.ui.theme.SafeSailorTheme

/** Pedido de PIN mostrado pela proteção contra desinstalação, por cima das configurações. */
class PinUnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            val s by Repo.settings.collectAsStateWithLifecycle()
            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SafeSailorTheme(dark = dark, dynamicColor = s.dynamicColor) {
                PinPromptDialog(
                    title = "Safe Sailor protegido",
                    message = "Para desinstalar, parar ou desligar o Safe Sailor, digite o PIN. " +
                        "Depois você terá 2 minutos para fazer a alteração.",
                    onDismiss = { finish() },
                    onSubmit = { pin ->
                        if (Repo.checkPin(pin)) {
                            Repo.unlock()
                            finish()
                            true
                        } else {
                            false
                        }
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }
}
