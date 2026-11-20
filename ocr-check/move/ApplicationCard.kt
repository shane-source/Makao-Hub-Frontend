package com.makaohub.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    SharedCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(application.renterAvatarUrl)
            Column(Modifier.weight(1f)) {
                Text(application.renterName ?: "Applicant", color = Ink, fontWeight = FontWeight.Bold)
                if (application.renterVerificationStatus == "VERIFIED") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp), tint = androidx.compose.ui.graphics.Color(0xFF417653))
                        Text("Verified renter", color = androidx.compose.ui.graphics.Color(0xFF417653), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(listing, modifier = Modifier.weight(1f), color = Brown, style = MaterialTheme.typography.labelMedium)
        }
        val approved = application.status == "APPROVED"
        Text(application.status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            color = if (approved) androidx.compose.ui.graphics.Color(0xFF417653) else Muted)
        Text("Submitted ${kenyaTime(application.createdAt, "d MMM yyyy · h:mm a")} EAT", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onView, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                Text("View application", color = Brown, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            if (note?.hidden == true) TextButton(onClick = onRestore, enabled = !busy) { Text("Restore") }
            IconButton(onClick = onChat, enabled = !busy) { Icon(Icons.Outlined.ChatBubbleOutline, "Open applicant conversation", tint = Brown) }
        }
    }
}
