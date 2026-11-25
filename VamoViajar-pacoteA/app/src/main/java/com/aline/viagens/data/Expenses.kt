package com.aline.viagens.data

import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.abs
import kotlin.math.round

/**
 * Como um valor é dividido.
 * - todos: igual entre todas as pessoas que estão na viagem agora
 * - alguns: igual entre as pessoas de participants
 * - valores: cada pessoa paga o valor em shares
 */
data class Split(
    val mode: String = "todos",
    val participants: List<String> = emptyList(),
    val shares: Map<String, Double> = emptyMap(),
) {
    /** Quanto cada pessoa deve deste valor. */
    fun sharesFor(amount: Double, members: List<String>): Map<String, Double> = when (mode) {
        "valores" -> shares.filterValues { it > 0 }
        "alguns" -> equal(amount, participants.ifEmpty { members })
        else -> equal(amount, members)
    }

    private fun equal(amount: Double, people: List<String>): Map<String, Double> {
        if (people.isEmpty()) return emptyMap()
        val each = amount / people.size
        return people.associateWith { each }
    }

    fun toMap(): Map<String, Any> = mapOf("splitMode" to mode, "participants" to participants, "shares" to shares)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(doc: DocumentSnapshot): Split = Split(
            mode = doc.getString("splitMode") ?: "todos",
            participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            shares = (doc.get("shares") as? Map<String, Any?>).orEmpty()
                .mapNotNull { (k, v) -> (v as? Number)?.toDouble()?.let { k to it } }.toMap(),
        )
    }
}

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "outro",
    val date: String = "",
    val paidById: String = "",
    val paidByName: String = "",
    val split: Split = Split(),
    val createdByName: String = "",
    val createdAtMs: Long = 0L,
    val fromBookingId: String = "", // preenchido quando o gasto vem de uma reserva (não é salvo)
)

fun DocumentSnapshot.toExpense(): Expense? {
    if (!exists()) return null
    return Expense(
        id = id,
        title = getString("title").orEmpty(),
        amount = getDouble("amount") ?: 0.0,
        category = getString("category") ?: "outro",
        date = getString("date").orEmpty(),
        paidById = getString("paidById").orEmpty(),
        paidByName = getString("paidByName").orEmpty(),
        split = Split.from(this),
        createdByName = getString("createdByName").orEmpty(),
        createdAtMs = getLong("createdAtMs") ?: 0L,
    )
}

/** Um acerto entre duas pessoas: from pagou amount para to. */
data class Payment(
    val id: String = "",
    val fromId: String = "", val fromName: String = "",
    val toId: String = "", val toName: String = "",
    val amount: Double = 0.0,
    val createdAtMs: Long = 0L,
)

fun DocumentSnapshot.toPayment(): Payment? {
    if (!exists()) return null
    return Payment(
        id = id,
        fromId = getString("fromId").orEmpty(), fromName = getString("fromName").orEmpty(),
        toId = getString("toId").orEmpty(), toName = getString("toName").orEmpty(),
        amount = getDouble("amount") ?: 0.0,
        createdAtMs = getLong("createdAtMs") ?: 0L,
    )
}

/** Reserva com valor e quem pagou vira gasto automaticamente. */
fun Booking.asExpense(): Expense? {
    val p = price ?: return null
    if (paidById.isBlank() || p <= 0) return null
    return Expense(
        id = "reserva-$id",
        title = title.ifBlank { bookingType.label },
        amount = p,
        category = when (bookingType) {
            BookingType.PASSAGEM, BookingType.TRANSPORTE -> "transporte"
            BookingType.HOSPEDAGEM -> "hospedagem"
            BookingType.INGRESSO -> "passeio"
        },
        date = startDate,
        paidById = paidById,
        paidByName = paidByName,
        split = split,
        fromBookingId = id,
    )
}

data class Transfer(val fromId: String, val toId: String, val amount: Double)

object Balances {
    private fun cents(v: Double) = round(v * 100) / 100

    /** Saldo de cada pessoa: positivo = tem a receber, negativo = deve. */
    fun of(expenses: List<Expense>, payments: List<Payment>, members: List<String>): Map<String, Double> {
        val bal = members.associateWith { 0.0 }.toMutableMap()
        for (e in expenses) {
            bal[e.paidById] = (bal[e.paidById] ?: 0.0) + e.amount
            e.split.sharesFor(e.amount, members).forEach { (uid, share) ->
                bal[uid] = (bal[uid] ?: 0.0) - share
            }
        }
        for (p in payments) {
            bal[p.fromId] = (bal[p.fromId] ?: 0.0) + p.amount
            bal[p.toId] = (bal[p.toId] ?: 0.0) - p.amount
        }
        return bal.mapValues { cents(it.value) }
    }

    /** Menor conjunto de pagamentos que zera os saldos (quem deve mais paga primeiro para quem tem mais a receber). */
    fun settle(balances: Map<String, Double>): List<Transfer> {
        val debtors = balances.filterValues { it < -0.015 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val creditors = balances.filterValues { it > 0.015 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val result = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (d, owe) = debtors[i]
            val (c, credit) = creditors[j]
            val amt = cents(minOf(owe, credit))
            if (amt > 0) result += Transfer(d, c, amt)
            debtors[i] = d to cents(owe - amt)
            creditors[j] = c to cents(credit - amt)
            if (abs(debtors[i].second) < 0.01) i++
            if (abs(creditors[j].second) < 0.01) j++
        }
        return result
    }
}
