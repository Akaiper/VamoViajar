package com.aline.viagens.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aline.viagens.data.Repo
import com.aline.viagens.data.UserPrefs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(uid: String, prefs: UserPrefs, onBack: () -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    val user = Repo.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(Repo.displayName(user), style = MaterialTheme.typography.titleMedium)
                    user?.email?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column {
                SectionTitle("Cor do app", Modifier.padding(bottom = 4.dp))
                Text(
                    "Só muda o app para você. Cada pessoa escolhe a sua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 3,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Palette.entries.forEach { p ->
                        PaletteSwatch(
                            palette = p,
                            selected = p.id == prefs.palette,
                            modifier = Modifier.weight(1f),
                            onClick = { Repo.setPrefs(uid, prefs.copy(palette = p.id)) },
                        )
                    }
                }
            }

            Column {
                SectionTitle("Aparência", Modifier.padding(bottom = 8.dp))
                val modes = listOf("claro" to "Claro", "escuro" to "Escuro", "sistema" to "Automático")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (id, label) ->
                        SegmentedButton(
                            selected = prefs.mode == id,
                            onClick = { Repo.setPrefs(uid, prefs.copy(mode = id)) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Automático segue o modo do celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RemindersSetting()

            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair da conta?") },
            text = { Text("Suas viagens continuam salvas. É só entrar de novo para ver tudo.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; Repo.signOut() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = palette.base,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(Icons.Filled.Check, "Selecionada", tint = Color.White)
                } else {
                    // Bolinha menor mostra a cor de destaque da paleta
                    Surface(shape = CircleShape, color = palette.accent, modifier = Modifier.size(16.dp)) {}
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(palette.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RemindersSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(com.aline.viagens.data.Reminders.enabled(context)) }
    var allowed by remember { mutableStateOf(com.aline.viagens.data.Reminders.hasPermission(context)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> allowed = granted }

    Column {
        SectionTitle("Lembretes", Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Avisos das reservas", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Véspera e 3 horas antes das passagens, e no dia de check-in, retirada e ingressos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(
                checked = on && allowed,
                onCheckedChange = { checked ->
                    on = checked
                    com.aline.viagens.data.Reminders.setEnabled(context, checked)
                    if (checked && !allowed && android.os.Build.VERSION.SDK_INT >= 33) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        if (on && !allowed) {
            Text(
                "O celular não deixou o app mostrar notificações. Ative em Configurações do Android → Apps → VamoViajar → Notificações.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
