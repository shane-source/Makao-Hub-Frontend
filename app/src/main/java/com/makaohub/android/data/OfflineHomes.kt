package com.makaohub.android.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class OfflineHome(val title: String, val area: String, val rent: Long, val bedrooms: Int?, val bathrooms: Int,
    val id: String = "", val source: String = "", val cover: String? = null)

/** Only public listing summaries are saved; no account data or private media URLs. */
class OfflineHomes(context: Context, private val baseUrl: String) {
    private val prefs = context.getSharedPreferences("offline_homes", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(): List<OfflineHome> = try {
        val covers = covers()
        (gson.fromJson(prefs.getString("homes", "[]"), Array<OfflineHome>::class.java)?.toList() ?: emptyList())
            .map { it.copy(cover = covers["${it.source}:${it.id}"]) }
    } catch (_: Exception) { emptyList() }
    private fun covers(): Map<String, String> = try {
        @Suppress("UNCHECKED_CAST")
        (gson.fromJson(prefs.getString("covers", "{}"), Map::class.java) as? Map<String, String>) ?: emptyMap()
    } catch (_: Exception) { emptyMap() }
    /** Remembers a home's cover link (the photo itself is kept by PhotoCache) so offline browsing can show it. */
    fun saveCover(source: String, id: String, url: String) {
        try {
            val all = LinkedHashMap(covers())
            all.remove("$source:$id"); all["$source:$id"] = url
            while (all.size > 100) all.remove(all.keys.first())
            prefs.edit().putString("covers", gson.toJson(all)).apply()
        } catch (_: Exception) { /* Browsing does not depend on cache storage. */ }
    }
    fun save(homes: List<RenterHome>) {
        val summaries = homes.take(100).map { OfflineHome(it.title, it.areaLabel, it.monthlyRentKes, it.bedroomCount, it.bathroomCount, it.id, it.source) }
        prefs.edit().putString("homes", gson.toJson(summaries))
            .putString("homes_full", gson.toJson(homes.take(40)))
            .putLong("homes_full_at", System.currentTimeMillis()).apply()
    }
    /** The last home list from a successful load, shown at once while a fresh one is fetched. Ignored after a day. */
    fun readRecent(): List<RenterHome> {
        val savedAt = prefs.getLong("homes_full_at", 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > 24L * 60 * 60 * 1000) return emptyList()
        return try {
            gson.fromJson(prefs.getString("homes_full", "[]"), Array<RenterHome>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
    suspend fun unavailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).followRedirects(false).build()
            client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + "/actuator/health").build()).execute().use { it.code >= 500 }
        } catch (_: java.io.IOException) { true }
    }
}
