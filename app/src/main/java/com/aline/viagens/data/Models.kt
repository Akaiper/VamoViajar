package com.aline.viagens.data

import com.google.firebase.firestore.DocumentSnapshot

data class Trip(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: String, // formato yyyy-MM-dd
    val endDate: String,
    val ownerId: String,
    val members: List<String>,
    val memberNames: Map<String, String>,
    val inviteCode: String,
)

data class UserPrefs(
    val palette: String = "oceano",
    val mode: String = "sistema", // sistema | claro | escuro
)

sealed interface TripState {
    data object Loading : TripState
    data object Missing : TripState
    data class Found(val trip: Trip) : TripState
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toTrip(): Trip? {
    if (!exists()) return null
    return Trip(
        id = id,
        name = getString("name").orEmpty(),
        destination = getString("destination").orEmpty(),
        startDate = getString("startDate").orEmpty(),
        endDate = getString("endDate").orEmpty(),
        ownerId = getString("ownerId").orEmpty(),
        members = (get("members") as? List<String>).orEmpty(),
        memberNames = (get("memberNames") as? Map<String, String>).orEmpty(),
        inviteCode = getString("inviteCode").orEmpty(),
    )
}

/** Uma atividade do roteiro. day vazio = "ideia sem dia". */
data class Activity(
    val id: String = "",
    val title: String = "",
    val day: String = "",   // yyyy-MM-dd, ou vazio
    val time: String = "",  // HH:mm, ou vazio
    val place: String = "",
    val notes: String = "",
    val category: String = "passeio",
    val order: Double? = null, // preenchido só quando o dia foi reordenado na mão
    val createdByName: String = "",
)

fun DocumentSnapshot.toActivity(): Activity? {
    if (!exists()) return null
    return Activity(
        id = id,
        title = getString("title").orEmpty(),
        day = getString("day").orEmpty(),
        time = getString("time").orEmpty(),
        place = getString("place").orEmpty(),
        notes = getString("notes").orEmpty(),
        category = getString("category") ?: "passeio",
        order = getDouble("order"),
        createdByName = getString("createdByName").orEmpty(),
    )
}

/** Ordem de um dia: se alguém reordenou na mão, usa essa ordem; senão, pelo horário (sem horário vai pro fim). */
fun List<Activity>.sortedForDay(): List<Activity> {
    val byTime = compareBy<Activity>({ it.time.isBlank() }, { it.time }, { it.title.lowercase() })
    return if (any { it.order != null }) {
        sortedWith(compareBy<Activity> { it.order ?: Double.MAX_VALUE }.then(byTime))
    } else sortedWith(byTime)
}
