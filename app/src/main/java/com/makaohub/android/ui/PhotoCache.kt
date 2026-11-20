package com.makaohub.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Memory and disk cache for listing photos.
 *
 * Signed photo links carry a token that changes on every response, so the cache key is the object path
 * only (for example /storage/v1/object/sign/listing-media/<owner>/<listing>/<id>.jpg). Every upload gets a
 * new random id, so a replaced photo is never served from an old entry.
 * Only links that pass allowedHomeMedia() are ever fetched, and the disk copy lives in the app's private cache folder.
 */
internal object PhotoCache {
    /** Large phone photos are accepted and shrunk while decoding instead of failing at 2 MB. */
    private const val MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024
    private const val TARGET_SIDE = 700
    private const val MAX_DISK_BYTES = 60L * 1024 * 1024
    private const val MAX_DIMENSION = 20_000

    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
    private val gate = Semaphore(4)
    private val writes = AtomicInteger()
    private val memory = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Stable key: the object path without the changing signing token. */
    fun keyOf(url: String): String? = runCatching { url.toHttpUrl().encodedPath }.getOrNull()

    /** Instant lookup for a photo already seen in this session. */
    fun peek(url: String): Bitmap? = keyOf(url)?.let { memory.get(it) }

    /** True when the photo is already on this device, so it can be shown without a connection. */
    fun cached(context: Context, url: String): Boolean {
        if (!allowedHomeMedia(url)) return false
        val key = keyOf(url) ?: return false
        return memory.get(key) != null || File(File(context.cacheDir, "photos"), hash(key)).isFile
    }

    suspend fun load(context: Context, url: String): Bitmap? {
        if (!allowedHomeMedia(url)) return null
        val key = keyOf(url) ?: return null
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(folder, hash(key))
            readDisk(file)?.let { memory.put(key, it); return@withContext it }
            val bitmap = gate.withPermit { download(url) } ?: return@withContext null
            memory.put(key, bitmap)
            writeDisk(file, folder, bitmap)
            bitmap
        }
    }

    private fun download(url: String): Bitmap? {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    if (out.size() + n > MAX_DOWNLOAD_BYTES) return null
                    out.write(chunk, 0, n)
                }
            }
            val bytes = out.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..MAX_DIMENSION || bounds.outHeight !in 1..MAX_DIMENSION) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > TARGET_SIDE) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun readDisk(file: File): Bitmap? {
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.path)?.also { file.setLastModified(System.currentTimeMillis()) }
                ?: run { file.delete(); null }
        } catch (_: Exception) { file.delete(); null }
    }

    private fun writeDisk(file: File, folder: File, bitmap: Bitmap) {
        try {
            val temp = File(folder, file.name + ".tmp")
            temp.outputStream().use {
                if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                else bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
            if (!temp.renameTo(file)) temp.delete()
            if (writes.incrementAndGet() % 20 == 0) prune(folder)
        } catch (_: Exception) { /* A failed cache write only means the photo is downloaded again next time. */ }
    }

    /** Keeps the disk cache under its limit by removing the least recently used photos. */
    private fun prune(folder: File) {
        val files = folder.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_DISK_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
}
