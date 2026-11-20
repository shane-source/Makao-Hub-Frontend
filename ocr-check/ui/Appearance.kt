package com.makaohub.android.ui

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppearanceMode(val label: String) {
    SYSTEM("Follow device"), LIGHT("Light"), DARK("Dark");
    fun isDark(systemDark: Boolean) = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM }
}

internal class AppearancePreferences(private val preferences: SharedPreferences) {
    fun read() = try { AppearanceMode.fromStored(preferences.getString("mode", null)) }
    catch (_: ClassCastException) { AppearanceMode.SYSTEM }
    fun save(mode: AppearanceMode) { preferences.edit().putString("mode", mode.name).apply() }
}

data class AppearanceChoice(val mode: AppearanceMode, val choose: (AppearanceMode) -> Unit)
val LocalAppearanceChoice = staticCompositionLocalOf { AppearanceChoice(AppearanceMode.SYSTEM) {} }

@Composable
fun MakaoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { AppearancePreferences(context.getSharedPreferences("appearance", 0)) }
    var mode by remember { mutableStateOf(store.read()) }
    val dark = mode.isDark(isSystemInDarkTheme())
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFD7BBA9), background = Color(0xFF1D1917), surface = Color(0xFF241F1C),
        onSurface = Color(0xFFF4EEE9), onBackground = Color(0xFFF4EEE9)
    ) else lightColorScheme(
        primary = Brown, onPrimary = Color.White, background = Color.White, surface = Color.White,
        onSurface = Ink, onBackground = Ink, outline = Border
    )
    CompositionLocalProvider(LocalAppearanceChoice provides AppearanceChoice(mode) {
        mode = it; store.save(it)
    }) { MaterialTheme(colorScheme = scheme, content = content) }
}
