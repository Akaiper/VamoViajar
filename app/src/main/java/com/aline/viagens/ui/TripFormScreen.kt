package com.aline.viagens.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aline.viagens.data.Dates
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import com.aline.viagens.data.CarInfo
import com.aline.viagens.data.Money
import com.aline.viagens.data.Repo
import com.aline.viagens.data.TripState
import kotlinx.coroutines.flow.flowOf

/** Tela de criar (tripId = null) ou editar uma viagem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFormScreen(tripId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val editing = tripId != null
    val state by remember(tripId) { tripId?.let { Repo.trip(it) } ?: flowOf(TripState.Missing) }
        .collectAsStateWithLifecycle(initialValue = TripState.Loading)

    var name by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var destLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var destLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var startPlace by rememberSaveable { mutableStateOf("") }
    var startLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var startLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var kmPerLiter by rememberSaveable { mutableStateOf("") }
    var fuelPrice by rememberSaveable { mutableStateOf("") }
    var roundTrip by rememberSaveable { mutableStateOf(true) }
    var byCar by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        val s = state
        if (editing && !loaded && s is TripState.Found) {
            name = s.trip.name; destination = s.trip.destination
            start = s.trip.startDate; end = s.trip.endDate
            val c = s.trip.car
            destLat = c.destLat; destLng = c.destLng
            startPlace = c.startPlace; startLat = c.startLat; startLng = c.startLng
            kmPerLiter = c.kmPerLiter?.let { Money.toField(it) }.orEmpty()
            fuelPrice = c.fuelPrice?.let { Money.toField(it) }.orEmpty()
            roundTrip = c.roundTrip
            byCar = c.kmPerLiter != null || c.fuelPrice != null || c.startPlace.isNotBlank()
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Editar viagem" else "Nova viagem") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it; error = null },
                label = { Text("Nome da viagem") },
                placeholder = { Text("Ex.: Férias em Floripa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PlaceField(
                value = destination, lat = destLat, lng = destLng,
                onChange = { t, la, ln -> destination = t; destLat = la; destLng = ln },
                label = "Destino",
                destination = "",
                placeholder = "Ex.: Florianópolis, SC",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Quando", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(if (start.isBlank()) "Escolher datas" else Dates.range(start, end))
            }
            Dates.nights(start, end)?.let { n ->
                Spacer(Modifier.height(4.dp))
                Text(
                    if (n == 0L) "Bate e volta" else if (n == 1L) "1 noite" else "$n noites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vão de carro?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Para estimar a distância e o gasto com combustível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = byCar, onCheckedChange = { byCar = it })
            }
            if (byCar) {
                Spacer(Modifier.height(12.dp))
                PlaceField(
                    value = startPlace, lat = startLat, lng = startLng,
                    onChange = { t, la, ln -> startPlace = t; startLat = la; startLng = ln },
                    label = "Saindo de (opcional)",
                    destination = "",
                    placeholder = "Ex.: sua casa ou cidade",
                    supporting = "Escolha uma sugestão para calcular a distância da ida.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedTextField(
                        value = kmPerLiter, onValueChange = { v -> kmPerLiter = v.filter { it.isDigit() || it == ',' || it == '.' }; error = null },
                        label = { Text("Consumo") }, suffix = { Text("km/l") }, placeholder = { Text("12") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = fuelPrice, onValueChange = { v -> fuelPrice = v.filter { it.isDigit() || it == ',' || it == '.' }; error = null },
                        label = { Text("Preço do litro") }, prefix = { Text("R$ ") }, placeholder = { Text("6,00") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = roundTrip, onCheckedChange = { roundTrip = it })
                    Text("Contar a volta também")
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val kml = Money.parse(kmPerLiter)
                    val price = Money.parse(fuelPrice)
                    val car = if (byCar) CarInfo(
                        destLat = destLat, destLng = destLng,
                        startPlace = startPlace, startLat = startLat, startLng = startLng,
                        kmPerLiter = kml, fuelPrice = price, roundTrip = roundTrip,
                    ) else CarInfo(destLat = destLat, destLng = destLng)
                    when {
                        name.isBlank() -> error = "Dê um nome para a viagem."
                        start.isBlank() -> error = "Escolha as datas da viagem."
                        byCar && kmPerLiter.isNotBlank() && (kml == null || kml <= 0) -> error = "Consumo inválido. Ex.: 12 ou 11,5"
                        byCar && fuelPrice.isNotBlank() && (price == null || price <= 0) -> error = "Preço inválido. Ex.: 6,29"
                        editing -> {
                            Repo.updateTrip(tripId!!, name, destination, start, end.ifBlank { start }, car)
                            onSaved(tripId)
                        }
                        else -> onSaved(Repo.createTrip(name, destination, start, end.ifBlank { start }, car))
                    }
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing) "Salvar" else "Criar viagem") }
        }
    }

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = Dates.toPickerMillis(start),
            initialSelectedEndDateMillis = Dates.toPickerMillis(end),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val s = pickerState.selectedStartDateMillis
                        val e = pickerState.selectedEndDateMillis ?: s
                        if (s != null && e != null) {
                            start = Dates.fromPickerMillis(s)
                            end = Dates.fromPickerMillis(e)
                            error = null
                        }
                        showPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("Datas da viagem", Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
