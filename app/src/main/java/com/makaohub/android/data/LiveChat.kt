package com.makaohub.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one chat connection open per channel for as long as the person is signed in, so a new message
 * from the other side arrives at once on any screen.
 *
 * It reconnects by itself after a drop. It also swaps in a fresh connection every few minutes, because the
 * server only delivers to a connection whose login token is still valid (tokens last 15 minutes). The new
 * connection is opened before the old one is closed, so nothing is missed during the swap.
 */
class LiveChat(
    private val scope: CoroutineScope,
    private val channels: List<ChatChannel>,
    private val open: suspend (ChatChannel, (ChatEvent) -> Unit) -> RentalChatSocket,
    private val onEvent: (ChatChannel, ChatEvent) -> Unit,
) {
    private val jobs = ConcurrentHashMap<ChatChannel, Job>()
    private val sockets = ConcurrentHashMap<ChatChannel, RentalChatSocket>()
    private val tokens = ConcurrentHashMap<ChatChannel, Any>()
    private val kicks = ConcurrentHashMap<ChatChannel, Channel<Unit>>()

    /** Safe to call as often as you like; it only starts what is not already running. */
    fun start() {
        for (channel in channels) {
            if (jobs[channel]?.isActive == true) continue
            jobs[channel] = scope.launch { keep(channel) }
        }
    }

    fun stop() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        sockets.values.forEach { it.close() }
        sockets.clear(); tokens.clear()
    }

    /** Drop everything and connect again now, for example when the person taps Reconnect. */
    fun restart() { stop(); start() }

    fun socket(channel: ChatChannel): RentalChatSocket? = sockets[channel]
    fun isReady(channel: ChatChannel): Boolean = tokens.containsKey(channel)

    private suspend fun keep(channel: ChatChannel) {
        val kick = kicks.getOrPut(channel) { Channel(Channel.CONFLATED) }
        var wait = 1_000L
        try {
            while (true) {
                if (connectOnce(channel)) {
                    wait = 1_000L
                    val dropped = withTimeoutOrNull(REFRESH_MS) { kick.receive(); true } == true
                    if (dropped) delay(1_000L)
                } else {
                    delay(wait)
                    wait = minOf(wait * 2, 30_000L)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SessionExpired) {
            // Signed out: nothing to keep alive.
        } catch (_: WorkspaceUnavailable) {
        }
    }

    private suspend fun connectOnce(channel: ChatChannel): Boolean {
        val token = Any()
        val connected = CompletableDeferred<Boolean>()
        val socket = try {
            open(channel) { event -> handle(channel, token, connected, event) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionExpired) {
            throw e
        } catch (e: WorkspaceUnavailable) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val up = try {
            withTimeoutOrNull(15_000L) { connected.await() } == true
        } catch (e: CancellationException) {
            socket.close()
            throw e
        }
        if (!up) { socket.close(); return false }
        val old = sockets.put(channel, socket)
        tokens[channel] = token
        old?.close()
        onEvent(channel, ChatEvent.Connected)
        return true
    }

    private fun handle(channel: ChatChannel, token: Any, connected: CompletableDeferred<Boolean>, event: ChatEvent) {
        when (event) {
            ChatEvent.Connected -> connected.complete(true)
            is ChatEvent.Message -> onEvent(channel, event)
            is ChatEvent.Unavailable -> {
                connected.complete(false)
                // Only the connection currently in use matters; a replaced one may drop quietly.
                if (tokens[channel] === token) {
                    tokens.remove(channel)
                    sockets.remove(channel)?.close()
                    kicks[channel]?.trySend(Unit)
                    onEvent(channel, event)
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 5 * 60 * 1000L
    }
}
