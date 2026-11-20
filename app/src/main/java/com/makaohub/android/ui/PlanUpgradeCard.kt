package com.makaohub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.LandlordOffers
import kotlinx.coroutines.CancellationException

/**
 * Dashboard upgrade card. Every number comes from the server's plan catalogue (the same data the
 * Plans screen and checkout use), so the card can never disagree with what is actually charged.
 */
@Composable
internal fun PlanUpgradeCard(role: String, offers: suspend () -> LandlordOffers, enabled: Boolean, onOpen: () -> Unit) {
    var info by remember { mutableStateOf<LandlordOffers?>(null) }
    LaunchedEffect(Unit) {
        try { info = offers() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The card still works as a link to the Plans screen. */ }
    }
    val max = info?.offers?.firstOrNull { it.code.removePrefix("SUB_") == "PRO_MAX_30D" }
    val onMax = info?.activeGrants.orEmpty().any { it.listingId == null && it.code.contains("PRO_MAX") }

    val title = if (max != null || onMax) "Makao $role Pro Max" else "Makao $role Plans"
    val tagline = when {
        onMax -> "Your plan is active"
        max != null -> "Scale your rental yield"
        else -> "Free, Pro and Pro Max"
    }
    val body = when {
        max == null -> "See photo limits, prices and the boost included with Pro Max."
        else -> {
            val photos = if (max.photoLimit < 0) "Unlimited photos per listing" else "${max.photoLimit} photos per listing"
            val boost = if (max.includedBoosts > 0) " and ${max.includedBoosts} free 7-day boost included" else ""
            val ready = if (onMax && (info?.boostCredits ?: 0) > 0) " Your included boost is ready to use." else ""
            "$photos, up to ${max.listingLimit} active listings$boost.$ready"
        }
    }
    val action = when { onMax -> "Manage plan"; max != null -> "Upgrade now"; else -> "View plans" }

    Surface(onClick = onOpen, enabled = enabled, shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF6B2F08), Color(0xFF3E1904)))).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFDEA5)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.WorkspacePremium, null, tint = Color(0xFF6B2F08))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(tagline, color = Color(0xFFE8946A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(body, color = Color(0xFFE6D5CB), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (max != null && !onMax) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("KSh " + "%,d".format(max.amountMinor / 100), color = Color(0xFFFFDEA5), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(" / ${max.days} days", color = Color(0xFFE6D5CB), fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                } else Spacer(Modifier.width(1.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFFFDEA5)) {
                    Text(action, color = Color(0xFF3E1904), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }
    }
}
