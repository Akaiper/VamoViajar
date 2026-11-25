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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Balances
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Days
import com.aline.viagens.data.Expense
import com.aline.viagens.data.Money
import com.aline.viagens.data.Payment
import com.aline.viagens.data.Repo
import com.aline.viagens.data.Transfer
import com.aline.viagens.data.Trip
import com.aline.viagens.data.TripState
import com.aline.viagens.data.asExpense
import kotlin.math.abs
import kotlinx.coroutines.flow.flowOf

private fun Trip.nameOf(uid: String) = memberNames[uid] ?: "Alguém"

/** Todos os gastos da viagem: os lançados na mão + as reservas com valor e quem pagou. */
@Composable
fun rememberAllExpenses(tripId: String): Triple<List<Expense>?, List<Payment>, List<com.aline.viagens.data.Booking>> {
    val expenses by remember(tripId) { Repo.expenses(tripId) }.collectAsStateWithLifecycle(initialValue = null)
    val bookings by remember(tripId) { Repo.bookings(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by remember(tripId) { Repo.payments(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val all = expenses?.let { it + bookings.mapNotNull { b -> b.asExpense() } }
    return Triple(all, payments, bookings)
}

// ===================== Resumo e lista =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    tripId: String,
    uid: String,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val (allExpenses, payments, bookings) = rememberAllExpenses(tripId)
    var paying by remember { mutableStateOf<Transfer?>(null) }
    var manualPayment by remember { mutableStateOf(false) }
    var deletePayment by remember { mutableStateOf<Payment?>(null) }

    if (tripState is TripState.Missing) LaunchedEffect(Unit) { onBack() }
    val trip = (tripState as? TripState.Found)?.trip

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gastos e divisão")
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
                actions = {
                    IconButton(onClick = { manualPayment = true }) { Icon(Icons.Outlined.Payments, "Registrar pagamento") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Novo gasto") })
        },
    ) { pad ->
        val expenses = allExpenses
        if (trip == null || expenses == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        val balances = Balances.of(expenses, payments, trip.members)
        val transfers = Balances.settle(balances)
        val total = expenses.sumOf { it.amount }
        val iPaid = expenses.filter { it.paidById == uid }.sumOf { it.amount }
        val myShare = expenses.sumOf { it.split.sharesFor(it.amount, trip.members)[uid] ?: 0.0 }
        val unpaidBookings = bookings.filter { (it.price ?: 0.0) > 0 && it.paidById.isBlank() }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                SummaryCard(total, iPaid, myShare, balances[uid] ?: 0.0)
            }

            if (unpaidBookings.isNotEmpty()) {
                item(key = "unpaid") {
                    Text(
                        if (unpaidBookings.size == 1) "1 reserva tem valor mas não diz quem pagou, então não entra na conta."
                        else "${unpaidBookings.size} reservas têm valor mas não dizem quem pagou, então não entram na conta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "h-transfers") { SectionTitle("Quem deve para quem", Modifier.padding(top = 4.dp)) }
            if (transfers.isEmpty()) {
                item(key = "settled") {
                    Text(
                        if (expenses.isEmpty()) "Nenhum gasto ainda." else "Tudo acertado! Ninguém deve nada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(transfers, key = { "${it.fromId}-${it.toId}" }) { t ->
                    TransferRow(trip.nameOf(t.fromId), trip.nameOf(t.toId), t.amount, highlight = t.fromId == uid || t.toId == uid) {
                        paying = t
                    }
                }
            }

            item(key = "h-balances") { SectionTitle("Saldo de cada um", Modifier.padding(top = 8.dp)) }
            item(key = "balances") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        trip.members.sortedByDescending { balances[it] ?: 0.0 }.forEach { m ->
                            val b = balances[m] ?: 0.0
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(trip.nameOf(m) + if (m == uid) " (você)" else "", Modifier.weight(1f))
                                Text(
                                    when {
                                        abs(b) < 0.015 -> "quitado"
                                        b > 0 -> "recebe ${Money.format(b)}"
                                        else -> "deve ${Money.format(-b)}"
                                    },
                                    color = when {
                                        abs(b) < 0.015 -> MaterialTheme.colorScheme.onSurfaceVariant
                                        b > 0 -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "h-expenses") { SectionTitle("Gastos (${expenses.size})", Modifier.padding(top = 8.dp)) }
            items(
                expenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAtMs }),
                key = { it.id },
            ) { e ->
                ExpenseRow(e, trip, uid) {
                    if (e.fromBookingId.isNotBlank()) onOpenBooking(e.fromBookingId) else onOpenExpense(e.id)
                }
            }

            if (payments.isNotEmpty()) {
                item(key = "h-payments") { SectionTitle("Pagamentos registrados", Modifier.padding(top = 8.dp)) }
                items(payments.sortedByDescending { it.createdAtMs }, key = { "p-" + it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { deletePayment = p }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("${p.fromName} pagou ${p.toName}", Modifier.weight(1f))
                        Text(Money.format(p.amount), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    paying?.let { t ->
        val tr = trip
        if (tr != null) {
            AlertDialog(
                onDismissRequest = { paying = null },
                title = { Text("Registrar pagamento?") },
                text = { Text("${tr.nameOf(t.fromId)} pagou ${Money.format(t.amount)} para ${tr.nameOf(t.toId)}.") },
                confirmButton = {
                    TextButton(onClick = {
                        Repo.addPayment(tripId, t.fromId, tr.nameOf(t.fromId), t.toId, tr.nameOf(t.toId), t.amount)
                        paying = null
                    }) { Text("Registrar") }
                },
                dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancelar") } },
            )
        }
    }

    if (manualPayment && trip != null) {
        PaymentDialog(trip, onDismiss = { manualPayment = false }) { from, to, amount ->
            Repo.addPayment(tripId, from, trip.nameOf(from), to, trip.nameOf(to), amount)
            manualPayment = false
        }
    }

    deletePayment?.let { p ->
        AlertDialog(
            onDismissRequest = { deletePayment = null },
            title = { Text("Apagar pagamento?") },
            text = { Text("${p.fromName} pagou ${Money.format(p.amount)} para ${p.toName}. Os saldos voltam a contar como se ele não tivesse acontecido.") },
            confirmButton = {
                TextButton(onClick = { Repo.deletePayment(tripId, p.id); deletePayment = null }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletePayment = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SummaryCard(total: Double, iPaid: Double, myShare: Double, myBalance: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Total da viagem", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                Money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                SummaryCell("Você pagou", Money.format(iPaid), Modifier.weight(1f))
                SummaryCell("Sua parte", Money.format(myShare), Modifier.weight(1f))
                SummaryCell(
                    if (myBalance >= 0) "A receber" else "Você deve",
                    Money.format(abs(myBalance)),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun TransferRow(from: String, to: String, amount: Double, highlight: Boolean, onSettle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(from, fontWeight = FontWeight.Medium)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "deve para", Modifier.padding(horizontal = 6.dp).size(16.dp))
                    Text(to, fontWeight = FontWeight.Medium)
                }
                Text(Money.format(amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick = onSettle) { Text("Pago") }
        }
    }
}

@Composable
private fun ExpenseRow(e: Expense, trip: Trip, uid: String, onClick: () -> Unit) {
    val myShare = e.split.sharesFor(e.amount, trip.members)[uid]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Category.from(e.category).icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    "${e.paidByName.ifBlank { trip.nameOf(e.paidById) }} pagou",
                    Dates.parse(e.date)?.let { Days.label(it) },
                    "reserva".takeIf { e.fromBookingId.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(e.amount), fontWeight = FontWeight.Medium)
            Text(
                if (myShare != null) "sua parte ${Money.format(myShare)}" else "não participa",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaymentDialog(trip: Trip, onDismiss: () -> Unit, onSave: (from: String, to: String, amount: Double) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fromMenu by remember { mutableStateOf(false) }
    var toMenu by remember { mutableStateOf(false) }
    val parsed = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar pagamento") },
        text = {
            Column {
                Text("Quem pagou", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (from.isBlank()) "Escolher" else trip.nameOf(from))
                    }
                    DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) {
                        trip.members.forEach { m -> DropdownMenuItem(text = { Text(trip.nameOf(m)) }, onClick = { from = m; fromMenu = false }) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Para quem", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (to.isBlank()) "Escolher" else trip.nameOf(to))
                    }
                    DropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) {
                        trip.members.filter { it != from }.forEach { m ->
                            DropdownMenuItem(text = { Text(trip.nameOf(m)) }, onClick = { to = m; toMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("Valor") }, prefix = { Text("R$ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = from.isNotBlank() && to.isNotBlank() && from != to && (parsed ?: 0.0) > 0,
                onClick = { onSave(from, to, parsed ?: 0.0) },
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ===================== Formulário de gasto =====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormScreen(tripId: String, expenseId: String?, uid: String, onDone: () -> Unit) {
    val editing = expenseId != null
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val existing by remember(tripId, expenseId) {
        expenseId?.let { Repo.expense(tripId, it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val trip = (tripState as? TripState.Found)?.trip

    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("comida") }
    var date by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var paidById by rememberSaveable { mutableStateOf(uid) }
    var splitMode by rememberSaveable { mutableStateOf("todos") }
    var splitParts by rememberSaveable { mutableStateOf("") }
    var splitShares by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var payerMenu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        val e = existing
        if (editing && !loaded && e != null) {
            title = e.title; amount = Money.toField(e.amount); category = e.category; date = e.date; paidById = e.paidById
            SplitState.from(e.split).let { splitMode = it.mode; splitParts = it.participants; splitShares = it.shares }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar gasto" else "Novo gasto") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = { if (editing) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Apagar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (!loaded || trip == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val parsed = Money.parse(amount)
        val members = trip.members.map { it to trip.nameOf(it) }
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
                label = { Text("Com o quê?") },
                placeholder = { Text("Ex.: Jantar no restaurante") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' }; error = null },
                label = { Text("Valor") }, prefix = { Text("R$ ") }, placeholder = { Text("0,00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            FormLabel("Categoria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c.id, onClick = { category = c.id },
                        label = { Text(c.label) },
                        leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    FormLabel("Quando")
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Dates.parse(date)?.let { Days.label(it) } ?: "Sem data", maxLines = 1)
                    }
                }
                Column(Modifier.weight(1f)) {
                    FormLabel("Quem pagou")
                    Box {
                        OutlinedButton(onClick = { payerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Person, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(trip.nameOf(paidById), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = payerMenu, onDismissRequest = { payerMenu = false }) {
                            trip.members.forEach { m ->
                                DropdownMenuItem(text = { Text(trip.nameOf(m)) }, onClick = { paidById = m; payerMenu = false })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("Dividir entre")
            SplitEditor(
                state = SplitState(splitMode, splitParts, splitShares),
                onChange = { splitMode = it.mode; splitParts = it.participants; splitShares = it.shares; error = null },
                members = members,
                amount = parsed,
            )

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val split = SplitState(splitMode, splitParts, splitShares)
                    when {
                        title.isBlank() -> error = "Diga com o que foi o gasto."
                        parsed == null || parsed <= 0 -> error = "Informe um valor. Ex.: 120,50"
                        split.problem(parsed) != null -> error = split.problem(parsed)
                        else -> {
                            Repo.saveExpense(
                                tripId,
                                Expense(
                                    id = expenseId.orEmpty(), title = title, amount = parsed ?: 0.0, category = category, date = date,
                                    paidById = paidById, paidByName = trip.nameOf(paidById), split = split.toSplit(),
                                ),
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing) "Salvar" else "Adicionar gasto") }

            existing?.createdByName?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text("Lançado por $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (pickDate) {
        DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it; pickDate = false })
    }

    if (confirmDelete && expenseId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar gasto?") },
            text = { Text("Ele sai da conta de todo mundo da viagem.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteExpense(tripId, expenseId)
                    onDone()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
