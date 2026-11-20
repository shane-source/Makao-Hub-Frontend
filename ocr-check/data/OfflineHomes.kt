package com.makaohub.android.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class OfflineHome(val title: String, val area: String, val rent: Long, val bedrooms: Int?, val bathrooms: Int)

/** Only public listing summaries are saved; no account data or private media URLs. */
class OfflineHomes(context: Context, private val baseUrl: String) {
    private val prefs = context.getSharedPreferences("offline_homes", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(): List<OfflineHome> = try {
        gson.fromJson(prefs.getString("homes", "[]"), Array<OfflineHome>::class.java)?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }
    fun save(homes: List<RenterHome>) {
        val summaries = homes.take(100).map { OfflineHome(it.title, it.areaLabel, it.monthlyRentKes, it.bedroomCount, it.bathroomCount) }
        prefs.edit().putString("homes", gson.toJson(summaries)).apply()
    }
    suspend fun unavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).followRedirects(false).build()
            client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + "/actuator/health").build()).execute().use { it.code >= 500 }
        } catch (_: java.io.IOException) { true }
    }
}
