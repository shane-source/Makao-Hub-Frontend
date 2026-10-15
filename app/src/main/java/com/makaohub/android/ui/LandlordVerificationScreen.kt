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

/** Step 1 is deliberately absent. Readiness and outcomes come only from server state. */
@Composable
fun LandlordVerificationScreen(model: LandlordViewModel, profile: MyProfile) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(2) }
    var correcting by rememberSaveable { mutableStateOf(false) }
    var sourceFor by remember { mutableStateOf<OwnerEvidence?>(null) }
    var choosing by remember { mutableStateOf<OwnerEvidence?>(null) }
    var cameraId by remember { mutableStateOf<OwnerEvidence?>(null) }
    var retakeSelfie by remember { mutableStateOf(false) }
    var captureRequest by remember { mutableIntStateOf(0) }
    val status = model.verification?.status
    val terminal = status in listOf("PENDING", "VERIFIED") || (status == "REJECTED" && !correcting)
    val editable = status in listOf("NOT_STARTED", "REJECTED") && !model.busy && model.verificationPendingId == null
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
            else if (!terminal && step > 2) step--
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.notice) { model.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
    val scroll = rememberScrollState()
    LaunchedEffect(step, terminal) { scroll.scrollTo(0) }
    val selfieReady = evidenceReady(OwnerEvidence.SELFIE, model.verificationDocuments)
    Scaffold(containerColor = VerifyCream, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (!terminal) Column(Modifier.statusBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(back, enabled = !model.busy, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(20.dp), tint = VerifyBrown)
                    Spacer(Modifier.width(5.dp))
                    Text("Back", color = VerifyBrown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                if (step <= 4) Text("Step $step of 4", color = VerifyMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (step <= 4) LinearProgressIndicator(progress = { step / 4f }, color = VerifyBrown,
                trackColor = Color(0xFFE0E0E0), drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        }
    }, bottomBar = {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp)) {
            when {
                terminal && status == "VERIFIED" -> VerificationAction("Add Your First Listing", !model.busy, action = model::newListing)
                terminal && status == "REJECTED" -> VerificationAction("Re-submit documents", !model.busy) { correcting = true; step = 2 }
                terminal -> VerificationAction("Close & Back to Main", !model.busy, outlined = true) { model.go(OwnerScreen.HOME) }
                cameraId != null -> VerificationAction("Capture ID Photo", editable) { captureRequest++ }
                model.verificationPendingId != null -> VerificationAction("Retry", !model.busy, action = model::retryEvidence)
                step == 2 -> VerificationAction("Continue", !model.busy && evidenceReady(OwnerEvidence.ID_FRONT, model.verificationDocuments)
                    && evidenceReady(OwnerEvidence.ID_BACK, model.verificationDocuments)) { step = 3; captureRequest = 0 }
                step == 3 -> VerificationAction(if (selfieReady && !retakeSelfie) "Continue" else "Capture Live Photo", !model.busy) {
                    if (selfieReady && !retakeSelfie) step = 4 else captureRequest++
                }
                step == 4 -> VerificationAction("Proceed to Summary", !model.busy && evidenceReady(OwnerEvidence.OWNERSHIP_PROOF, model.verificationDocuments)) { step = 5 }
                else -> VerificationAction("Submit Verification", !model.busy && verificationReady(model.verificationDocuments), action = model::submitVerification)
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(scroll).padding(horizontal = 24.dp)
            .padding(top = if (terminal) 40.dp else if (step == 5) 8.dp else 20.dp, bottom = 20.dp)) {
            if (terminal) VerificationOutcome(model, status!!)
            else when (step) {
                2 -> {
                    VerificationTitle("Upload National ID")
                    Spacer(Modifier.height(5.dp))
                    VerificationCopy("Government-issued documents help our team review your identity. Upload both sides clearly.")
                    Spacer(Modifier.height(38.dp))
                    if (cameraId != null) {
                        val type = cameraId!!
                        key(type) { EvidenceCamera(false, editable, { bitmap -> cameraId = null; model.uploadCameraEvidence(type, bitmap) },
                            model::showNotice, captureRequest = captureRequest, showControls = false) }
                    } else UploadArea(Icons.Outlined.CropFree, "Take ID Photo", "or choose from gallery", 166, editable) {
                        sourceFor = if (!evidenceReady(OwnerEvidence.ID_FRONT, model.verificationDocuments)) OwnerEvidence.ID_FRONT else OwnerEvidence.ID_BACK
                    }
                    listOf(OwnerEvidence.ID_FRONT, OwnerEvidence.ID_BACK).forEach { type ->
                        if (hasEvidence(model, type)) {
                            Spacer(Modifier.height(20.dp))
                            EvidenceFile(model, type) { if (editable) sourceFor = type }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Surface(color = VerifySand, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.GppMaybe, null, Modifier.size(20.dp), tint = VerifyBrown)
                            Column {
                                Text("Requirements", color = VerifyBrown, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text("Clear photos of National ID front and back. JPG, PNG up to 10MB. Make sure edges are visible and unblurred.", color = VerifyMuted, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
                3 -> {
                    VerificationTitle("Verify Live Face")
                    Spacer(Modifier.height(5.dp))
                    VerificationCopy("Capture a clear live photo for our team to review with your ID.")
                    Spacer(Modifier.height(26.dp))
                    val preview = model.evidencePreviews[OwnerEvidence.SELFIE]
                    if (!retakeSelfie && preview != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PrivateEvidencePhoto(preview, Modifier.size(260.dp).clip(CircleShape).border(3.dp, VerifyBrown, CircleShape)
                            .clickable(enabled = editable) { retakeSelfie = true; captureRequest = 0 })
                    } else EvidenceCamera(true, editable, { bitmap -> retakeSelfie = false; model.uploadSelfie(bitmap) },
                        model::showNotice, captureRequest = captureRequest, showControls = false)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(color = VerifyGreenFill, shape = CircleShape) {
                            Text(if (model.busy) model.evidenceStage ?: "Uploading photo" else if (selfieReady && !retakeSelfie) "Photo uploaded • Ready for review" else "Keep your face centred • Hold still",
                                color = VerifyGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    FaceTip("Ensure you are in a well-lit room")
                    FaceTip("Remove glasses or hats before starting")
                }
                4 -> {
                    VerificationTitle("Proof of Ownership")
                    Spacer(Modifier.height(5.dp))
                    VerificationCopy("Upload any document that matches your legal name to the property you wish to list.")
                    Spacer(Modifier.height(36.dp))
                    Text("Accepted Documents", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerifyInk)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProofFact(Icons.Outlined.Description, "Title Deed", "Official Copy", Modifier.weight(1f))
                        ProofFact(Icons.Outlined.ReceiptLong, "Utility Bill", "Water/Power", Modifier.weight(1f))
                        ProofFact(Icons.Outlined.InsertDriveFile, "Mgmt Contract", "Valid Signed", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    UploadArea(Icons.Outlined.CloudUpload, "Tap to upload document file", "PDF only, up to 10MB size", 126, editable) { choose(OwnerEvidence.OWNERSHIP_PROOF) }
                    if (hasEvidence(model, OwnerEvidence.OWNERSHIP_PROOF)) {
                        Spacer(Modifier.height(20.dp))
                        EvidenceFile(model, OwnerEvidence.OWNERSHIP_PROOF) { if (editable) choose(OwnerEvidence.OWNERSHIP_PROOF) }
                    }
                }
                else -> {
                    VerificationTitle("Review Submission")
                    Spacer(Modifier.height(5.dp))
                    VerificationCopy("Ensure all details are accurate before sending to the Makao Hub verify desk.")
                    Spacer(Modifier.height(38.dp))
                    ReviewItem(if (profile.phoneNumber != null) "PHONE NUMBER" else "EMAIL ADDRESS", profile.phoneNumber ?: profile.email,
                        if (profile.phoneNumber != null) Icons.Outlined.Phone else Icons.Outlined.Email, null, "View", !model.busy) { model.go(OwnerScreen.PROFILE) }
                    Spacer(Modifier.height(16.dp))
                    ReviewItem("NATIONAL ID PHOTOS", evidenceName(model, OwnerEvidence.ID_FRONT) + "\n" + evidenceName(model, OwnerEvidence.ID_BACK),
                        Icons.Outlined.Badge, model.evidencePreviews[OwnerEvidence.ID_FRONT], "Edit", editable) { step = 2 }
                    Spacer(Modifier.height(16.dp))
                    ReviewItem("LIVE PHOTO", evidenceName(model, OwnerEvidence.SELFIE), Icons.Outlined.PersonOutline,
                        model.evidencePreviews[OwnerEvidence.SELFIE], "Retake", editable, round = true) { retakeSelfie = true; captureRequest = 0; step = 3 }
                    Spacer(Modifier.height(16.dp))
                    ReviewItem("PROOF OF OWNERSHIP", evidenceName(model, OwnerEvidence.OWNERSHIP_PROOF), Icons.Outlined.Check,
                        null, "Edit", editable, success = true) { step = 4 }
                }
            }
        }
    }
    sourceFor?.let { type -> AlertDialog(onDismissRequest = { sourceFor = null }, title = { Text(type.title) }, text = {
        Column {
            TextButton({ sourceFor = null; cameraId = type; captureRequest = 0 }) { Text("Take ID Photo") }
            TextButton({ sourceFor = null; choose(type) }) { Text("Choose from gallery") }
            TextButton({ sourceFor = if (type == OwnerEvidence.ID_FRONT) OwnerEvidence.ID_BACK else OwnerEvidence.ID_FRONT }) {
                Text(if (type == OwnerEvidence.ID_FRONT) "ID back" else "ID front")
            }
        }
    }, confirmButton = { TextButton({ sourceFor = null }) { Text("Cancel") } }) }
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
