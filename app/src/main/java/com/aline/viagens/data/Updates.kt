package com.aline.viagens.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Confere na página de Releases do GitHub se existe uma versão mais nova do app. */
object Updates {
    const val RELEASES_URL = "https://github.com/Akaiper/VamoViajar/releases/latest"
    private const val API_URL = "https://api.github.com/repos/Akaiper/VamoViajar/releases/latest"

    /** Número do build instalado (igual ao número da Release, ex.: v0.1.12 → 12). */
    fun installedBuild(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    /** Número do build da última Release, ou null se não deu para consultar (sem internet, por exemplo). */
    suspend fun latestBuild(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("tag_name").substringAfterLast(".").toLongOrNull()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
