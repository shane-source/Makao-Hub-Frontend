package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.HomeMedia
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import java.util.Locale

@Composable internal fun PartnerListingHeader(
    role: String, selected: String, counts: Map<String, Int>, total: Int, busy: Boolean,
    select: (String) -> Unit, create: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (role == "LANDLORD") "Your Properties" else "Your Shared Spaces", modifier = Modifier.weight(1f),
            color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        TextButton({ select("ALL") }, enabled = !busy) { Text("View all ($total)", color = Brown) }
    }
    Gap(12)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("PUBLISHED" to "Active", "DRAFT" to "Drafts", "PAUSED" to "Paused").forEach { (status, label) ->
            FilterChip(selected = selected == status, onClick = { select(status) },
                label = { Text("$label (${counts[status] ?: 0})", fontSize = 11.sp) },
                enabled = !busy, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF1E8E3)))
        }
    }
    Gap(16)
    PrimaryButton("Add new listing", !busy, create)
    Gap(20)
}

@Composable internal fun PartnerListingCard(
    id: String, title: String, status: String, rent: Long, detail: String, available: String?,
    views: Long?, leads: Int?, busy: Boolean, cover: suspend (String) -> HomeMedia?,
    onLeads: () -> Unit, onManage: () -> Unit, onUpdate: () -> Unit, onDelete: () -> Unit, boost: @Composable () -> Unit
) {
    var managing by remember(id) { mutableStateOf(false) }
    var confirmingUpdate by remember(id) { mutableStateOf(false) }
    var confirmingDelete by remember(id) { mutableStateOf(false) }
    val image by produceState<HomeMedia?>(null, id) {
        value = try { cover(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFFF1E8E3)), contentAlignment = Alignment.Center) {
                if (image != null) RemoteHomePhoto(image!!.signedReadUrl, Modifier.fillMaxSize())
                else Icon(Icons.Outlined.Photo, "Listing photo unavailable", tint = Muted, modifier = Modifier.size(38.dp))
                Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB3000000))))
                Text("KSh ${NumberFormat.getIntegerInstance(Locale.UK).format(rent)} / month", color = Color.White,
                    fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 14.dp))
                Surface(color = when (status) { "PUBLISHED" -> Color(0xFFE6F0E8); "PAUSED" -> Color(0xFFFAEECB); else -> Color(0xFFF8F2EF) },
                    shape = RoundedCornerShape(24.dp), modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Text(status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Ink,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Muted, fontSize = 12.sp)
                available?.let { Text("Available $it", color = Muted, fontSize = 12.sp) }
                Text(views?.let { "$it views today" } ?: "Views unavailable", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLeads, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brown)) { Text(leads?.let { "View leads ($it)" } ?: "View leads", fontSize = 12.sp) }
                    OutlinedButton(onClick = { managing = true }, enabled = !busy, shape = RoundedCornerShape(30.dp)) { Text("Manage", fontSize = 12.sp) }
                }
                boost()
            }
        }
    }
    Gap(18)
    if (managing) AlertDialog(onDismissRequest = { managing = false }, title = { Text("Manage listing") },
        text = { Column {
            Text(title)
            TextButton(onClick = { managing = false; confirmingUpdate = true }, enabled = !busy) { Text("Update listing") }
            TextButton(onClick = { managing = false; onManage() }, enabled = !busy) { Text("Review and status") }
            TextButton(onClick = { managing = false; confirmingDelete = true }, enabled = !busy) { Text("Delete listing", color = Color(0xFFAB4B3F)) }
        } }, confirmButton = { TextButton(onClick = { managing = false }) { Text("Close") } })
    if (confirmingUpdate) AlertDialog(onDismissRequest = { confirmingUpdate = false }, title = { Text("Update listing?") },
        text = { Text("A published listing will be removed from search while you edit it. Submit your changes for review before it can be published again.") },
        confirmButton = { TextButton(onClick = { confirmingUpdate = false; onUpdate() }, enabled = !busy) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { confirmingUpdate = false }) { Text("Cancel") } })
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false }, title = { Text("Delete listing?") },
        text = { Text("This removes the listing from your listings and public search. It is archived to keep application and payment records.") },
        confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete() }, enabled = !busy) { Text("Delete listing", color = Color(0xFFAB4B3F)) } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } })
}
