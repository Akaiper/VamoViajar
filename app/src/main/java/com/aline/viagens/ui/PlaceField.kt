package com.aline.viagens.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aline.viagens.data.PlaceSuggestion
import com.aline.viagens.data.Places
import kotlinx.coroutines.delay

/**
 * Campo de local com sugestões enquanto digita.
 * Ao escolher uma sugestão, guarda as coordenadas exatas (lat/lng). Digitar de novo apaga as coordenadas,
 * e o texto livre continua valendo como antes.
 */
@Composable
fun PlaceField(
    value: String,
    lat: Double?,
    lng: Double?,
    onChange: (text: String, lat: Double?, lng: Double?) -> Unit,
    label: String,
    destination: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var typedSincePick by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val exact = lat != null && lng != null

    // Busca depois de uma pausa na digitação, para não chamar o servidor a cada letra
    LaunchedEffect(value, focused, typedSincePick) {
        if (!focused || !typedSincePick || value.trim().length < 3 || value.trim().startsWith("http")) {
            suggestions = emptyList(); loading = false
            return@LaunchedEffect
        }
        delay(450)
        loading = true
        val near = Places.destinationPoint(destination)
        suggestions = Places.search(value, near)
        loading = false
    }

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                typedSincePick = true
                onChange(text, null, null) // mudou o texto: deixa de ser o local exato
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = when {
                exact -> ({ Text("Local exato no mapa") })
                supporting != null -> ({ Text(supporting) })
                else -> null
            },
            singleLine = true,
            leadingIcon = if (exact) ({ Icon(Icons.Outlined.Place, "Local exato", tint = MaterialTheme.colorScheme.primary) }) else null,
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    value.isNotBlank() -> IconButton(onClick = { openInMaps(context, value, destination, lat, lng) }) {
                        Icon(Icons.Outlined.Map, "Ver no mapa")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )

        if (focused && suggestions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    typedSincePick = false
                                    suggestions = emptyList()
                                    onChange(s.label, s.lat, s.lng)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Place, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (s.detail.isNotBlank()) {
                                    Text(
                                        s.detail, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Sugestões: © OpenStreetMap",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
