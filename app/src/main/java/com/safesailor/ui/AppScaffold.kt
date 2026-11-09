package com.safesailor.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.safesailor.data.Repo
import com.safesailor.ui.components.PinPromptDialog
import com.safesailor.ui.screens.ActivityScreen
import com.safesailor.ui.screens.HomeScreen
import com.safesailor.ui.screens.SettingsScreen
import com.safesailor.ui.screens.SitesScreen
import com.safesailor.vpn.DnsVpnService
import kotlinx.coroutines.launch

/** Ações globais disponíveis para todas as telas. */
class AppController(
    val showMessage: (String) -> Unit,
    /** Executa a ação após confirmar o PIN; aceita o PIN digitado nos últimos 2 minutos. */
    val guard: (() -> Unit) -> Unit,
    /** Executa a ação somente após digitar o PIN agora (desligar proteção, mudar PIN...). */
    val guardStrict: (String, () -> Unit) -> Unit,
    /** Abre uma tela do sistema (VPN, acessibilidade, app) liberando-a por 2 minutos. */
    val openSystemScreen: (Intent) -> Unit,
    val setProtection: (Boolean) -> Unit,
    val navigate: (Int) -> Unit,
)

private class PendingGuard(val message: String, val action: () -> Unit)

val LocalAppController = staticCompositionLocalOf<AppController> { error("AppController ausente") }

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Início", Icons.Outlined.Shield, Icons.Rounded.Shield),
    Tab("Sites", Icons.Outlined.Language, Icons.Rounded.Language),
    Tab("Atividade", Icons.Outlined.History, Icons.Rounded.History),
    Tab("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

const val TAB_HOME = 0
const val TAB_SITES = 1
const val TAB_ACTIVITY = 2
const val TAB_SETTINGS = 3

@Composable
fun SafeSailorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var pending by remember { mutableStateOf<PendingGuard?>(null) }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun startService(ctx: Context) {
        Repo.setProtectionEnabled(true)
        try {
            DnsVpnService.start(ctx)
        } catch (e: Exception) {
            Repo.setProtectionEnabled(false)
            message("Não foi possível iniciar a proteção")
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startService(context)
        else message("Permissão de VPN necessária para ativar a proteção")
    }

    fun requestVpn() {
        val intent = VpnService.prepare(context)
        if (intent == null) {
            startService(context)
        } else {
            try {
                vpnLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                message("Este aparelho não permite apps de VPN")
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestVpn()
    }

    val controller = remember {
        val guard: (() -> Unit) -> Unit = { action ->
            if (Repo.isUnlocked()) action() else pending = PendingGuard("Esta ação está protegida por PIN.", action)
        }
        val guardStrict: (String, () -> Unit) -> Unit = { message, action ->
            if (!Repo.settings.value.hasPin) action() else pending = PendingGuard(message, action)
        }
        AppController(
            showMessage = ::message,
            guard = guard,
            guardStrict = guardStrict,
            openSystemScreen = { intent ->
                guard {
                    Repo.allowLeavingUnlocked()
                    try {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        message("Configuração indisponível neste aparelho")
                    }
                }
            },
            setProtection = { enable ->
                if (enable) {
                    val needsNotif = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsNotif) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpn()
                } else {
                    guardStrict("Digite o PIN para desativar a proteção.") {
                        Repo.setProtectionEnabled(false)
                        DnsVpnService.stop(context)
                    }
                }
            },
            navigate = { tab = it },
        )
    }

    CompositionLocalProvider(LocalAppController provides controller) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(if (tab == i) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Crossfade(targetState = tab, label = "tab") { current ->
                    when (current) {
                        TAB_HOME -> HomeScreen()
                        TAB_SITES -> SitesScreen()
                        TAB_ACTIVITY -> ActivityScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }

        pending?.let { p ->
            PinPromptDialog(
                message = p.message,
                onDismiss = { pending = null },
                onSubmit = { pin ->
                    if (Repo.checkPin(pin)) {
                        Repo.unlock()
                        pending = null
                        p.action()
                        true
                    } else {
                        false
                    }
                },
            )
        }
    }
}
