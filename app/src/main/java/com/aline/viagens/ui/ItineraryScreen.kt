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
import com.aline.viagens.data.EndpointSource
import com.aline.viagens.data.Endpoint
import com.aline.viagens.data.DayEndsResolver
import com.aline.viagens.data.DayEnds
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.LaunchedEffect
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
import com.aline.viagens.data.Booking
import com.aline.viagens.data.BookingEvent
import com.aline.viagens.data.Days
import com.aline.viagens.data.CarInfo
import com.aline.viagens.data.ExpenseDraft
import com.aline.viagens.data.Leg
import com.aline.viagens.data.MapLinks
import com.aline.viagens.data.Money
import com.aline.viagens.data.Routing
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Trip
import com.aline.viagens.data.Repo
import com.aline.viagens.data.TripState
import com.aline.viagens.data.sortedForDay

/**
 * Abre um local no Google Maps. Aceita nome, endereço ou link.
 * Junta o destino da viagem na busca, para o Maps procurar perto de lá e não perto de onde a pessoa está.
 */
fun openInMaps(context: Context, place: String, destination: String = "", lat: Double? = null, lng: Double? = null) {
    val p = place.trim()
    if (lat != null && lng != null) {
        // Local escolhido nas sugestões: abre direto no ponto exato, com o nome como etiqueta
        val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(" + Uri.encode(p.ifBlank { "Local" }) + ")")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (e: ActivityNotFoundException) {
            val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
        }
        return
    }
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
    onNewExpense: () -> Unit,
    onEditTrip: () -> Unit,
) {
    val tripState by remember(tripId) { Repo.trip(tripId) }.collectAsStateWithLifecycle(initialValue = TripState.Loading)
    val activities by remember(tripId) { Repo.activities(tripId) }.collectAsStateWithLifecycle(initialValue = null)
    val bookings by remember(tripId) { Repo.bookings(tripId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val dayPlans by remember(tripId) { Repo.dayPlans(tripId) }.collectAsStateWithLifecycle(initialValue = emptyMap())
    var editingEnd by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (dia, é o início?)
    var reorderingDay by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

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
                actions = {
                    val t = trip
                    val acts = activities
                    if (t != null && acts != null) {
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, itineraryText(t, acts, bookings, DayEndsResolver.resolve(Days.of(t.startDate, t.endDate), dayPlans, bookings)))
                            }
                            context.startActivity(Intent.createChooser(send, "Compartilhar roteiro"))
                        }) { Icon(Icons.Outlined.Share, "Compartilhar roteiro") }
                    }
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
        val bookingsById = bookings.associateBy { it.id }
        val ideas = byDay[""].orEmpty().sortedBy { it.title.lowercase() }
        val outside = list.filter { it.day.isNotBlank() && it.day !in dayIds }.sortedWith(compareBy({ it.day }, { it.time }))

        // Distâncias de carro: ida/volta e a rota de cada dia (só atividades com local exato)
        val car = trip.car
        val ends = DayEndsResolver.resolve(days, dayPlans, bookings)
        // Rota de cada dia: início → atividades com local exato → fim (sem repetir o mesmo ponto seguido)
        val dayPoints = days.associate { d ->
            val id = d.toString()
            val actPoints = byDay[id].orEmpty().sortedForDay().mapNotNull { a ->
                val la = a.placeLat
                val ln = a.placeLng
                if (la != null && ln != null) la to ln else null
            }
            val all = listOfNotNull(ends[id]?.start?.point) + actPoints + listOfNotNull(ends[id]?.end?.point)
            id to all.fold(emptyList<Pair<Double, Double>>()) { acc, p ->
                if (DayEndsResolver.samePlace(acc.lastOrNull(), p)) acc else acc + p
            }
        }
        // Se o Dia 1 já começa na partida (ou o último dia termina nela), a ida/volta já está na rota do dia
        val carStart = car.startLat?.let { la -> car.startLng?.let { ln -> la to ln } }
        val idaInDay1 = DayEndsResolver.samePlace(days.firstOrNull()?.let { ends[it.toString()]?.start?.point }, carStart)
        val voltaInLastDay = DayEndsResolver.samePlace(days.lastOrNull()?.let { ends[it.toString()]?.end?.point }, carStart)
        val legKey = dayPoints.toString() + car.toString()
        var dayLegs by remember { mutableStateOf<Map<String, Leg>>(emptyMap()) }
        var outLeg by remember { mutableStateOf<Leg?>(null) }
        var routing by remember { mutableStateOf(false) }
        LaunchedEffect(legKey) {
            routing = true
            val sLa = car.startLat; val sLn = car.startLng; val dLa = car.destLat; val dLn = car.destLng
            outLeg = if (sLa != null && sLn != null && dLa != null && dLn != null) Routing.route(listOf(sLa to sLn, dLa to dLn)) else null
            val m = mutableMapOf<String, Leg>()
            dayPoints.forEach { (id, pts) -> if (pts.size >= 2) Routing.route(pts)?.let { m[id] = it } }
            dayLegs = m
            routing = false
        }
        val carConfigured = car.kmPerLiter != null || car.fuelPrice != null || car.startPlace.isNotBlank()
        val showCar = carConfigured || dayLegs.isNotEmpty()

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showCar) {
                item(key = "car") {
                    CarCard(
                        car = car,
                        destination = trip.destination,
                        outLeg = outLeg,
                        showOut = !idaInDay1,
                        showBack = !voltaInLastDay,
                        onConfigure = onEditTrip,
                        daysKm = dayLegs.values.sumOf { it.km },
                        daysWithRoute = dayLegs.size,
                        loading = routing,
                        onLaunch = { title, cost ->
                            ExpenseDraft.title = title
                            ExpenseDraft.amount = Math.round(cost * 100) / 100.0
                            ExpenseDraft.category = "transporte"
                            onNewExpense()
                        },
                    )
                }
            }
            days.forEachIndexed { index, date ->
                val dayId = date.toString()
                val dayList = byDay[dayId].orEmpty().sortedForDay()
                daySection(
                    key = dayId,
                    destination = trip.destination,
                    // Reserva ligada a uma atividade deste dia não aparece duplicada
                    events = eventsByDay[dayId].orEmpty()
                        .filter { e -> dayList.none { e.booking.id in it.bookingIds } }
                        .sortedBy { it.time.ifBlank { "99" } },
                    bookingsById = bookingsById,
                    onOpenBooking = onOpenBooking,
                    title = "Dia ${index + 1}",
                    subtitle = Days.label(date) + (dayLegs[dayId]?.let { leg ->
                        " · ≈ " + Routing.formatKm(leg.km) +
                            (Routing.fuel(leg.km, car)?.let { " · " + Money.format(it.cost) } ?: "")
                    } ?: ""),
                    activities = dayList,
                    ends = ends[dayId] ?: DayEnds(null, null),
                    isFirst = index == 0,
                    isLast = index == days.lastIndex,
                    onEditEnd = { isStart -> editingEnd = dayId to isStart },
                    reordering = reorderingDay == dayId,
                    manual = dayList.any { it.order != null },
                    onAdd = { onAdd(dayId) },
                    onToggleReorder = { reorderingDay = if (reorderingDay == dayId) null else dayId },
                    onRoute = routePlaces(dayList, ends[dayId]).takeIf { it.size >= 2 }?.let { places ->
                        { openRoute(context, places, trip.destination) }
                    },
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
                itemsIndexed(ideas, key = { _, a -> a.id }) { _, a -> ActivityCard(a, trip.destination, linked = a.bookingIds.mapNotNull { bookingsById[it] }, onOpenBooking = onOpenBooking, onClick = { onOpen(a.id) }) }
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
                itemsIndexed(outside, key = { _, a -> a.id }) { _, a -> ActivityCard(a, trip.destination, linked = a.bookingIds.mapNotNull { bookingsById[it] }, onOpenBooking = onOpenBooking, showDay = true, onClick = { onOpen(a.id) }) }
            }
        }
    }

    editingEnd?.let { (dayId, isStart) ->
        val t = trip
        val current = t?.let { tr ->
            DayEndsResolver.resolve(Days.of(tr.startDate, tr.endDate), dayPlans, bookings)[dayId]
        }?.let { if (isStart) it.start else it.end }
        DayEndDialog(
            isStart = isStart,
            dayLabel = Dates.parse(dayId)?.let { Days.label(it) }.orEmpty(),
            current = current,
            destination = t?.destination.orEmpty(),
            onDismiss = { editingEnd = null },
            onSave = { label, la, ln ->
                Repo.setDayEnd(tripId, dayId, isStart, label, la, ln)
                editingEnd = null
            },
        )
    }
}

