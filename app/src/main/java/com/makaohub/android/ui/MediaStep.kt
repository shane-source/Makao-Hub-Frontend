package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared by the landlord and sublessor "photos and video" step so both look the same. */

/** Four-segment progress bar, the step label, a big title and one friendly line. */
@Composable
internal fun MediaStepHeader(step: Int, total: Int, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (index < step) Brown else Brown.copy(alpha = 0.15f))
                )
            }
        }
        Text("STEP $step OF $total · PHOTOS AND VIDEO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(title, color = Brown, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** The big "add photos" card with a count and a progress bar toward the plan's photo limit. */
@Composable
internal fun PhotoAddCard(count: Int, limit: Int, enabled: Boolean, onAdd: () -> Unit) {
    val unlimited = limit == Int.MAX_VALUE
    val fraction = if (unlimited || limit <= 0) 0f else (count.toFloat() / limit).coerceIn(0f, 1f)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Sand.copy(alpha = 0.55f),
        border = BorderStroke(1.5.dp, Brown.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Brown), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(
                if (count == 0) "Add your photos" else "$count ${if (count == 1) "photo" else "photos"} added",
                color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Text(
                if (unlimited) "Add as many as you like · JPG, PNG or WebP" else "$count of $limit photos · JPG, PNG or WebP",
                color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            if (!unlimited) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brown.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Brown))
                }
            }
            Button(
                onClick = onAdd,
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brown),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (count == 0) "Choose photos" else "Add more photos", fontWeight = FontWeight.SemiBold) }
            Text("Your first photo becomes the cover.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** The optional video walkthrough card. */
@Composable
internal fun VideoAddCard(
    hasVideo: Boolean,
    confirmed: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onManage: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Brown.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Sand), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = Brown, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Video walkthrough", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !hasVideo -> "Optional · MP4 · a short tour builds trust"
                        confirmed -> "Upload complete"
                        else -> "Awaiting confirmation"
                    },
                    color = Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
            if (hasVideo) {
                OutlinedButton(onClick = onManage, enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text("Manage", color = Brown) }
            } else {
                Button(
                    onClick = onAdd, enabled = enabled, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brown)
                ) { Text("Add video") }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** Three quick tips so the first photos are good. */
@Composable
internal fun PhotoTipsCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = Sand.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Photo tips", color = Brown, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("•  Shoot in daylight and open the curtains", color = Ink, fontSize = 13.sp)
            Text("•  Hold your phone sideways for wider rooms", color = Ink, fontSize = 13.sp)
            Text("•  Tidy first. Clean rooms get more enquiries", color = Ink, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(20.dp))
}
