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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerMessage

private val Success = Color(0xFF417653)
private val Warning = Color(0xFF9C6611)
private val WarningSurface = Color(0xFFFFF1CC)

/** Shared visual contract for the three role-specific inboxes. It searches only fields already returned by the server. */
@Composable
internal fun ProtectedInboxHeader(subtitle: String, query: String, onQuery: (String) -> Unit) {
    Text("Conversations that feel safe", color = Brown, fontSize = 28.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    Gap(9)
    Text(subtitle, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    Gap(20)
    OutlinedTextField(
        value = query,
        onValueChange = { onQuery(it.take(100)) },
        label = { Text("Search conversations") },
        placeholder = { Text("Role or reference") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Gap(18)
}

/** One accessible conversation summary. Disabled rows explain locked or closed state without offering a dead action. */
@Composable
internal fun ProtectedConversationRow(
    title: String,
    detail: String,
    status: String,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
            Text(status, color = if (enabled) Success else Warning, fontSize = 12.sp)
            if (enabled) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
                    Text("Open conversation", color = Brown, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Gap(14)
}

/**
 * Role-neutral protected chat presentation.
 *
 * Messages are rendered only from server responses. An outgoing message is described as "Saved" because the
 * ViewModels add it only after the server echo; this component intentionally does not invent typing, delivery,
 * read-receipt, counterparty-presence, report, block, or close-conversation states.
 */
@Composable
internal fun ProtectedConversation(
    title: String,
    roleLabel: String,
    conversationType: String,
    contextLabel: String,
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
    onDraft: (String) -> Unit,
    onSend: () -> Unit
) {
    Text(title, color = Brown, fontSize = 28.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    Gap(9)
    Text("$roleLabel · ${if (connected) "Protected chat connected" else "Reconnecting"}",
        color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
    Gap(15)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Brown, shape = CircleShape, modifier = Modifier.size(9.dp)) {}
        Spacer(Modifier.width(7.dp))
        Text(conversationType, color = Brown, fontSize = 12.sp)
    }
    Gap(18)

    SharedNote("Keep communication inside Makao Hub\nPhone numbers, email addresses and social handles are masked and flagged by the server.")
    Text(contextLabel, color = Muted, fontSize = 12.sp)
    Gap(14)

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
        val own = message.senderId == ownUserId
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
                    Text(
                        buildString {
                            if (time.isNotBlank()) append(time).append(" · ")
                            append(if (message.contactMasked) "Flagged contact attempt · masked" else if (own) "Saved" else "Received")
                        },
                        color = if (message.contactMasked) Warning else Muted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Gap(12)
    }

    if (ordered.any { it.contactMasked }) {
        StatusNotice(
            title = "Contact details removed",
            body = "The attempted contact share was masked and flagged. Continue safely in this conversation."
        )
    }

    if (!threadOpen) {
        StatusNotice("Conversation closed", "Saved messages remain visible, but new messages cannot be sent.")
        return
    }
    if (!connected) {
        StatusNotice("Reconnecting…", "Saved messages remain visible. Reconnect before sending.")
        OutlinedButton(onClick = onReconnect, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Reconnect protected chat", color = Brown, fontWeight = FontWeight.SemiBold)
        }
        Gap(14)
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { onDraft(it.take(2_000)) },
        label = { Text("Message") },
        placeholder = { Text("Write a message…") },
        enabled = connected && !awaitingEcho,
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Gap(12)
    Button(
        onClick = onSend,
        enabled = connected && !awaitingEcho && draft.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
    ) {
        Text(if (awaitingEcho) "Waiting for saved message…" else "Send message", fontWeight = FontWeight.SemiBold)
    }
}

/** ISO timestamps are displayed without parsing failures affecting the conversation. */
internal fun protectedChatTime(value: String): String =
    value.substringAfter('T', "").take(5).takeIf { it.length == 5 } ?: ""

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
