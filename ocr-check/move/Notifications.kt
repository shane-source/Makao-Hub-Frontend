package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
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

@Composable
fun NotificationHost(auth: AuthViewModel, profile: MyProfile, content: @Composable () -> Unit) {
    val model: NotificationViewModel = viewModel(key = "notifications-${profile.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotificationViewModel(NotificationRepository(auth.repository)) { auth.navigate(Page.LOGIN) } as T
    })
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.refresh() }
    DisposableEffect(model) { onDispose(model::clear) }
    Box(Modifier.fillMaxSize()) {
        content()
        BadgedBox(
            badge = { if (model.unreadCount > 0) Badge { Text(model.unreadCount.coerceAtMost(99).toString()) } },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp)
        ) {
            Surface(shape = CircleShape, tonalElevation = 5.dp) {
                IconButton({ open = true; model.refresh() }) {
                    Icon(Icons.Outlined.Notifications, "Notifications")
                }
            }
        }
    }
    if (open) NotificationInbox(model.notifications, model.busy, model.error, { open = false }, model::markRead)
}

@Composable
private fun NotificationInbox(items: List<InboxNotification>, busy: Boolean, error: String?, close: () -> Unit,
    read: (InboxNotification) -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Notifications") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!busy && items.isEmpty()) Text("You’re all caught up.")
                items.forEach { item ->
                    Card(onClick = { read(item) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(item.body, style = MaterialTheme.typography.bodyMedium)
                            if (!item.read) Text("New", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(close) { Text("Close") } }
    )
}