private fun LazyListScope.daySection(
    key: String,
    destination: String,
    events: List<BookingEvent>,
    onOpenBooking: (String) -> Unit,
    bookingsById: Map<String, Booking>,
    title: String,
    subtitle: String,
    activities: List<Activity>,
    ends: DayEnds,
    isFirst: Boolean,
    isLast: Boolean,
    onEditEnd: (isStart: Boolean) -> Unit,
    reordering: Boolean,
    manual: Boolean,
    onAdd: () -> Unit,
    onToggleReorder: () -> Unit,
    onRoute: (() -> Unit)?,
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
                    if (!reordering && onRoute != null) {
                        IconButton(onClick = onRoute) { Icon(Icons.Outlined.Directions, "Ver rota do dia no mapa") }
                    }
                    if (!reordering) IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, "Adicionar atividade") }
                },
            )
            if (reordering && manual) {
                TextButton(onClick = onResetOrder) { Text("Voltar a ordenar por horário") }
            }
        }
    }
    if (!reordering) {
        item(key = "start-$key") {
            DayEndRow(isStart = true, endpoint = ends.start, isFirstOrLast = isFirst) { onEditEnd(true) }
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
                linked = a.bookingIds.mapNotNull { bookingsById[it] },
                onOpenBooking = onOpenBooking,
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
    if (!reordering) {
        item(key = "end-$key") {
            DayEndRow(isStart = false, endpoint = ends.end, isFirstOrLast = isLast) { onEditEnd(false) }
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
    linked: List<Booking> = emptyList(),
    onOpenBooking: (String) -> Unit = {},
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
                        onClick = { openInMaps(context, activity.place, destination, activity.placeLat, activity.placeLng) },
                        label = { Text(MapLinks.display(activity.place), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) },
                    )
                }
                if (reorderControls == null) {
                    linked.forEach { b ->
                        AssistChip(
                            onClick = { onOpenBooking(b.id) },
                            label = {
                                Text(
                                    listOf(b.title.ifBlank { b.bookingType.label }, b.code).filter { it.isNotBlank() }.joinToString(" · "),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(bookingIcon(b), null, Modifier.size(18.dp)) },
                        )
                    }
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

/** Uma parada da rota: coordenadas exatas quando existem, senão o texto do local. */
private data class Stop(val text: String, val exact: String?)

/** Locais das atividades do dia, na ordem do roteiro (links do Maps não entram, porque não servem de parada). */
private fun routePlaces(day: List<Activity>, ends: DayEnds?): List<Stop> {
    fun endStop(e: Endpoint?): Stop? = e?.let { Stop(it.label, if (it.lat != null && it.lng != null) "${it.lat},${it.lng}" else null) }
    val acts = day.filter { it.place.isNotBlank() && !it.place.trim().startsWith("http") }
        .map { a ->
            Stop(
                a.place.trim(),
                if (a.placeLat != null && a.placeLng != null) "${a.placeLat},${a.placeLng}" else null,
            )
        }
    val all = listOfNotNull(endStop(ends?.start)) + acts + listOfNotNull(endStop(ends?.end))
    // Tira paradas repetidas em sequência (ex.: começa e termina no mesmo hotel sem nada no meio)
    return all.fold(emptyList<Stop>()) { acc, stop -> if (acc.lastOrNull()?.let { (it.exact ?: it.text) == (stop.exact ?: stop.text) } == true) acc else acc + stop }
}

/** Abre o Google Maps com o trajeto passando pelos locais do dia. */
private fun openRoute(context: Context, places: List<Stop>, destination: String) {
    fun withCity(p: String) =
        if (destination.isBlank() || p.contains(destination.trim(), ignoreCase = true)) p else "$p, ${destination.trim()}"
    val stops = places.take(10).map { it.exact ?: withCity(it.text) }
    val url = StringBuilder("https://www.google.com/maps/dir/?api=1")
        .append("&origin=").append(Uri.encode(stops.first()))
        .append("&destination=").append(Uri.encode(stops.last()))
    if (stops.size > 2) url.append("&waypoints=").append(Uri.encode(stops.subList(1, stops.size - 1).joinToString("|")))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))) }
}

