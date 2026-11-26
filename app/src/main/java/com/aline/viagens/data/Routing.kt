package com.aline.viagens.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Um trecho de carro: distância pela estrada. */
data class Leg(val meters: Double, val seconds: Double) {
    val km get() = meters / 1000.0
}

data class FuelEstimate(val km: Double, val liters: Double, val cost: Double)

/**
 * Distância pela estrada usando o OSRM (servidor público e gratuito, dados do OpenStreetMap).
 * O servidor pede uso moderado: as respostas ficam guardadas para não perguntar a mesma rota de novo.
 */
object Routing {
    private const val TAG = "VamoViajar"
    private const val API = "https://router.project-osrm.org/route/v1/driving/"
    private val cache = mutableMapOf<String, Leg?>()

    /** Rota passando pelos pontos em ordem (lat, lng). Null se não deu para calcular. */
    suspend fun route(points: List<Pair<Double, Double>>): Leg? {
        if (points.size < 2) return null
        // OSRM aceita no máximo algumas dezenas de pontos; um dia de viagem não chega nisso
        val coords = points.take(25).joinToString(";") { (lat, lng) -> "%.6f,%.6f".format(java.util.Locale.US, lng, lat) }
        cache[coords]?.let { return it }
        if (cache.containsKey(coords)) return null
        val leg = withContext(Dispatchers.IO) {
            try {
                val conn = URL("$API$coords?overview=false").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "VamoViajar/1.0 (app pessoal de viagens)")
                try {
                    if (conn.responseCode != 200) return@withContext null
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.optString("code") != "Ok") return@withContext null
                    val r = json.optJSONArray("routes")?.optJSONObject(0) ?: return@withContext null
                    Leg(r.optDouble("distance"), r.optDouble("duration"))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não deu para calcular a rota", e)
                null
            }
        }
        // Só guarda o que deu certo: sem internet agora, tenta de novo depois
        if (leg != null) cache[coords] = leg
        return leg
    }

    fun fuel(km: Double, car: CarInfo): FuelEstimate? {
        val kml = car.kmPerLiter ?: return null
        val price = car.fuelPrice ?: return null
        if (kml <= 0 || price <= 0) return null
        val liters = km / kml
        return FuelEstimate(km, liters, liters * price)
    }

    fun formatKm(km: Double): String =
        if (km < 10) String.format(java.util.Locale("pt", "BR"), "%.1f km", km)
        else String.format(java.util.Locale("pt", "BR"), "%,.0f km", km)

    fun formatLiters(l: Double): String = String.format(java.util.Locale("pt", "BR"), "%.1f L", l)
}

/** Rascunho de gasto para abrir o formulário já preenchido (ex.: combustível estimado). */
object ExpenseDraft {
    var title: String? = null
    var amount: Double? = null
    var category: String? = null

    fun take(): Triple<String, Double, String>? {
        val t = title ?: return null
        val a = amount ?: return null
        val c = category ?: "transporte"
        title = null; amount = null; category = null
        return Triple(t, a, c)
    }
}
