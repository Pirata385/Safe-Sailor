@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.safesailor.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safesailor.data.Repo
import com.safesailor.data.SiteEntry
import com.safesailor.data.SitePacks
import com.safesailor.ui.LocalAppController
import com.safesailor.ui.components.EmptyState
import com.safesailor.ui.components.LetterAvatar
import com.safesailor.ui.components.ScreenHeader
import com.safesailor.util.DomainUtils


@Composable
fun SitesScreen() {
    val c = LocalAppController.current
    val context = LocalContext.current
    val blocked by Repo.blocked.collectAsStateWithLifecycle()
    val allowed by Repo.allowed.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = bloqueados, 1 = permitidos
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val isBlocked = mode == 0
    val source = if (isBlocked) blocked else allowed
    val list = remember(source, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) source else source.filter { it.domain.contains(q) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                source.forEach { w.write(it.domain); w.newLine() }
            }
            c.showMessage("${source.size} sites exportados")
        } catch (e: Exception) {
            c.showMessage("Não foi possível exportar")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val domains = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
                r.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
                    .map { line -> line.split(Regex("\\s+")).let { p -> if (p.size >= 2) p[1] else p[0] } }
                    .mapNotNull(DomainUtils::normalize).distinct()
            }.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        if (domains.isEmpty()) {
            c.showMessage("Nenhum endereço válido encontrado no arquivo")
        } else if (isBlocked) {
            c.showMessage("${Repo.addBlocked(domains)} sites importados")
        } else {
            c.guard { c.showMessage("${Repo.addAllowed(domains)} sites importados") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Sites", "Escolha o que bloquear e o que liberar") {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Importar lista") },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar lista") },
                            leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                            enabled = source.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch(if (isBlocked) "safesailor-bloqueados.txt" else "safesailor-permitidos.txt")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Limpar lista") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                            enabled = source.isNotEmpty(),
                            onClick = { menuOpen = false; confirmClear = true },
                        )
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = isBlocked,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { SegmentedButtonDefaults.Icon(isBlocked) { Icon(Icons.Rounded.Block, null) } },
                ) { Text("Bloqueados (${blocked.size})") }
                SegmentedButton(
                    selected = !isBlocked,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { SegmentedButtonDefaults.Icon(!isBlocked) { Icon(Icons.Rounded.VerifiedUser, null) } },
                ) { Text("Permitidos (${allowed.size})") }
            }
            if (source.size > 5) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Pesquisar") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Limpar") } }) else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (list.isEmpty()) {
                if (query.isNotEmpty()) {
                    EmptyState(Icons.Rounded.Search, "Nada encontrado", "Nenhum site corresponde a \"$query\".")
                } else if (isBlocked) {
                    EmptyState(
                        Icons.Rounded.Block,
                        "Nenhum site bloqueado",
                        "Toque em \"Adicionar\" para bloquear sites ou use um pacote pronto, como redes sociais ou apostas.",
                    )
                } else {
                    EmptyState(
                        Icons.Rounded.VerifiedUser,
                        "Nenhum site permitido",
                        "Sites desta lista nunca são bloqueados — útil se algum site legítimo for filtrado por engano.",
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp)) {
                    items(list, key = { it.domain }) { entry ->
                        SiteRow(
                            entry = entry,
                            showSwitch = isBlocked,
                            onToggle = { enabled ->
                                if (enabled) Repo.setBlockedEnabled(entry.domain, true)
                                else c.guard { Repo.setBlockedEnabled(entry.domain, false) }
                            },
                            onDelete = {
                                if (isBlocked) c.guard {
                                    Repo.removeBlocked(entry.domain)
                                    c.showMessage("${entry.domain} removido")
                                } else {
                                    Repo.removeAllowed(entry.domain)
                                    c.showMessage("${entry.domain} removido")
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Adicionar") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (showAdd) {
        AddSitesDialog(
            blockedMode = isBlocked,
            onDismiss = { showAdd = false },
            onAdd = { domains ->
                showAdd = false
                if (isBlocked) {
                    val n = Repo.addBlocked(domains)
                    c.showMessage(if (n == 0) "Já estavam na lista" else if (n == 1 && domains.size == 1) "${domains[0]} bloqueado" else "$n sites bloqueados")
                } else {
                    c.guard {
                        val n = Repo.addAllowed(domains)
                        c.showMessage(if (n == 0) "Já estavam na lista" else "$n sites permitidos")
                    }
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Limpar lista?") },
            text = { Text(if (isBlocked) "Todos os ${blocked.size} sites bloqueados serão removidos." else "Todos os ${allowed.size} sites permitidos serão removidos.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    if (isBlocked) c.guard { Repo.clearBlocked(); c.showMessage("Lista limpa") }
                    else { Repo.clearAllowed(); c.showMessage("Lista limpa") }
                }) { Text("Limpar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SiteRow(entry: SiteEntry, showSwitch: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        ListItem(
            headlineContent = { Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(if (entry.enabled) "Inclui subdomínios" else "Pausado", maxLines = 1)
            },
            leadingContent = {
                LetterAvatar(
                    entry.domain,
                    background = if (entry.enabled) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    foreground = if (entry.enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showSwitch) Switch(checked = entry.enabled, onCheckedChange = onToggle)
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Remover", tint = scheme.onSurfaceVariant) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = scheme.background),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HorizontalDivider(Modifier.padding(start = 76.dp, end = 20.dp), color = scheme.outlineVariant)
    }
}

private fun packIcon(name: String): ImageVector = when (name) {
    "Redes sociais" -> Icons.Rounded.Groups
    "Apostas" -> Icons.Rounded.Casino
    "Jogos online" -> Icons.Rounded.SportsEsports
    else -> Icons.Rounded.SmartDisplay
}

@Composable
private fun AddSitesDialog(blockedMode: Boolean, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { DomainUtils.parseMany(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (blockedMode) "Bloquear sites" else "Permitir sites") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Endereços") },
                    placeholder = { Text("instagram.com, tiktok.com") },
                    supportingText = {
                        Text(if (parsed.isEmpty()) "Separe vários por vírgula ou linha" else "${parsed.size} endereço(s) válido(s)")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                if (blockedMode) {
                    Text("Pacotes prontos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SitePacks.all.forEach { pack ->
                            AssistChip(
                                onClick = { onAdd(pack.domains) },
                                label = { Text(pack.name) },
                                leadingIcon = { Icon(packIcon(pack.name), null, Modifier.size(AssistChipDefaults.IconSize)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(0.dp))
                }
            }
        },
        confirmButton = { Button(onClick = { onAdd(parsed) }, enabled = parsed.isNotEmpty()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
