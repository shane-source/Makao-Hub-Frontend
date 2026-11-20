package com.makaohub.android

import android.content.Context

/** Remembers, on this phone only, that someone has signed in before and which role they used. Holds no credentials. */
class LaunchMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("makao_launch", Context.MODE_PRIVATE)
    val knownUser: Boolean get() = prefs.getBoolean("known_user", false)
    val lastRole: String? get() = prefs.getString("last_role", null)
    fun remember(role: String) { prefs.edit().putBoolean("known_user", true).putString("last_role", role).apply() }
}
