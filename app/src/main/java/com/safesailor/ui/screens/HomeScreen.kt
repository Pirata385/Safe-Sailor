package com.safesailor.ui.screens

import android.content.Intent
import android.provider.Settings
import android.net.VpnService
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.safesailor.R
import com.safesailor.guard.UninstallGuard
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.data.AdultBlocklist
import com.safesailor.data.Repo
import com.safesailor.data.Stats
import com.safesailor.ui.LocalAppController
import com.safesailor.ui.TAB_SETTINGS
import com.safesailor.ui.TAB_SITES
import com.safesailor.ui.components.IconBadge
import com.safesailor.ui.components.SoftCard
import com.safesailor.util.DomainUtils
import com.safesailor.vpn.VpnStatus
import java.text.NumberFormat
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
fun formatNumber(n: Number): String = NumberFormat.getIntegerInstance(ptBR).format(n)

@Composable
fun HomeScreen() {
    val c = LocalAppController.current
    val running by VpnStatus.running.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    val stats by Repo.stats.collectAsStateWithLifecycle()
    val blocked by Repo.blocked.collectAsStateWithLifecycle()
    val adultCount by AdultBlocklist.count.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A proteção deveria estar ligada, mas caiu e precisa da permissão de VPN de novo.
    val interrupted = remember(running, settings.protectionEnabled) {
        settings.protectionEnabled && !running && VpnService.prepare(context) != null
    }
    var guardEnabled by remember { mutableStateOf(UninstallGuard.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        guardEnabled = UninstallGuard.isEnabled(context)
        onPauseOrDispose {}
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Safe Sailor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Navegue com tranquilidade",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (interrupted) {
            item { InterruptedCard { c.setProtection(true) } }
        }
        if (!settings.onboardingDone) {
            item { WelcomeCard { Repo.update { it.copy(onboardingDone = true) } } }
        }
        item { ShieldCard(running) { c.setProtection(!running) } }
        item { StatsRow(stats) }
        item { QuickBlockCard() }
        item {
            SoftCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "Resumo da proteção",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    StatusLine(
                        Icons.Rounded.VisibilityOff,
                        "Conteúdo adulto",
                        if (!settings.adultFilter) "Desligado" else if (adultCount == 0) "Carregando lista…" else "Bloqueado · ${formatNumber(adultCount)} sites",
                        settings.adultFilter,
                    ) { c.navigate(TAB_SETTINGS) }
                    StatusLine(
                        Icons.Rounded.Language,
                        "Seus bloqueios",
                        "${blocked.count { it.enabled }} sites",
                        blocked.any { it.enabled },
                    ) { c.navigate(TAB_SITES) }
                    StatusLine(
                        Icons.Rounded.TravelExplore,
                        "Busca segura",
                        if (settings.safeSearch) "Google, Bing e DuckDuckGo" else "Desligada",
                        settings.safeSearch,
                    ) { c.navigate(TAB_SETTINGS) }
                    if (UninstallGuard.available) StatusLine(
                        Icons.Rounded.AdminPanelSettings,
                        "Proteção contra desinstalação",
                        when {
                            !settings.hasPin -> "Defina um PIN em Ajustes"
                            guardEnabled -> "Ativa"
                            else -> "Desativada"
                        },
                        settings.hasPin && guardEnabled,
                    ) { c.navigate(TAB_SETTINGS) }
                    StatusLine(Icons.Rounded.Dns, "Servidor DNS", settings.dnsProvider.label, true) { c.navigate(TAB_SETTINGS) }
                }
            }
        }
        if (running && !settings.alwaysOnTipDismissed) {
            item { AlwaysOnTip { Repo.update { it.copy(alwaysOnTipDismissed = true) } } }
        }
    }
}

@Composable
private fun WelcomeCard(onDismiss: () -> Unit) {
    SoftCard {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Rounded.WavingHand)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Bem-vindo!", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toque no escudo para ativar. O Android vai pedir permissão para criar uma VPN local: " +
                        "ela só filtra endereços de sites, nada sai do seu aparelho.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Fechar") }
        }
    }
}

