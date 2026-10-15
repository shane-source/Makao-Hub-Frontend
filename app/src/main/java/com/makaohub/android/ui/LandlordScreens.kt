package com.makaohub.android.ui

import android.graphics.BitmapFactory
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makaohub.android.*
import com.makaohub.android.data.*
import kotlinx.coroutines.Dispatchers
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
        OwnerScreen.APPLICATION, OwnerScreen.APPLICATIONS -> OwnerScreen.APPLICATIONS
        OwnerScreen.MESSAGES, OwnerScreen.CHAT -> OwnerScreen.MESSAGES
        OwnerScreen.PROFILE, OwnerScreen.VERIFICATION, OwnerScreen.PAYMENTS, OwnerScreen.PAYOUT, OwnerScreen.PLANS -> OwnerScreen.PROFILE
        else -> OwnerScreen.LISTINGS
    }
    Scaffold(containerColor = Cream, bottomBar = {
        if (model.screen !in listOf(OwnerScreen.VERIFICATION, OwnerScreen.EDIT, OwnerScreen.MEDIA, OwnerScreen.LOCATION, OwnerScreen.PREVIEW, OwnerScreen.BOOST)) {
            NavigationBar(containerColor = Color.White) {
                val tabs = listOf(Triple(OwnerScreen.HOME, "Home", Icons.Outlined.Home),
                    Triple(OwnerScreen.LISTINGS, "Listings", Icons.Outlined.Apartment),
                    Triple(OwnerScreen.APPLICATIONS, "Applications", Icons.Outlined.Description),
                    Triple(OwnerScreen.MESSAGES, "Messages", Icons.Outlined.ChatBubbleOutline),
                    Triple(OwnerScreen.PROFILE, "Profile", Icons.Outlined.PersonOutline))
                tabs.forEach { (page, label, icon) -> NavigationBarItem(selected = tab == page,
                    onClick = { model.go(page) }, enabled = !model.busy,
                    icon = { Icon(icon, label, Modifier.size(21.dp)) }, label = { Text(label, fontSize = 10.sp) }) }
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                if (model.screen != OwnerScreen.HOME) IconButton(back, enabled = !model.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Brown) }
                Text("makao hub", color = Brown, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Gap(12)
            if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(12) }
            model.notice?.let { Notice(it); Gap(12) }
            when(model.screen) {
                OwnerScreen.HOME -> OwnerHome(model, profile)
                OwnerScreen.VERIFICATION -> LandlordVerificationScreen(model, profile)
                OwnerScreen.LISTINGS -> OwnerListings(model)
                OwnerScreen.EDIT -> PropertyEditor(model)
                OwnerScreen.MEDIA -> ListingMedia(model)
                OwnerScreen.LOCATION -> PrivateLocation(model)
                OwnerScreen.PREVIEW -> ListingPreview(model, profile)
                OwnerScreen.CHECKS -> PublicationChecks(model, profile)
                OwnerScreen.APPLICATIONS -> ApplicationList(model)
                OwnerScreen.APPLICATION -> ApplicationReview(model)
                OwnerScreen.MESSAGES -> Conversations(model)
                OwnerScreen.CHAT -> Conversation(model, profile)
                OwnerScreen.PAYMENTS -> PaymentHistory(model)
                OwnerScreen.PAYOUT -> PayoutDestination()
                OwnerScreen.PLANS, OwnerScreen.BOOST -> TestCheckout(model)
                OwnerScreen.PROFILE -> OwnerProfile(model, profile, auth)
            }
        }
    }
}

