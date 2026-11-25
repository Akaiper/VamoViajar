package com.aline.viagens.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Activity
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Days
import com.aline.viagens.data.Repo
import com.aline.viagens.data.TripState
import kotlinx.coroutines.flow.flowOf

/** Criar (activityId = null) ou editar uma atividade do roteiro. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityFormScreen(
    tripId: String,
    activityId: String?,
    initialDay: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val editing = activityId != null
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val existing by remember(tripId, activityId) {
        activityId?.let { Repo.activity(tripId, it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("passeio") }
    var day by rememberSaveable { mutableStateOf(initialDay) }
    var originalDay by rememberSaveable { mutableStateOf(initialDay) }
    var time by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var dayMenu by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        val a = existing
        if (editing && !loaded && a != null) {
            title = a.title; category = a.category; day = a.day; originalDay = a.day
            time = a.time; place = a.place; notes = a.notes
            loaded = true
        }
    }

    val trip = (tripState as? TripState.Found)?.trip
    val days = trip?.let { Days.of(it.startDate, it.endDate) }.orEmpty()

    fun dayText(id: String): String {
        if (id.isBlank()) return "Ideia sem dia"
        val i = days.indexOfFirst { it.toString() == id }
        val d = Dates.parse(id) ?: return id
        return if (i >= 0) "Dia ${i + 1} · ${Days.label(d)}" else "${Days.label(d)} (fora das datas)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar atividade" else "Nova atividade") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (editing) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Apagar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; error = null },
                label = { Text("O que vão fazer?") },
                placeholder = { Text("Ex.: Trilha da Lagoinha do Leste") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("Categoria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c.id,
                        onClick = { category = c.id },
                        label = { Text(c.label) },
                        leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldLabel("Quando")
            Box {
                OutlinedButton(onClick = { dayMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(dayText(day))
                }
                DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                    DropdownMenuItem(text = { Text("Ideia sem dia") }, onClick = { day = ""; dayMenu = false })
                    days.forEach { d ->
                        DropdownMenuItem(text = { Text(dayText(d.toString())) }, onClick = { day = d.toString(); dayMenu = false })
                    }
                }
            }
            if (day.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { timeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (time.isBlank()) "Sem horário" else "Às $time")
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = place, onValueChange = { place = it },
                label = { Text("Local (opcional)") },
                placeholder = { Text("Nome, endereço ou link do Maps") },
                singleLine = true,
                trailingIcon = {
                    if (place.isNotBlank()) {
                        IconButton(onClick = { openInMaps(context, place) }) { Icon(Icons.Outlined.Map, "Ver no mapa") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Observações (opcional)") },
                placeholder = { Text("Preço, horário de funcionamento, o que levar...") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "Dê um nome para a atividade."
                    } else {
                        Repo.saveActivity(
                            tripId,
                            Activity(
                                id = activityId.orEmpty(),
                                title = title,
                                day = day,
                                time = if (day.isBlank()) "" else time,
                                place = place,
                                notes = notes,
                                category = category,
                            ),
                            movedDay = editing && day != originalDay,
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing) "Salvar" else "Adicionar") }

            existing?.createdByName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Adicionado por $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (timeDialog) {
        val parts = time.split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("Horário") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(state.hour, state.minute)
                    timeDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { time = ""; timeDialog = false }) { Text("Sem horário") }
            },
        )
    }

    if (confirmDelete && activityId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar atividade?") },
            text = { Text("Ela some do roteiro de todo mundo da viagem.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteActivity(tripId, activityId)
                    onDone()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
