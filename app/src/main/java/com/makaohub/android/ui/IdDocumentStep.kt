package com.makaohub.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.data.OwnerVerificationDocument

@Composable
internal fun VerificationIntro(role: String, subtitle: String, roleDocument: String?, onStart: () -> Unit) {
    Heading("Verify as a ${role.lowercase()}")
    Gap(22)
    BodyCopy(subtitle, muted = true)
    Gap(22)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(Color(0xFFA56C10), CircleShape))
        Text("$role · Not yet verified", color = Color(0xFF966313), fontSize = 14.sp)
    }
    Gap(22)
    IdCard {
        Text("Your checklist", color = Brown, fontSize = 19.sp)
        Gap(28)
        ChecklistItem("Front of your ID", "Clear, uncropped image")
        ChecklistItem("Back of your ID", "Required for every role")
        ChecklistItem("A fresh selfie", "Face visible, no filters")
        ChecklistItem(roleDocument ?: "No ownership document needed",
            if (roleDocument == null) "Your identity checks are enough for this step." else "Private supporting evidence")
    }
    Gap(20)
    IdNote("Private by design\nOnly authorised Makao Hub verification staff can access these documents. Identity evidence and role documents are never published or shown to marketplace users.", false)
    Gap(4)
    BodyCopy("Review outcome is shown in your profile and notifications. Public badges show only your verified role.", muted = true)
    Gap(22)
    PrimaryButton("Start verification", onClick = onStart)
}

@Composable private fun ChecklistItem(title: String, detail: String) {
    BodyCopy("$title\n$detail")
    Gap(22)
}

@Composable
internal fun VerificationSelfieStep(ready: Boolean, busy: Boolean, onTake: () -> Unit) {
    Heading("A quick selfie")
    Gap(22)
    BodyCopy("Step 3 of 3 · Let’s make sure it’s you", muted = true)
    Gap(22)
    Box(Modifier.fillMaxWidth().height(260.dp).background(Sand, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.width(166.dp).height(206.dp).border(2.dp, Brown, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            BodyCopy(if (ready) "Selfie uploaded\nReady for review" else "Position your face\ninside the guide")
        }
    }
    Gap(22)
    BodyCopy("Good light, no sunglasses, no filters. Camera permission is requested only when you take a photo.")
    Gap(20)
    IdNote("Camera permission denied?\nOpen device settings to allow camera access. No document is submitted automatically.", true)
    Gap(20)
    PrimaryButton(if (ready) "Continue" else if (busy) "Uploading selfie…" else "Take selfie", !busy, onTake)
}

@Composable
internal fun VerificationRoleDocumentStep(
    title: String,
    subtitle: String,
    label: String,
    name: String?,
    ready: Boolean,
    busy: Boolean,
    guidance: String,
    privacy: String,
    choose: () -> Unit,
    next: () -> Unit
) {
    Heading(title)
    Gap(22)
    BodyCopy(subtitle, muted = true)
    Gap(22)
    IdCard {
        BodyCopy(label)
        BodyCopy(when { busy -> "Uploading…"; ready -> name?.let { "$it · Uploaded" } ?: "Uploaded"; else -> "Ready to upload" })
        BodyCopy("Private · Authorised verification staff only")
        Gap(16)
        TextButton(choose, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (ready) "Replace file" else "Choose PDF", fontWeight = FontWeight.SemiBold)
        }
    }
    Gap(22)
    IdCard { BodyCopy(guidance) }
    Gap(20)
    IdNote(privacy, false)
    Gap(20)
    PrimaryButton("Save and review", !busy && ready, next)
}

