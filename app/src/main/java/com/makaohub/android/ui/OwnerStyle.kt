package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerApplication

/** Picks a banner icon that matches the screen title. */
internal fun headingIcon(title: String): ImageVector {
    val t = title.lowercase()
    return when {
        "payment" in t || "payout" in t -> Icons.Outlined.Payments
        "application" in t || "interested" in t -> Icons.Outlined.Description
        "photo" in t || "media" in t || "show the home" in t -> Icons.Outlined.Image
        "location" in t -> Icons.Outlined.Place
        "preview" in t -> Icons.Outlined.Visibility
        "publish" in t || "check" in t || "review" in t -> Icons.Outlined.CheckCircle
        "message" in t || "chat" in t || "inbox" in t -> Icons.Outlined.ChatBubbleOutline
        "listing" in t || "propert" in t || "home" in t -> Icons.Outlined.Apartment
        else -> Icons.Outlined.Home
    }
}

/** Heading banner used at the top of every owner-side screen. */
@Composable internal fun OwnerBanner(title: String, subtitle: String) {
    HeroBanner(Brown, headingIcon(title), title, subtitle)
}

/** Small coloured pill for a status such as Verified, Pending or Rejected. */
@Composable internal fun StatusPill(status: String, label: String? = null) {
    val s = status.uppercase()
    val good = s.startsWith("VERIFIED") || s in listOf("PUBLISHED", "APPROVED", "SUCCEEDED", "OPEN", "PAID")
    val bad = s in listOf("REJECTED", "FAILED")
    val color = when { good -> Color(0xFF417653); bad -> Color(0xFFB24D45); else -> Color(0xFFA56C10) }
    val soft = when { good -> Color(0xFFE4F2E8); bad -> Color(0xFFFBE9E7); else -> Color(0xFFFFF1CC) }
    val icon = when { good -> Icons.Outlined.CheckCircle; bad -> Icons.Outlined.Cancel; else -> Icons.Outlined.HourglassTop }
    val text = label ?: status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    Surface(color = soft, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** White rounded card with a soft shadow and an optional bold title. */
@Composable internal fun StyledCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFEBD9BF)),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            title?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(5.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(Brown))
                    Text(it, color = Brown, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            content()
        }
    }
}

/** Banner for a single application: applicant, home and a status colour. */
@Composable internal fun ApplicationHero(app: OwnerApplication, listingTitle: String) {
    val look = appStatusLook(app.status)
    HeroBanner(look.color, look.icon, app.renterName ?: "Applicant", "$listingTitle · ${look.label}")
}

/** Three steps: received, under review, decision. */
@Composable internal fun ReviewTracker(status: String) {
    val look = appStatusLook(status)
    val s = status.uppercase()
    val step = when (s) { "SUBMITTED" -> 0; "UNDER_REVIEW" -> 1; else -> 2 }
    val labels = listOf("Received", "Under review", if (step == 2) look.label else "Decision")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            val reached = index <= step
            val tint = if (index == 2 && reached) look.color else Brown
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (reached) tint else Color(0xFFEBD9BF)), contentAlignment = Alignment.Center) {
                    if (index < step || (index == 2 && step == 2)) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    else Text("${index + 1}", color = if (reached) Color.White else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = if (reached) Ink else Muted, fontSize = 12.sp, fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** One fact with a small icon, a muted label and a bold value. */
@Composable internal fun FactRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color(0xFFFBF3E8), shape = CircleShape) {
            Icon(icon, null, tint = Brown, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, fontSize = 12.sp)
            Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        }
    }
}

/** Renter's message shown as a quote block. */
@Composable internal fun MessageQuote(text: String) {
    Surface(color = Color(0xFFFBF3E8), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Message from the renter", color = Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

/** Everything the owner needs to know about an application, above the action buttons. */
@Composable internal fun ApplicationFacts(app: OwnerApplication, listingTitle: String) {
    ApplicationHero(app, listingTitle)
    HighlightCard { ReviewTracker(app.status) }
    HighlightCard {
        FactRow(Icons.Outlined.Apartment, "Home applied for", listingTitle)
        FactRow(Icons.Outlined.Schedule, "Submitted", kenyaTime(app.createdAt, "d MMM yyyy · h:mm a"))
        moveInLabel(app.moveInDate)?.let { FactRow(Icons.Outlined.Event, "Wants to move in", it) }
        app.message?.takeIf { it.isNotBlank() }?.let { MessageQuote(it) }
        app.rejectionReason?.takeIf { it.isNotBlank() }?.let { FactRow(Icons.Outlined.Cancel, "Reason given", it) }
    }
}
