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