@Composable
internal fun VerificationReviewSummary(roleDocument: String?, ready: Boolean, busy: Boolean, submit: () -> Unit, replace: () -> Unit) {
    Heading("Ready for review")
    Gap(22)
    BodyCopy("Check your private uploads before submitting.", muted = true)
    Gap(22)
    IdCard {
        Text("Progress", color = Brown, fontSize = 19.sp)
        Gap(28)
        ReviewLine("ID front")
        ReviewLine("ID back")
        ReviewLine("Selfie")
        roleDocument?.let { ReviewLine(it) }
    }
    Gap(20)
    IdNote("What happens next\nAuthorised staff review your evidence. We’ll notify you when your role is approved or if something needs replacing.", false)
    Gap(4)
    BodyCopy("Submitting does not publish your documents. The public badge appears only after approval.", muted = true)
    Gap(22)
    PrimaryButton("Submit verification", !busy && ready, submit)
    TextButton(replace, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Replace a document", fontWeight = FontWeight.SemiBold) }
}

@Composable private fun ReviewLine(label: String) {
    BodyCopy("$label · ✓\nUploaded · private")
    Gap(22)
}

@Composable
internal fun VerificationOutcomeScreen(
    role: String,
    status: String,
    rejectionReason: String?,
    busy: Boolean,
    approvedAction: String,
    onApproved: () -> Unit,
    onCorrect: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    Heading("$role review status")
    Gap(22)
    BodyCopy("Your verification result", muted = true)
    Gap(22)
    when (status) {
        "VERIFIED" -> IdCard {
            Text("Approved", color = Ink, fontSize = 19.sp)
            Gap(18)
            Text("● Verified $role", color = Color(0xFF417653), fontSize = 14.sp)
            Gap(18)
            BodyCopy("Your role has been approved. The badge is now visible on your public role profile.")
            Gap(18)
            PrimaryButton(approvedAction, !busy, onApproved)
        }
        "REJECTED" -> IdCard {
            Text("Rejected · action needed", color = Ink, fontSize = 19.sp)
            Gap(18)
            Text("● Re-upload required", color = Color(0xFFB24D45), fontSize = 14.sp)
            Gap(18)
            BodyCopy(rejectionReason?.takeIf { it.isNotBlank() } ?: "A document needs replacing. Upload a clear matching file and submit again.")
            Gap(18)
            PrimaryButton("Re-submit verification", !busy, onCorrect)
        }
        else -> IdNote("Verification pending\nYour evidence is with authorised staff. You can keep browsing while we review.", true)
    }
    Gap(14)
    if (status == "PENDING") PrimaryButton("Close & Back to Main", !busy, onClose)
    TextButton(onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Refresh status") }
}

/** App-owned content only; Android owns the status bar, back gesture and device frame. */
@Composable
internal fun IdDocumentStep(
    backSide: Boolean,
    document: OwnerVerificationDocument?,
    pending: Boolean,
    busy: Boolean,
    editable: Boolean,
    progress: Int?,
    stage: String?,
    name: String? = null,
    frontReady: Boolean,
    choose: () -> Unit,
    retry: () -> Unit,
    next: () -> Unit
) {
    val side = if (backSide) "ID back" else "ID front"
    val ready = document?.status == "READY" && document.securityChecked && !pending
    val selected = pending || document != null || name != null
    Heading(if (backSide) "Back of your ID" else "Front of your ID")
    Gap(22)
    BodyCopy(if (backSide) "Step 2 of 3 · Required for every role" else "Step 1 of 3 · Identity", muted = true)
    Gap(22)
    IdCard {
        BodyCopy(side)
        BodyCopy(when {
            busy -> if (stage?.startsWith("Uploading") == true && progress != null) "Uploading $progress%" else stage ?: "Preparing image"
            ready -> name?.let { "$it · Uploaded" } ?: "Uploaded"
            selected -> "Upload not confirmed"
            else -> "Ready to upload"
        })
        BodyCopy("Private · Authorised verification staff only")
        Gap(16)
        TextButton(choose, enabled = editable && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (selected) "Replace file" else "Choose file or take photo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Gap(22)
    if (busy) {
        if (progress != null && stage?.startsWith("Uploading") == true) LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            color = Brown, trackColor = Border, drawStopIndicator = {})
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Brown, trackColor = Border)
        Gap(20)
        IdNote("Keep the app open\nYour upload is in progress. Continue becomes available once it finishes.", true)
    } else if (pending || document?.status == "UPLOADING") {
        IdNote("Upload not confirmed\nChoose the correct ID image with Replace file, or retry checks for the current file.", true)
        TextButton(retry, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Retry current file") }
    } else if (!backSide) {
        IdCard {
            Text("For a clear photo", color = Brown, fontSize = 17.sp, lineHeight = 23.sp)
            Gap(30)
            BodyCopy("Place it on a plain surface\nKeep all four corners visible.")
            Gap(26)
            BodyCopy("Avoid glare or blur\nName and photo must be legible.")
            Gap(26)
            BodyCopy("Accepted formats\nJPG or PNG · Up to 10 MB")
        }
        Gap(20)
        IdNote("This is a private upload\nDo not attach your ID in chats or listing photos.", false)
    }
    Gap(20)
    PrimaryButton(when {
        busy -> "Continue · ${if (stage?.startsWith("Uploading") == true) "uploading" else "checking"}…"
        backSide -> "Continue to live photo"
        else -> "Continue to ID back"
    }, enabled = editable && !busy && ready, onClick = next)
    if (backSide) {
        Gap(22)
        BodyCopy((if (frontReady) "Front is saved privately. " else "") + "You can replace either side before submitting.", muted = true)
    }
}

@Composable private fun IdCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable private fun IdNote(text: String, warning: Boolean) {
    Surface(color = if (warning) Color(0xFFFAEDCC) else Sand, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = if (warning) Color(0xFF996714) else Brown, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(16.dp))
    }
}

/** A new user choice always starts replacement, never implicitly retries the rejected bytes. */
@Composable internal fun IdSourceDialog(label: String, dismiss: () -> Unit, photo: () -> Unit, file: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(label) }, text = {
        Column {
            TextButton(photo, modifier = Modifier.fillMaxWidth()) { Text("Take photo") }
            TextButton(file, modifier = Modifier.fillMaxWidth()) { Text("Choose from gallery") }
        }
    }, confirmButton = { TextButton(dismiss) { Text("Cancel") } })
}
