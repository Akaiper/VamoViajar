package com.aline.viagens.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Booking
import com.aline.viagens.data.BookingType
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Days
import com.aline.viagens.data.Money
import com.aline.viagens.data.Repo
import com.aline.viagens.data.TravelMode
import com.aline.viagens.data.TripState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

fun bookingIcon(b: Booking): ImageVector = when (b.bookingType) {
    BookingType.PASSAGEM -> modeIcon(TravelMode.from(b.mode))
    BookingType.HOSPEDAGEM -> Icons.Outlined.Hotel
    BookingType.TRANSPORTE -> Icons.Outlined.DirectionsCar
    BookingType.INGRESSO -> Icons.Outlined.ConfirmationNumber
}

fun typeIcon(t: BookingType): ImageVector = when (t) {
    BookingType.PASSAGEM -> Icons.Outlined.Flight
    BookingType.HOSPEDAGEM -> Icons.Outlined.Hotel
    BookingType.TRANSPORTE -> Icons.Outlined.DirectionsCar
    BookingType.INGRESSO -> Icons.Outlined.ConfirmationNumber
}

fun modeIcon(m: TravelMode): ImageVector = when (m) {
    TravelMode.AVIAO -> Icons.Outlined.Flight
    TravelMode.ONIBUS -> Icons.Outlined.DirectionsBus
    TravelMode.TREM -> Icons.Outlined.Train
    TravelMode.BARCO -> Icons.Outlined.DirectionsBoat
}

private fun dateTime(date: String, time: String): String =
    listOfNotNull(Dates.parse(date)?.let { Days.label(it) }, time.takeIf { it.isNotBlank() }).joinToString(" às ")

private fun openLink(context: android.content.Context, url: String) {
    val u = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
}

