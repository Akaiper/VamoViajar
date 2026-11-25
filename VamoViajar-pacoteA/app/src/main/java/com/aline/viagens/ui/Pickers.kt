package com.aline.viagens.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aline.viagens.data.Dates
import com.aline.viagens.data.Days

/** Linha com dois botões: data e horário. Valores vazios = não definido. */
@Composable
fun DateTimeRow(
    date: String,
    time: String,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    defaultDate: String = "",
    modifier: Modifier = Modifier,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Row(modifier) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1.4f)) {
            Icon(Icons.Outlined.CalendarMonth, null)
            Spacer(Modifier.width(8.dp))
            Text(Dates.parse(date)?.let { Days.label(it) } ?: "Data")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Schedule, null)
            Spacer(Modifier.width(8.dp))
            Text(time.ifBlank { "Horário" })
        }
    }

    if (pickDate) {
        DateDialog(
            initial = date.ifBlank { defaultDate },
            onDismiss = { pickDate = false },
            onPick = { onDate(it); pickDate = false },
        )
    }
    if (pickTime) {
        TimeDialog(
            initial = time,
            onDismiss = { pickTime = false },
            onPick = { onTime(it); pickTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { Dates.fromPickerMillis(it) }.orEmpty()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onPick("") }) { Text("Sem data") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horário") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onPick("") }) { Text("Sem horário") } },
    )
}
