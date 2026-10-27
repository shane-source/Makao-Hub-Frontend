package com.makaohub.android.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makaohub.android.*
import com.makaohub.android.data.*
import java.util.Locale

private val VerifyBrown = Color(0xFF6A2E00)
private val VerifyInk = Color(0xFF21150E)
private val VerifyMuted = Color(0xFF706259)
private val VerifyCream = Color(0xFFFAF7F4)
private val VerifyBorder = Color(0xFFD6CEC8)
private val VerifySand = Color(0xFFF0EAE5)
private val VerifyGreen = Color(0xFF417653)
private val VerifyGreenFill = Color(0xFFEBF4EF)
private val VerifyAmber = Color(0xFFE78200)
private val VerifyRed = Color(0xFFC91D20)

/** Role-specific entry with shared private-evidence steps; readiness and outcomes come only from server state. */
@Composable
fun LandlordVerificationScreen(model: LandlordViewModel, @Suppress("UNUSED_PARAMETER") profile: MyProfile) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    var idBack by rememberSaveable { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf(false) }
    var sourceFor by remember { mutableStateOf<OwnerEvidence?>(null) }
    var choosing by remember { mutableStateOf<OwnerEvidence?>(null) }
    var cameraId by remember { mutableStateOf<OwnerEvidence?>(null) }
    val status = model.verification?.status
    val terminal = status in listOf("PENDING", "VERIFIED") || (status == "REJECTED" && !correcting)
    val editable = status in listOf("NOT_STARTED", "REJECTED") && !model.busy
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = choosing; choosing = null
        if (uri != null && type != null) model.uploadEvidence(type, context.contentResolver, uri)
    }
    fun choose(type: OwnerEvidence) {
        choosing = type
        picker.launch(if (type == OwnerEvidence.OWNERSHIP_PROOF) arrayOf("application/pdf") else arrayOf("image/jpeg", "image/png"))
    }
    val back = {
        if (!model.busy) {
            if (cameraId != null) cameraId = null
            else if (!terminal && step == 2 && idBack) idBack = false
            else if (!terminal && step > 1) step--
            else model.go(OwnerScreen.HOME)
        }
    }
    BackHandler(onBack = back)
    LaunchedEffect(status) { if (status in listOf("PENDING", "VERIFIED")) correcting = false }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model, status) {
        val observer = LifecycleEventObserver { _, event ->
            // Never race a returning document picker with a refresh that would consume the busy gate.
            if (event == Lifecycle.Event.ON_RESUME && status in listOf("PENDING", "VERIFIED") && !model.busy) model.loadVerification()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    sourceFor?.let { type -> IdSourceDialog(type.title, { sourceFor = null },
        { sourceFor = null; cameraId = type }, { sourceFor = null; choose(type) }) }
    if (!terminal && step == 1) {
        PageBody(back, !model.busy) {
            model.notice?.let { SharedNote(it); Gap(8) }
            VerificationIntro(
                role = "Landlord",
                subtitle = "Confirms your identity and authority to list a whole property or unit.",
                roleDocument = "Proof of ownership",
                onStart = { step = 2 }
            )
        }
        return
    }
    if (!terminal && step == 2) {
        val type = if (idBack) OwnerEvidence.ID_BACK else OwnerEvidence.ID_FRONT
        PageBody(back, !model.busy) {
            model.notice?.let { SharedNote(it); Gap(8) }
            if (cameraId != null) {
                Heading(if (idBack) "Back of your ID" else "Front of your ID"); Gap(20)
                key(type) { EvidenceCamera(false, editable, { bitmap -> cameraId = null; model.uploadCameraEvidence(type, bitmap) }, model::showNotice) }
                SharedLink("Cancel camera", !model.busy) { cameraId = null }
            } else IdDocumentStep(idBack, model.verificationDocuments.firstOrNull { it.documentType == type.name },
                pending = model.pendingEvidenceType == type.name, busy = model.busy, editable = editable,
                progress = model.evidenceProgress, stage = model.evidenceStage, name = model.evidenceNames[type],
                frontReady = evidenceReady(OwnerEvidence.ID_FRONT, model.verificationDocuments),
                choose = { sourceFor = type }, retry = {
                    if (model.pendingEvidenceType == type.name) model.retryEvidence()
                    else model.verificationDocuments.firstOrNull { it.documentType == type.name }?.let { model.confirmSavedEvidence(it.documentId) }
                }, next = { if (!idBack) idBack = true else step = 3 })
        }
        return
    }
    PageBody(back, !model.busy) {
        model.notice?.let { SharedNote(it); Gap(8) }
        when {
            terminal -> VerificationOutcomeScreen(
                role = "Landlord", status = status!!, rejectionReason = model.verification?.rejectionReason,
                busy = model.busy, approvedAction = "Continue to listings",
                onApproved = { model.go(OwnerScreen.LISTINGS) },
                onCorrect = { correcting = true; step = 2; idBack = false },
                onClose = { model.go(OwnerScreen.HOME) }, onRefresh = model::loadVerification)
            cameraId != null -> {
                val type = cameraId!!
                Heading(if (type == OwnerEvidence.SELFIE) "A quick selfie" else type.title)
                Gap(20)
                key(type) {
                    EvidenceCamera(type == OwnerEvidence.SELFIE, editable, { bitmap ->
                        cameraId = null
                        if (type == OwnerEvidence.SELFIE) model.uploadSelfie(bitmap) else model.uploadCameraEvidence(type, bitmap)
                    }, model::showNotice)
                }
                SharedLink("Cancel camera", !model.busy) { cameraId = null }
            }
            step == 3 -> {
                val ready = evidenceReady(OwnerEvidence.SELFIE, model.verificationDocuments)
                VerificationSelfieStep(ready, model.busy) {
                    if (ready) step = 4 else cameraId = OwnerEvidence.SELFIE
                }
            }
            step == 4 -> VerificationRoleDocumentStep(
                title = "Proof of ownership",
                subtitle = "Landlord · Confirm your authority to list",
                label = "Ownership document",
                name = model.evidenceNames[OwnerEvidence.OWNERSHIP_PROOF],
                ready = evidenceReady(OwnerEvidence.OWNERSHIP_PROOF, model.verificationDocuments),
                busy = model.busy,
                guidance = "Include the relevant pages\n\nProperty address\nMatches your whole-property or unit listing.\n\nOwner or authorised representative\nThe name and authority must be readable.",
                privacy = "Staff-only evidence\nThis document is never published in the listing or sent to applicants.",
                choose = { choose(OwnerEvidence.OWNERSHIP_PROOF) }, next = { step = 5 })
            else -> VerificationReviewSummary(
                roleDocument = "Proof of ownership",
                ready = verificationReady(model.verificationDocuments), busy = model.busy,
                submit = model::submitVerification, replace = { step = 2; idBack = false })
        }
        model.verificationDocuments.filter { it.status == "UPLOADING" && model.verificationPendingId == null }.forEach { value ->
            SharedLink("Confirm ${value.documentType.lowercase().replace('_', ' ')}", !model.busy) {
                model.confirmSavedEvidence(value.documentId)
            }
        }
        if (model.verificationPendingId != null) SharedButton("Retry current document", !model.busy, model::retryEvidence)
    }
}

@Composable private fun VerificationOutcome(model: LandlordViewModel, status: String) {
    val colour = when (status) { "VERIFIED" -> VerifyGreen; "REJECTED" -> VerifyRed; else -> VerifyAmber }
    val fill = when (status) { "VERIFIED" -> VerifyGreenFill; "REJECTED" -> Color(0xFFFFE3E3); else -> Color(0xFFFFF4C6) }
    val icon = when (status) { "VERIFIED" -> Icons.Outlined.Verified; "REJECTED" -> Icons.Outlined.Cancel; else -> Icons.Outlined.Alarm }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(42.dp), tint = colour)
        }
        Spacer(Modifier.height(24.dp))
        VerificationTitle(when (status) { "VERIFIED" -> "Verification Approved!"; "REJECTED" -> "Verification Rejected"; else -> "Documents Under Review" }, true)
        Spacer(Modifier.height(9.dp))
        VerificationCopy(when (status) {
            "VERIFIED" -> "Welcome aboard. Your profile is now fully stamped as a verified landlord."
            "REJECTED" -> "Some documents failed our verification standards. Please review the reason below."
            else -> "Our review desk is processing your landlord submission."
        }, true)
        Spacer(Modifier.height(44.dp))
        when (status) {
            "REJECTED" -> VerificationCard(border = VerifyRed, fill = Color(0xFFFFE3E3), padding = 17) {
                Text("Reason for rejection", color = VerifyRed, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(model.verification?.rejectionReason?.takeIf { it.isNotBlank() } ?: "Please contact support for the required corrections.", color = VerifyRed, fontSize = 15.sp, lineHeight = 21.sp)
            }
            "VERIFIED" -> VerificationCard(border = VerifyGreen, padding = 17) {
                Text("You can now add listings!", color = VerifyGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Your landlord verification has been approved. Complete your property details, photos and private location to prepare your first listing.", color = VerifyMuted, fontSize = 15.sp, lineHeight = 21.sp)
            }
            else -> {
                Text("SUBMISSION DETAILS", color = VerifyMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                VerificationCard(padding = 20) {
                    listOf("National ID Uploaded", "Live Photo Uploaded", "Property Ownership Document").forEachIndexed { index, label ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        Text(label, color = VerifyInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun hasEvidence(model: LandlordViewModel, type: OwnerEvidence) =
    model.verificationDocuments.any { it.documentType == type.name } || model.evidencePreviews.containsKey(type) || model.evidenceNames.containsKey(type)

private fun evidenceName(model: LandlordViewModel, type: OwnerEvidence) = model.evidenceNames[type] ?: when (type) {
    OwnerEvidence.ID_FRONT -> "National ID front"
    OwnerEvidence.ID_BACK -> "National ID back"
    OwnerEvidence.SELFIE -> "Live photo"
    OwnerEvidence.OWNERSHIP_PROOF -> "Ownership document.pdf"
}

@Composable private fun EvidenceFile(model: LandlordViewModel, type: OwnerEvidence, onClick: () -> Unit) {
    val ready = evidenceReady(type, model.verificationDocuments)
    val document = model.verificationDocuments.firstOrNull { it.documentType == type.name }
    VerificationCard(border = if (ready && type == OwnerEvidence.OWNERSHIP_PROOF) VerifyGreen else VerifyBorder, padding = 11) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !model.busy) {
            if (document?.status == "UPLOADING" && model.verificationPendingId == null) model.confirmSavedEvidence(document.documentId)
            else onClick()
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val bytes = model.evidencePreviews[type]
            if (bytes != null) PrivateEvidencePhoto(bytes, Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(4.dp)))
            else Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (ready) VerifyGreenFill else VerifySand), contentAlignment = Alignment.Center) {
                Icon(if (ready) Icons.Outlined.Check else Icons.Outlined.Description, null, Modifier.size(24.dp), tint = if (ready) VerifyGreen else VerifyBrown)
            }
            Column(Modifier.weight(1f)) {
                Text(evidenceName(model, type), color = VerifyInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(if (ready) document?.sizeBytes?.let { String.format(Locale.ROOT, "%.1f MB • Upload complete", it / 1048576.0) } ?: "Upload complete"
                    else model.evidenceStage ?: if (document?.status == "UPLOADING") "Confirmation required" else "Upload not confirmed", color = if (ready) VerifyGreen else VerifyMuted, fontSize = 12.sp)
                if (!ready && model.busy) {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = VerifyGreen, trackColor = Color(0xFFE0E0E0))
                }
            }
        }
    }
}

@Composable private fun ReviewItem(label: String, value: String, icon: ImageVector, preview: ByteArray?, actionLabel: String, enabled: Boolean, round: Boolean = false, success: Boolean = false, action: () -> Unit) {
    VerificationCard(padding = 15) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (preview != null) PrivateEvidencePhoto(preview, Modifier.size(40.dp).clip(if (round) CircleShape else RoundedCornerShape(4.dp)))
            else Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (success) VerifyGreenFill else VerifySand), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (success) VerifyGreen else VerifyBrown)
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = VerifyMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(value, color = VerifyInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(action, enabled = enabled, contentPadding = PaddingValues(0.dp)) { Text(actionLabel, color = VerifyBrown, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable private fun PrivateEvidencePhoto(bytes: ByteArray, modifier: Modifier) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Your selected evidence photo", modifier, contentScale = ContentScale.Crop)
}

@Composable private fun VerificationTitle(text: String, centered: Boolean = false) {
    Text(text, color = VerifyInk, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.fillMaxWidth(), textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}
@Composable private fun VerificationCopy(text: String, centered: Boolean = false) {
    Text(text, color = VerifyMuted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth(), textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}
@Composable private fun VerificationCard(border: Color = VerifyBorder, fill: Color = Color.White, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = fill, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(padding.dp), content = content)
    }
}
@Composable private fun FaceTip(text: String) {
    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = VerifyGreen)
        Text(text, color = VerifyMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
@Composable private fun ProofFact(icon: ImageVector, label: String, detail: String, modifier: Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VerifyBorder), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(25.dp), tint = VerifyBrown)
            Spacer(Modifier.height(8.dp))
            Text(label, color = VerifyInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = VerifyMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}
@Composable private fun UploadArea(icon: ImageVector, title: String, detail: String, height: Int, enabled: Boolean, action: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(height.dp).drawWithCache {
            val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
            val inset = stroke.width / 2
            onDrawWithContent {
                drawContent()
                drawRoundRect(VerifyBorder, topLeft = Offset(inset, inset), size = Size(size.width - stroke.width, size.height - stroke.width),
                    cornerRadius = CornerRadius(20.dp.toPx()), style = stroke)
            }
        }.clickable(enabled = enabled, role = Role.Button, onClick = action)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (height > 150) VerifySand else Color.White), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(28.dp), tint = if (height > 150) VerifyBrown else VerifyMuted)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = VerifyInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp)); Text(detail, color = VerifyMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
@Composable private fun VerificationAction(text: String, enabled: Boolean, outlined: Boolean = false, action: () -> Unit) {
    if (outlined) OutlinedButton(action, enabled = enabled, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VerifyBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = VerifyMuted),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    else Button(action, enabled = enabled, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VerifyBrown, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

/** Real pending state: no fabricated name, property, view count or biometric result. */
@Composable
fun LandlordPendingDashboard(model: LandlordViewModel, profile: MyProfile) {
    PendingVerificationDashboard(
        fullName = profile.fullName,
        idUploaded = listOf(OwnerEvidence.ID_FRONT, OwnerEvidence.ID_BACK).all { evidenceReady(it, model.verificationDocuments) },
        proofLabel = "Proof of Ownership (Land Title/Bill)",
        draftTitles = model.listings.filter { it.status == "DRAFT" }.take(20).map { it.title },
        busy = model.busy,
        onDashboard = { model.go(OwnerScreen.HOME) }, onListings = { model.go(OwnerScreen.LISTINGS) },
        onInbox = { model.go(OwnerScreen.MESSAGES) }, onProfile = { model.go(OwnerScreen.PROFILE) },
        onVerification = { model.go(OwnerScreen.VERIFICATION) }
    )
}

/** Same pending layout for each owner role; callers supply only that role's status/data/actions. */
@Composable
internal fun PendingVerificationDashboard(
    fullName: String, idUploaded: Boolean, proofLabel: String, draftTitles: List<String>, busy: Boolean,
    onDashboard: () -> Unit, onListings: () -> Unit, onInbox: () -> Unit, onProfile: () -> Unit,
    onVerification: () -> Unit
) {
    Scaffold(containerColor = VerifyCream, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            listOf(Triple(onDashboard, "Dashboard", Icons.Outlined.Dashboard), Triple(onListings, "Listings", Icons.Outlined.Home),
                Triple(onInbox, "Inbox", Icons.Outlined.ChatBubbleOutline), Triple(onProfile, "Profile", Icons.Outlined.AccountCircle)).forEach { (action, label, icon) ->
                NavigationBarItem(selected = label == "Dashboard", onClick = action, enabled = !busy,
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = VerifyBrown, selectedTextColor = VerifyBrown,
                        unselectedIconColor = VerifyMuted, unselectedTextColor = VerifyMuted),
                    icon = { Icon(icon, null, Modifier.size(23.dp)) }, label = { Text(label, fontSize = 12.sp) })
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fullName, color = VerifyInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFFFFAE9), shape = RoundedCornerShape(7.dp)) {
                    Text("PENDING", color = VerifyAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }
            Text("Verification in Progress", color = VerifyMuted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp))
            Surface(color = Color(0xFFFFFAE9), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VerifyAmber),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onVerification)) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(17.dp), tint = VerifyAmber)
                        Text("Verification Pending", color = VerifyAmber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(5.dp)); Text("Our team is verifying your submission.", color = VerifyMuted, fontSize = 14.sp, lineHeight = 18.sp)
                }
            }
            VerificationCard {
                Text("Required Documents", color = VerifyInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                PendingDocument("National ID Card", if (idUploaded) "Uploaded" else "Under Review", false)
                PendingDocument("Selfie Verification", "Under Review", true)
                PendingDocument(proofLabel, "Under Review", true)
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(12.dp)); Text("Quick Actions", color = Color(0xFFA59E98), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button({}, enabled = false, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(42.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFF1EFED), disabledContentColor = Color(0xFFC2B9B2))) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Add Listing (Locked)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text("Available after verification", color = Color(0xFFDCA36F), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                Spacer(Modifier.height(24.dp)); Text("My Drafts", color = Color(0xFFA59E98), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                draftTitles.forEach { title ->
                    Surface(color = Color.White.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFF1EEEB))) {
                        Text(title, color = Color(0xFFB5AEA8), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(12.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
@Composable private fun PendingDocument(label: String, state: String, reviewing: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reviewing) Icon(Icons.Outlined.Schedule, null, Modifier.size(17.dp), tint = VerifyAmber) else Spacer(Modifier.width(17.dp))
        Text(label, color = VerifyInk, fontSize = 14.sp, modifier = Modifier.weight(1f), lineHeight = 18.sp)
        Text(state, color = if (reviewing) VerifyAmber else VerifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
