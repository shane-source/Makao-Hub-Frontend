package com.makaohub.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A friendly empty screen: a soft icon, a short line, and one clear next step. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(50), color = Sand) {
            Icon(icon, contentDescription = null, tint = Brown, modifier = Modifier.padding(22.dp).size(34.dp))
        }
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(16.dp)) { Text(action) }
        }
    }
}
