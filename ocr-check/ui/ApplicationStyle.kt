package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared look for application cards, used by the renter, landlord and sublessor screens. */
internal data class AppStatusLook(val label: String, val color: Color, val soft: Color, val icon: ImageVector, val hint: String)

private val WaitingColor = Color(0xFFA56C10)
private val ApprovedColor = Color(0xFF417653)

internal fun appStatusLook(status: String): AppStatusLook {
    val s = status.uppercase()
    val label = status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    return when {
        s.contains("REJECT") || s.contains("DECLIN") || s.contains("FAILED") ->
            AppStatusLook(label, Color(0xFFB24D45), Color(0xFFFBE9E7), Icons.Outlined.Cancel, "Not approved this time")
        s.contains("APPROV") || s.contains("ACCEPT") || s.contains("ACTIVE") ->
            AppStatusLook(label, ApprovedColor, Color(0xFFE4F2E8), Icons.Outlined.CheckCircle, "Approved")
        s.contains("WITHDRAW") || s.contains("CANCEL") || s.contains("EXPIRE") ->
            AppStatusLook(label, Color(0xFF7A7A7A), Color(0xFFEFEFEF), Icons.Outlined.RemoveCircleOutline, "No longer active")
        else ->
            AppStatusLook(label, WaitingColor, Color(0xFFFFF1CC), Icons.Outlined.HourglassTop, "Waiting for a response")
    }
}

@Composable internal fun AppStatusBadge(status: String) {
    val look = appStatusLook(status)
    Surface(color = look.soft, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(look.icon, null, tint = look.color, modifier = Modifier.size(15.dp))
            Text(look.label, color = look.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** White rounded card with a coloured edge that matches the application's status. */
@Composable internal fun AppAccentCard(status: String, content: @Composable ColumnScope.() -> Unit) {
    val look = appStatusLook(status)
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFEBD9BF)),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(7.dp).fillMaxHeight().background(look.color))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable private fun SummaryTile(value: Int, label: String, color: Color, soft: Color, modifier: Modifier) {
    Surface(color = soft, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = color, fontSize = 12.sp)
        }
    }
}

/** Three small totals above a list of applications. */
@Composable internal fun AppSummaryRow(statuses: List<String>) {
    if (statuses.isEmpty()) return
    val looks = statuses.map { appStatusLook(it) }
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile(statuses.size, "Total", Brown, Color(0xFFFBF3E8), Modifier.weight(1f))
        SummaryTile(looks.count { it.color == WaitingColor }, "Waiting", WaitingColor, Color(0xFFFFF1CC), Modifier.weight(1f))
        SummaryTile(looks.count { it.color == ApprovedColor }, "Approved", ApprovedColor, Color(0xFFE4F2E8), Modifier.weight(1f))
    }
}