/** O roteiro em texto, para mandar no WhatsApp. */
private fun itineraryText(trip: Trip, activities: List<Activity>, bookings: List<Booking>, ends: Map<String, DayEnds>): String {
    val sb = StringBuilder()
    sb.append("🧳 ").append(trip.name)
    if (trip.destination.isNotBlank()) sb.append(" — ").append(trip.destination)
    sb.append("\n").append(Dates.range(trip.startDate, trip.endDate)).append("\n")
    val byDay = activities.groupBy { it.day }
    val events = bookings.flatMap { it.events() }.groupBy { it.day }
    Days.of(trip.startDate, trip.endDate).forEachIndexed { i, date ->
        val id = date.toString()
        val dayActs = byDay[id].orEmpty().sortedForDay()
        val dayEvents = events[id].orEmpty().filter { e -> dayActs.none { e.booking.id in it.bookingIds } }
        val dayEnds = ends[id]
        if (dayActs.isEmpty() && dayEvents.isEmpty() && dayEnds?.start == null && dayEnds?.end == null) return@forEachIndexed
        sb.append("\n*Dia ").append(i + 1).append(" · ").append(Days.label(date)).append("*\n")
        dayEnds?.start?.let { sb.append("☀️ Começa em ").append(MapLinks.display(it.label)).append("\n") }
        dayEvents.sortedBy { it.time.ifBlank { "99" } }.forEach { e ->
            sb.append("• ")
            if (e.time.isNotBlank()) sb.append(e.time).append(" ")
            sb.append(e.label).append("\n")
        }
        dayActs.forEach { a ->
            sb.append("• ")
            if (a.time.isNotBlank()) sb.append(a.time).append(" ")
            sb.append(a.title)
            if (a.place.isNotBlank() && !a.place.startsWith("http")) sb.append(" (").append(a.place).append(")")
            sb.append("\n")
        }
        dayEnds?.end?.let { sb.append("🌙 Dorme em ").append(MapLinks.display(it.label)).append("\n") }
    }
    val ideas = byDay[""].orEmpty()
    if (ideas.isNotEmpty()) {
        sb.append("\n*Ideias*\n")
        ideas.forEach { sb.append("• ").append(it.title).append("\n") }
    }
    sb.append("\nFeito no VamoViajar")
    return sb.toString()
}

