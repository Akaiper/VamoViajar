package com.aline.viagens.data

import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate
import kotlin.math.abs

/** Onde um dia começa e termina, quando alguém escolheu (documento trips/{id}/days/{data}). */
data class DayPlan(
    val date: String,
    val startPlace: String = "", val startLat: Double? = null, val startLng: Double? = null,
    val endPlace: String = "", val endLat: Double? = null, val endLng: Double? = null,
    val title: String = "", // título curto do dia, ex.: "Serra Gaúcha"
)

fun DocumentSnapshot.toDayPlan(): DayPlan? {
    if (!exists()) return null
    return DayPlan(
        date = id,
        startPlace = getString("startPlace").orEmpty(), startLat = getDouble("startLat"), startLng = getDouble("startLng"),
        endPlace = getString("endPlace").orEmpty(), endLat = getDouble("endLat"), endLng = getDouble("endLng"),
        title = getString("title").orEmpty(),
    )
}

/** De onde veio o ponto: escolhido no dia, herdado da noite anterior, ou sugerido pela hospedagem. */
enum class EndpointSource { ESCOLHIDO, NOITE_ANTERIOR, HOSPEDAGEM }

data class Endpoint(val label: String, val lat: Double?, val lng: Double?, val source: EndpointSource) {
    val point: Pair<Double, Double>? get() = if (lat != null && lng != null) lat to lng else null
}

data class DayEnds(val start: Endpoint?, val end: Endpoint?)

object DayEndsResolver {

    /** Hospedagem em que vocês dormem na noite deste dia (check-in até a véspera do check-out). */
    private fun hotelFor(day: LocalDate, bookings: List<Booking>): Booking? =
        bookings.filter { it.bookingType == BookingType.HOSPEDAGEM }.firstOrNull { b ->
            val checkIn = Dates.parse(b.startDate) ?: return@firstOrNull false
            val checkOut = Dates.parse(b.endDate) ?: checkIn.plusDays(1)
            !day.isBefore(checkIn) && day.isBefore(checkOut)
        }

    /**
     * Para cada dia: o fim é o escolhido, ou a hospedagem daquela noite.
     * O início é o escolhido, ou onde dormiram na noite anterior. O primeiro dia só tem início se alguém escolher.
     */
    fun resolve(days: List<LocalDate>, plans: Map<String, DayPlan>, bookings: List<Booking>): Map<String, DayEnds> {
        val result = mutableMapOf<String, DayEnds>()
        var previousEnd: Endpoint? = null
        for (day in days) {
            val id = day.toString()
            val plan = plans[id]
            val start = when {
                plan != null && plan.startPlace.isNotBlank() ->
                    Endpoint(plan.startPlace, plan.startLat, plan.startLng, EndpointSource.ESCOLHIDO)
                previousEnd != null -> previousEnd.copy(source = EndpointSource.NOITE_ANTERIOR)
                else -> null
            }
            val hotel = hotelFor(day, bookings)
            val end = when {
                plan != null && plan.endPlace.isNotBlank() ->
                    Endpoint(plan.endPlace, plan.endLat, plan.endLng, EndpointSource.ESCOLHIDO)
                hotel != null -> Endpoint(
                    hotel.title.ifBlank { "Hospedagem" }, hotel.addressLat, hotel.addressLng, EndpointSource.HOSPEDAGEM,
                )
                else -> null
            }
            result[id] = DayEnds(start, end)
            previousEnd = end
        }
        return result
    }

    /** Dois pontos a menos de uns 200 metros contam como o mesmo lugar. */
    fun samePlace(a: Pair<Double, Double>?, b: Pair<Double, Double>?): Boolean =
        a != null && b != null && abs(a.first - b.first) < 0.002 && abs(a.second - b.second) < 0.002
}
