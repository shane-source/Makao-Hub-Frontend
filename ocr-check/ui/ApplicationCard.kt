package com.makaohub.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.ApplicationNote
import com.makaohub.android.data.OwnerApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val avatarClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(15, TimeUnit.SECONDS).build()

@Composable internal fun ProfileAvatar(url: String?, modifier: Modifier = Modifier.size(48.dp)) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, url) {
        value = null
        if (url == null || !allowedHomeMedia(url)) return@produceState
        try { value = withContext(Dispatchers.IO) {
            avatarClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful)
                val bytes = response.body!!.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 5_242_880); out.write(buffer, 0, n) }; out.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth in 1..5000 && bounds.outHeight in 1..5000)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        } } catch (e: CancellationException) { throw e } catch (_: Exception) { value = null }
    }
    Box(modifier.clip(CircleShape).background(Sand), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Person, "Profile photo unavailable", tint = Brown)
    }
}

@Composable internal fun ApplicationCard(
    application: OwnerApplication, listing: String, note: ApplicationNote?, busy: Boolean,
    onView: () -> Unit, onRestore: () -> Unit, onChat: () -> Unit
) {
    AppAccentCard(application.status) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(application.renterAvatarUrl, Modifier.size(56.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(application.renterName ?: "Applicant", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                if (application.renterVerificationStatus == "VERIFIED") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(15.dp), tint = androidx.compose.ui.graphics.Color(0xFF417653))
                        Text("Verified renter", color = androidx.compose.ui.graphics.Color(0xFF417653), style = MaterialTheme.typography.labelMedium)
                    }
                }
                AppStatusBadge(application.status)
            }
        }
        Surface(color = androidx.compose.ui.graphics.Color(0xFFFBF3E8), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Home, null, tint = Brown, modifier = Modifier.size(18.dp))
                Text(listing, color = Brown, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Muted, modifier = Modifier.size(16.dp))
            Text("Submitted ${kenyaTime(application.createdAt, "d MMM yyyy · h:mm a")}", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        moveInLabel(application.moveInDate)?.let {
            Surface(color = androidx.compose.ui.graphics.Color(0xFFFFF4D6), shape = RoundedCornerShape(12.dp)) {
                Text("Wants to move in $it", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onView, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 46.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brown)) {
                Text("View application", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
            }
            if (note?.hidden == true) TextButton(onClick = onRestore, enabled = !busy) { Text("Restore") }
            Surface(onClick = onChat, enabled = !busy, shape = CircleShape, color = androidx.compose.ui.graphics.Color(0xFFFBF3E8)) {
                Icon(Icons.Outlined.ChatBubbleOutline, "Open applicant conversation", tint = Brown, modifier = Modifier.padding(12.dp).size(22.dp))
            }
        }
    }
}
