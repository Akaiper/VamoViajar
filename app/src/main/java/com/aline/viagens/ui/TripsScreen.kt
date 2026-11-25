package com.aline.viagens.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Repo
import com.aline.viagens.data.Trip
import com.aline.viagens.data.UserMessageException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    uid: String,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val trips by remember(uid) { Repo.trips(uid) }.collectAsStateWithLifecycle(initialValue = null)
    var showJoin by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas viagens") },
                actions = {
                    IconButton(onClick = { showJoin = true }) { Icon(Icons.Outlined.GroupAdd, "Entrar com código") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Configurações") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nova viagem") },
            )
        },
    ) { pad ->
        val list = trips
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyTrips(Modifier.padding(pad), onNew = onNew, onJoin = { showJoin = true })
            else -> {
                val upcoming = list.filterNot { Dates.isPast(it.endDate) }.sortedBy { it.startDate }
                val past = list.filter { Dates.isPast(it.endDate) }.sortedByDescending { it.startDate }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(upcoming, key = { it.id }) { TripCard(it, uid) { onOpen(it.id) } }
                    if (past.isNotEmpty()) {
                        item(key = "past-header") { SectionTitle("Viagens que já aconteceram", Modifier.padding(top = 12.dp)) }
                        items(past, key = { it.id }) { TripCard(it, uid, faded = true) { onOpen(it.id) } }
                    }
                }
            }
        }
    }

    if (showJoin) {
        JoinTripDialog(
            onDismiss = { showJoin = false },
            onJoined = { id -> showJoin = false; onOpen(id) },
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripCard(trip: Trip, uid: String, faded: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (faded) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (faded) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trip.name.ifBlank { "Viagem sem nome" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(Dates.status(trip.startDate, trip.endDate), faded)
            }
            if (trip.destination.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                InfoLine(Icons.Outlined.Place, trip.destination)
            }
            Spacer(Modifier.height(4.dp))
            InfoLine(Icons.Outlined.CalendarMonth, Dates.range(trip.startDate, trip.endDate))
            Spacer(Modifier.height(4.dp))
            val others = trip.members.size - 1
            InfoLine(
                Icons.Outlined.Group,
                when {
                    others <= 0 -> "Só você, por enquanto"
                    others == 1 -> "Você e mais 1 pessoa"
                    else -> "Você e mais $others pessoas"
                },
            )
        }
    }
}

@Composable
fun StatusPill(text: String, muted: Boolean = false) {
    if (text.isBlank()) return
    androidx.compose.material3.Surface(
        modifier = Modifier.padding(start = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyTrips(modifier: Modifier, onNew: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Luggage, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Nenhuma viagem ainda", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Crie a primeira viagem ou entre numa que alguém compartilhou com você.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onJoin) {
            Icon(Icons.Outlined.GroupAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Tenho um código de convite")
        }
    }
}

@Composable
private fun JoinTripDialog(onDismiss: () -> Unit, onJoined: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Entrar com código") },
        text = {
            Column {
                Text("Digite o código de 6 letras que te mandaram.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.uppercase().take(6); error = null },
                    singleLine = true,
                    placeholder = { Text("ABC123") },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 4.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.orEmpty()) }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 6 && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            onJoined(Repo.joinTrip(code))
                        } catch (e: UserMessageException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "Não foi possível entrar na viagem."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Entrar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
