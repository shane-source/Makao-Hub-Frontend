package com.makaohub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makaohub.android.AuthViewModel
import com.makaohub.android.NotificationViewModel
import com.makaohub.android.Page
import com.makaohub.android.data.InboxNotification
import com.makaohub.android.data.MyProfile
import com.makaohub.android.data.NotificationRepository
import com.makaohub.android.data.MoveInAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun NotificationHost(auth: AuthViewModel, profile: MyProfile, content: @Composable () -> Unit) {
    val model: NotificationViewModel = viewModel(key = "notifications-${profile.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotificationViewModel(NotificationRepository(auth.repository)) { auth.navigate(Page.LOGIN) } as T
    })
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var answering by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(model) { model.refresh() }
    // Check for new notices every 30 seconds so move-in pop-ups arrive without reopening the app.
    LaunchedEffect(model) { while (true) { delay(30_000); if (!open && !answering) model.refresh() } }
    val isRenter = profile.role == "RENTER"
    val question = if (isRenter) model.notifications.firstOrNull {
        it.type == "MOVE_IN_REPORTED" && !it.read && it.bookingId != null && it.id !in dismissed } else null
    val result = if (!isRenter) model.notifications.firstOrNull {
        it.type == "MOVE_IN_RESPONSE" && !it.read && it.id !in dismissed } else null
    DisposableEffect(model) { onDispose(model::clear) }
    Box(Modifier.fillMaxSize()) {
        content()
        BadgedBox(
            badge = { if (model.unreadCount > 0) Badge { Text(model.unreadCount.coerceAtMost(99).toString()) } },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
        ) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 6.dp) {
                IconButton({ open = true; model.refresh() }) {
                    Icon(Icons.Outlined.Notifications, "Notifications", tint = Brown)
                }
            }
        }
    }
    if (question != null && !open) MoveInQuestionDialog(question.body, answering, { accepted ->
        scope.launch {
            answering = true
            try {
                auth.repository.authorized("RENTER") { api, h ->
                    api.renterMoveInResponse(h, question.bookingId!!, UUID.randomUUID().toString(), MoveInAnswer(accepted))
                }
                model.markRead(question)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The booking screen shows the exact error; keep the pop-up from looping. */ }
            finally { dismissed = dismissed + question.id; answering = false; model.refresh() }
        }
    }, { dismissed = dismissed + question.id })
    if (result != null && !open) MoveInResultDialog(result.title, result.body) {
        dismissed = dismissed + result.id; model.markRead(result)
    }
    if (open) NotificationInbox(model.notifications, model.busy, model.error, { open = false }, model::markRead, model::markAllRead)
}

private val Good = Color(0xFF2E7D32)
private val Bad = Color(0xFFC62828)
private val Warn = Color(0xFFB26A00)

/** The look of one kind of notice: its icon and the colour behind it. */
private fun styleFor(type: String): Pair<ImageVector, Color> = when (type) {
    "APPLICATION_APPROVED", "ROOMMATE_INTEREST_ACCEPTED" -> Icons.Outlined.Check to Good
    "APPLICATION_REJECTED", "ROOMMATE_INTEREST_DECLINED" -> Icons.Outlined.Close to Bad
    "APPLICATION_SUBMITTED", "APPLICATION_UNDER_REVIEW" -> Icons.Outlined.Description to Brown
    "ROOMMATE_INTEREST_SUBMITTED", "ROOMMATE_INTEREST_UNDER_REVIEW" -> Icons.Outlined.Person to Brown
    "VERIFICATION_DECISION" -> Icons.Outlined.WorkspacePremium to Good
    "LISTING_REVIEWED" -> Icons.Outlined.AddHome to Brown
    "MODERATION_WARNING", "REPORT_RESOLVED" -> Icons.Outlined.Warning to Warn
    "MOVE_IN_REPORTED", "MOVE_IN_RESPONSE" -> Icons.Outlined.Home to Brown
    "TEST_PAYOUT_STATUS" -> Icons.Outlined.Send to Good
    else -> Icons.Outlined.Notifications to Brown
}

private fun parse(value: String): OffsetDateTime? = runCatching { OffsetDateTime.parse(value) }.getOrNull()

private fun dayOf(item: InboxNotification): String {
    val at = parse(item.createdAt) ?: return "Earlier"
    val zone = ZoneId.systemDefault()
    val day = at.atZoneSameInstant(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" }
}

private fun ago(value: String): String {
    val at = parse(value) ?: return ""
    val minutes = Duration.between(at, OffsetDateTime.now()).toMinutes()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> at.atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/** A full-screen inbox: newest first, grouped by day, with unread notices marked clearly. */
@Composable
private fun NotificationInbox(items: List<InboxNotification>, busy: Boolean, error: String?, close: () -> Unit,
    read: (InboxNotification) -> Unit, readAll: () -> Unit) {
    val unread = items.count { !it.read }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = DashboardBackground) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Ink) }
                    Column(Modifier.weight(1f)) {
                        Text("Notifications", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (unread > 0) "$unread new" else "You're all caught up", color = Muted, fontSize = 13.sp)
                    }
                    if (unread > 0) TextButton(readAll) { Text("Mark all read", color = Brown, fontWeight = FontWeight.SemiBold) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = Bad, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    if (!busy && items.isEmpty()) {
                        EmptyState(Icons.Outlined.Notifications, "No notifications yet",
                            "Updates about your applications, messages and bookings will show up here.")
                    }
                    listOf("Today", "Yesterday", "Earlier").forEach { group ->
                        val rows = items.filter { dayOf(it) == group }
                        if (rows.isNotEmpty()) {
                            Text(group, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                rows.forEach { item -> NotificationRow(item) { read(item) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: InboxNotification, onClick: () -> Unit) {
    val (icon, accent) = styleFor(item.type)
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape, shadowElevation = if (item.read) 0.dp else 3.dp,
        color = if (item.read) Color.White else Color(0xFFFFF8F0),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (item.read) Border else accent.copy(alpha = 0.35f), shape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, color = Ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!item.read) Box(Modifier.padding(start = 8.dp).size(10.dp).background(Brown, CircleShape))
                }
                Text(item.body, color = Muted, fontSize = 14.sp)
                Text(ago(item.createdAt), color = Muted.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}
