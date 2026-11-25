package com.aline.viagens.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aline.viagens.data.Money
import com.aline.viagens.data.Split
import kotlin.math.abs

/**
 * Estado da divisão guardado como texto simples, para sobreviver a giros de tela.
 * participants: "uid1,uid2"   shares: "uid1=10,50;uid2=20"
 */
data class SplitState(val mode: String = "todos", val participants: String = "", val shares: String = "") {
    val participantList get() = participants.split(",").filter { it.isNotBlank() }

    val shareTexts: Map<String, String>
        get() = shares.split(";").filter { it.contains("=") }.associate {
            val i = it.indexOf("=")
            it.substring(0, i) to it.substring(i + 1)
        }

    fun withShare(uid: String, text: String): SplitState {
        val m = shareTexts.toMutableMap()
        m[uid] = text.filter { c -> c.isDigit() || c == ',' || c == '.' }
        return copy(shares = m.entries.joinToString(";") { "${it.key}=${it.value}" })
    }

    fun toSplit(): Split = Split(
        mode = mode,
        participants = if (mode == "alguns") participantList else emptyList(),
        shares = if (mode == "valores") shareTexts.mapNotNull { (k, v) -> Money.parse(v)?.let { k to it } }.toMap() else emptyMap(),
    )

    /** Mensagem de erro, ou null se está tudo certo. */
    fun problem(amount: Double?): String? = when (mode) {
        "alguns" -> if (participantList.isEmpty()) "Escolha quem participa deste gasto." else null
        "valores" -> {
            val sum = toSplit().shares.values.sum()
            when {
                amount == null -> null
                abs(sum - amount) > 0.01 -> "A soma das partes (${Money.format(sum)}) não fecha com o total (${Money.format(amount)})."
                else -> null
            }
        }
        else -> null
    }

    companion object {
        fun from(s: Split) = SplitState(
            mode = s.mode,
            participants = s.participants.joinToString(","),
            shares = s.shares.entries.joinToString(";") { "${it.key}=${Money.toField(it.value)}" },
        )
    }
}

/** Escolha de como dividir um valor entre as pessoas da viagem. members = lista de (uid, nome). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(
    state: SplitState,
    onChange: (SplitState) -> Unit,
    members: List<Pair<String, String>>,
    amount: Double?,
) {
    val modes = listOf("todos" to "Todos", "alguns" to "Alguns", "valores" to "Valores")
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (id, label) ->
                SegmentedButton(
                    selected = state.mode == id,
                    onClick = {
                        // Ao escolher "Alguns" pela primeira vez, começa com todo mundo marcado
                        val next = if (id == "alguns" && state.participantList.isEmpty())
                            state.copy(mode = id, participants = members.joinToString(",") { it.first })
                        else state.copy(mode = id)
                        onChange(next)
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        when (state.mode) {
            "alguns" -> {
                val chosen = state.participantList
                val each = amount?.takeIf { chosen.isNotEmpty() }?.let { it / chosen.size }
                members.forEach { (uid, name) ->
                    val checked = uid in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val next = if (checked) chosen - uid else chosen + uid
                            onChange(state.copy(participants = next.joinToString(",")))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(name, Modifier.weight(1f))
                        if (checked && each != null) Text(Money.format(each), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            "valores" -> {
                val texts = state.shareTexts
                members.forEach { (uid, name) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f))
                        OutlinedTextField(
                            value = texts[uid].orEmpty(),
                            onValueChange = { onChange(state.withShare(uid, it)) },
                            placeholder = { Text("0,00") },
                            prefix = { Text("R$ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
                if (amount != null) {
                    val left = amount - state.toSplit().shares.values.sum()
                    Text(
                        when {
                            abs(left) <= 0.01 -> "A soma fecha com o total."
                            left > 0 -> "Faltam ${Money.format(left)} para fechar o total."
                            else -> "Passou ${Money.format(-left)} do total."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (abs(left) <= 0.01) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                val each = amount?.takeIf { members.isNotEmpty() }?.let { it / members.size }
                Text(
                    if (each != null) "Dividido igualmente entre as ${members.size} pessoas da viagem: ${Money.format(each)} para cada."
                    else "Dividido igualmente entre todas as pessoas da viagem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
