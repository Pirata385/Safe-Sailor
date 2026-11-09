@file:OptIn(ExperimentalMaterial3Api::class)

package com.safesailor.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.guard.UninstallGuardService
import com.safesailor.BuildConfig
import com.safesailor.data.ActivityLog
import com.safesailor.data.AdultBlocklist
import com.safesailor.data.DnsProvider
import com.safesailor.data.Repo
import com.safesailor.data.ThemeMode
import com.safesailor.ui.LocalAppController
import com.safesailor.ui.components.IconBadge
import com.safesailor.ui.components.PinSetupDialog
import com.safesailor.ui.components.ScreenHeader
import com.safesailor.ui.components.SectionTitle
import com.safesailor.ui.components.SettingRow
import com.safesailor.ui.components.SoftCard
import com.safesailor.ui.components.SwitchRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val c = LocalAppController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.settings.collectAsStateWithLifecycle()
    val adultCount by AdultBlocklist.count.collectAsStateWithLifecycle()
    var showDns by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var showGuard by remember { mutableStateOf(false) }
    var guardEnabled by remember { mutableStateOf(UninstallGuardService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        guardEnabled = UninstallGuardService.isEnabled(context)
        onPauseOrDispose {}
    }

    /** Ligar um filtro é livre; desligar (afrouxar) pede o PIN. */
    fun toggle(newValue: Boolean, apply: () -> Unit) = if (newValue) apply() else c.guard(apply)

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Ajustes", "Personalize a proteção e o app") }

        item { SectionTitle("Aparência") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SettingRow(Icons.Rounded.DarkMode, "Tema", "Escolha entre claro, escuro ou seguir o sistema")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { Repo.update { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        SwitchRow(
                            Icons.Rounded.Palette,
                            "Cores do papel de parede",
                            "Usa as cores do sistema (Material You) no lugar do azul",
                            checked = s.dynamicColor,
                        ) { v -> Repo.update { it.copy(dynamicColor = v) } }
                    }
                }
            }
        }

        item { SectionTitle("Filtros") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SwitchRow(
                        Icons.Rounded.VisibilityOff,
                        "Bloquear conteúdo adulto",
                        if (adultCount == 0) "Carregando lista…" else "Lista com ${formatNumber(adultCount)} sites pornográficos",
                        checked = s.adultFilter,
                    ) { v -> toggle(v) { Repo.update { it.copy(adultFilter = v) } } }
                    SwitchRow(
                        Icons.Rounded.Abc,
                        "Filtro por palavras-chave",
                        "Bloqueia endereços com termos como \"porn\" ou \"xxx\"",
                        checked = s.keywordFilter,
                        enabled = s.adultFilter,
                    ) { v -> toggle(v) { Repo.update { it.copy(keywordFilter = v) } } }
                    SwitchRow(
                        Icons.Rounded.TravelExplore,
                        "Busca segura",
                        "Força o SafeSearch no Google, Bing e DuckDuckGo",
                        checked = s.safeSearch,
                    ) { v -> toggle(v) { Repo.update { it.copy(safeSearch = v) } } }
                    SwitchRow(
                        Icons.Rounded.SmartDisplay,
                        "Modo restrito do YouTube",
                        "Oculta vídeos potencialmente impróprios",
                        checked = s.youtubeRestricted,
                    ) { v -> toggle(v) { Repo.update { it.copy(youtubeRestricted = v) } } }
                    SwitchRow(
                        Icons.Rounded.Route,
                        "Impedir desvios",
                        "Bloqueia \"DNS seguro\" de navegadores, que ignoraria o filtro",
                        checked = s.blockBypass,
                    ) { v -> toggle(v) { Repo.update { it.copy(blockBypass = v) } } }
                    SettingRow(
                        Icons.Rounded.Dns,
                        "Servidor DNS",
                        s.dnsProvider.label,
                        onClick = { showDns = true },
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    SettingRow(
                        Icons.Rounded.Refresh,
                        "Atualizar lista adulta",
                        if (s.adultListUpdatedAt > 0) {
                            "Atualizada em " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(s.adultListUpdatedAt))
                        } else {
                            "Usando a lista que veio com o app"
                        },
                        enabled = !updating,
                        onClick = {
                            updating = true
                            scope.launch {
                                val r = AdultBlocklist.update(context)
                                updating = false
                                c.showMessage(
                                    r.fold(
                                        { "Lista atualizada: ${formatNumber(it)} sites" },
                                        { "Falha ao atualizar. Verifique sua conexão." },
                                    ),
                                )
                            }
                        },
                    ) { if (updating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                }
            }
        }

        item { SectionTitle("Segurança") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SettingRow(
                        if (s.hasPin) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        if (s.hasPin) "Alterar PIN" else "Proteger com PIN",
                        if (s.hasPin) "PIN ativo: necessário para desligar ou afrouxar a proteção"
                        else "Impede que outras pessoas desliguem a proteção",
                        onClick = { c.guardStrict("Digite o PIN atual para alterá-lo.") { showPinSetup = true } },
                    )
                    if (s.hasPin) {
                        SettingRow(Icons.Rounded.LockOpen, "Remover PIN", onClick = {
                            c.guardStrict("Digite o PIN para removê-lo.") { Repo.clearPin(); c.showMessage("PIN removido") }
                        })
                    }
                    SettingRow(
                        Icons.Rounded.AdminPanelSettings,
                        "Proteção contra desinstalação",
                        when {
                            !s.hasPin -> "Defina um PIN para usar"
                            guardEnabled -> "Ativa: desinstalar ou parar o app nas configurações exige o PIN"
                            else -> "Desativada · toque para configurar"
                        },
                        enabled = s.hasPin,
                        onClick = { showGuard = true },
                    ) {
                        if (s.hasPin) {
                            Text(
                                if (guardEnabled) "Ativa" else "Ativar",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (guardEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    SwitchRow(
                        Icons.Rounded.PowerSettingsNew,
                        "Iniciar com o aparelho",
                        "Religa a proteção automaticamente ao ligar o celular",
                        checked = s.startOnBoot,
                    ) { v -> toggle(v) { Repo.update { it.copy(startOnBoot = v) } } }
                    SettingRow(
                        Icons.Rounded.VpnLock,
                        "VPN sempre ativa",
                        "Abra as configurações do Android para impedir que a proteção seja desligada",
                        onClick = { c.openSystemScreen(Intent(Settings.ACTION_VPN_SETTINGS)) },
                    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        item { SectionTitle("Atividade e dados") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SwitchRow(
                        Icons.Rounded.History,
                        "Registrar atividade",
                        "Mostra os sites consultados (fica só no aparelho)",
                        checked = s.logActivity,
                    ) { v -> Repo.update { it.copy(logActivity = v) }; if (!v) ActivityLog.clear() }
                    SwitchRow(
                        Icons.Rounded.Visibility,
                        "Registrar sites permitidos",
                        "Além dos bloqueios, lista todos os acessos",
                        checked = s.logAllowed,
                        enabled = s.logActivity,
                    ) { v -> Repo.update { it.copy(logAllowed = v) } }
                    SettingRow(Icons.Rounded.BarChart, "Zerar estatísticas", onClick = {
                        c.guard { Repo.resetStats(); c.showMessage("Estatísticas zeradas") }
                    })
                    SettingRow(Icons.Rounded.DeleteSweep, "Limpar registro de atividade", onClick = {
                        ActivityLog.clear(); c.showMessage("Registro limpo")
                    })
                }
            }
        }

        item { SectionTitle("Sobre") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SettingRow(Icons.Rounded.VpnLock, "Como funciona", "Entenda o filtro e suas limitações", onClick = { showHelp = true })
                    SettingRow(Icons.Rounded.Info, "Safe Sailor", "Versão ${BuildConfig.VERSION_NAME}", onClick = { showAbout = true })
                    SettingRow(Icons.Rounded.DeleteForever, "Desinstalar o Safe Sailor", "Exige o PIN, se houver um", onClick = {
                        c.guardStrict("Digite o PIN para desinstalar o Safe Sailor.") {
                            Repo.unlock()
                            Repo.allowLeavingUnlocked()
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) {
                                c.showMessage("Não foi possível abrir a desinstalação")
                            }
                        }
                    })
                }
            }
        }
    }

    if (showDns) {
        DnsDialog(
            current = s.dnsProvider,
            onDismiss = { showDns = false },
            onSelect = { p ->
                showDns = false
                if (p != s.dnsProvider) c.guard { Repo.update { it.copy(dnsProvider = p) }; c.showMessage("DNS alterado para ${p.label}") }
            },
        )
    }
    if (showPinSetup) {
        PinSetupDialog(onDismiss = { showPinSetup = false }, onConfirm = { pin ->
            Repo.setPin(pin)
            showPinSetup = false
            c.showMessage("PIN definido")
        })
    }
    if (showGuard) {
        GuardDialog(
            enabled = guardEnabled,
            onDismiss = { showGuard = false },
            onOpenAccessibility = {
                showGuard = false
                c.openSystemScreen(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenAppInfo = {
                showGuard = false
                c.openSystemScreen(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
    if (showHelp) {
        InfoDialog("Como funciona", onDismiss = { showHelp = false }) {
            listOf(
                "O Safe Sailor cria uma VPN local no seu aparelho. Ela não envia seu tráfego para nenhum servidor: apenas verifica o endereço de cada site antes da conexão.",
                "Se o site estiver na sua lista de bloqueio ou na lista de conteúdo adulto, ele simplesmente não abre. Os demais endereços são consultados no servidor DNS escolhido.",
                "Para máxima proteção: defina um PIN, ative a proteção contra desinstalação e ligue \"VPN sempre ativa\" e \"Bloquear conexões sem VPN\" nas configurações do Android.",
                "Limitações: apps com VPN própria ou navegadores com \"DNS seguro\" configurado manualmente podem ignorar o filtro. A opção \"Impedir desvios\" reduz isso. Outra VPN ativa desliga o Safe Sailor.",
            )
        }
    }
    if (showAbout) {
        InfoDialog("Safe Sailor ${BuildConfig.VERSION_NAME}", onDismiss = { showAbout = false }) {
            listOf(
                "Bloqueador de sites simples e privado. Nenhum dado pessoal é coletado e o registro de atividade fica apenas no seu aparelho.",
                "Lista de conteúdo adulto: projeto StevenBlack/hosts (extensão \"porn\"), licença MIT.",
            )
        }
    }
}

@Composable
private fun GuardDialog(enabled: Boolean, onDismiss: () -> Unit, onOpenAccessibility: () -> Unit, onOpenAppInfo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
        title = { Text("Proteção contra desinstalação") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (enabled) {
                    Text(
                        "Ativa. Ao tentar desinstalar, forçar a parada, desconectar a VPN ou desligar este serviço " +
                            "nas configurações do Android, o Safe Sailor fecha a tela e pede o PIN. " +
                            "Com o PIN correto, você tem 2 minutos para fazer a alteração.",
                    )
                    Text(
                        "Para desativá-la, toque em \"Abrir acessibilidade\" (pede o PIN) e desligue o Safe Sailor.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "O Android não permite que um app bloqueie a própria desinstalação. Como alternativa, o Safe Sailor " +
                            "usa um serviço de acessibilidade: ele percebe as telas do sistema que desinstalam ou desligam o app " +
                            "e exige o PIN antes de continuar.",
                    )
                    Text("Como ativar:", style = MaterialTheme.typography.titleSmall)
                    Text("1. Toque em \"Abrir acessibilidade\".")
                    Text("2. Procure \"Safe Sailor\" (às vezes em \"Apps instalados\" ou \"Serviços baixados\") e ative.")
                    if (Build.VERSION.SDK_INT >= 33) {
                        Text(
                            "Se aparecer \"Configuração restrita\": toque em \"Informações do app\", abra o menu ⋮ no canto e " +
                                "escolha \"Permitir configurações restritas\". Depois volte ao passo 1.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "O serviço só procura telas que mencionam o Safe Sailor. Nada é guardado nem enviado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onOpenAccessibility) { Text("Abrir acessibilidade") } },
        dismissButton = {
            Row {
                if (!enabled && Build.VERSION.SDK_INT >= 33) TextButton(onClick = onOpenAppInfo) { Text("Informações do app") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

@Composable
private fun DnsDialog(current: DnsProvider, onDismiss: () -> Unit, onSelect: (DnsProvider) -> Unit) {
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Dns, null) },
        title = { Text("Servidor DNS") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Endereços que não estão bloqueados são consultados neste servidor. Os de \"Família\" adicionam outra camada de filtro adulto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                DnsProvider.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == p, onClick = { choice = p }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == p, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.bodyLarge)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSelect(choice) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun InfoDialog(title: String, onDismiss: () -> Unit, paragraphs: () -> List<String>) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(Icons.Rounded.Info) },
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                paragraphs().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}
