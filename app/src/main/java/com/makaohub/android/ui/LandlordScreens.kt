package com.makaohub.android.ui

import android.graphics.BitmapFactory
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makaohub.android.*
import com.makaohub.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Entered only after /profile/me confirms the active LANDLORD role. */
@Composable fun LandlordWorkspace(auth: AuthViewModel, profile: MyProfile) {
    val model: LandlordViewModel = viewModel(key = "landlord-${profile.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LandlordViewModel(LandlordRepository(auth.repository)) { auth.navigate(Page.LOGIN) } as T
    })
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycle) {
        val listener = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.disconnectChat() }
        lifecycle.lifecycle.addObserver(listener)
        onDispose { lifecycle.lifecycle.removeObserver(listener); model.clearPrivateState() }
    }
    LaunchedEffect(model) { model.start() }
    LaunchedEffect(model, lifecycle, model.verification?.status) {
        if (model.verification?.status == "PENDING") lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                model.refreshReviewStatus()
                delay(15_000L) // Foreground-only status reads; no push channel or document upload retries.
            }
        }
    }
    val context = LocalContext.current
    val window = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
    }
    val sensitive = model.screen in listOf(OwnerScreen.VERIFICATION, OwnerScreen.LOCATION)
    DisposableEffect(window, sensitive) {
        if (sensitive) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (sensitive) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val back = { model.go(parentOf(model.screen)) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    BackHandler(model.screen != OwnerScreen.HOME, onBack = back)
    if (model.screen == OwnerScreen.VERIFICATION) {
        LandlordVerificationScreen(model, profile)
        return // Verification owns its step header/layout, not the dashboard scaffold.
    }
    if (model.screen == OwnerScreen.HOME && model.verificationStatus(profile.verificationStatus) == "PENDING") {
        LandlordPendingDashboard(model, profile)
        return
    }
    val tab = when(model.screen) {
        OwnerScreen.HOME -> OwnerScreen.HOME
        OwnerScreen.APPLICATION, OwnerScreen.APPLICATIONS, OwnerScreen.APPLICATION_DECISION -> OwnerScreen.APPLICATIONS
        OwnerScreen.MESSAGES, OwnerScreen.CHAT -> OwnerScreen.MESSAGES
        OwnerScreen.PROFILE, OwnerScreen.VERIFICATION, OwnerScreen.PAYMENTS, OwnerScreen.PLANS -> OwnerScreen.PROFILE
        else -> OwnerScreen.LISTINGS
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.notice) {
        model.notice?.let { message -> snackbar.showSnackbar(message); model.dismissNotice() }
    }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (model.screen in listOf(OwnerScreen.HOME, OwnerScreen.LISTINGS, OwnerScreen.APPLICATIONS,
                OwnerScreen.MESSAGES, OwnerScreen.PROFILE, OwnerScreen.PAYMENTS)) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp, top = 8.dp)) {
                OwnerBottomBar(tab, !model.busy, model::go)
            }
        }
    }) { insets ->
        // A fresh scroll position for each page avoids opening the next step halfway down.
        key(model.screen) {
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            OwnerHeader(!model.busy) {
                if (model.screen == OwnerScreen.HOME) backDispatcher?.onBackPressed() else back()
            }
            Gap(20)
            when(model.screen) {
                OwnerScreen.HOME -> OwnerHome(model, profile)
                OwnerScreen.VERIFICATION -> LandlordVerificationScreen(model, profile)
                OwnerScreen.LISTINGS -> OwnerListings(model)
                OwnerScreen.EDIT -> PropertyEditor(model)
                OwnerScreen.MEDIA -> ListingMedia(model)
                OwnerScreen.MEDIA_MANAGE -> ManageListingMedia(model)
                OwnerScreen.LOCATION -> PrivateLocation(model)
                OwnerScreen.PREVIEW -> ListingPreview(model, profile)
                OwnerScreen.CHECKS -> PublicationChecks(model, profile)
                OwnerScreen.APPLICATIONS -> ApplicationList(model)
                OwnerScreen.APPLICATION -> ApplicationReview(model)
                OwnerScreen.APPLICATION_DECISION -> ApplicationDecision(model)
                OwnerScreen.MESSAGES -> Conversations(model)
                OwnerScreen.CHAT -> Conversation(model, profile)
                OwnerScreen.PAYMENTS -> PaymentHistory(model)
                OwnerScreen.PLANS, OwnerScreen.BOOST -> TestCheckout(model)
                OwnerScreen.PROFILE -> OwnerProfile(model, profile, auth)
            }
        }
        }
    }
}

