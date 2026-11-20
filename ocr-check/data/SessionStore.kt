package com.makaohub.android.data

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The key remains in Android Keystore; backup is disabled in the manifest. */
interface SessionStore {
    fun read(): AuthTokens?
    fun write(tokens: AuthTokens)
    fun clear()
    fun clearForRefresh() = clear()
    fun recordActivity(wallMillis: Long, elapsedMillis: Long) {}
    val wasInactive: Boolean get() = false
}

internal const val SESSION_IDLE_MILLIS = 60_000L
internal fun sessionInactive(lastWall: Long, lastElapsed: Long, nowWall: Long, nowElapsed: Long): Boolean {
    if (lastWall <= 0 || lastElapsed < 0 || nowWall < lastWall) return true
    val wallGap = nowWall - lastWall
    val elapsedGap = if (nowElapsed >= lastElapsed) nowElapsed - lastElapsed else 0L
    return wallGap >= SESSION_IDLE_MILLIS || elapsedGap >= SESSION_IDLE_MILLIS
}

class AndroidSessionStore(context: Context) : SessionStore {
    private val preferences = context.applicationContext.getSharedPreferences("secure_session_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = "makao_hub_session_v1"
    override var wasInactive: Boolean = false; private set
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    override fun read(): AuthTokens? {
        val stored = preferences.getString("session", null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val result = gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), AuthTokens::class.java)
            val activity = preferences.getString("activity", null)
            if (activity == null || inactive(activity)) { wasInactive = true; clear(); return null }
            require(!result.accessToken.isNullOrBlank() && !result.refreshToken.isNullOrBlank() &&
                result.role in setOf("RENTER", "LANDLORD", "SUBLESSOR") && !result.userId.isNullOrBlank())
            result
        } catch (_: Exception) { clear(); null }
    }
    override fun write(tokens: AuthTokens) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(gson.toJson(tokens).toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    override fun recordActivity(wallMillis: Long, elapsedMillis: Long) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val value = "$wallMillis:$elapsedMillis".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.iv + cipher.doFinal(value)
        check(preferences.edit().putString("activity", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    private fun inactive(value: String): Boolean {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val times = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8).split(":")
        require(times.size == 2)
        return sessionInactive(times[0].toLong(), times[1].toLong(), System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }
    override fun clearForRefresh() { check(preferences.edit().remove("session").commit()) }
    override fun clear() { check(preferences.edit().remove("session").remove("activity").commit()) }
}
