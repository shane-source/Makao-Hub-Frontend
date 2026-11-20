package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.AuthRepository
import com.makaohub.android.data.JiraniRepository
import com.makaohub.android.data.JiraniTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val JIRANI_SUGGESTIONS = listOf(
    "How do I get verified?",
    "How does payment and move-in work?",
    "How do I boost my listing?"
)

/** Jirani, the Makao Hub help assistant. It never sees the account, only the questions asked here. */
@Composable
fun JiraniChat(role: String, repository: AuthRepository) {
    val scope = rememberCoroutineScope()
    val client = remember(repository) { JiraniRepository(repository) }
    val turns = remember { mutableStateListOf<JiraniTurn>() }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || sending) return
        if (clean.length > 1000) { error = "Please keep your message under 1000 characters."; return }
        error = null
        sending = true
        turns.add(JiraniTurn("user", clean))
        draft = ""
        scope.launch {
            try {
                val reply = client.ask(role, turns.takeLast(12))
                turns.add(JiraniTurn("assistant", reply))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                // The question goes back into the box so nothing is lost.
                if (turns.isNotEmpty()) turns.removeAt(turns.lastIndex)
                draft = clean
                error = when {
                    failure is HttpException && failure.code() == 429 ->
                        "You're sending messages quickly, or you've reached today's limit. Please try again later."
                    failure.javaClass.simpleName == "SessionExpired" -> "Please sign in again."
                    else -> "Jirani is busy right now. Please try again, or use WhatsApp below."
                }
            } finally {
                sending = false
            }
        }
    }

    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Jirani", color = Brown, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("AI assistant", color = Muted, fontSize = 12.sp)
            }
            Text("Ask about verification, listings, applications, bookings, payments or plans. Jirani can't see your account, so never share passwords or codes.",
                color = Muted, fontSize = 13.sp)

            if (turns.isEmpty()) {
                JIRANI_SUGGESTIONS.forEach { question ->
                    OutlinedButton(onClick = { send(question) }, enabled = !sending, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Border)) {
                        Text(question, color = Brown, fontSize = 13.sp)
                    }
                }
            }

            turns.forEach { turn ->
                val mine = turn.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (mine) Brown else Color(0xFFF6F1EA),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(turn.content, color = if (mine) Color.White else Ink, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
            if (sending) Text("Jirani is typing…", color = Muted, fontSize = 12.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(1000) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask Jirani…") },
                shape = RoundedCornerShape(24.dp),
                minLines = 1,
                maxLines = 4,
                trailingIcon = {
                    IconButton(onClick = { send(draft) }, enabled = !sending && draft.isNotBlank()) {
                        Icon(Icons.Outlined.Send, contentDescription = "Send")
                    }
                }
            )
            Text("Jirani is an AI and can make mistakes. For payment disputes or safety concerns, use WhatsApp.", color = Muted, fontSize = 11.sp)
        }
    }
}
