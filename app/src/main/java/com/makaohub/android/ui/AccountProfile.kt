package com.makaohub.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.AuthViewModel
import com.makaohub.android.data.MyProfile
import com.makaohub.android.data.OwnerVerificationDocument

private const val SUPPORT_WHATSAPP_NUMBER = "+254718450706"
private const val SUPPORT_WHATSAPP_URL = "https://wa.me/254718450706"

data class ProfileAction(val title: String, val icon: ImageVector, val action: () -> Unit)

@Composable
fun AccountProfile(
    profile: MyProfile,
    status: String,
    busy: Boolean,
    auth: AuthViewModel,
    documents: List<OwnerVerificationDocument> = emptyList(),
    actions: List<ProfileAction> = emptyList(),
    onRefreshDocuments: () -> Unit = {},
    onVerification: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    CelebrateNew("verified", if (status == "VERIFIED") listOf("me") else emptyList(), true, "You're verified!", "Your badge is live. Renters and owners can now trust your profile.")
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { auth.uploadAvatar(context.contentResolver, it) }
    }
    var removingPhoto by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<LegalDraft?>(null) }
    var accountAction by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var supportError by remember { mutableStateOf<String?>(null) }
    val appearance = LocalAppearanceChoice.current
    BackHandler(enabled = section != null) { section = null }

    if (section != null) {
        TextButton({ section = null }) { Text(if (section == "support") "← Profile" else "‹ Back to profile") }
        if (section == "appearance") {
            Text("Appearance", style = MaterialTheme.typography.headlineMedium, color = Brown)
            Gap(16)
            AppearanceMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(appearance.mode == mode, { appearance.choose(mode) })
                    Text(mode.label)
                }
            }
        } else if (section == "privacy") {
            Text("Privacy and account", style = MaterialTheme.typography.headlineMedium, color = Brown)
            Gap(16)
            ProfileCard {
                Text(profile.email, color = Ink)
                profile.phoneNumber?.let { Text(it, color = Muted) }
                ProfileRow("Privacy notice", Icons.Outlined.PrivacyTip, true) { legal = LegalDraft.PRIVACY }
                ProfileRow("Terms of Service", Icons.Outlined.Description, true) { legal = LegalDraft.TERMS }
                ProfileRow("Deactivate account", Icons.Outlined.PauseCircle, !busy) { accountAction = "DEACTIVATE" }
                ProfileRow("Request account deletion", Icons.Outlined.DeleteOutline, !busy) { accountAction = "DELETE" }
            }
            legal?.let { AccountLegalDialog(it) { legal = null } }
            accountAction?.let { action -> AccountActionDialog(action, profile.email, auth) { auth.clearAccountAction(); accountAction = null } }
        } else if (section == "support") {
            val who = "a " + profile.role.lowercase(java.util.Locale.ROOT)
            fun openSupport(about: String?) {
                supportError = null
                val message = "Hello Makao Hub support. I'm $who and I need help" +
                    (about?.let { " with $it" } ?: "") + "."
                val url = Uri.parse(SUPPORT_WHATSAPP_URL).buildUpon().appendQueryParameter("text", message).build()
                try { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                catch (_: ActivityNotFoundException) { supportError = "No app is available to open WhatsApp. You can save the number and message us directly." }
            }
            Text("Help and support", style = MaterialTheme.typography.headlineMedium, color = Brown)
            Gap(12)
            Text("Ask Jirani, our AI helper, or talk to the Makao Hub team on WhatsApp.", color = Muted)
            Gap(16)
            JiraniChat(profile.role, auth.repository)
            Gap(16)
            Button(onClick = { openSupport(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chat with support on WhatsApp")
            }
            Text("Support number: $SUPPORT_WHATSAPP_NUMBER", color = Muted)
            supportError?.let { Gap(8); Text(it, color = MaterialTheme.colorScheme.error) }
            Gap(24)
            ProfileSectionLabel("CHOOSE A TOPIC")
            val topics = listOf(
                Triple("Verification", "Documents, review and your badge", "verifying my account"),
                Triple("Payments and payouts", "Bookings, deposits and payout numbers", "a payment or payout"),
                Triple("Listings", "Creating, editing or boosting a listing", "my listings"),
                Triple("Sign-in and account", "Codes, passwords and account changes", "signing in or my account"),
                Triple("Something else", "Tell us what happened", "something else")
            )
            ProfileMenu {
                topics.forEachIndexed { index, (label, note, about) ->
                    if (index > 0) HorizontalDivider(color = Border)
                    TextButton(onClick = { openSupport(about) }, shape = RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Brown, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(note, color = Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                }
            }
            Gap(24)
            ProfileSectionLabel("STAY SAFE")
            ProfileCard {
                Text("Support will never ask for your password or the 6-digit code we email you. Don't share them with anyone.", color = Ink)
                Text("Use WhatsApp only to reach Makao Hub support. Talk to renters, landlords and roommates inside Messages, where chats are protected.", color = Ink)
            }
        } else if (section == "about") {
            Text("About Makao Hub", style = MaterialTheme.typography.headlineMedium, color = Brown)
            Gap(16)
            ProfileCard {
                Text("Find a place. Find your people.", color = Brown, fontWeight = FontWeight.Bold)
                Text("Makao Hub brings renters, landlords and sublessors together to find homes and shared spaces.", color = Ink)
                Text("Renters can explore and save listings, apply for homes and chat with hosts. Landlords can manage properties and applications, while sublessors can offer shared spaces and connect with potential roommates.", color = Ink)
                Text("Verification and protected messaging help people navigate each step with more confidence.", color = Muted)
            }
        } else {
            Text("Verification documents", style = MaterialTheme.typography.headlineMedium, color = Brown)
            Gap(12)
            Text("Verification status: ${status.lowercase().replace('_', ' ')}")
            Gap(12)
            val required = listOf("ID_FRONT" to "National ID (front)", "ID_BACK" to "National ID (back)", "SELFIE" to "Selfie") +
                when (profile.role) { "LANDLORD" -> listOf("OWNERSHIP_PROOF" to "Proof of ownership"); "SUBLESSOR" -> listOf("RENTAL_AGREEMENT" to "Rental agreement"); else -> emptyList() }
            required.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (key, label) ->
                        val document = documents.firstOrNull { it.documentType == key }
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Column(Modifier.padding(14.dp)) {
                                Icon(if (key == "SELFIE") Icons.Outlined.Face else Icons.Outlined.Description, null, tint = Brown)
                                Gap(8); Text(label, fontWeight = FontWeight.SemiBold)
                                Text(if (document?.status == "READY" && document.securityChecked) "Ready for review" else if (document != null) "Processing" else "Not uploaded", color = Muted)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Gap(10)
            }
            TextButton(onRefreshDocuments, enabled = !busy) { Text("Refresh status") }
            Button(onVerification, enabled = !busy) { Text("Manage verification") }
        }
        return
    }

    Gap(20)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ProfileAvatar(profile.avatarUrl, Modifier.size(78.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.fullName, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(profile.role.lowercase().replaceFirstChar { it.uppercase() } + " account", color = Muted, fontSize = 14.sp)
            Text(status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                color = if (status == "VERIFIED") Color(0xFF417653) else Muted, fontSize = 12.sp)
            Row {
                TextButton({ photo.launch("image/*") }, enabled = !busy && !auth.busy, contentPadding = PaddingValues(0.dp)) {
                    Text(if (profile.avatarUrl == null) "Add photo" else "Change photo", color = Brown)
                }
                if (profile.avatarUrl != null) {
                    Spacer(Modifier.width(16.dp))
                    TextButton({ removingPhoto = true }, enabled = !busy && !auth.busy, contentPadding = PaddingValues(0.dp)) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (auth.busy) Text("Updating account…", color = Muted, fontSize = 12.sp)
            auth.message?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
    }
    if (removingPhoto) AlertDialog(onDismissRequest = { removingPhoto = false },
        title = { Text("Remove profile photo?") },
        text = { Text("Your profile will show the default icon instead.") },
        confirmButton = { TextButton(onClick = { removingPhoto = false; auth.deleteAvatar() }, enabled = !auth.busy) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removingPhoto = false }) { Text("Cancel") } })
    Gap(34)
    ProfileSectionLabel("YOUR ACCOUNT")
    ProfileMenu {
        actions.forEachIndexed { index, action ->
            if (index > 0) HorizontalDivider(color = Border)
            ProfileRow(action.title, action.icon, !busy, action.action)
        }
        if (actions.isNotEmpty()) HorizontalDivider(color = Border)
        ProfileRow("Verification documents", Icons.Outlined.VerifiedUser, !busy) { section = "documents"; onRefreshDocuments() }
    }
    Gap(24)
    ProfileSectionLabel("ACCOUNT SETTINGS")
    ProfileMenu {
        ProfileRow("Appearance", Icons.Outlined.Palette, !busy) { section = "appearance" }
        HorizontalDivider(color = Border)
        ProfileRow("Privacy and account", Icons.Outlined.Security, !busy) { section = "privacy" }
    }
    Gap(16)
    ProfileMenu { ProfileRow("Help & Support", Icons.Outlined.HelpOutline, !busy) { supportError = null; section = "support" } }
    Gap(10)
    ProfileMenu { ProfileRow("About Makao Hub", Icons.Outlined.Info, !busy) { section = "about" } }
    Gap(22)
    OutlinedButton(onLogout, enabled = !busy && !auth.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) { Text("Log out", color = Brown, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun ProfileSectionLabel(label: String) {
    Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    Gap(12)
}

@Composable private fun ProfileMenu(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
    Gap(16)
}

@Composable private fun ProfileRow(title: String, icon: ImageVector, enabled: Boolean, action: () -> Unit) {
    TextButton(action, enabled = enabled, shape = RoundedCornerShape(0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
        Icon(icon, null, tint = Brown, modifier = Modifier.size(23.dp)); Spacer(Modifier.width(20.dp))
        Text(title, modifier = Modifier.weight(1f), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
    }
}

@Composable private fun AccountActionDialog(action: String, accountEmail: String, auth: AuthViewModel, close: () -> Unit) {
    var email by remember { mutableStateOf(accountEmail) }
    var code by remember { mutableStateOf("") }
    val destructive = action == "DELETE"
    AlertDialog(onDismissRequest = close,
        title = { Text(if (destructive) "Request account deletion" else "Deactivate account") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (destructive) "Deletion disables sign-in while staff review the request." else "You can reactivate later by signing in and confirming an email code.")
            OutlinedTextField(email, { email = it }, label = { Text("Account email") }, singleLine = true)
            if (auth.accountActionChallenge == null) {
                Button({ auth.startAccountAction(email, action) }, enabled = !auth.busy) { Text("Send email code") }
            } else {
                Text("A code was sent to ${auth.accountActionChallenge?.maskedEmail}.")
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Six-digit code") }, singleLine = true)
                Button({ auth.confirmAccountAction(email, action, code) }, enabled = !auth.busy && code.length == 6) { Text("Confirm") }
            }
            auth.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {}, dismissButton = { TextButton(close) { Text("Cancel") } })
}
