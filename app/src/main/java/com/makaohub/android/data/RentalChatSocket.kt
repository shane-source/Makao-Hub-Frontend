package com.makaohub.android.data

import com.google.gson.Gson
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString
import java.util.concurrent.TimeUnit

sealed interface ChatEvent {
    data object Connected : ChatEvent
    data class Message(val message: OwnerMessage) : ChatEvent
    data class Unavailable(val text: String) : ChatEvent
}

enum class ChatChannel { RENTAL, ROOMMATE }

/** STOMP 1.2 over Spring's native /ws endpoint. JWT is sent only in CONNECT. */
class RentalChatSocket(private val baseUrl: String, private val authorization: String,
    private val channel: ChatChannel = ChatChannel.RENTAL, private val events: (ChatEvent) -> Unit) : WebSocketListener() {
    private val subscription = if (channel == ChatChannel.RENTAL) "rental-messages" else "roommate-messages"
    private val destination = if (channel == ChatChannel.RENTAL) "/user/queue/rental-chat.messages" else "/user/queue/chat.messages"
    private val sendDestination = if (channel == ChatChannel.RENTAL) "/app/rental-chat.send" else "/app/chat.send"
    private var socket: WebSocket? = null
    private val parser = StompFrames()
    private val gson = Gson()
    @Volatile private var ready = false
    @Volatile private var stopped = false

    fun connect() {
        val base = baseUrl.toHttpUrl()
        val url = base.newBuilder().encodedPath("/ws").query(null).build()
        socket = client.newWebSocket(Request.Builder().url(url).build(), this)
    }
    override fun onOpen(webSocket: WebSocket, response: Response) {
        require(!authorization.contains('\n') && !authorization.contains('\r'))
        webSocket.send("CONNECT\naccept-version:1.2\nhost:${baseUrl.toHttpUrl().host}\nAuthorization:$authorization\nheart-beat:0,0\n\n\u0000")
    }
    override fun onMessage(webSocket: WebSocket, text: String) = receive(webSocket, text.toByteArray(Charsets.UTF_8))
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(webSocket, bytes.toByteArray())

    private fun receive(ws: WebSocket, bytes: ByteArray) {
        try {
            for (frame in parser.feed(bytes)) when (frame.command) {
                "CONNECTED" -> {
                    require(frame.headers["version"] == "1.2")
                    ws.send("SUBSCRIBE\nid:$subscription\ndestination:$destination\nack:auto\n\n\u0000")
                    ws.send("SUBSCRIBE\nid:chat-errors\ndestination:/user/queue/chat.errors\nack:auto\n\n\u0000")
                    ready = true
                    events(ChatEvent.Connected)
                }
                "MESSAGE" -> {
                    if (frame.headers["subscription"] == subscription) {
                        val message = gson.fromJson(frame.body, OwnerMessage::class.java)
                        require(message.id.isNotBlank() && message.threadId.isNotBlank() && message.content.length <= 2000)
                        events(ChatEvent.Message(message))
                    } else if (frame.headers["subscription"] == "chat-errors") {
                        events(ChatEvent.Unavailable("The server could not send this message. Check your access and reconnect."))
                    }
                }
                "ERROR" -> { ws.cancel(); unavailable() }
            }
        } catch (_: Exception) { ws.cancel(); unavailable() }
    }

    /** Display only the server echo: it contains persisted, contact-masked text. */
    fun send(threadId: String, clientMessageId: String, text: String): Boolean {
        if (!ready || text.isBlank() || text.length > 2000) return false
        val body = gson.toJson(mapOf("threadId" to threadId, "clientMessageId" to clientMessageId, "content" to text))
        val frame = "SEND\ndestination:$sendDestination\ncontent-type:application/json\ncontent-length:${body.toByteArray(Charsets.UTF_8).size}\n\n$body\u0000"
        if (frame.toByteArray(Charsets.UTF_8).size > 8192) return false
        return socket?.send(frame) == true
    }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = unavailable()
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null); unavailable() }
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = unavailable()
    private fun unavailable() {
        ready = false
        if (!stopped) events(ChatEvent.Unavailable("Chat disconnected. Reconnect to continue; your saved history is still available."))
    }
    fun close() { stopped = true; ready = false; socket?.close(1000, null); socket = null }

    companion object {
        private val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).pingInterval(20, TimeUnit.SECONDS).build()
    }
}

internal class StompFrame(val command: String, val headers: Map<String, String>, val body: String)

/** Bounded incremental parser; byte content-length keeps Unicode and fragmented frames correct. */
internal class StompFrames {
    private var pending = byteArrayOf()
    fun feed(bytes: ByteArray): List<StompFrame> {
        require(pending.size + bytes.size <= 131072)
        pending += bytes
        val result = mutableListOf<StompFrame>()
        while (pending.isNotEmpty()) {
            val first = pending.indexOfFirst { it != 10.toByte() && it != 13.toByte() }
            if (first < 0) { pending = byteArrayOf(); break }
            if (first > 0) pending = pending.copyOfRange(first, pending.size)
            var headerEnd = -1
            var separator = 2
            for (i in 0 until pending.size - 1) {
                if (pending[i] == 10.toByte() && pending[i + 1] == 10.toByte()) { headerEnd = i; break }
                if (i + 3 < pending.size && pending[i] == 13.toByte() && pending[i+1] == 10.toByte()
                    && pending[i+2] == 13.toByte() && pending[i+3] == 10.toByte()) { headerEnd = i; separator = 4; break }
            }
            if (headerEnd < 0) break
            require(headerEnd <= 8192)
            val lines = pending.copyOfRange(0, headerEnd).toString(Charsets.UTF_8).replace("\r\n", "\n").split('\n')
            val headers = linkedMapOf<String, String>()
            for (line in lines.drop(1)) {
                val pair = line.split(':', limit = 2)
                require(pair.size == 2)
                headers.putIfAbsent(unescape(pair[0]), unescape(pair[1]))
            }
            val start = headerEnd + separator
            val length = headers["content-length"]?.let { require(it.matches(Regex("[0-9]{1,6}"))); it.toInt() }
            val end = if (length != null) {
                require(length <= 65536)
                start + length
            } else (start until pending.size).firstOrNull { pending[it] == 0.toByte() } ?: break
            if (end >= pending.size) break
            require(pending[end] == 0.toByte())
            result += StompFrame(lines.first(), headers, pending.copyOfRange(start, end).toString(Charsets.UTF_8))
            pending = pending.copyOfRange(end + 1, pending.size)
        }
        return result
    }
    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] != '\\') append(value[i++]) else {
                require(++i < value.length)
                append(when (value[i++]) { 'n' -> '\n'; 'r' -> '\r'; 'c' -> ':'; '\\' -> '\\'; else -> error("Invalid header escape") })
            }
        }
    }
}
