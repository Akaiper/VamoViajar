package com.aline.viagens.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.CheckItem
import com.aline.viagens.data.ChecklistTemplate
import com.aline.viagens.data.Repo
import com.aline.viagens.data.Trip
import com.aline.viagens.data.TripState
import com.aline.viagens.data.sortedForList

// ===================== Lista de listas =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsScreen(tripId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val lists by remember(tripId) { Repo.checklists(tripId) }.collectAsStateWithLifecycle(initialValue = null)
    var creating by remember { mutableStateOf(false) }

    if (tripState is TripState.Missing) LaunchedEffect(Unit) { onBack() }
    val trip = (tripState as? TripState.Found)?.trip

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Checklist")
                        trip?.let {
                            Text(
                                it.name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nova lista") },
            )
        },
    ) { pad ->
        val all = lists
        when {
            all == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            all.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.Checklist, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Nenhuma lista ainda", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Crie listas para a mala, documentos ou o que precisa resolver antes de viajar.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val shared = all.filter { !it.personal }
                val mine = all.filter { it.personal }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (shared.isNotEmpty()) {
                        item(key = "h-shared") { SectionTitle("Do grupo") }
                        items(shared, key = { it.id }) { ListCard(tripId, it) { onOpen(it.id) } }
                    }
                    if (mine.isNotEmpty()) {
                        item(key = "h-mine") { SectionTitle("Só para você", Modifier.padding(top = 8.dp)) }
                        items(mine, key = { it.id }) { ListCard(tripId, it) { onOpen(it.id) } }
                    }
                }
            }
        }
    }

    if (creating) {
        NewListDialog(
            onDismiss = { creating = false },
            onCreate = { name, personal, template ->
                creating = false
                onOpen(Repo.createChecklist(tripId, name, personal, template.items))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListCard(tripId: String, list: com.aline.viagens.data.Checklist, onClick: () -> Unit) {
    val items by remember(tripId, list.id) { Repo.items(tripId, list.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val done = items.count { it.done }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (list.personal) Icon(Icons.Outlined.Lock, "Só você vê", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    items.isEmpty() -> "Lista vazia"
                    done == items.size -> "Tudo pronto! ${items.size} de ${items.size}"
                    else -> "$done de ${items.size} feitos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / items.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, Boolean, ChecklistTemplate) -> Unit) {
    var template by remember { mutableStateOf(ChecklistTemplate.VAZIA) }
    var name by remember { mutableStateOf("") }
    var personal by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova lista") },
        text = {
            Column {
                Text("Começar com", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChecklistTemplate.entries.forEach { t ->
                        FilterChip(
                            selected = template == t,
                            onClick = {
                                // Se a pessoa ainda não escreveu um nome, usa o do modelo
                                if (name.isBlank() || ChecklistTemplate.entries.any { it.label == name }) {
                                    name = if (t == ChecklistTemplate.VAZIA) "" else t.label
                                }
                                template = t
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
                if (template != ChecklistTemplate.VAZIA) {
                    Text(
                        "${template.items.size} itens sugeridos, dá para editar depois.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome da lista") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Quem vê", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !personal, onClick = { personal = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("O grupo") }
                    SegmentedButton(
                        selected = personal, onClick = { personal = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Só eu") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (personal) "Ninguém mais da viagem vê esta lista." else "Todos da viagem veem e podem marcar os itens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, personal, template) }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ===================== Uma lista =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(tripId: String, listId: String, onBack: () -> Unit) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val list by remember(tripId, listId) { Repo.checklist(tripId, listId) }.collectAsStateWithLifecycle(initialValue = null)
    val allItems by remember(tripId, listId) { Repo.items(tripId, listId) }.collectAsStateWithLifecycle(initialValue = emptyList())

    var newText by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CheckItem?>(null) }

    val trip = (tripState as? TripState.Found)?.trip
    val sorted = allItems.sortedForList()
    val done = allItems.count { it.done }

    fun add() {
        if (newText.isNotBlank()) {
            Repo.addItem(tripId, listId, newText)
            newText = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(list?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (allItems.isNotEmpty()) {
                            Text(
                                "$done de ${allItems.size} feitos" + if (list?.personal == true) " · só você vê" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mais opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Renomear lista") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; renaming = true },
                            )
                            if (done > 0) {
                                DropdownMenuItem(
                                    text = { Text("Desmarcar tudo") },
                                    leadingIcon = { Icon(Icons.Outlined.RemoveDone, null) },
                                    onClick = {
                                        menu = false
                                        Repo.uncheckAll(tripId, listId, allItems.filter { it.done }.map { it.id })
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Apagar lista") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it },
                    placeholder = { Text("Adicionar item") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { add() }, enabled = newText.isNotBlank()) { Icon(Icons.Filled.Add, "Adicionar") }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
                items(sorted, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        showAssignee = list?.personal != true,
                        onToggle = { Repo.setItemDone(tripId, listId, item.id, !item.done) },
                        onEdit = { editing = item },
                    )
                }
                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Lista vazia. Escreva um item acima e toque em +.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            trip = trip,
            showAssignee = list?.personal != true,
            onDismiss = { editing = null },
            onSave = { text, assigneeId, assigneeName ->
                Repo.updateItem(tripId, listId, item.id, text, assigneeId, assigneeName)
                editing = null
            },
            onDelete = {
                Repo.deleteItem(tripId, listId, item.id)
                editing = null
            },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(list?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renomear lista") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    Repo.renameChecklist(tripId, listId, name)
                    renaming = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${list?.name.orEmpty()}\"?") },
            text = {
                Text(
                    if (list?.personal == true) "A lista e todos os itens dela serão apagados. Isso não pode ser desfeito."
                    else "A lista some para todas as pessoas da viagem. Isso não pode ser desfeito."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteChecklist(tripId, listId)
                    onBack()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ItemRow(item: CheckItem, showAssignee: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val extra = listOfNotNull(
                item.assigneeName.takeIf { showAssignee && it.isNotBlank() }?.let { "Com $it" },
                item.doneByName.takeIf { showAssignee && item.done && it.isNotBlank() }?.let { "feito por $it" },
            )
            if (extra.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.assigneeName.isNotBlank() && showAssignee) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        extra.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditItemDialog(
    item: CheckItem,
    trip: Trip?,
    showAssignee: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, assigneeId: String, assigneeName: String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var assigneeId by remember(item.id) { mutableStateOf(item.assigneeId) }
    var assigneeName by remember(item.id) { mutableStateOf(item.assigneeName) }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar item") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (showAssignee && trip != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("Responsável", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text(assigneeName.ifBlank { "Ninguém" })
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Ninguém") }, onClick = { assigneeId = ""; assigneeName = ""; menu = false })
                            trip.members.forEach { uid ->
                                val name = trip.memberNames[uid] ?: "Viajante"
                                DropdownMenuItem(text = { Text(name) }, onClick = { assigneeId = uid; assigneeName = name; menu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Apagar item", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text, assigneeId, assigneeName) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
