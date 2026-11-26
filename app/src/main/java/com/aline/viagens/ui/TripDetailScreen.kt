package com.aline.viagens.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Repo
import com.aline.viagens.data.Trip
import com.aline.viagens.data.TripState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    uid: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onItinerary: () -> Unit,
    onChecklist: () -> Unit,
    onBookings: () -> Unit,
    onExpenses: () -> Unit,
) {
    val state by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val activities by remember(tripId) { Repo.activities(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val checklists by remember(tripId) { Repo.checklists(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookings by remember(tripId) { Repo.bookings(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmNewCode by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Se a viagem foi apagada (por você ou por quem criou), volta para a lista
    LaunchedEffect(state) { if (state is TripState.Missing) onBack() }

    val trip = (state as? TripState.Found)?.trip
    val isOwner = trip?.ownerId == uid

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(trip?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (trip != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Mais opções") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Gerar novo código de convite") },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = { menuOpen = false; confirmNewCode = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apagar viagem") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = { menuOpen = false; confirmDelete = true },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Sair da viagem") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                        onClick = { menuOpen = false; confirmLeave = true },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (trip == null || deleting) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Overview(trip) }
                item {
                    val planned = activities.count { it.day.isNotBlank() }
                    val ideas = activities.count { it.day.isBlank() }
                    FeatureEntry(
                        icon = Icons.Outlined.Map,
                        title = "Roteiro dia a dia",
                        subtitle = if (planned == 0 && ideas == 0) "Comece a planejar o que fazer em cada dia"
                        else listOfNotNull(
                            planned.takeIf { it > 0 }?.let { if (it == 1) "1 atividade" else "$it atividades" },
                            ideas.takeIf { it > 0 }?.let { if (it == 1) "1 ideia sem dia" else "$it ideias sem dia" },
                        ).joinToString(" · "),
                        onClick = onItinerary,
                    )
                }
                item {
                    val n = checklists.size
                    FeatureEntry(
                        icon = Icons.Outlined.Checklist,
                        title = "Checklist",
                        subtitle = when (n) {
                            0 -> "Mala, documentos e o que resolver antes de ir"
                            1 -> "1 lista"
                            else -> "$n listas"
                        },
                        onClick = onChecklist,
                    )
                }
                item {
                    val n = bookings.size
                    FeatureEntry(
                        icon = Icons.Outlined.ConfirmationNumber,
                        title = "Reservas",
                        subtitle = when (n) {
                            0 -> "Passagens, hospedagem, transporte e ingressos"
                            1 -> "1 reserva"
                            else -> "$n reservas"
                        },
                        onClick = onBookings,
                    )
                }
                item {
                    FeatureEntry(
                        icon = Icons.Outlined.Payments,
                        title = "Gastos e divisão",
                        subtitle = "Quem pagou o quê e quem deve para quem",
                        onClick = onExpenses,
                    )
                }
                item { InviteCard(trip, snackbar) }
                item { People(trip, canRemove = isOwner, onRemove = { removing = it }) }
            }
        }
    }

    if (confirmDelete && trip != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${trip.name}\"?") },
            text = { Text("A viagem some para todas as pessoas que participam. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleting = true
                    Repo.deleteTrip(trip)
                    onBack()
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    if (confirmLeave && trip != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Sair de \"${trip.name}\"?") },
            text = { Text("Você deixa de ver a viagem. Para voltar, alguém precisa te mandar o código de convite de novo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    Repo.leaveTrip(trip.id)
                    onBack()
                }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancelar") } },
        )
    }

    if (confirmNewCode && trip != null) {
        AlertDialog(
            onDismissRequest = { confirmNewCode = false },
            title = { Text("Gerar novo código?") },
            text = { Text("O código atual para de funcionar. Quem já está na viagem continua normalmente.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewCode = false
                    Repo.newInviteCodeFor(trip)
                }) { Text("Gerar") }
            },
            dismissButton = { TextButton(onClick = { confirmNewCode = false }) { Text("Cancelar") } },
        )
    }

    removing?.let { memberId ->
        val name = trip?.memberNames?.get(memberId) ?: "esta pessoa"
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remover $name?") },
            text = {
                Text(
                    "$name deixa de ver a viagem. Os gastos que envolvem essa pessoa continuam lançados, " +
                        "mas ela sai das divisões \"entre todos\". Se ela tiver o código, consegue entrar de novo: " +
                        "gere um código novo se quiser evitar isso."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    trip?.let { Repo.removeMember(it.id, memberId) }
                    removing = null
                }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Overview(trip: Trip) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                Dates.status(trip.startDate, trip.endDate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                trip.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            if (trip.destination.isNotBlank()) {
                OverviewLine(Icons.Outlined.Place, com.aline.viagens.data.MapLinks.display(trip.destination))
                Spacer(Modifier.height(6.dp))
            }
            val nights = Dates.nights(trip.startDate, trip.endDate)
            val nightsText = when (nights) {
                null -> ""
                0L -> " · bate e volta"
                1L -> " · 1 noite"
                else -> " · $nights noites"
            }
            OverviewLine(Icons.Outlined.CalendarMonth, Dates.range(trip.startDate, trip.endDate) + nightsText)
        }
    }
}

@Composable
private fun OverviewLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun InviteCard(trip: Trip, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Convidar pessoas", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Quem entrar com este código pode ver e editar a viagem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                trip.inviteCode.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(trip.inviteCode))
                    scope.launch { snackbar.showSnackbar("Código copiado") }
                }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copiar")
                }
                FilledTonalButton(onClick = {
                    val text = "Bora planejar \"${trip.name}\" juntos no VamoViajar! " +
                        "Abra o app, toque em \"Entrar com código\" e digite: ${trip.inviteCode}"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
                }) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar")
                }
            }
        }
    }
}

@Composable
private fun People(trip: Trip, canRemove: Boolean, onRemove: (String) -> Unit) {
    Column {
        SectionTitle("Quem vai (${trip.members.size})", Modifier.padding(bottom = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                trip.members.forEach { memberId ->
                    val name = trip.memberNames[memberId] ?: "Viajante"
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    name.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            if (memberId == trip.ownerId) {
                                Text(
                                    "Criou a viagem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (canRemove && memberId != trip.ownerId) {
                            IconButton(onClick = { onRemove(memberId) }) {
                                Icon(Icons.Outlined.PersonRemove, "Remover $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComingSoon() {
    Column {
        SectionTitle("Em breve", Modifier.padding(bottom = 8.dp))
        val items = listOf(
            Icons.Outlined.Payments to "Gastos e divisão",
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                items.forEach { (icon, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.width(16.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