private fun parentOf(page: OwnerScreen) = when(page) {
    OwnerScreen.MEDIA -> OwnerScreen.EDIT
    OwnerScreen.LOCATION -> OwnerScreen.MEDIA
    OwnerScreen.PREVIEW -> OwnerScreen.LOCATION
    OwnerScreen.CHECKS, OwnerScreen.EDIT, OwnerScreen.BOOST -> OwnerScreen.LISTINGS
    OwnerScreen.APPLICATION -> OwnerScreen.APPLICATIONS
    OwnerScreen.CHAT -> OwnerScreen.MESSAGES
    OwnerScreen.PAYMENTS, OwnerScreen.PAYOUT, OwnerScreen.PLANS -> OwnerScreen.PROFILE
    else -> OwnerScreen.HOME
}
@Composable private fun OwnerHeading(title: String, subtitle: String) {
    Text(title, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = Brown)
    Gap(9); Text(subtitle, fontSize = 13.sp, lineHeight = 19.sp, color = Muted); Gap(20)
}
@Composable private fun OwnerCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    Gap(12)
}
@Composable private fun Small(text: String, muted: Boolean = false) { Text(text, color = if(muted) Muted else Ink, fontSize = 13.sp, lineHeight = 19.sp) }
@Composable private fun Notice(text: String, tone: String = "info") {
    val color = when(tone) { "good" -> Color(0xFFE6F0E8); "bad" -> Color(0xFFF8E5DF); "warning" -> Color(0xFFFAEECB); else -> Sand }
    Surface(color = color, shape = RoundedCornerShape(12.dp)) { Text(text, color = Brown, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(14.dp)) }
}
@Composable private fun Badge(status: String) {
    val color = when(status) { "VERIFIED", "PUBLISHED", "APPROVED", "SUCCEEDED", "OPEN" -> Color(0xFF3D7655)
        "REJECTED", "FAILED" -> Color(0xFFAB4B3F); else -> Color(0xFF94712B) }
    Text(status.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}
@Composable private fun CardLink(title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) { Text(title, color = Brown, fontSize = 14.sp); Small(detail, true) }
    }
}
@Composable private fun OwnerInput(label: String, value: String, enabled: Boolean = true, numeric: Boolean = false, lines: Int = 1, onChange: (String) -> Unit = {}) {
    OutlinedTextField(value, onChange, enabled = enabled, label = { Text(label, fontSize = 12.sp) },
        singleLine = lines == 1, minLines = lines, shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if(numeric) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}
private fun kes(value: Long) = "KSh " + NumberFormat.getIntegerInstance(Locale.UK).format(value)
private fun moneyMinor(value: Long) = "KSh " + BigDecimal.valueOf(value, 2).toPlainString()
private fun shortId(id: String) = id.take(8).uppercase()
private fun stamp(value: String) = runCatching { DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)
    .withZone(ZoneId.of("Africa/Nairobi")).format(Instant.parse(value)) + " EAT" }.getOrDefault("Date unavailable")

@Composable private fun OwnerHome(model: LandlordViewModel, profile: MyProfile) {
    OwnerHeading("Welcome home, ${profile.fullName.substringBefore(' ')}", "A clear view of your properties and people.")
    Badge(model.verificationStatus(profile.verificationStatus)); Gap(12)
    if (model.verificationStatus(profile.verificationStatus) != "VERIFIED") {
        Notice("Complete your identity and ownership verification before publishing or receiving payouts.", "warning"); Gap(10)
        PrimaryButton("Verify as a landlord", !model.busy) { model.go(OwnerScreen.VERIFICATION) }; Gap(16)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { OwnerCard {
            Small("${model.summary?.publishedListings ?: "—"} published")
            Small("${model.summary?.draftListings ?: "—"} draft", true)
        } }
        Column(Modifier.weight(1f)) { OwnerCard {
            Small("${if(model.applicationsLoaded) model.applications.size else "—"} applications")
            Small("${if(model.applicationsLoaded) model.applications.count { it.status == "SUBMITTED" || it.status == "UNDER_REVIEW" } else "—"} need review", true)
        } }
    }
    PrimaryButton("Create property listing", !model.busy, model::newListing); Gap(20)
    OwnerCard {
        Small("Your next steps", true)
        model.applications.firstOrNull { it.status in listOf("SUBMITTED", "UNDER_REVIEW") }?.let { app ->
            CardLink("Review application ${shortId(app.id)}", "${model.listings.find { it.id == app.listingId }?.title ?: "Your property"}", !model.busy) { model.viewApplication(app) }
        }
        model.listings.firstOrNull { it.status == "DRAFT" }?.let { listing -> CardLink("Finish ${listing.title}", "Edit draft and complete publish checks", !model.busy) { model.edit(listing) } }
        CardLink("Messages", "View your protected conversations", !model.busy) { model.go(OwnerScreen.MESSAGES) }
        if (model.listings.isEmpty()) Small("Your first property starts with a private draft.", true)
    }
    OwnerCard {
        Small("Your business", true)
        CardLink("Test payment history", "View payment records", !model.busy) { model.go(OwnerScreen.PAYMENTS) }
        CardLink("Subscription", "Review test checkout", !model.busy) { model.openCheckout("PLAN_UPGRADE") }
        CardLink("Boost a listing", "Choose a listing from your portfolio", !model.busy) { model.go(OwnerScreen.LISTINGS) }
    }
    OutlinedButton({ model.refresh() }, enabled = !model.busy) { Text("Refresh workspace") }
}

