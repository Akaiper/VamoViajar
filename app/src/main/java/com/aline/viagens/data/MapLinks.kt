package com.aline.viagens.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Lê links do Google Maps (curtos, como maps.app.goo.gl/…, ou completos) e tira deles o nome e o ponto exato.
 * O formato dos links não é oficial e pode mudar: quando não der para ler, o link continua valendo como está.
 */
object MapLinks {
    private const val TAG = "VamoViajar"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun isLink(text: String) = text.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    /** Texto para mostrar no lugar de um link cru. */
    fun display(text: String): String = if (isLink(text)) "Local no Google Maps" else text

    private val pin = Regex("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)")          // ponto marcado do lugar
    private val query = Regex("[?&](?:q|query|ll|center|destination)=(-?\\d+(?:\\.\\d+)?)(?:,|%2C)\\s*(-?\\d+(?:\\.\\d+)?)")
    private val view = Regex("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")              // centro do mapa (menos exato)
    private val placeName = Regex("/maps/place/([^/@?]+)")
    private val textQuery = Regex("[?&](?:q|query)=([^&]+)")
    private val embeddedMapsUrl = Regex("https://www\\.google\\.[a-z.]+/maps/[^\"'\\s<>\\\\]+")

    /** Resolve o link em nome + coordenadas. Null se não conseguir. */
    suspend fun resolve(link: String): PlaceSuggestion? {
        val finalUrl = withContext(Dispatchers.IO) { follow(link.trim()) } ?: return null
        val coords = pin.find(finalUrl)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: query.find(finalUrl)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            ?: view.find(finalUrl)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        val name = (placeName.find(finalUrl)?.groupValues?.get(1)
            ?: textQuery.find(finalUrl)?.groupValues?.get(1)?.takeIf { !it.first().isDigit() && !it.startsWith("-") })
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.replace('+', ' ')?.trim()
            .orEmpty()

        // Sem ponto no link não dá para marcar o local exato
        if (coords == null) return null
        val (lat, lng) = coords
        val around = Places.reverse(lat, lng)
        val finalName = name.substringBefore(",").trim().ifBlank { around?.name.orEmpty() }.ifBlank { "Local marcado" }
        return PlaceSuggestion(
            name = finalName,
            detail = around?.detail.orEmpty(),
            city = around?.city.orEmpty(),
            lat = lat,
            lng = lng,
        )
    }

    /** Segue os redirecionamentos do link curto até o endereço completo do Google Maps. */
    private fun follow(start: String): String? {
        var current = start
        repeat(6) {
            if (pin.containsMatchIn(current) || placeName.containsMatchIn(current)) return current
            val conn = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return null
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return current
                    current = URL(URL(current), loc).toString()
                } else {
                    // Alguns links curtos devolvem uma página com o endereço completo dentro
                    if (code == 200) {
                        val body = conn.inputStream.bufferedReader().use { r ->
                            val buf = CharArray(200_000)
                            val n = r.read(buf)
                            if (n > 0) String(buf, 0, n) else ""
                        }
                        embeddedMapsUrl.find(body)?.value?.let { return it.replace("\\u0026", "&").replace("&amp;", "&") }
                    }
                    return current
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não deu para abrir o link do Maps", e)
                return null
            } finally {
                conn.disconnect()
            }
        }
        return current
    }
}