@Composable
private fun ShieldCard(active: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val circle by animateColorAsState(if (active) scheme.primary else scheme.surfaceContainerHighest, tween(500), label = "circle")
    val iconTint by animateColorAsState(if (active) scheme.onPrimary else scheme.onSurfaceVariant, tween(500), label = "icon")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val breathe by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    SoftCard {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                if (active) {
                    val ringColor = scheme.primary
                    Box(
                        Modifier.size(200.dp).drawBehind {
                            val base = 75.dp.toPx()
                            drawCircle(ringColor.copy(alpha = (1f - ring) * 0.35f), radius = base * (1f + ring * 0.33f))
                        },
                    )
                }
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(if (active) breathe else 1f)
                        .clip(CircleShape)
                        .background(circle)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(color = Color.White),
                            onClick = onToggle,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (active) Icons.Rounded.GppGood else Icons.Rounded.Shield,
                        contentDescription = if (active) "Desativar proteção" else "Ativar proteção",
                        tint = iconTint,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (active) "Você está protegido" else "Proteção desligada",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (active) "Sites bloqueados e conteúdo adulto estão sendo filtrados."
                else "Toque no escudo para começar a filtrar sites.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (active) {
                OutlinedButton(onClick = onToggle) { Text("Desativar") }
            } else {
                Button(onClick = onToggle) { Text("Ativar proteção") }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: Stats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Bloqueios hoje", formatNumber(stats.blockedToday), Modifier.weight(1f))
        StatTile("Total bloqueado", formatNumber(stats.blockedTotal), Modifier.weight(1f))
        StatTile("Consultas", formatNumber(stats.queriesTotal), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    SoftCard(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
        }
    }
}

@Composable
private fun QuickBlockCard() {
    val c = LocalAppController.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val add = {
        val domains = DomainUtils.parseMany(text)
        if (domains.isEmpty()) {
            c.showMessage("Digite um endereço válido, como instagram.com")
        } else {
            val added = Repo.addBlocked(domains)
            c.showMessage(
                when {
                    added == 0 -> "Já estava na sua lista"
                    domains.size == 1 -> "${domains[0]} bloqueado"
                    else -> "$added sites bloqueados"
                },
            )
            text = ""
            focus.clearFocus()
        }
    }
    SoftCard {
        Column(Modifier.padding(20.dp)) {
            Text("Bloquear um site", style = MaterialTheme.typography.titleMedium)
            Text(
                "Subdomínios também serão bloqueados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("ex.: instagram.com") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = add, modifier = Modifier.size(52.dp), enabled = text.isNotBlank()) {
                    Icon(Icons.Rounded.Add, "Bloquear")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, title: String, value: String, ok: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon,
            tint = if (ok) scheme.primary else scheme.onSurfaceVariant,
            background = if (ok) scheme.primaryContainer else scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Color(0xFF1565D8)),
    ) {
        // A arte do ícone usa a área central (72 de 108); amplia para preencher o círculo.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.5f),
        )
    }
}

@Composable
private fun InterruptedCard(onRestart: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.errorContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = scheme.onErrorContainer)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Proteção interrompida", style = MaterialTheme.typography.titleMedium, color = scheme.onErrorContainer)
                Text(
                    "A VPN foi desconectada fora do app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onErrorContainer,
                )
            }
            Button(onClick = onRestart) { Text("Religar") }
        }
    }
}

@Composable
private fun AlwaysOnTip(onDismiss: () -> Unit) {
    val c = LocalAppController.current
    SoftCard {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Rounded.Lightbulb)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dica: proteção contínua", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nas configurações de VPN do Android, toque na engrenagem do Safe Sailor e ative " +
                            "\"VPN sempre ativa\" e \"Bloquear conexões sem VPN\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Agora não") }
                TextButton(onClick = { c.openSystemScreen(Intent(Settings.ACTION_VPN_SETTINGS)) }) { Text("Abrir configurações") }
            }
        }
    }
}