@Composable private fun OwnerListings(model: LandlordViewModel) {
    var visible by remember(model.listings) { mutableIntStateOf(20) }
    OwnerHeading("Your listings", "Manage availability and review status.")
    PrimaryButton("Create property listing", !model.busy, model::newListing); Gap(18)
    if (model.listings.isEmpty()) Notice("No listings yet. Create a private draft to get started.")
    model.listings.take(visible).forEach { listing -> OwnerCard {
        Small(listing.title); Badge(listing.status)
        Small("${kes(listing.monthlyRentKes)} / month · ${listing.bedroomCount} beds · ${listing.bathroomCount} baths", true)
        Small("Updated ${stamp(listing.updatedAt)}", true)
        if (listing.status == "DRAFT") CardLink("Continue draft", "Media and private location", !model.busy) { model.edit(listing) }
        if (listing.status in listOf("PUBLISHED", "PAUSED")) {
            CardLink(if (listing.status == "PUBLISHED") "Pause listing" else "Resume published listing", "Availability is controlled by the server", !model.busy) {
                model.edit(listing); model.listingAction(if(listing.status == "PUBLISHED") "pause" else "resume")
            }
            CardLink("Review test boost", "A mock payment does not yet activate promotion", !model.busy) { model.openCheckout("BOOST", listing.id) }
        }
    } }
    if (visible < model.listings.size) TextButton({ visible += 20 }) { Text("Show more listings") }
    Gap(12); Notice("Listing moderation submission and rejection states are not available in the current API. Published, paused and draft states shown here come from the server.")
}

