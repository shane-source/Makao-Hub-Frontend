package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Send
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerMessage

private val Success = Color(0xFF417653)
private val Warning = Color(0xFF9C6611)
private val WarningSurface = Color(0xFFFFF1CC)

/** Shared visual contract for the three role-specific inboxes. It searches only fields already returned by the server. */
@Composable
internal fun ProtectedInboxHeader(query: String, onQuery: (String) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Messages", color = Ink, fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Surface(shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, Border)) {
            IconButton(onClick = { searching = !searching; if (!searching) onQuery("") }) {
                Icon(Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search conversations", tint = Ink)
            }
        }
    }
    if (searching) {
    Gap(12)
    OutlinedTextField(
        value = query,
        onValueChange = { onQuery(it.take(100)) },
        label = { Text("Search conversations") },
        placeholder = { Text("Name or message") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
    }
    Gap(18)
}

/** Switches an inbox between live conversations and the ones the person archived. */
@Composable
internal fun ArchiveTabs(archivedCount: Int, showingArchived: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Inbox", true to "Archived ($archivedCount)").forEach { (archived, label) ->
            val selected = showingArchived == archived
            Surface(
                onClick = { onChange(archived) },
                shape = RoundedCornerShape(50),
                color = if (selected) Ink else Color.White,
                border = BorderStroke(1.dp, Border)
            ) {
                Text(label, color = if (selected) Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
    Gap(14)
}

/** One accessible conversation summary. Disabled rows explain locked or closed state without offering a dead action. */
@Composable
internal fun ProtectedConversationRow(
    title: String,
    detail: String,
    status: String,
    enabled: Boolean,
    avatarUrl: String? = null,
    onArchive: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        enabled = enabled,
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          ProfileAvatar(avatarUrl, Modifier.size(48.dp))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status.isNotBlank()) Text(status, color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Text(detail, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (onArchive != null || onRestore != null || onDelete != null) Box {
            IconButton(onClick = { menu = true }, enabled = enabled) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Conversation options", tint = Muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onArchive?.let { act -> DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; act() }) }
                onRestore?.let { act -> DropdownMenuItem(text = { Text("Move to inbox") }, onClick = { menu = false; act() }) }
                onDelete?.let { DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true }) }
            }
          }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this conversation?") },
        text = { Text("It disappears from your list only. The other person keeps it, and it comes back if they send a new message.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
    Gap(14)
}

/** A single tick is server-confirmed; two ticks require a recipient delivery receipt. */
@Composable
internal fun ProtectedConversation(
    title: String,
    roleLabel: String,
    conversationType: String,
    @Suppress("UNUSED_PARAMETER") contextLabel: String,
    threadId: String,
    messages: List<OwnerMessage>,
    ownUserId: String,
    connected: Boolean,
    awaitingEcho: Boolean,
    threadOpen: Boolean,
    draft: String,
    busy: Boolean,
    hasOlder: Boolean,
    onOlder: () -> Unit,
    onReconnect: () -> Unit,
    onAcknowledge: (List<String>) -> Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit
) {
    Text(title, color = Brown, fontSize = 28.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    Gap(9)
    Text(roleLabel, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    Gap(15)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Brown, shape = CircleShape, modifier = Modifier.size(9.dp)) {}
        Spacer(Modifier.width(7.dp))
        Text(conversationType, color = Brown, fontSize = 12.sp)
    }
    Gap(18)

    val readContext = androidx.compose.ui.platform.LocalContext.current
    val newestServerAt = messages.filter { it.senderId != "__me__" }.maxOfOrNull { it.createdAt }
    LaunchedEffect(threadId, newestServerAt) { ChatSeen.mark(readContext, threadId, newestServerAt) }
    androidx.compose.runtime.DisposableEffect(threadId) { onDispose { ChatSeen.mark(readContext, threadId, newestServerAt) } }

    val acknowledged = remember(threadId, connected) { mutableSetOf<String>() }
    LaunchedEffect(threadId, connected, messages) {
        if (connected) {
            val incoming = messages.filter { it.senderId != ownUserId && it.senderId != "__me__" && it.deliveredAt == null && it.id !in acknowledged }.map { it.id }
            for (batch in incoming.chunked(100)) {
                if (onAcknowledge(batch)) acknowledged.addAll(batch) else break
            }
        }
    }

    if (hasOlder) {
        TextButton(onClick = onOlder, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
            Text("Load older messages", color = Brown, fontWeight = FontWeight.SemiBold)
        }
        Gap(6)
    }

    val ordered = messages.sortedWith(compareBy<OwnerMessage> { it.createdAt }.thenBy { it.id })
    if (ordered.isEmpty()) {
        SharedCard {
            SharedText("No messages yet.")
            SharedText("Start the conversation when you’re ready.", true)
        }
    }
    ordered.forEach { message ->
        val own = message.senderId == ownUserId || message.senderId == "__me__"
        Box(Modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
            Surface(
                color = if (own) Sand else Color.White,
                shape = RoundedCornerShape(17.dp),
                border = if (own) null else BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp)
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(message.content, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                    val time = protectedChatTime(message.createdAt)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (message.contactMasked) "$time · Contact masked" else time,
                            color = if (message.contactMasked) Warning else Muted, fontSize = 11.sp)
                        if (own) Icon(if (message.deliveredAt != null) Icons.Outlined.DoneAll else Icons.Outlined.Done,
                            contentDescription = if (message.deliveredAt != null) "Delivered" else "Sent",
                            tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Gap(12)
    }

    if (!threadOpen) {
        StatusNotice("Conversation closed", "Saved messages remain visible, but new messages cannot be sent.")
        return
    }
    // Connect automatically and keep retrying with a short back-off; no manual button.
    var attempt by remember(threadId) { mutableIntStateOf(0) }
    LaunchedEffect(threadId, connected, attempt) {
        if (connected) attempt = 0
        else {
            delay((800L * (attempt + 1)).coerceAtMost(4_000L))
            onReconnect()
            attempt++
        }
    }
    val canSend = connected && !awaitingEcho && draft.isNotBlank()
    OutlinedTextField(
        value = draft,
        onValueChange = { onDraft(it.take(2_000)) },
        placeholder = { Text(if (connected) "Write a message…" else "Connecting…") },
        minLines = 1,
        maxLines = 5,
        shape = RoundedCornerShape(24.dp),
        trailingIcon = {
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(Icons.Outlined.Send, contentDescription = "Send message",
                    tint = if (canSend) Brown else Muted)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Gap(8)
}

/** ISO timestamps are displayed without parsing failures affecting the conversation. */
internal fun protectedChatTime(value: String): String =
    kenyaTime(value, "h:mm a")

@Composable
private fun StatusNotice(title: String, body: String) {
    Surface(color = WarningSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Warning, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
}