/** Cartão com a estimativa de distância e combustível da viagem de carro. */
@Composable
private fun CarCard(
    car: CarInfo,
    destination: String,
    outLeg: Leg?,
    showOut: Boolean,
    showBack: Boolean,
    onConfigure: () -> Unit,
    daysKm: Double,
    daysWithRoute: Int,
    loading: Boolean,
    onLaunch: (title: String, cost: Double) -> Unit,
) {
    val outKm = outLeg?.km?.takeIf { showOut }
    val backKm = if (car.roundTrip && showBack) outLeg?.km else null
    val totalKm = (outKm ?: 0.0) + (backKm ?: 0.0) + daysKm
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Carro e combustível", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(8.dp))
            val dest = MapLinks.display(destination).ifBlank { "destino" }
            val from = MapLinks.display(car.startPlace).ifBlank { "partida" }
            if (outKm != null) CarLine("Ida: $from → $dest", outKm, car) { onLaunch("Combustível: ida", it) }
            if (backKm != null) CarLine("Volta: $dest → $from", backKm, car) { onLaunch("Combustível: volta", it) }
            if (daysWithRoute > 0) {
                CarLine(
                    if (daysWithRoute == 1) "Nos dias (1 dia com rota)" else "Nos dias ($daysWithRoute dias com rota)",
                    daysKm, car,
                ) { onLaunch("Combustível: dias da viagem", it) }
            }
            if (totalKm > 0) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                CarLine("Total da viagem", totalKm, car, bold = true) { onLaunch("Combustível: viagem toda", it) }
            }

            val hints = listOfNotNull(
                "Para a ida e volta, escolha o ponto de partida e o destino nas sugestões, em Editar viagem."
                    .takeIf { !car.hasLeg && car.startPlace.isNotBlank() },
                "Ative \"Vão de carro?\" e informe o consumo e o preço do litro para ver o custo."
                    .takeIf { !car.hasFuel },
                "A rota de cada dia usa o início, as atividades e o fim do dia que têm local exato (escolhido nas sugestões)."
                    .takeIf { daysWithRoute == 0 },
            )
            hints.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!car.hasFuel || (!car.hasLeg && car.startPlace.isNotBlank())) {
                TextButton(onClick = onConfigure) { Text("Configurar") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Estimativa pela estrada, sem trânsito nem pedágio. Rotas: OSRM · © OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CarLine(label: String, km: Double, car: CarInfo, bold: Boolean = false, onLaunch: (Double) -> Unit) {
    val fuel = Routing.fuel(km, car)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else null,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(Routing.formatKm(km), fuel?.let { Routing.formatLiters(it.liters) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fuel != null) {
            Text(
                Money.format(fuel.cost),
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            )
            TextButton(onClick = { onLaunch(fuel.cost) }) { Text("Lançar") }
        }
    }
}

/** Linha de onde o dia começa ou termina (onde vão dormir). */
@Composable
private fun DayEndRow(isStart: Boolean, endpoint: Endpoint?, isFirstOrLast: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isStart) Icons.Outlined.WbSunny else Icons.Outlined.Hotel, null,
            Modifier.size(20.dp),
            tint = if (endpoint != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (endpoint == null) {
                Text(
                    when {
                        isStart && isFirstOrLast -> "De onde vocês saem no primeiro dia?"
                        isStart -> "Definir onde o dia começa"
                        isFirstOrLast -> "Onde termina o último dia?"
                        else -> "Definir onde vão dormir"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    (if (isStart) "Começa em " else "Dorme em ") + MapLinks.display(endpoint.label),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        when (endpoint.source) {
                            EndpointSource.NOITE_ANTERIOR -> "onde dormiram na noite anterior"
                            EndpointSource.HOSPEDAGEM -> "da reserva de hospedagem"
                            EndpointSource.ESCOLHIDO -> null
                        },
                        "sem local exato".takeIf { endpoint.point == null },
                    ).joinToString(" · ").ifBlank { "escolhido para este dia" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.Outlined.Edit, "Alterar", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayEndDialog(
    isStart: Boolean,
    dayLabel: String,
    current: Endpoint?,
    destination: String,
    onDismiss: () -> Unit,
    onSave: (label: String, lat: Double?, lng: Double?) -> Unit,
) {
    var text by remember { mutableStateOf(current?.label.orEmpty()) }
    var lat by remember { mutableStateOf(current?.lat) }
    var lng by remember { mutableStateOf(current?.lng) }
    val chosen = current?.source == EndpointSource.ESCOLHIDO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isStart) "Onde o dia começa" else "Onde vão dormir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (dayLabel.isNotBlank()) {
                    Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                PlaceField(
                    value = text, lat = lat, lng = lng,
                    onChange = { t, la, ln -> text = t; lat = la; lng = ln },
                    label = if (isStart) "Começa em" else "Dorme em",
                    destination = destination,
                    placeholder = if (isStart) "Ex.: hotel, casa de amigos" else "Ex.: hotel, pousada, Airbnb",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isStart) "Se ficar em branco, o dia começa onde vocês dormiram na noite anterior."
                    else "Se ficar em branco, o app usa a reserva de hospedagem daquela noite, se houver. O dia seguinte começa aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, lat, lng) }, enabled = text.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (chosen) TextButton(onClick = { onSave("", null, null) }) { Text("Voltar ao automático") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
