package com.aline.viagens.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Um lugar escolhido na lista de sugestões. */
data class PlaceSuggestion(
    val name: String,     // ex.: "Mercado Público"
    val detail: String,   // ex.: "Rua Felipe Schmidt, 123 · Florianópolis, SC, Brasil"
    val city: String,
    val lat: Double,
    val lng: Double,
) {
    /** Texto que vai para o campo depois de escolher, ex.: "Mercado Público, Florianópolis". */
    val label: String get() = listOf(name, city).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * Sugestões de lugares enquanto a pessoa digita, usando o Photon (dados do OpenStreetMap).
 * Servidor público e gratuito: pede uso razoável, por isso a busca só roda depois de uma pausa na digitação.
 */
object Places {
    private const val TAG = "VamoViajar"
    private const val API = "https://photon.komoot.io/api/"

    /** Coordenadas de cada destino já buscado, para não procurar de novo. */
    private val destinationCache = mutableMapOf<String, Pair<Double, Double>?>()

    suspend fun search(query: String, near: Pair<Double, Double>? = null, limit: Int = 6): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 3) return@withContext emptyList()
            val url = StringBuilder(API)
                .append("?q=").append(URLEncoder.encode(q, "UTF-8"))
                .append("&limit=").append(limit)
            if (near != null) url.append("&lat=").append(near.first).append("&lon=").append(near.second)
            try {
                val conn = URL(url.toString()).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "VamoViajar/1.0 (app pessoal de viagens)")
                try {
                    if (conn.responseCode != 200) return@withContext emptyList()
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(body)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sem sugestões de lugares", e)
                emptyList()
            }
        }

    /** Coordenadas aproximadas do destino da viagem, para as sugestões virem de perto de lá. */
    suspend fun destinationPoint(destination: String): Pair<Double, Double>? {
        val key = destination.trim().lowercase()
        if (key.length < 3) return null
        if (destinationCache.containsKey(key)) return destinationCache[key]
        val found = search(destination, limit = 1).firstOrNull()?.let { it.lat to it.lng }
        destinationCache[key] = found
        return found
    }

    /**
     * Endereços com número, pelo buscador do próprio Android (usa os serviços do Google no celular).
     * O OpenStreetMap quase não tem numeração de casas no Brasil, então para "Rua X, 438" este é o melhor caminho.
     */
    suspend fun searchAddress(context: Context, query: String, destination: String): List<PlaceSuggestion> {
        if (!Geocoder.isPresent()) return emptyList()
        val q = query.trim()
        if (q.length < 5) return emptyList()
        val dest = destination.trim()
        // Junta a cidade do destino quando a pessoa não escreveu cidade, para não cair em outra "Rua ABC"
        val full = if (dest.isNotBlank() && !q.contains(",") && !q.contains(dest, ignoreCase = true)) "$q, $dest" else q
        val geocoder = Geocoder(context, Locale("pt", "BR"))
        val found: List<Address> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(6000) {
                    suspendCancellableCoroutine<List<Address>> { cont ->
                        geocoder.getFromLocationName(full, 4, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.toList())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        })
                    }
                } ?: emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(full, 4).orEmpty()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Buscador de endereços do Android indisponível", e)
            emptyList()
        }
        return found.filter { it.hasLatitude() && it.hasLongitude() }.map { a ->
            val street = listOfNotNull(a.thoroughfare, a.subThoroughfare).filter { it.isNotBlank() }.joinToString(", ")
            val city = a.locality ?: a.subAdminArea ?: ""
            val name = street.ifBlank { a.getAddressLine(0)?.substringBefore(" - ")?.substringBefore(",").orEmpty() }
                .ifBlank { a.featureName.orEmpty() }
            val detail = listOfNotNull(a.subLocality, city.ifBlank { null }, a.adminArea, a.countryName)
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            PlaceSuggestion(name.ifBlank { q }, detail, city, a.latitude, a.longitude)
        }
    }

    /** Endereço com número? Aí vale perguntar também ao buscador do Android. */
    fun looksLikeAddress(query: String): Boolean = query.any { it.isDigit() } && query.trim().length >= 5

    /** O que fica num ponto (lat, lng): nome, rua e cidade. Null se não achar. */
    suspend fun reverse(lat: Double, lng: Double): PlaceSuggestion? = withContext(Dispatchers.IO) {
        try {
            val url = "https://photon.komoot.io/reverse?lat=$lat&lon=$lng&limit=1"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "VamoViajar/1.0 (app pessoal de viagens)")
            try {
                if (conn.responseCode != 200) return@withContext null
                parse(conn.inputStream.bufferedReader().use { it.readText() }).firstOrNull()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sem endereço para o ponto", e)
            null
        }
    }

    private fun parse(body: String): List<PlaceSuggestion> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val list = mutableListOf<PlaceSuggestion>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val p = f.optJSONObject("properties") ?: continue
            fun s(k: String) = p.optString(k, "").trim()

            val street = listOf(s("street"), s("housenumber")).filter { it.isNotBlank() }.joinToString(", ")
            val city = s("city").ifBlank { s("district") }.ifBlank { s("county") }
            val region = listOf(city, s("state"), s("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            val name = s("name").ifBlank { street }.ifBlank { city }
            if (name.isBlank()) continue
            val detail = listOf(street.takeIf { it != name }.orEmpty(), region.takeIf { it != name }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            list += PlaceSuggestion(name, detail, city, lat = coords.optDouble(1), lng = coords.optDouble(0))
        }
        // O mesmo lugar às vezes vem repetido (por exemplo, o prédio e o ponto de interesse)
        return list.distinctBy { "${it.name}|${it.detail}" }
    }
}