// ===================== Lista de reservas =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: (BookingType) -> Unit,
) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val bookings by remember(tripId) { Repo.bookings(tripId) }.collectAsStateWithLifecycle(initialValue = null)
    var choosing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    if (tripState is TripState.Missing) LaunchedEffect(Unit) { onBack() }
    val trip = (tripState as? TripState.Found)?.trip

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Reservas")
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
                onClick = { choosing = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nova reserva") },
            )
        },
    ) { pad ->
        val all = bookings
        when {
            all == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            all.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.ConfirmationNumber, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Nenhuma reserva ainda", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Guarde passagens, hospedagens, transporte e ingressos num lugar só, com os códigos à mão.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val dated = all.filter { it.startDate.isNotBlank() }.sortedWith(compareBy({ it.startDate }, { it.startTime.ifBlank { "99" } }))
                val undated = all.filter { it.startDate.isBlank() }.sortedBy { it.title.lowercase() }
                val total = all.mapNotNull { it.price }.sum()
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (total > 0) {
                        item(key = "total") {
                            Text(
                                "Total em reservas: ${Money.format(total)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(dated, key = { it.id }) { b -> BookingCard(b, snackbar) { onOpen(b.id) } }
                    if (undated.isNotEmpty()) {
                        item(key = "h-undated") { SectionTitle("Sem data", Modifier.padding(top = 8.dp)) }
                        items(undated, key = { it.id }) { b -> BookingCard(b, snackbar) { onOpen(b.id) } }
                    }
                }
            }
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Que tipo de reserva?") },
            text = {
                Column {
                    BookingType.entries.forEach { t ->
                        ListItem(
                            headlineContent = { Text(t.label) },
                            leadingContent = { Icon(typeIcon(t), null) },
                            supportingContent = {
                                Text(
                                    when (t) {
                                        BookingType.PASSAGEM -> "Avião, ônibus, trem ou barco"
                                        BookingType.HOSPEDAGEM -> "Hotel, pousada, Airbnb"
                                        BookingType.TRANSPORTE -> "Aluguel de carro, transfer"
                                        BookingType.INGRESSO -> "Shows, museus, passeios, restaurantes"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().clickable { choosing = false; onNew(t) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingCard(b: Booking, snackbar: SnackbarHostState, onClick: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(bookingIcon(b), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        b.title.ifBlank { b.bookingType.label },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    b.summary().takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val start = dateTime(b.startDate, b.startTime)
                    val end = dateTime(b.endDate, b.endTime)
                    if (start.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (end.isNotBlank() && end != start) "$start → $end" else start,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                b.price?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(Money.format(it), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (b.code.isNotBlank() || b.link.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (b.code.isNotBlank()) {
                        AssistChip(
                            onClick = {
                                clipboard.setText(AnnotatedString(b.code))
                                scope.launch { snackbar.showSnackbar("Código copiado") }
                            },
                            label = { Text(b.code, fontFamily = FontFamily.Monospace) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, "Copiar código", Modifier.size(16.dp)) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (b.link.isNotBlank()) {
                        AssistChip(
                            onClick = { openLink(context, b.link) },
                            label = { Text("Comprovante") },
                            leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

// ===================== Formulário =====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookingFormScreen(tripId: String, bookingId: String?, initialType: BookingType, onDone: () -> Unit) {
    val context = LocalContext.current
    val editing = bookingId != null
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val existing by remember(tripId, bookingId) {
        bookingId?.let { Repo.booking(tripId, it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val trip = (tripState as? TripState.Found)?.trip

    var type by rememberSaveable { mutableStateOf(initialType.id) }
    var mode by rememberSaveable { mutableStateOf("aviao") }
    var title by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var startTime by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var paidById by rememberSaveable { mutableStateOf("") }
    var paidByName by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var payerMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        val b = existing
        if (editing && !loaded && b != null) {
            type = b.type; mode = b.mode; title = b.title; number = b.number; from = b.from; to = b.to
            address = b.address; startDate = b.startDate; startTime = b.startTime; endDate = b.endDate; endTime = b.endTime
            code = b.code; link = b.link; price = Money.toField(b.price); paidById = b.paidById; paidByName = b.paidByName
            notes = b.notes
            loaded = true
        }
    }

    val t = BookingType.from(type)
    val tripStart = trip?.startDate.orEmpty()
    val tripEnd = trip?.endDate.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar reserva" else "Nova reserva") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = { if (editing) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Apagar") } },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon(t), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(t.label, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))

            if (t == BookingType.PASSAGEM) {
                Label("Meio de transporte")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m.id,
                            onClick = { mode = m.id },
                            label = { Text(m.label) },
                            leadingIcon = { Icon(modeIcon(m), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Field(
                title, { title = it; error = null },
                when (t) {
                    BookingType.PASSAGEM -> "Companhia"
                    BookingType.HOSPEDAGEM -> "Nome do lugar"
                    BookingType.TRANSPORTE -> "Empresa ou tipo (ex.: aluguel de carro)"
                    BookingType.INGRESSO -> "Nome do ingresso ou passeio"
                },
            )

            when (t) {
                BookingType.PASSAGEM -> {
                    Field(number, { number = it }, if (mode == "aviao") "Número do voo" else "Número ou linha", caps = KeyboardCapitalization.Characters)
                    Field(from, { from = it }, "De (cidade, aeroporto ou rodoviária)")
                    Field(to, { to = it }, "Para")
                    Label("Saída")
                    DateTimeRow(startDate, startTime, { startDate = it }, { startTime = it }, defaultDate = tripStart, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Label("Chegada")
                    DateTimeRow(endDate, endTime, { endDate = it }, { endTime = it }, defaultDate = startDate.ifBlank { tripStart }, modifier = Modifier.fillMaxWidth())
                }
                BookingType.HOSPEDAGEM -> {
                    AddressField(address, { address = it }) { openInMaps(context, address, trip?.destination.orEmpty()) }
                    Label("Check-in")
                    DateTimeRow(startDate, startTime, { startDate = it }, { startTime = it }, defaultDate = tripStart, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Label("Check-out")
                    DateTimeRow(endDate, endTime, { endDate = it }, { endTime = it }, defaultDate = tripEnd, modifier = Modifier.fillMaxWidth())
                }
                BookingType.TRANSPORTE -> {
                    Field(from, { from = it }, "Local de retirada")
                    Label("Retirada")
                    DateTimeRow(startDate, startTime, { startDate = it }, { startTime = it }, defaultDate = tripStart, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Field(to, { to = it }, "Local de devolução")
                    Label("Devolução")
                    DateTimeRow(endDate, endTime, { endDate = it }, { endTime = it }, defaultDate = tripEnd, modifier = Modifier.fillMaxWidth())
                }
                BookingType.INGRESSO -> {
                    AddressField(address, { address = it }) { openInMaps(context, address, trip?.destination.orEmpty()) }
                    Label("Quando")
                    DateTimeRow(startDate, startTime, { startDate = it }, { startTime = it }, defaultDate = tripStart, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(20.dp))
            Field(code, { code = it }, "Código de confirmação (opcional)", caps = KeyboardCapitalization.Characters)
            Field(link, { link = it }, "Link do comprovante (opcional)", keyboard = KeyboardType.Uri, caps = KeyboardCapitalization.None)

            Spacer(Modifier.height(8.dp))
            Label("Valor")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = price, onValueChange = { v -> price = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("R$") },
                    placeholder = { Text("0,00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { payerMenu = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Person, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(paidByName.ifBlank { "Quem pagou?" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = payerMenu, onDismissRequest = { payerMenu = false }) {
                        DropdownMenuItem(text = { Text("Ninguém ainda") }, onClick = { paidById = ""; paidByName = ""; payerMenu = false })
                        trip?.let { tr ->
                            tr.members.forEach { uid ->
                                val name = tr.memberNames[uid] ?: "Viajante"
                                DropdownMenuItem(text = { Text(name) }, onClick = { paidById = uid; paidByName = name; payerMenu = false })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Observações (opcional)") },
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
                    val parsed = Money.parse(price)
                    when {
                        title.isBlank() -> error = "Preencha o nome (${
                            when (t) {
                                BookingType.PASSAGEM -> "companhia"
                                BookingType.HOSPEDAGEM -> "nome do lugar"
                                BookingType.TRANSPORTE -> "empresa ou tipo"
                                BookingType.INGRESSO -> "nome do passeio"
                            }
                        })."
                        price.isNotBlank() && parsed == null -> error = "Valor inválido. Use por exemplo 1234,50."
                        endDate.isNotBlank() && startDate.isNotBlank() && endDate < startDate ->
                            error = "A data final está antes da inicial."
                        else -> {
                            Repo.saveBooking(
                                tripId,
                                Booking(
                                    id = bookingId.orEmpty(),
                                    type = type, mode = mode, title = title, number = number,
                                    from = if (t == BookingType.PASSAGEM || t == BookingType.TRANSPORTE) from else "",
                                    to = if (t == BookingType.PASSAGEM || t == BookingType.TRANSPORTE) to else "",
                                    address = if (t == BookingType.HOSPEDAGEM || t == BookingType.INGRESSO) address else "",
                                    startDate = startDate, startTime = startTime,
                                    endDate = if (t == BookingType.INGRESSO) "" else endDate,
                                    endTime = if (t == BookingType.INGRESSO) "" else endTime,
                                    code = code, link = link, price = parsed,
                                    paidById = paidById, paidByName = paidByName, notes = notes,
                                ),
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing) "Salvar" else "Adicionar reserva") }

            existing?.createdByName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text("Adicionada por $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmDelete && bookingId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar reserva?") },
            text = { Text("Ela some para todo mundo da viagem, e sai do roteiro também.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteBooking(tripId, bookingId)
                    onDone()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    caps: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = keyboard, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

@Composable
private fun AddressField(value: String, onChange: (String) -> Unit, onMap: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text("Endereço (opcional)") },
        singleLine = true,
        trailingIcon = {
            if (value.isNotBlank()) IconButton(onClick = onMap) { Icon(Icons.Outlined.Map, "Ver no mapa") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}
