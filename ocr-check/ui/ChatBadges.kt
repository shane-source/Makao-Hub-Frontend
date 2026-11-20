package com.makaohub.android.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime

/** Remembers, on this phone, when each conversation was last read so unread counts survive restarts. */
internal object ChatSeen {
    private var version by mutableIntStateOf(0)
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("makao_chat_seen", Context.MODE_PRIVATE)
    private fun millis(v: String?): Long? = v?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    fun mark(c: Context, threadId: String, at: String?) {
        val new = millis(at) ?: return
        val old = millis(prefs(c).getString(threadId, null))
        if (old == null || new > old) { prefs(c).edit().putString(threadId, at).apply(); version++ }
    }

    /** Conversations that already exist the first time the badge runs are treated as read. */
    fun unread(c: Context, threads: List<Pair<String, String?>>): Int {
        @Suppress("UNUSED_EXPRESSION") version
        val p = prefs(c)
        if (!p.contains("__initialised")) {
            val e = p.edit(); threads.forEach { (id, at) -> if (at != null) e.putString(id, at) }
            e.putBoolean("__initialised", true).apply(); return 0
        }
        return threads.count { (id, at) ->
            val last = millis(at) ?: return@count false
            val seen = millis(p.getString(id, null))
            seen == null || last > seen
        }
    }
}

/**
 * Remembers, on this phone, which applications (and which status of each) have already been looked at,
 * so the Applications number counts only new or changed ones and clears once the tab is opened.
 */
internal object ApplicationSeen {
    private var version by mutableIntStateOf(0)
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("makao_application_seen", Context.MODE_PRIVATE)

    fun unseen(c: Context, keys: List<String>): Int {
        @Suppress("UNUSED_EXPRESSION") version
        val seen = prefs(c).getStringSet("keys", emptySet()).orEmpty()
        return keys.count { it !in seen }
    }

    fun markSeen(c: Context, keys: List<String>) {
        if (keys.isEmpty()) return
        val seen = prefs(c).getStringSet("keys", emptySet()).orEmpty().toMutableSet()
        if (seen.addAll(keys)) { prefs(c).edit().putStringSet("keys", seen).apply(); version++ }
    }
}

/** Tab icon with a small red count; hidden when zero. */
@Composable
internal fun BadgedTabIcon(icon: ImageVector, count: Int, tint: Color, size: Int = 21) {
    BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size.dp))
    }
}
