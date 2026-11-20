package com.makaohub.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.InboxNotification
import com.makaohub.android.data.NotificationRepository
import com.makaohub.android.data.SessionExpired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val expired: () -> Unit
) : ViewModel() {
    var notifications by mutableStateOf<List<InboxNotification>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    val unreadCount get() = notifications.count { !it.read }
    private var work: Job? = null

    fun refresh() = run {
        notifications = repository.notifications()
    }

    fun markRead(notification: InboxNotification) {
        if (notification.read) return
        run {
            repository.markRead(notification.id)
            notifications = notifications.map { if (it.id == notification.id) it.copy(read = true) else it }
        }
    }

    fun markAllRead() {
        val unread = notifications.filter { !it.read }
        if (unread.isEmpty()) return
        run {
            unread.forEach { repository.markRead(it.id) }
            notifications = notifications.map { it.copy(read = true) }
        }
    }

    fun clear() { work?.cancel(); work = null; notifications = emptyList(); error = null; busy = false }

    private fun run(action: suspend () -> Unit) {
        if (busy) return
        work = viewModelScope.launch {
            busy = true; error = null
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: SessionExpired) { expired() }
            catch (_: Exception) { error = "Notifications are unavailable right now." }
            finally { busy = false }
        }
    }
}
