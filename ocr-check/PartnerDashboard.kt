package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerApplication

internal val DashboardBackground = Color(0xFFFAF7F4)
private val DashboardTile = Color(0xFFF1E8E3)

/** The numbers and activity are supplied by the authenticated role's server responses. */
@Composable internal fun PartnerDashboard(
    role: String, verification: String, active: Int?, pending: Int?, drafts: Int?,
    applications: List<OwnerApplication>, applicationsLoaded: Boolean, listingTitle: (String) -> String?, busy: Boolean,
    plansOffers: suspend () -> com.makaohub.android.data.LandlordOffers,
    onPlans: () -> Unit, onCreate: () -> Unit, onApplications: () -> Unit,
    onBoost: () -> Unit, onApplication: (OwnerApplication) -> Unit,
    onMessages: () -> Unit, onPayments: () -> Unit, onVerify: () -> Unit
) {
    CelebrateNew("verified", if (verification == "VERIFIED") listOf("me") else emptyList(), true, "You're verified!", "Your badge is live. Renters and owners can now trust your profile.")
    if (verification == "VERIFIED") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = DashboardTile, shape = RoundedCornerShape(28.dp)) {
                Text("Verified $role", color = Ink, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
    Text("$role Dashboard", fontFamily = FontFamily.Serif, fontSize = 30.sp,
        lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Brown)
    Text(if (role == "Landlord") "Manage your properties and renters" else "Manage your shared spaces and renters", color = Muted, fontSize = 14.sp)
    Gap(24)
    if (verification != "VERIFIED") {
        TextButton(onClick = onVerify, enabled = !busy) { Text("Complete your verification") }
    }
    PlanUpgradeCard(role, plansOffers, !busy, onPlans)
    Gap(22)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardMetric("Active listings", active, Color(0xFF3D7655), Modifier.weight(1f))
        DashboardMetric("Pending applications", pending, Color(0xFFD08A1F), Modifier.weight(1f))
        DashboardMetric("Draft listings", drafts, Color(0xFF8A7B72), Modifier.weight(1f))
    }
    Gap(24)
    DashboardTitle("Quick Actions")
    Gap(12)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardAction("Add new listing", Icons.Outlined.AddHome, !busy, Modifier.weight(1f), onCreate, primary = true)
        DashboardAction("Applications", Icons.Outlined.Description, !busy, Modifier.weight(1f), onApplications)
        DashboardAction("Boost listing", Icons.Outlined.AutoAwesome, !busy, Modifier.weight(1f), onBoost)
    }
    Gap(24)
    DashboardTitle("Recent Applications")
    Gap(12)
    if (!applicationsLoaded) Text("Loading applications…", color = Muted, fontSize = 14.sp)
    else if (applications.isEmpty()) EmptyState(Icons.Outlined.Description, "No applications yet", "When renters apply, they will show up here.")
    applications.sortedByDescending { it.createdAt }.take(3).forEach { application ->
        Surface(onClick = { onApplication(application) }, enabled = !busy, color = Color.White,
            shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(listingTitle(application.listingId) ?: "Listing unavailable", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(application.status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 12.sp)
            }
        }
        Gap(9)
    }
}

@Composable private fun DashboardTitle(value: String) {
    Text(value, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Brown)
}

@Composable private fun DashboardMetric(label: String, value: Int?, accent: Color, modifier: Modifier) {
    Surface(modifier = modifier.heightIn(min = 118.dp), color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(25.dp))
            Text(value?.toString() ?: "—", color = accent, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
    }
}

@Composable private fun DashboardAction(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, action: () -> Unit, primary: Boolean = false) {
    Surface(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 108.dp), color = if (primary) Brown else DashboardTile, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (primary) Color.White else Brown, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(18.dp))
            Text(label, color = if (primary) Color.White else Brown, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
