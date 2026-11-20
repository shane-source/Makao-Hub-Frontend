package com.makaohub.android.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Keeps the bottom bar the same size however large the phone's font setting is, so every tab fits in full. */
@Composable internal fun FixedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, 1f)), content = content)
}

/** One-line tab label that never wraps onto a second line. */
@Composable internal fun NavLabel(text: String) {
    Text(text, fontSize = 10.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}
