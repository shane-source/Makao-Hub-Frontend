package com.makaohub.android.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun kenyaTime(value: String, pattern: String = "d MMM yyyy, h:mm a"): String = runCatching {
    DateTimeFormatter.ofPattern(pattern, Locale.UK).withZone(ZoneId.of("Africa/Nairobi")).format(Instant.parse(value))
}.getOrDefault("Time unavailable")
