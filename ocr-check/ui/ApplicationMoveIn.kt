package com.makaohub.android.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "12 Dec 2026", or null when the application has no move-in date (older applications). */
internal fun moveInLabel(value: String?): String? = value?.let {
    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) }.getOrNull()
}
