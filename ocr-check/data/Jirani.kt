package com.makaohub.android.data

/** One message in a conversation with Jirani. The role is "user" or "assistant". */
class JiraniTurn(val role: String, val content: String)
class JiraniRequest(val messages: List<JiraniTurn>)
class JiraniReply(val reply: String)

/** Asks Jirani, the Makao Hub help assistant. The server holds the key and applies the limits. */
class JiraniRepository(private val auth: AuthRepository) {
    suspend fun ask(role: String, turns: List<JiraniTurn>): String =
        auth.authorized(role) { a, h -> a.jiraniChat(h, JiraniRequest(turns)) }.reply
}
