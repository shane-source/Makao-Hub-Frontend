package com.makaohub.android.data

import java.time.OffsetDateTime

/** One person's own view of a conversation: ARCHIVED or DELETED. Absent means it sits in the Inbox. */
data class ChatThreadState(val kind: String, val threadId: String, val state: String, val changedAt: String, val clearedAt: String? = null)
data class ChatStateBody(val state: String)

/** Where a conversation shows for the signed-in person. Nothing here changes what the other person sees. */
object ChatStates {
    const val RENTAL = "RENTAL"
    const val ROOMMATE = "ROOMMATE"
    const val ACTIVE = "ACTIVE"
    const val ARCHIVED = "ARCHIVED"
    const val DELETED = "DELETED"

    fun key(kind: String, threadId: String) = "$kind:$threadId"

    private fun millis(value: String?): Long? =
        value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /** A message that arrives after archiving or deleting brings the conversation back to the Inbox. */
    fun placement(state: ChatThreadState?, lastMessageAt: String?): String {
        if (state == null) return ACTIVE
        val last = millis(lastMessageAt)
        val changed = millis(state.changedAt)
        return if (last != null && changed != null && last > changed) ACTIVE else state.state
    }

    /** After a delete, anything sent before it stays hidden even if the conversation comes back. */
    fun afterDelete(items: List<OwnerMessage>, state: ChatThreadState?): List<OwnerMessage> {
        val cleared = millis(state?.clearedAt) ?: return items
        return items.filter { (millis(it.createdAt) ?: Long.MAX_VALUE) > cleared }
    }
}
