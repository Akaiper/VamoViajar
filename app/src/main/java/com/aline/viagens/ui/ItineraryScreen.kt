package com.aline.viagens.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Activity
import com.aline.viagens.data.BookingEvent
import com.aline.viagens.data.Days
import com.aline.viagens.data.Repo
import com.aline.viagens.data.TripState
import com.aline.viagens.data.sortedForDay

/**
 * Abre um local no Google Maps. Aceita nome, endereço ou link.
 * Junta o destino da viagem na busca, para o Maps procurar perto de lá e não perto de onde a pessoa está.
 */
fun openInMaps(context: Context, place: String, destination: String = "") {
    val p = place.trim()
    if (p.startsWith("http")) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p))) }
        return
    }
    val dest = destination.trim()
    val query = if (dest.isBlank() || p.contains(dest, ignoreCase = true)) p else "$p, $dest"
    val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // Sem navegador nem Maps: não tem o que abrir
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    onBack: () -> Unit,
    onAdd: (day: String) -> Unit,
    onOpen: (activityId: String) -> Unit,
    onOpenBooking: (bookingId: String) -> Unit,
) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val activities by remember(tripId) { Repo.activities(tripId) }.collectAsStateWithLifecycle(initialValue = null)
    val bookings by remember(tripId) { Repo.bookings(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var reorderingDay by remember { mutableStateOf<String?>(null) }

    val trip = (tripState as? TripState.Found)?.trip
    if (tripState is TripState.Missing) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Roteiro")
                        trip?.let {
                            Text(
                                it.name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        val list = activities
        if (trip == null || list == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        val days = Days.of(trip.startDate, trip.endDate)
        val dayIds = days.map { it.toString() }.toSet()
        val byDay = list.groupBy { it.day }
        val eventsByDay = bookings.flatMap { it.events() }.groupBy { it.day }
        val ideas = byDay[""].orEmpty().sortedBy { it.title.lowercase() }
        val outside = list.filter { it.day.isNotBlank() && it.day !in dayIds }.sortedWith(compareBy({ it.day }, { it.time }))

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEachIndexed { index, date ->
                val dayId = date.toString()
                val dayList = byDay[dayId].orEmpty().sortedForDay()
                daySection(
                    key = dayId,
                    destination = trip.destination,
                    events = eventsByDay[dayId].orEmpty().sortedBy { it.time.ifBlank { "99" } },
                    onOpenBooking = onOpenBooking,
                    title = "Dia ${index + 1}",
                    subtitle = Days.label(date),
                    activities = dayList,
                    reordering = reorderingDay == dayId,
                    manual = dayList.any { it.order != null },
                    onAdd = { onAdd(dayId) },
                    onToggleReorder = { reorderingDay = if (reorderingDay == dayId) null else dayId },
                    onResetOrder = {
                        Repo.clearDayOrder(tripId, dayList.map { it.id })
                        reorderingDay = null
                    },
                    onMove = { from, to ->
                        val ids = dayList.map { it.id }.toMutableList()
                        ids.add(to, ids.removeAt(from))
                        Repo.setDayOrder(tripId, ids)
                    },
                    onOpen = onOpen,
                )
            }

            item(key = "ideas-header") {
                SectionHeader(
                    icon = Icons.Outlined.Lightbulb,
                    title = "Ideias sem dia",
                    subtitle = "Lugares que vocês querem conhecer, sem dia definido",
                    modifier = Modifier.padding(top = 16.dp),
                    actions = {
                        IconButton(onClick = { onAdd("") }) { Icon(Icons.Outlined.Add, "Adicionar ideia") }
                    },
                )
            }
            if (ideas.isEmpty()) {
                item(key = "ideas-empty") { EmptyHint("Nenhuma ideia ainda. Toque no + para anotar uma.") }
            } else {
                itemsIndexed(ideas, key = { _, a -> a.id }) { _, a -> ActivityCard(a, trip.destination, onClick = { onOpen(a.id) }) }
            }

            if (outside.isNotEmpty()) {
                item(key = "outside-header") {
                    SectionHeader(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Fora das datas da viagem",
                        subtitle = "As datas mudaram. Abra cada uma para escolher um novo dia.",
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                itemsIndexed(outside, key = { _, a -> a.id }) { _, a -> ActivityCard(a, trip.destination, showDay = true, onClick = { onOpen(a.id) }) }
            }
        }
    }
}

private fun LazyListScope.daySection(
    key: String,
    destination: String,
    events: List<BookingEvent>,
    onOpenBooking: (String) -> Unit,
    title: String,
    subtitle: String,
    activities: List<Activity>,
    reordering: Boolean,
    manual: Boolean,
    onAdd: () -> Unit,
    onToggleReorder: () -> Unit,
    onResetOrder: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onOpen: (String) -> Unit,
) {
    item(key = "header-$key") {
        Column(Modifier.padding(top = 12.dp)) {
            SectionHeader(
                title = title,
                subtitle = subtitle + if (manual && !reordering) " · ordem personalizada" else "",
                actions = {
                    if (activities.size >= 2) {
                        if (reordering) {
                            TextButton(onClick = onToggleReorder) { Text("Concluir") }
                        } else {
                            IconButton(onClick = onToggleReorder) { Icon(Icons.Outlined.SwapVert, "Reordenar") }
                        }
                    }
                    if (!reordering) IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Adicionar atividade") }
                },
            )
            if (reordering && manual) {
                TextButton(onClick = onResetOrder) { Text("Voltar a ordenar por horário") }
            }
        }
    }
    itemsIndexed(events, key = { i, e -> "ev-$key-${e.booking.id}-$i" }) { _, e ->
        BookingEventRow(e) { onOpenBooking(e.booking.id) }
    }
    if (activities.isEmpty()) {
        if (events.isEmpty()) item(key = "empty-$key") { EmptyHint("Nada planejado ainda.") }
    } else {
        itemsIndexed(activities, key = { _, a -> a.id }) { i, a ->
            ActivityCard(
                activity = a,
                destination = destination,
                onClick = { if (!reordering) onOpen(a.id) },
                reorderControls = if (reordering) {
                    {
                        Column {
                            IconButton(onClick = { onMove(i, i - 1) }, enabled = i > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, "Subir")
                            }
                            IconButton(onClick = { onMove(i, i + 1) }, enabled = i < activities.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, "Descer")
                            }
                        }
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCard(
    activity: Activity,
    destination: String,
    showDay: Boolean = false,
    onClick: () -> Unit,
    reorderControls: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val category = Category.from(activity.category)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(category.icon, category.label, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (activity.time.isNotBlank() || showDay) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            listOfNotNull(
                                activity.day.takeIf { showDay }?.let { com.aline.viagens.data.Dates.range(it, it) },
                                activity.time.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(activity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (activity.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        activity.notes, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (activity.place.isNotBlank() && reorderControls == null) {
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = { openInMaps(context, activity.place, destination) },
                        label = { Text(activity.place, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) },
                    )
                }
            }
            reorderControls?.invoke()
        }
    }
}

/** Uma reserva no roteiro: mais discreta que as atividades, com ícone da reserva. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingEventRow(e: BookingEvent, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(bookingIcon(e.booking), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(e.time.takeIf { it.isNotBlank() }, "Reserva").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    e.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
