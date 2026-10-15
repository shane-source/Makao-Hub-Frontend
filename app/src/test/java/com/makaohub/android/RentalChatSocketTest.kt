package com.makaohub.android

import com.makaohub.android.data.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RentalChatSocketTest {
    @Test fun parserHandlesFragmentedUnicodeMultipleFramesAndHeartbeats() {
        val parser = StompFrames()
        val body = "{\"content\":\"Karibu café\"}"
        val first = "\nCONNECTED\r\nversion:1.2\r\n\r\n\u0000"
        val second = "MESSAGE\ndestination:/user/queue/rental-chat.messages\ncontent-length:${body.toByteArray().size}\n\n$body\u0000"
        val bytes = (first + second).toByteArray()
        assertTrue(parser.feed(bytes.copyOfRange(0, 9)).isEmpty())
        val frames = parser.feed(bytes.copyOfRange(9, bytes.size))
        assertEquals(listOf("CONNECTED", "MESSAGE"), frames.map { it.command })
        assertEquals(body, frames.last().body)
    }

    @Test fun parserRejectsOversizedAndMalformedFrames() {
        try { StompFrames().feed(ByteArray(131073)); fail("Must reject oversized input") } catch (_: IllegalArgumentException) { }
        try { StompFrames().feed("MESSAGE\ncontent-length:abc\n\nbody\u0000".toByteArray()); fail("Must reject invalid byte count") }
        catch (_: IllegalArgumentException) { }
        try { StompFrames().feed("MESSAGE\ninvalid-header\n\nbody\u0000".toByteArray()); fail("Must reject malformed headers") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun websocketConnectsWithBearerAndDisplaysOnlyTheMaskedServerEcho() =
        checkSocket(ChatChannel.RENTAL, "rental-messages", "/user/queue/rental-chat.messages", "/app/rental-chat.send")

    @Test fun roommateChatUsesItsOwnQueuesAndKeepsServerMasking() =
        checkSocket(ChatChannel.ROOMMATE, "roommate-messages", "/user/queue/chat.messages", "/app/chat.send")

    private fun checkSocket(channel: ChatChannel, subscription: String, destination: String, sendDestination: String) {
        val server = MockWebServer()
        val ready = CountDownLatch(1); val echoed = CountDownLatch(1)
        val connectSeen = CountDownLatch(1)
        val subscriptionSeen = CountDownLatch(1)
        var connectFrame = ""
        var subscriptionFrame = ""
        var sendFrame = ""
        var received: OwnerMessage? = null
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // Complete the normal close handshake so MockWebServer can finish its socket task.
                webSocket.close(code, reason)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.startsWith("CONNECT\n")) {
                    connectFrame = text; connectSeen.countDown()
                    webSocket.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000")
                } else if (text.startsWith("SUBSCRIBE\n") && text.contains("id:$subscription\n")) {
                    subscriptionFrame = text; subscriptionSeen.countDown()
                } else if (text.startsWith("SEND\n")) {
                    sendFrame = text
                    val json = """{"id":"message-1","threadId":"thread-1","senderId":"owner-1","clientMessageId":"client-1","content":"Contact [masked]","contactMasked":true,"createdAt":"2026-10-13T12:00:00Z"}"""
                    webSocket.send("MESSAGE\nsubscription:$subscription\ncontent-type:application/json\ncontent-length:${json.toByteArray().size}\n\n$json\u0000")
                }
            }
        }))
        server.start()
        val socket = RentalChatSocket(server.url("/").toString(), "Bearer test-access", channel) { event ->
            if(event == ChatEvent.Connected) ready.countDown()
            if(event is ChatEvent.Message) { received = event.message; echoed.countDown() }
        }
        try {
            socket.connect()
            assertTrue(connectSeen.await(5, TimeUnit.SECONDS)); assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertTrue(subscriptionSeen.await(5, TimeUnit.SECONDS))
            assertTrue(subscriptionFrame.contains("destination:$destination\n"))
            assertTrue(connectFrame.contains("Authorization:Bearer test-access\n"))
            assertTrue(connectFrame.contains("heart-beat:0,0"))
            assertEquals("/ws", server.takeRequest().path)
            assertFalse(socket.send("thread-1", "client-1", "\u0001".repeat(2000)))
            assertTrue(socket.send("thread-1", "client-1", "Contact test@example.invalid"))
            assertTrue(echoed.await(5, TimeUnit.SECONDS)); assertTrue(received!!.contactMasked)
            assertTrue(sendFrame.contains("destination:$sendDestination\n"))
            assertEquals("Contact [masked]", received!!.content)
        } finally { socket.close(); server.shutdown() }
    }
}