private fun parentOf(page: OwnerScreen) = when(page) {
    OwnerScreen.MEDIA -> OwnerScreen.EDIT
    OwnerScreen.MEDIA_MANAGE -> OwnerScreen.MEDIA
    OwnerScreen.LOCATION -> OwnerScreen.MEDIA
    OwnerScreen.PREVIEW -> OwnerScreen.LOCATION
    OwnerScreen.CHECKS, OwnerScreen.EDIT, OwnerScreen.BOOST -> OwnerScreen.LISTINGS
    OwnerScreen.APPLICATION -> OwnerScreen.APPLICATIONS
    OwnerScreen.APPLICATION_DECISION -> OwnerScreen.APPLICATION
    OwnerScreen.CHAT -> OwnerScreen.MESSAGES
    OwnerScreen.PAYMENTS, OwnerScreen.PLANS -> OwnerScreen.PROFILE
    else -> OwnerScreen.HOME
}
@Composable internal fun OwnerHeader(enabled: Boolean, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back, enabled = enabled, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Brown, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("makao hub", color = Brown, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable internal fun OwnerBottomBar(selected: OwnerScreen, enabled: Boolean, select: (OwnerScreen) -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 6.dp, vertical = 8.dp)) {
            listOf(OwnerScreen.HOME to "Home", OwnerScreen.LISTINGS to "Listings",
                OwnerScreen.APPLICATIONS to "Applications", OwnerScreen.MESSAGES to "Messages",
                OwnerScreen.PROFILE to "Profile").forEach { (page, label) ->
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected == page, enabled = enabled, role = Role.Tab) { select(page) }
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(7.dp).background(if (selected == page) Brown else Color.Transparent, CircleShape))
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (selected == page) Brown else Muted, fontSize = 10.sp,
                        fontWeight = if (selected == page) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
@Composable internal fun OwnerHeading(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, lineHeight = 33.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.Bold, color = Brown)
    Gap(22); Text(subtitle, fontSize = 14.sp, lineHeight = 21.sp, color = Muted); Gap(20)
}
@Composable internal fun OwnerCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, color = Brown, fontSize = 16.sp, lineHeight = 22.sp); Gap(4) }
            content()
        }
    }
    Gap(20)
}
@Composable private fun Small(text: String, muted: Boolean = false) { Text(text, color = if(muted) Muted else Ink, fontSize = 14.sp, lineHeight = 21.sp) }
@Composable private fun Notice(text: String, tone: String = "info") {
    val color = when(tone) { "good" -> Color(0xFFE6F0E8); "bad" -> Color(0xFFF8E5DF); "warning" -> Color(0xFFFAEECB); else -> Sand }
    val foreground = when(tone) { "good" -> Color(0xFF3D7655); "bad" -> Color(0xFFAB4B3F); "warning" -> Color(0xFF9D640B); else -> Brown }
    Surface(color = color, shape = RoundedCornerShape(12.dp)) { Text(text, color = foreground, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
}
@Composable private fun Badge(status: String) {
    val color = when(status) { "VERIFIED", "PUBLISHED", "APPROVED", "SUCCEEDED", "OPEN" -> Color(0xFF3D7655)
        "REJECTED", "FAILED" -> Color(0xFFAB4B3F); else -> Color(0xFF94712B) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(if (status == "VERIFIED") "Verified Landlord" else status.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }, color = color, fontSize = 12.sp)
    }
}
@Composable private fun CardLink(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(title, color = Ink, fontSize = 14.sp, lineHeight = 21.sp); Small(detail) }
    }
}
@Composable internal fun OwnerTextAction(label: String, enabled: Boolean = true, click: () -> Unit) {
    TextButton(click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, color = if (enabled) Brown else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable internal fun OwnerInput(label: String, value: String, enabled: Boolean = true, numeric: Boolean = false, lines: Int = 1, onChange: (String) -> Unit = {}) {
    BasicTextField(value, onChange, enabled = enabled, singleLine = lines == 1, minLines = lines,
        textStyle = TextStyle(color = if (enabled) Ink else Muted, fontSize = 14.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(Brown),
        keyboardOptions = KeyboardOptions(keyboardType = if(numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).semantics { contentDescription = label },
        decorationBox = { field ->
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
                Column(Modifier.fillMaxWidth().padding(13.dp).heightIn(min = 46.dp), verticalArrangement = Arrangement.Center) {
                    Text(label, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                    Gap(2); field()
                }
            }
        })
}
private fun kes(value: Long) = "KSh " + NumberFormat.getIntegerInstance(Locale.UK).format(value)
private fun moneyMinor(value: Long) = "KSh " + BigDecimal.valueOf(value, 2).toPlainString()
private fun shortId(id: String) = id.take(8).uppercase()
private fun stamp(value: String) = runCatching { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)
    .withZone(ZoneId.of("Africa/Nairobi")).format(Instant.parse(value)) + " EAT" }.getOrDefault("Date unavailable")

@Composable private fun OwnerHome(model: LandlordViewModel, profile: MyProfile) {
    OwnerDashboard(profile.fullName, model.verificationStatus(profile.verificationStatus), model.summary,
        model.listings, model.applications, model.applicationsLoaded, model.busy, model::newListing,
        model::viewApplication, model::manage, model::go, { model.openCheckout("PLAN_UPGRADE") })
}

/** One layout for the authenticated dashboard and isolated, synthetic Android Studio previews. */
@Composable internal fun OwnerDashboard(name: String, verification: String, summary: LandlordSummary?,
    listings: List<OwnerListing>, applications: List<OwnerApplication>, applicationsLoaded: Boolean,
    busy: Boolean, create: () -> Unit, review: (OwnerApplication) -> Unit, manage: (OwnerListing) -> Unit,
    go: (OwnerScreen) -> Unit, plans: () -> Unit) {
    OwnerHeading("Welcome home, ${name.substringBefore(' ')}", "A clear view of your properties and people.")
    Badge(verification); Gap(20)
    if (verification != "VERIFIED") {
        Notice("Complete your identity and ownership verification before publishing or receiving payouts.", "warning"); Gap(10)
        PrimaryButton("Verify as a landlord", !busy) { go(OwnerScreen.VERIFICATION) }; Gap(16)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { OwnerCard {
            Text("${summary?.publishedListings ?: "—"} published", color = Brown, fontSize = 16.sp)
            Gap(4)
            Small("${summary?.draftListings ?: "—"} draft · ${if (summary != null) listings.count { it.status == "PAUSED" } else "—"} paused")
        } }
        Column(Modifier.weight(1f)) { OwnerCard {
            Text("${if(applicationsLoaded) applications.size else "—"} applications", color = Brown, fontSize = 16.sp)
            Gap(4)
            Small("${if(applicationsLoaded) applications.count { it.status == "SUBMITTED" || it.status == "UNDER_REVIEW" } else "—"} need review")
        } }
    }
    PrimaryButton("Create property listing", !busy, create); Gap(20)
    OwnerCard("Your next steps") {
        applications.firstOrNull { it.status in listOf("SUBMITTED", "UNDER_REVIEW") }?.let { app ->
            CardLink("Application ${shortId(app.id)} · Review", listings.find { it.id == app.listingId }?.title ?: "Your property", !busy) { review(app) }
        }
        listings.firstOrNull { it.status == "DRAFT" }?.let { listing -> CardLink("Finish ${listing.title} · Edit", "Review your saved draft", !busy) { manage(listing) } }
        CardLink("Messages · Open", "Protected conversations", !busy) { go(OwnerScreen.MESSAGES) }
    }
    OwnerCard("Your business") {
        CardLink("Test payment history · View", "Your payment records", !busy) { go(OwnerScreen.PAYMENTS) }
        CardLink("Subscription · Upgrade", "Compare plans", !busy, plans)
        CardLink("Boost a listing · Boost", "Reach more renters · TEST checkout", !busy) { go(OwnerScreen.LISTINGS) }
    }
}

@Composable private fun OwnerListings(model: LandlordViewModel) {
    var visible by remember(model.listings) { mutableIntStateOf(20) }
    var deletingDraft by remember { mutableStateOf<OwnerListing?>(null) }
    deletingDraft?.let { listing ->
        AlertDialog(
            onDismissRequest = { if (!model.busy) deletingDraft = null },
            title = { Text("Delete this draft?") },
            text = { Text("The draft and its uploaded media will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = { deletingDraft = null; model.deleteDraft(listing) },
                    enabled = !model.busy
                ) { Text("Delete draft", color = Color(0xFFAB4B3F)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingDraft = null }, enabled = !model.busy) { Text("Keep draft") }
            }
        )
    }
    OwnerHeading("Your listings", "Manage availability and review status.")
    if (model.listings.isEmpty()) {
        Notice("No listings yet."); Gap(20)
        PrimaryButton("Create property listing", !model.busy, model::newListing)
    }
    model.listings.take(visible).forEach { listing -> OwnerCard {
        Text(listing.title, color = Ink, fontSize = 16.sp, lineHeight = 22.sp); Badge(listing.status)
        Small("${kes(listing.monthlyRentKes)} / month · ${listing.bedroomCount} beds · ${listing.bathroomCount} baths", true)
        listing.availableFrom?.let { Small("Available ${it}") }
        if (listing.status == "DRAFT") {
            OwnerTextAction("Continue draft", !model.busy) { model.manage(listing) }
            OwnerTextAction("Delete draft", !model.busy) { deletingDraft = listing }
        }
        if (listing.status == "PUBLISHED") {
            OwnerTextAction("View listing status", !model.busy) { model.manage(listing) }
            OwnerTextAction("Pause listing", !model.busy) { model.edit(listing); model.listingAction("pause") }
            OwnerTextAction("Boost listing", !model.busy) { model.openCheckout("BOOST", listing.id) }
        }
        if (listing.status == "PAUSED") OwnerTextAction("Review before resuming", !model.busy) { model.manage(listing) }
    } }
    if (visible < model.listings.size) TextButton({ visible += 20 }) { Text("Show more listings") }
}

@Composable private fun PropertyEditor(model: LandlordViewModel) {
    val editable = model.selected == null || model.selected?.status == "DRAFT"
    val enabled = editable && !model.busy
    var typesOpen by remember { mutableStateOf(false) }
    OwnerHeading("Tell us about the property", "Step 1 of 4 · Whole property or unit")
    OwnerInput("Listing title", model.form.title, enabled) { model.form = model.form.copy(title = it.take(150)) }
    Box {
        Surface(onClick = { typesOpen = true }, enabled = enabled, shape = RoundedCornerShape(12.dp),
            color = Color.White, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp).heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Small("Property / unit type")
                    Small(model.form.propertyType.lowercase().replaceFirstChar { it.uppercase() })
                }
                Icon(Icons.Outlined.ArrowDropDown, "Choose property type", tint = Brown)
            }
        }
        DropdownMenu(typesOpen, { typesOpen = false }) { listOf("APARTMENT", "HOUSE", "STUDIO", "BEDSITTER", "ROOM").forEach { type ->
            DropdownMenuItem(text = { Text(type.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { model.form = model.form.copy(propertyType = type); typesOpen = false })
        } }
    }; Gap(20)
    OwnerInput("Bedrooms", model.form.bedrooms, enabled, numeric = true) { model.form = model.form.copy(bedrooms = it) }
    OwnerInput("Bathrooms", model.form.bathrooms, enabled, numeric = true) { model.form = model.form.copy(bathrooms = it) }
    OwnerInput("Monthly rent · KSh", model.form.rent, enabled, numeric = true) { model.form = model.form.copy(rent = it) }
    OwnerInput("Deposit · KSh", model.form.deposit, enabled, numeric = true) { model.form = model.form.copy(deposit = it) }
    OwnerInput("Description", model.form.description, enabled, lines = 2) { model.form = model.form.copy(description = it.take(3000)) }
    OwnerInput("Available from · YYYY-MM-DD", model.form.available, enabled) { model.form = model.form.copy(available = it) }
    Small("Amenities"); Gap(6)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        AmenityChoice("Water", model.form.waterAvailable == true, enabled) { model.form = model.form.copy(waterAvailable = it) }
        AmenityChoice("Security", model.form.securityAvailable == true, enabled) { model.form = model.form.copy(securityAvailable = it) }
        AmenityChoice("Parking", model.form.parkingAvailable == true, enabled) { model.form = model.form.copy(parkingAvailable = it) }
        AmenityChoice("Balcony", model.form.balcony == true, enabled) { model.form = model.form.copy(balcony = it) }
        AmenityChoice("Furnished", model.form.furnished, enabled) { model.form = model.form.copy(furnished = it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AmenityChoice("Pets allowed", model.form.petsAllowed == true, enabled) { model.form = model.form.copy(petsAllowed = it) }
    }
    Notice("List as a landlord\nWhole properties and units belong here. Spare-room and servant-quarter roommate listings belong in the Sublessor workspace."); Gap(12)
    PrimaryButton("Save and continue", enabled) { model.saveDraft(true) }
    TextButton({ model.saveDraft(false) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
}

@Composable private fun AmenityChoice(label: String, selected: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, change, enabled = enabled, modifier = Modifier.size(34.dp))
        Text(label, color = if (enabled) Ink else Muted, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
    }
}

/** Decode a selected image off the UI thread and downsample it; no signed read URL is cached. */
@Composable internal fun LocalPhoto(uri: Uri?) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, uri) {
        value = if(uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0)
                options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 1200).coerceAtLeast(1)
                options.inJustDecodeBounds = false
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    image?.let { Image(it, "Selected property photo", modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); Gap(12) }
}

@Composable private fun ListingMedia(model: LandlordViewModel) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.uploadFile(resolver, uri) }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.replacePhoto(resolver, uri) }
    val photos = model.media.filter { it.mediaType == "IMAGE" }.sortedBy { it.sortOrder }
    val selected = photos.find { it.id == model.selectedMediaId } ?: photos.firstOrNull()
    val video = model.media.firstOrNull { it.mediaType == "VIDEO" }
    OwnerHeading("Show the home clearly", "Step 2 of 4 · Photos and video")
    LocalPhoto(selected?.let { model.localPhotos[it.id] })
    if (photos.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            photos.forEachIndexed { index, photo ->
                TextButton({ model.selectMedia(photo.id) }, enabled = !model.busy) {
                    Text(if (index == 0) "Cover photo" else "Photo ${index + 1}", fontSize = 12.sp,
                        color = if (selected?.id == photo.id) Brown else Muted,
                        fontWeight = if (selected?.id == photo.id) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Gap(20)
    }
    Small("${photos.count { it.confirmed }} photos uploaded")
    if (photos.isNotEmpty()) Small("First photo is the cover", true)
    Gap(16)
    if (model.pendingConfirmationId != null) {
        Notice(if (model.busy) "Checking your upload…" else "Upload needs confirmation. Retry to finish.", "warning"); Gap(8)
        OutlinedButton(model::retryConfirmation, enabled = !model.busy) { Text("Retry confirmation") }; Gap(12)
    }
    model.mediaUploadError?.let { message ->
        Notice("Image upload failed\n$message", "bad"); Gap(8)
        if (model.mediaUploadPending) {
            OwnerTextAction("Retry upload", !model.busy) { model.retryMediaUpload(resolver) }
            Gap(12)
        }
    }
    OwnerTextAction("Add photos", !model.busy && model.pendingConfirmationId == null && photos.size < 20) {
        picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
    }
    Gap(20)
    OwnerCard("Video walkthrough") {
        if (video == null) {
            Small("No video added", true)
            OwnerTextAction("Add video", !model.busy && model.pendingConfirmationId == null) { picker.launch(arrayOf("video/mp4")) }
        } else {
            Small(if (video.confirmed) "Upload complete" else "Awaiting confirmation")
            OwnerTextAction("Manage video", !model.busy) { model.manageMedia(video.id) }
        }
    }
    selected?.let { photo -> OwnerCard("Manage selected photo") {
        OwnerTextAction("Replace photo", !model.busy && !model.mediaUploadPending) {
            model.selectMedia(photo.id)
            replacementPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        OwnerTextAction("Remove photo", !model.busy && !model.mediaUploadPending) { model.manageMedia(photo.id) }
        Small("Remove affects this draft. Publishing waits for required media to complete.")
    } }
    PrimaryButton("Continue to location", !model.busy, model::openLocation)
    Gap(20); OwnerTextAction("Save draft", !model.busy) { model.go(OwnerScreen.LISTINGS) }
}

/** The design's confirmation is a separate page; nothing is deleted just by opening it. */
@Composable private fun ManageListingMedia(model: LandlordViewModel) {
    val item = model.media.find { it.id == model.selectedMediaId } ?: return
    val kind = if (item.mediaType == "IMAGE") "photo" else "video"
    val resolver = LocalContext.current.contentResolver
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.replacePhoto(resolver, uri)
    }
    OwnerHeading("Manage listing media", "${model.selected?.title.orEmpty()} · Draft")
    LocalPhoto(model.localPhotos[item.id])
    OwnerCard("Delete this $kind?") {
        Small("This $kind will be removed from this draft. Your other media stays saved.")
        Button({ model.removeMedia(item.id) }, enabled = !model.busy && !model.mediaUploadPending, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB4B3F)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Confirm delete $kind") }
        OwnerTextAction("Keep $kind", !model.busy) { model.go(OwnerScreen.MEDIA) }
    }
    if (kind == "photo") {
        OwnerTextAction("Replace photo", !model.busy && !model.mediaUploadPending) {
            replacementPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
    }
    model.mediaUploadError?.let { message ->
        Gap(12)
        Notice("Image upload failed\n$message", "bad")
        Gap(8)
        if (model.mediaUploadPending) {
            OwnerTextAction("Retry upload", !model.busy) { model.retryMediaUpload(resolver) }
        }
    }
}

@Composable private fun PrivateLocation(model: LandlordViewModel) {
    var area by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.areaLabel ?: "") }
    var pin by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.let {
        org.maplibre.android.geometry.LatLng(it.latitude.toDouble(), it.longitude.toDouble())
    }) }
    OwnerHeading("Set the private location", "Step 3 of 4 · Public area, private map pin")
    OwnerInput("Public approximate area", area, !model.busy) { area = it }
    Notice("PRIVATE · Search or move the pin. Exact coordinates stay hidden publicly."); Gap(16)
    PropertyMapPicker(pin, !model.busy, showDeveloperDetails = false) { pin = it }
    Gap(16)
    PrimaryButton("Save private pin", !model.busy && pin != null) {
        pin?.let { model.saveMapLocation(area, it.latitude, it.longitude) }
    }
    TextButton({ model.go(OwnerScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft and return") }
}

@Composable private fun ListingPreview(model: LandlordViewModel, profile: MyProfile) {
    val listing = model.selected ?: return
    OwnerHeading("Preview your listing", "Step 4 of 4 · What renters will see")
    OwnerCard {
        LocalPhoto(model.media.sortedBy { it.sortOrder }.firstNotNullOfOrNull { model.localPhotos[it.id] })
        if (model.verificationStatus(profile.verificationStatus) == "VERIFIED") Badge("VERIFIED")
        Small(listing.title); Small("${model.location?.areaLabel ?: "Area not set"} · ${listing.propertyType.lowercase()}", true)
        Small("${kes(listing.monthlyRentKes)} / month"); Small("${listing.bedroomCount} beds · ${listing.bathroomCount} baths · ${if(listing.furnished) "Furnished" else "Unfurnished"}", true)
        Small(listing.description); Small("Available · ${listing.availableFrom ?: "Not specified"}", true)
        Small("Exact location locked", true)
    }
    OwnerCard("Ready for moderation") {
        Small("Role verification · ${model.verificationStatus(profile.verificationStatus).lowercase().replaceFirstChar { it.uppercase() }}")
        Gap(8)
        Small("Media · ${model.media.count { it.mediaType == "IMAGE" && it.confirmed }} photos${if (model.media.any { it.mediaType == "VIDEO" && it.confirmed }) " · Video" else ""}")
        Gap(8)
        Small("Private location · ${if(model.location != null) "Saved" else "Required"}")
        Gap(8)
        Small("Availability · ${listing.availableFrom ?: "Not specified"}")
    }
    Notice("Approval comes before publishing\nSubmit the complete listing for staff review. Once approved, publish from listing management. Only the approximate area is public."); Gap(20)
    val alreadySubmitted = model.listingReview?.moderationSubmittedAt != null
    if (alreadySubmitted) PrimaryButton("View review status", !model.busy) { model.go(OwnerScreen.CHECKS) }
    else PrimaryButton("Submit for review", !model.busy && model.verificationStatus(profile.verificationStatus) == "VERIFIED"
        && model.media.any { it.mediaType == "IMAGE" && it.confirmed } && model.location != null, model::submitListingReview)
    Gap(20); OwnerTextAction("Edit listing", !model.busy) { model.go(OwnerScreen.EDIT) }
}

@Composable private fun PublicationChecks(model: LandlordViewModel, profile: MyProfile) {
    OwnerHeading("Publish checks and outcomes", "Dedicated listing state board")
    val listing = model.selected ?: return
    Badge(listing.status); Gap(12)
    if (model.verificationStatus(profile.verificationStatus) != "VERIFIED") {
        Notice("Verification required\nComplete Landlord verification before publishing.", "warning"); Gap(12)
        OutlinedButton({ model.go(OwnerScreen.VERIFICATION) }, enabled = !model.busy) { Text("Open landlord verification") }; Gap(12)
    }
    if (model.media.none { it.mediaType == "IMAGE" }) { Notice("Media required\nUpload and confirm at least one photo.", "bad"); Gap(12) }
    if (model.location == null) { Notice("Private location\nSave the exact pin and public approximate area.", "warning"); Gap(12) }
    if (listing.status == "PUBLISHED") {
        Notice("Published\nYour listing is visible to renters.", "good"); Gap(12)
        PrimaryButton("Pause listing", !model.busy) { model.listingAction("pause") }
    } else when (val review = model.listingReview) {
        null -> {
            Notice("Review status unavailable\nRefresh before submitting or publishing.", "warning"); Gap(12)
            PrimaryButton("Refresh review status", !model.busy) { model.go(OwnerScreen.CHECKS) }
        }
        else -> when (review.moderationStatus) {
            "PENDING" -> if (review.moderationSubmittedAt == null) {
                Notice("Not submitted\nComplete the required media and private location before staff review.", "warning"); Gap(12)
                PrimaryButton("Submit listing for review", !model.busy && model.verificationStatus(profile.verificationStatus) == "VERIFIED"
                    && model.media.any { it.mediaType == "IMAGE" && it.confirmed } && model.location != null, model::submitListingReview)
            } else {
                Notice("Under staff review\nPublication remains locked until a moderator records a decision.", "warning"); Gap(12)
                TextButton({ model.go(OwnerScreen.CHECKS) }, enabled = !model.busy) { Text("Refresh review status") }
            }
            "REJECTED" -> {
                Notice("Changes required\n${review.moderationReason ?: "Open the listing and correct the submitted details."}", "bad"); Gap(12)
                PrimaryButton("Revise listing", !model.busy, model::reviseListingReview)
            }
            "APPROVED" -> {
                OwnerCard("Listing approved") {
                    Badge("APPROVED")
                    Small("Review checks passed. You can now publish your listing.")
                    if (listing.status == "DRAFT") PrimaryButton("Publish listing", !model.busy) { model.listingAction("publish") }
                    if (listing.status == "PAUSED") PrimaryButton("Resume published listing", !model.busy) { model.listingAction("resume") }
                }
            }
        }
    }
    TextButton({ model.go(OwnerScreen.LISTINGS) }, enabled = !model.busy) { Text("Back to listings") }
}

@Composable private fun ApplicationList(model: LandlordViewModel) {
    var visible by remember(model.applications) { mutableIntStateOf(20) }
    OwnerHeading("People interested in your home", "Review applications for your own listings.")
    Small("New ${model.applications.count { it.status == "SUBMITTED" }} · Under review ${model.applications.count { it.status == "UNDER_REVIEW" }} · Approved ${model.applications.count { it.status == "APPROVED" }}", true); Gap(12)
    if (model.applications.isEmpty()) Notice("No applications have been received yet.")
    model.applications.take(visible).forEach { app -> OwnerCard {
        CardLink("Application ${shortId(app.id)}", model.listings.find { it.id == app.listingId }?.title ?: "Property ${shortId(app.listingId)}", !model.busy) { model.viewApplication(app) }
        Badge(app.status); Small("Submitted ${stamp(app.createdAt)}", true)
    } }
    if (visible < model.applications.size) TextButton({ visible += 20 }) { Text("Show more applications") }
    Gap(12); Notice("Respect private identity evidence\nRenter ID images and face scans are never shown to hosts.")
}

@Composable private fun ApplicationReview(model: LandlordViewModel) {
    val app = model.selectedApplication ?: return
    OwnerHeading("Review this application", "Application ${shortId(app.id)}")
    OwnerCard("Application summary") {
        Small(model.listings.find { it.id == app.listingId }?.title ?: "Property ${shortId(app.listingId)}")
        Badge(app.status)
    }
    OwnerCard("Progress") {
        Small("Submitted\n${stamp(app.createdAt)}")
        Gap(8)
        Small("${app.status.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }}\n${stamp(app.updatedAt)}")
    }
    OwnerTextAction("Open protected chat", !model.busy) { model.go(OwnerScreen.MESSAGES) }
    Gap(20)
    Notice("No renter ID access\nIdentity verification evidence remains private."); Gap(18)
    if (app.status == "SUBMITTED") PrimaryButton("Mark under review", !model.busy) { model.decision("review") }
    if (app.status in listOf("SUBMITTED", "UNDER_REVIEW")) {
        Gap(12); PrimaryButton("Review decision", !model.busy) { model.go(OwnerScreen.APPLICATION_DECISION) }
    }
}

@Composable private fun ApplicationDecision(model: LandlordViewModel) {
    val app = model.selectedApplication ?: return
    var rejectionReason by remember(app.id) { mutableStateOf("") }
    OwnerHeading("Application decision states", "Application ${shortId(app.id)}")
    if (app.status in listOf("SUBMITTED", "UNDER_REVIEW")) {
        OwnerCard("Approve application?") {
            Small("Approval allows the applicant to continue to booking checkout. Approval doesn’t move money.")
            PrimaryButton("Confirm approval", !model.busy) { model.decision("approve") }
            OwnerTextAction("Keep reviewing", !model.busy) { model.go(OwnerScreen.APPLICATION) }
        }
        OwnerCard("Reject application?") {
            Small("The applicant will see the saved rejection reason.")
            OwnerInput("Rejection reason", rejectionReason, !model.busy, lines = 3) {
                rejectionReason = it.take(500)
            }
            Button({ model.decision("reject", rejectionReason.trim()) }, enabled = !model.busy && rejectionReason.isNotBlank(), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB4B3F)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Reject with reason") }
        }
    } else {
        Notice("${app.status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}\n${app.rejectionReason ?: "Decision recorded ${stamp(app.updatedAt)}"}",
            if (app.status == "APPROVED") "good" else "bad")
        Gap(20); OwnerTextAction("Back to applications", !model.busy) { model.go(OwnerScreen.APPLICATIONS) }
    }
}

@Composable private fun Conversations(model: LandlordViewModel) {
    var query by remember { mutableStateOf("") }
    ProtectedInboxHeader("Messages from renters", query) { query = it }
    val visible = model.threads?.items.orEmpty().filter { thread ->
        query.isBlank() || listOf(thread.id, thread.rentalApplicationId, thread.status)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    if (visible.isEmpty()) SharedText(if (query.isBlank()) "No conversations yet." else "No conversations match your search.", true)
    visible.forEach { thread ->
        ProtectedConversationRow(
            title = "Verified Renter",
            detail = "Rental conversation · Application ${shortId(thread.rentalApplicationId)}",
            status = "${thread.status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} · Updated ${stamp(thread.updatedAt)}",
            enabled = !model.busy
        ) { model.openThread(thread) }
    }
    model.threads?.let { Pagination(it.page, it.hasNext, model.busy, model::loadThreads) }
    Gap(12)
    SharedNote("Keep communication inside Makao Hub\nPhone numbers, email addresses and social handles are masked and flagged for both people.")
}
@Composable private fun Conversation(model: LandlordViewModel, profile: MyProfile) {
    val selectedThread = model.thread ?: return
    ProtectedConversation(
        title = "Verified Renter",
        roleLabel = "Renter",
        conversationType = "Rental conversation",
        contextLabel = "Application ${shortId(selectedThread.rentalApplicationId)} · Conversation ${shortId(selectedThread.id)}",
        messages = model.messages?.items.orEmpty(),
        ownUserId = profile.id,
        connected = model.chatReady,
        awaitingEcho = model.awaitingEcho,
        threadOpen = selectedThread.status == "OPEN",
        draft = model.chatDraft,
        busy = model.busy,
        hasOlder = model.messages?.hasNext == true,
        onOlder = model::olderMessages,
        onReconnect = model::reconnectChat,
        onDraft = model::changeChatDraft,
        onSend = model::sendMessage
    )
}

@Composable private fun PaymentHistory(model: LandlordViewModel) {
    OwnerHeading("Rent and payout history", if (BuildConfig.MOCK_PAYMENTS_ENABLED) "Every record here is a TEST simulation." else "Your payment records")
    if (BuildConfig.MOCK_PAYMENTS_ENABLED) Notice("TEST / MOCK PAYMENT\nNo real money moves. Test receipts describe simulated payment states.", "warning")
    Gap(12)
    if(model.payments?.items.isNullOrEmpty()) Notice("No payment records found for your account.")
    model.payments?.items?.forEach { item -> OwnerCard {
        Small(item.paymentType.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }); Badge(item.status)
        Small(if(item.currency == "KES") moneyMinor(item.amountMinor) else "${item.currency} · ${item.amountMinor} minor units")
        Small("Receipt ${shortId(item.id)} · ${stamp(item.createdAt)}", true)
    } }
    model.payments?.let { Pagination(it.page, it.hasNext, model.busy, model::loadPayments) }
}
@Composable private fun Pagination(page: Int, next: Boolean, busy: Boolean, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton({ select(page - 1) }, enabled = page > 0 && !busy) { Text("Previous") }
        Small("Page ${page + 1}")
        TextButton({ select(page + 1) }, enabled = next && !busy) { Text("Next") }
    }
}
@Composable private fun TestCheckout(model: LandlordViewModel) {
    val boost = model.checkoutType == "BOOST"
    OwnerHeading(if(boost) "Give your home a boost" else "Choose a plan that fits", "Illustrative plans · No live billing")
    Notice("TEST / MOCK PAYMENT\nNo real money moves. Only a payment receipt is simulated; a paid receipt does not establish a plan or boost entitlement.", "warning"); Gap(16)
    OwnerCard {
        Small(if(boost) "Boost checkout" else "Plan upgrade checkout")
        Small("Mock M-Pesa")
    }
    val payment = model.checkout
    if(payment == null) PrimaryButton("Review TEST checkout", !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED, model::createCheckout)
    else {
        OwnerCard { Small("Quote · ${if(payment.currency == "KES") moneyMinor(payment.amountMinor) else payment.currency}"); Badge(payment.status); Small("Receipt ${shortId(payment.id)}", true) }
        if(payment.status in listOf("CREATED", "PENDING")) {
            PrimaryButton("Confirm simulated payment", !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED) { model.finishMock("SUCCESS") }
            TextButton({ model.finishMock("FAILED") }, enabled = !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED) { Text("Simulate payment failure") }
        }
        if(payment.status == "SUCCEEDED") Notice("Mock payment confirmed.", "good")
    }
    Gap(14); TextButton({ model.go(OwnerScreen.PAYMENTS) }, enabled = !model.busy) { Text("View payment history") }
}
@Composable private fun OwnerProfile(model: LandlordViewModel, profile: MyProfile, auth: AuthViewModel) {
    OwnerHeading("Your profile", "Landlord · Private account settings")
    OwnerCard { Small(profile.fullName); Small(profile.email, true); Badge(model.verificationStatus(profile.verificationStatus))
        CardLink("Landlord verification", "ID front/back, selfie and ownership proof", !model.busy) { model.go(OwnerScreen.VERIFICATION) } }
    OwnerCard {
        CardLink("Payments", "Your payment records", !model.busy) { model.go(OwnerScreen.PAYMENTS) }
        CardLink("Plans", "TEST checkout", !model.busy) { model.openCheckout("PLAN_UPGRADE") }
    }
    OutlinedButton({ auth.reloadProfile() }, enabled = !auth.busy && !model.busy) { Text("Refresh account status") }
    TextButton({ model.clearPrivateState(); auth.logout() }, enabled = !auth.busy && !model.busy) { Text("Sign out") }
}