@Composable private fun PropertyEditor(model: LandlordViewModel) {
    val editable = model.selected == null || model.selected?.status == "DRAFT"
    val enabled = editable && !model.busy
    var typesOpen by remember { mutableStateOf(false) }
    OwnerHeading("Tell us about the property", "Step 1 of 4 · Whole property or unit")
    OwnerInput("Listing title", model.form.title, enabled) { model.form = model.form.copy(title = it.take(150)) }
    Box {
        OutlinedButton({ typesOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Property / unit type · ${model.form.propertyType.lowercase().replaceFirstChar { it.uppercase() }}") }
        DropdownMenu(typesOpen, { typesOpen = false }) { listOf("APARTMENT", "HOUSE", "STUDIO", "BEDSITTER", "ROOM").forEach { type ->
            DropdownMenuItem(text = { Text(type.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { model.form = model.form.copy(propertyType = type); typesOpen = false })
        } }
    }; Gap(12)
    OwnerInput("Bedrooms", model.form.bedrooms, enabled, numeric = true) { model.form = model.form.copy(bedrooms = it) }
    OwnerInput("Bathrooms", model.form.bathrooms, enabled, numeric = true) { model.form = model.form.copy(bathrooms = it) }
    OwnerInput("Monthly rent · KSh", model.form.rent, enabled, numeric = true) { model.form = model.form.copy(rent = it) }
    OwnerInput("Description", model.form.description, enabled, lines = 4) { model.form = model.form.copy(description = it.take(3000)) }
    OwnerInput("Available from · YYYY-MM-DD", model.form.available, enabled) { model.form = model.form.copy(available = it) }
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(model.form.furnished, { model.form = model.form.copy(furnished = it) }, enabled = enabled); Small("Furnished") }
    Notice("List as a landlord\nWhole properties and units belong here. Spare-room and servant-quarter roommate listings belong in the Sublessor workspace."); Gap(12)
    Notice("Deposit and additional amenities cannot yet be saved by the listing API. They are not included in this draft."); Gap(16)
    PrimaryButton("Save and continue", enabled) { model.saveDraft(true) }
    TextButton({ model.saveDraft(false) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
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
    var remove by remember { mutableStateOf<OwnerMedia?>(null) }
    OwnerHeading("Show the home clearly", "Step 2 of 4 · Photos and video")
    LocalPhoto(model.media.firstNotNullOfOrNull { model.localPhotos[it.id] })
    Small("${model.media.count { it.mediaType == "IMAGE" }} photo records · ${model.media.count { it.mediaType == "VIDEO" }} video records", true); Gap(12)
    Notice("Choose clear photos. Files are uploaded privately and must pass the server's size and content checks before publication."); Gap(12)
    if (model.pendingConfirmationId != null) {
        Notice("The file reached storage, but confirmation is incomplete. Retry the server check without uploading it again.", "warning"); Gap(8)
        OutlinedButton(model::retryConfirmation, enabled = !model.busy) { Text("Retry confirmation") }; Gap(12)
    }
    OutlinedButton({ picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !model.busy && model.pendingConfirmationId == null && model.media.count { it.mediaType == "IMAGE" } < 20, modifier = Modifier.fillMaxWidth()) { Text("Add photos") }
    OutlinedButton({ picker.launch(arrayOf("video/mp4")) }, enabled = !model.busy && model.pendingConfirmationId == null && model.media.none { it.mediaType == "VIDEO" }, modifier = Modifier.fillMaxWidth()) { Text("Add video walkthrough") }; Gap(14)
    model.media.forEach { item -> OwnerCard {
        LocalPhoto(model.localPhotos[item.id])
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if(item.mediaType == "IMAGE") Icons.Outlined.Image else Icons.Outlined.Videocam, item.mediaType); Spacer(Modifier.width(8.dp)); Small("${item.mediaType.lowercase().replaceFirstChar { it.uppercase() }} · ${item.sizeBytes / 1024} KB") }
        Small("Record ${shortId(item.id)} · ${item.contentType}", true)
        Small("The server checks readiness before publishing.", true)
        TextButton({ remove = item }, enabled = !model.busy) { Text("Remove ${item.mediaType.lowercase()}", color = Color(0xFFAB4B3F)) }
    } }
    if (model.media.isEmpty()) Small("No uploaded media yet.", true)
    TextButton(model::loadMedia, enabled = !model.busy) { Text("Refresh saved media records") }
    Notice("Existing media thumbnails, cover selection and reordering need additional backend fields. Photos selected in this session are previewed above."); Gap(16)
    PrimaryButton("Continue to location", !model.busy, model::openLocation)
    TextButton({ model.go(OwnerScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Return to saved drafts") }
    remove?.let { item -> AlertDialog(onDismissRequest = { if(!model.busy) remove = null }, title = { Text("Delete this ${item.mediaType.lowercase()}?") },
        text = { Text("This removes the file from the draft and storage. Your other media is unchanged. Choose another file afterwards to replace it.") },
        confirmButton = { TextButton({ remove = null; model.removeMedia(item.id) }, enabled = !model.busy) { Text("Confirm delete") } },
        dismissButton = { TextButton({ remove = null }) { Text("Keep media") } }) }
}

@Composable private fun PrivateLocation(model: LandlordViewModel) {
    var area by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.areaLabel ?: "") }
    var pin by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.let {
        org.maplibre.android.geometry.LatLng(it.latitude.toDouble(), it.longitude.toDouble())
    }) }
    OwnerHeading("Set the private location", "Step 3 of 4 · Public area, private map pin")
    OwnerInput("Public approximate area", area, !model.busy) { area = it }
    Notice("The exact pin stays private. Only the approximate area is public; the backend controls paid location access."); Gap(16)
    PropertyMapPicker(pin, !model.busy) { pin = it }
    Gap(16)
    PrimaryButton("Save private pin", !model.busy && pin != null) {
        pin?.let { model.saveMapLocation(area, it.latitude, it.longitude) }
    }
    TextButton({ model.go(OwnerScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft and return") }
}

@Composable private fun ListingPreview(model: LandlordViewModel, profile: MyProfile) {
    val listing = model.selected ?: return
    OwnerHeading("Preview your listing", "Step 4 of 4 · What renters will see")
    LocalPhoto(model.media.firstNotNullOfOrNull { model.localPhotos[it.id] })
    OwnerCard {
        if (model.verificationStatus(profile.verificationStatus) == "VERIFIED") Badge("VERIFIED")
        Small(listing.title); Small("${model.location?.areaLabel ?: "Area not set"} · ${listing.propertyType.lowercase()}", true)
        Small("${kes(listing.monthlyRentKes)} / month"); Small("${listing.bedroomCount} beds · ${listing.bathroomCount} baths · ${if(listing.furnished) "Furnished" else "Unfurnished"}", true)
        Small(listing.description); Small("Available · ${listing.availableFrom ?: "Not specified"}", true)
        Small("Exact location locked", true)
    }
    OwnerCard {
        Small("Ready for publication?")
        Small("Role verification · ${model.verificationStatus(profile.verificationStatus)}")
        Small("Media · ${model.media.size} records; confirmation checked by server")
        Small("Private location · ${if(model.location != null) "Saved" else "Required"}")
    }
    Notice("The current backend publishes directly after its verification, media and location checks. Staff review submission is not available yet."); Gap(16)
    PrimaryButton("Review publish checks", !model.busy) { model.go(OwnerScreen.CHECKS) }
    TextButton({ model.go(OwnerScreen.EDIT) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Edit listing") }
}

@Composable private fun PublicationChecks(model: LandlordViewModel, profile: MyProfile) {
    OwnerHeading("Publish checks and outcomes", "Listing state from the server")
    val listing = model.selected ?: return
    Badge(listing.status); Gap(12)
    if (model.verificationStatus(profile.verificationStatus) != "VERIFIED") {
        Notice("Verification required\nComplete Landlord verification before publishing.", "warning"); Gap(12)
        OutlinedButton({ model.go(OwnerScreen.VERIFICATION) }, enabled = !model.busy) { Text("Open landlord verification") }; Gap(12)
    }
    if (model.media.none { it.mediaType == "IMAGE" }) { Notice("Media required\nUpload and confirm at least one photo.", "bad"); Gap(12) }
    if (model.location == null) { Notice("Private location\nThe server requires a saved pin and public approximate area.", "warning"); Gap(12) }
    if (listing.status == "DRAFT") PrimaryButton("Publish listing", !model.busy && model.verificationStatus(profile.verificationStatus) == "VERIFIED") { model.listingAction("publish") }
    if (listing.status == "PUBLISHED") {
        Notice("Published\nYour listing is visible. Location and contact protections remain enforced by the server.", "good"); Gap(12)
        PrimaryButton("Pause listing", !model.busy) { model.listingAction("pause") }
    }
    if (listing.status == "PAUSED") { Notice("Paused\nHidden from discovery. Existing applications and payment records are retained.", "warning"); Gap(12)
        PrimaryButton("Resume published listing", !model.busy) { model.listingAction("resume") } }
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
    Gap(12); Notice("Respect private identity evidence\nRenter ID images and face scans are never shown to hosts. The current application API returns identifiers and status; renter names and employment summaries are not supplied.")
}

@Composable private fun ApplicationReview(model: LandlordViewModel) {
    val app = model.selectedApplication ?: return
    var decision by remember(app.id) { mutableStateOf<String?>(null) }
    OwnerHeading("Review this application", "Application ${shortId(app.id)}")
    OwnerCard {
        Small("Application summary")
        Small(model.listings.find { it.id == app.listingId }?.title ?: "Property ${shortId(app.listingId)}")
        Badge(app.status); Small("Submitted · ${stamp(app.createdAt)}"); Small("Updated · ${stamp(app.updatedAt)}")
        Small("Employment, income and move-in date are not provided by this API.", true)
    }
    Notice("No renter ID access\nDo not request identity documents or phone numbers in chat. Identity verification evidence remains private."); Gap(18)
    if (app.status == "SUBMITTED") PrimaryButton("Mark under review", !model.busy) { model.decision("review") }
    if (app.status in listOf("SUBMITTED", "UNDER_REVIEW")) {
        Gap(12); PrimaryButton("Approve application", !model.busy) { decision = "approve" }
        TextButton({ decision = "reject" }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Reject application", color = Color(0xFFAB4B3F)) }
    }
    Gap(12); Notice("The current rejection endpoint records status only. A stored rejection reason and renter notification require a backend extension.")
    CardLink("Open protected conversations", "No contact information is exposed", !model.busy) { model.go(OwnerScreen.MESSAGES) }
    decision?.let { action -> AlertDialog(onDismissRequest = { decision = null }, title = { Text(if(action == "approve") "Approve application?" else "Reject application?") },
        text = { Text(if(action == "approve") "The server will record approval. This action does not move any money." else "The server will record rejection without a saved reason. This cannot be changed from this screen.") },
        confirmButton = { TextButton({ decision = null; model.decision(action) }, enabled = !model.busy) { Text("Confirm ${action}") } },
        dismissButton = { TextButton({ decision = null }) { Text("Keep reviewing") } }) }
}

@Composable private fun Conversations(model: LandlordViewModel) {
    OwnerHeading("Conversations with renters", "Protected communication inside Makao Hub")
    if (model.threads?.items.isNullOrEmpty()) Notice("No conversations are available. Eligible renter conversations appear here after they are created on the server.")
    model.threads?.items?.forEach { thread -> OwnerCard {
        CardLink("Conversation ${shortId(thread.id)}", "Application ${shortId(thread.rentalApplicationId)}", !model.busy) { model.openThread(thread) }
        Badge(thread.status); Small("Updated ${stamp(thread.updatedAt)}", true)
    } }
    model.threads?.let { Pagination(it.page, it.hasNext, model.busy, model::loadThreads) }
}
@Composable private fun Conversation(model: LandlordViewModel, profile: MyProfile) {
    OwnerHeading("Your protected chat", "Conversation ${model.thread?.id?.let(::shortId) ?: ""}")
    Notice("Personal contact attempts are masked and flagged by the server. Keep booking and payment discussions inside Makao Hub."); Gap(12)
    model.messages?.let { page ->
        if (page.hasNext) TextButton(model::olderMessages, enabled = !model.busy) { Text("Load older messages") }
        page.items.asReversed().forEach { item -> OwnerCard {
            Small(if(item.senderId == profile.id) "You" else "Renter", true)
            Small(item.content)
            if(item.contactMasked) Notice("Contact information masked · Flagged for moderation", "warning")
            Small(stamp(item.createdAt), true)
        } }
    }
    if (!model.chatReady && model.thread?.status == "OPEN") OutlinedButton(model::reconnectChat, enabled = !model.busy) { Text("Connect protected chat") }
    OwnerInput("Message · up to 2,000 characters", model.chatDraft, model.chatReady && !model.awaitingEcho, lines = 3, onChange = model::changeChatDraft)
    PrimaryButton(if(model.awaitingEcho) "Waiting for saved message…" else "Send message", model.chatReady && !model.awaitingEcho && model.chatDraft.isNotBlank(), model::sendMessage)
}

@Composable private fun PaymentHistory(model: LandlordViewModel) {
    OwnerHeading("Your payments, clearly", "Amounts and statuses returned by the server")
    if (BuildConfig.MOCK_PAYMENTS_ENABLED) Notice("TEST / MOCK PAYMENT\nNo real money moves. Test receipts describe simulated payment states.", "warning")
    Gap(12)
    if(model.payments?.items.isNullOrEmpty()) Notice("No payment records found for your account.")
    model.payments?.items?.forEach { item -> OwnerCard {
        Small(item.paymentType.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }); Badge(item.status)
        Small(if(item.currency == "KES") moneyMinor(item.amountMinor) else "${item.currency} · ${item.amountMinor} minor units")
        Small("Receipt ${shortId(item.id)} · ${stamp(item.createdAt)}", true)
    } }
    model.payments?.let { Pagination(it.page, it.hasNext, model.busy, model::loadPayments) }
    Notice("Incoming rent, deposit ledgers and owner payout history are not returned by this payment-history endpoint."); Gap(12)
    CardLink("View payout destination", "Backend integration pending", !model.busy) { model.go(OwnerScreen.PAYOUT) }
}
@Composable private fun Pagination(page: Int, next: Boolean, busy: Boolean, select: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton({ select(page - 1) }, enabled = page > 0 && !busy) { Text("Previous") }
        Small("Page ${page + 1}")
        TextButton({ select(page + 1) }, enabled = next && !busy) { Text("Next") }
    }
}
@Composable private fun PayoutDestination() {
    OwnerHeading("Your payout destination", "Private account settings")
    OwnerInput("Destination method", "M-Pesa", false)
    OwnerInput("Destination phone", "Not available from the backend", false)
    OwnerInput("Account holder", "Not available from the backend", false)
    Notice("A destination change must require re-verification and a cooling-off period. The current backend exposes no payout-destination update endpoint, so editing is unavailable.", "warning"); Gap(18)
    PrimaryButton("Save and request re-verification", false) {}
}
@Composable private fun TestCheckout(model: LandlordViewModel) {
    val boost = model.checkoutType == "BOOST"
    OwnerHeading(if(boost) "Give your home a boost" else "Choose a plan that fits", "Test checkout · No live billing")
    Notice("TEST / MOCK PAYMENT\nNo real money moves. Only a payment receipt is simulated; a paid receipt does not establish a plan or boost entitlement.", "warning"); Gap(16)
    OwnerCard {
        Small(if(boost) "Boost checkout" else "Plan upgrade checkout")
        Small("Price is set by the backend and shown before you confirm.", true)
        Small("Mock M-Pesa")
        Small("Plan tiers, allowances and promotion duration require a backend catalogue.", true)
    }
    val payment = model.checkout
    if(payment == null) PrimaryButton("Review TEST checkout", !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED, model::createCheckout)
    else {
        OwnerCard { Small("Server quote · ${if(payment.currency == "KES") moneyMinor(payment.amountMinor) else payment.currency}"); Badge(payment.status); Small("Receipt ${shortId(payment.id)}", true) }
        if(payment.status in listOf("CREATED", "PENDING")) {
            PrimaryButton("Confirm simulated payment", !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED) { model.finishMock("SUCCESS") }
            TextButton({ model.finishMock("FAILED") }, enabled = !model.busy && BuildConfig.MOCK_PAYMENTS_ENABLED) { Text("Simulate payment failure") }
        }
        if(payment.status == "SUCCEEDED") Notice("Mock payment confirmed\nPlan or boost activation is not yet returned by the backend.", "good")
    }
    Gap(14); TextButton({ model.go(OwnerScreen.PAYMENTS) }, enabled = !model.busy) { Text("View payment history") }
}
@Composable private fun OwnerProfile(model: LandlordViewModel, profile: MyProfile, auth: AuthViewModel) {
    OwnerHeading("Your profile", "Landlord · Private account settings")
    OwnerCard { Small(profile.fullName); Small(profile.email, true); Badge(model.verificationStatus(profile.verificationStatus))
        CardLink("Landlord verification", "ID front/back, selfie and ownership proof", !model.busy) { model.go(OwnerScreen.VERIFICATION) } }
    OwnerCard {
        CardLink("Payments", "Your payment records", !model.busy) { model.go(OwnerScreen.PAYMENTS) }
        CardLink("Payout destination", "Re-verification required for changes", !model.busy) { model.go(OwnerScreen.PAYOUT) }
        CardLink("Plans", "TEST checkout", !model.busy) { model.openCheckout("PLAN_UPGRADE") }
    }
    OutlinedButton({ auth.reloadProfile() }, enabled = !auth.busy && !model.busy) { Text("Refresh account status") }
    TextButton({ model.clearPrivateState(); auth.logout() }, enabled = !auth.busy && !model.busy) { Text("Sign out") }
}
