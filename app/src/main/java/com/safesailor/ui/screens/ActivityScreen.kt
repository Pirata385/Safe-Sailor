@file:OptIn(ExperimentalMaterial3Api::class)

package com.safesailor.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.data.ActivityLog
import com.safesailor.data.LogEntry
import com.safesailor.data.LogType
import com.safesailor.data.Repo
import com.safesailor.ui.LocalAppController
import com.safesailor.ui.components.EmptyState
import com.safesailor.ui.components.IconBadge
import com.safesailor.ui.components.ScreenHeader
import com.safesailor.ui.components.SettingRow
import com.safesailor.ui.components.SoftCard
import com.safesailor.util.DomainUtils
import com.safesailor.vpn.VpnStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR"))

@Composable
fun ActivityScreen() {
    val c = LocalAppController.current
    val log by ActivityLog.entries.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    val running by VpnStatus.running.collectAsStateWithLifecycle()
    var onlyBlocked by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<LogEntry?>(null) }
    val items = remember(log, onlyBlocked) { if (onlyBlocked) log.filter { it.type.isBlock } else log }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Atividade", "Sites consultados recentemente") {
            IconButton(onClick = { ActivityLog.clear(); c.showMessage("Registro limpo") }, enabled = log.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, "Limpar registro")
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = onlyBlocked, onClick = { onlyBlocked = true }, label = { Text("Bloqueados") })
            FilterChip(selected = !onlyBlocked, onClick = { onlyBlocked = false }, label = { Text("Tudo") })
        }
        when {
            !settings.logActivity -> EmptyState(
                Icons.Rounded.History,
                "Registro desligado",
                "Ative \"Registrar atividade\" em Ajustes para ver os sites consultados.",
            )
            items.isEmpty() -> EmptyState(
                Icons.Rounded.History,
                if (onlyBlocked) "Nenhum bloqueio ainda" else "Nenhuma atividade ainda",
                if (running) "Quando um site for bloqueado, ele aparecerá aqui." else "Ative a proteção na tela inicial para começar.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                items(items, key = { it.id }) { entry ->
                    ActivityRow(entry, Modifier.animateItem()) { selected = entry }
                }
            }
        }
    }

    selected?.let { entry ->
        EntrySheet(entry, onDismiss = { selected = null })
    }
}

@Composable
private fun typeVisual(type: LogType): Triple<ImageVector, Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (type) {
        LogType.ALLOWED -> Triple(Icons.Rounded.CheckCircle, s.onSurfaceVariant, s.surfaceContainerHighest)
        LogType.SAFESEARCH -> Triple(Icons.Rounded.TravelExplore, s.tertiary, s.tertiaryContainer)
        else -> Triple(Icons.Rounded.Block, s.error, s.errorContainer)
    }
}

@Composable
private fun ActivityRow(entry: LogEntry, modifier: Modifier, onClick: () -> Unit) {
    val (icon, tint, bg) = typeVisual(entry.type)
    Column(modifier) {
        ListItem(
            headlineContent = { Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(timeFmt.format(Date(entry.time))) },
            leadingContent = { IconBadge(icon, tint = tint, background = bg) },
            trailingContent = {
                Surface(color = bg, shape = MaterialTheme.shapes.small) {
                    Text(
                        entry.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        )
        HorizontalDivider(Modifier.padding(start = 76.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EntrySheet(entry: LogEntry, onDismiss: () -> Unit) {
    val c = LocalAppController.current
    val context = LocalContext.current
    val root = DomainUtils.rootDomain(entry.domain)
    val exact = DomainUtils.normalize(entry.domain) ?: entry.domain
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(entry.domain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.type.label} às ${timeFmt.format(Date(entry.time))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SoftCard(Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (!entry.type.isBlock) {
                        SettingRow(Icons.Rounded.Block, "Bloquear $exact", "Inclui subdomínios", onClick = {
                            Repo.addBlocked(listOf(exact)); c.showMessage("$exact bloqueado"); onDismiss()
                        })
                        if (root != exact) {
                            SettingRow(Icons.Rounded.Block, "Bloquear todo $root", "Bloqueia o domínio principal", onClick = {
                                Repo.addBlocked(listOf(root)); c.showMessage("$root bloqueado"); onDismiss()
                            })
                        }
                    } else {
                        SettingRow(Icons.Rounded.VerifiedUser, "Sempre permitir $exact", "Use se o bloqueio foi um engano", onClick = {
                            onDismiss()
                            c.guard { Repo.addAllowed(listOf(exact)); c.showMessage("$exact liberado") }
                        })
                    }
                    SettingRow(Icons.Rounded.ContentCopy, "Copiar endereço", onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("domínio", entry.domain))
                        c.showMessage("Endereço copiado")
                        onDismiss()
                    })
                }
            }
        }
    }
}
