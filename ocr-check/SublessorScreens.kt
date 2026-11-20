package com.makaohub.android.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makaohub.android.*
import com.makaohub.android.data.*
import org.maplibre.android.geometry.LatLng
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Called only after the authenticated profile selects the active SUBLESSOR workspace. */
@Composable fun SublessorWorkspace(auth: AuthViewModel, profile: MyProfile) {
    val model: SublessorViewModel = viewModel(key = "sublessor-${profile.id}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SublessorViewModel(SublessorRepository(auth.repository)) { auth.navigate(Page.LOGIN) } as T
    })
    val context = LocalContext.current
    val window = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window }
    val sensitive = model.screen in listOf(SharedScreen.VERIFICATION, SharedScreen.LOCATION)
    DisposableEffect(window, sensitive) {
        if (sensitive) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (sensitive) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.background()
            else if (event == Lifecycle.Event.ON_RESUME) model.foreground()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); model.clear() }
    }
    LaunchedEffect(model) { model.start() }
    LaunchedEffect(model, lifecycle, model.verification?.status) {
        if (model.verification?.status == "PENDING") lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                model.refreshReviewStatus()
                delay(15_000L) // Foreground-only status reads; no push channel.
            }
        }
    }
    val back = { model.go(when (model.screen) {
        SharedScreen.MEDIA -> SharedScreen.DETAILS
        SharedScreen.LOCATION -> SharedScreen.MEDIA
        SharedScreen.PREVIEW -> SharedScreen.LOCATION
        SharedScreen.APPLICATION -> SharedScreen.APPLICATIONS
        SharedScreen.CHAT -> SharedScreen.INBOX
        SharedScreen.PLANS -> SharedScreen.PROFILE
        SharedScreen.RENTER_PROFILE -> SharedScreen.DISCOVERY
        SharedScreen.DISCOVERY -> SharedScreen.PREFERENCE_SUMMARY
        else -> SharedScreen.HOME
    }) }
    BackHandler(model.screen != SharedScreen.HOME, onBack = back)
    if (model.screen == SharedScreen.VERIFICATION) { SublessorVerification(model); return }
    if (model.screen == SharedScreen.HOME && model.verification?.status == "PENDING") {
        PendingVerificationDashboard(
            fullName = profile.fullName,
            idUploaded = listOf(SharedEvidence.ID_FRONT, SharedEvidence.ID_BACK).all { sharedEvidenceReady(it, model.documents) },
            proofLabel = "Rental Agreement (PDF)",
            draftTitles = model.listings.filter { it.status == "DRAFT" }.take(20).map { it.title },
            busy = model.busy,
            onDashboard = { model.go(SharedScreen.HOME) }, onListings = { model.go(SharedScreen.LISTINGS) },
            onInbox = { model.go(SharedScreen.INBOX) }, onProfile = { model.go(SharedScreen.PROFILE) },
            onVerification = { model.go(SharedScreen.VERIFICATION) }
        )
        return
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.notice) { model.notice?.let { snackbar.showSnackbar(it); model.dismissNotice() } }
    val editor = model.screen in listOf(SharedScreen.DETAILS, SharedScreen.MEDIA, SharedScreen.LOCATION, SharedScreen.PREVIEW)
    Scaffold(containerColor = if (model.screen in listOf(SharedScreen.HOME, SharedScreen.LISTINGS, SharedScreen.PROFILE, SharedScreen.INBOX, SharedScreen.APPLICATIONS)) DashboardBackground else Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (!editor) Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp, top = 8.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) { FixedFontScale { NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            listOf(Triple(SharedScreen.HOME, "Home", Icons.Outlined.Home), Triple(SharedScreen.LISTINGS, "Listings", Icons.Outlined.Apartment),
                Triple(SharedScreen.APPLICATIONS, "Applications", Icons.Outlined.Description), Triple(SharedScreen.INBOX, "Messages", Icons.Outlined.ChatBubbleOutline),
                Triple(SharedScreen.PROFILE, "Profile", Icons.Outlined.PersonOutline)).forEach { (target, label, icon) ->
                val selected = when (target) {
                    SharedScreen.HOME -> model.screen == SharedScreen.HOME
                    SharedScreen.LISTINGS -> model.screen in listOf(SharedScreen.LISTINGS, SharedScreen.STATES)
                    SharedScreen.APPLICATIONS -> model.screen in listOf(SharedScreen.APPLICATIONS, SharedScreen.APPLICATION)
                    SharedScreen.INBOX -> model.screen in listOf(SharedScreen.INBOX, SharedScreen.CHAT)
                    SharedScreen.PROFILE -> model.screen in listOf(SharedScreen.PROFILE, SharedScreen.PAYMENTS, SharedScreen.PLANS)
                    else -> false
                }
                NavigationBarItem(selected = selected, onClick = { model.go(target) }, enabled = !model.busy,
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = Brown, selectedTextColor = Brown, unselectedIconColor = Muted, unselectedTextColor = Muted),
                    icon = { BadgedTabIcon(icon, when (target) { SharedScreen.APPLICATIONS -> ApplicationSeen.unseen(androidx.compose.ui.platform.LocalContext.current, model.applications.map { "${it.id}:${it.status}" })
                        SharedScreen.INBOX -> ChatSeen.unread(androidx.compose.ui.platform.LocalContext.current, model.inboxThreads.map { it.id to it.lastMessageAt })
                        else -> 0 }, LocalContentColor.current) }, label = { NavLabel(label) })
            }
        } } } }
    }) { insets ->
        val scroll = rememberScrollState()
        LaunchedEffect(model.screen) { scroll.scrollTo(0) }
        LaunchedEffect(model) { while (true) { model.refreshBadges(); kotlinx.coroutines.delay(8_000) } }
        val seenContext = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(model.screen, model.applications) {
            if (model.screen in listOf(SharedScreen.APPLICATIONS, SharedScreen.APPLICATION))
                ApplicationSeen.markSeen(seenContext, model.applications.map { "${it.id}:${it.status}" })
        }
        Column(Modifier.fillMaxSize().padding(insets).statusBarsPadding().imePadding().verticalScroll(scroll).padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back, enabled = !model.busy, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Brown, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(6.dp)); Text("makao hub", color = Brown, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton({ model.go(SharedScreen.PAYMENTS) }, enabled = !model.busy, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Payments, "Payments", tint = Brown, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(56.dp))
            }
            if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(10) }
            when (model.screen) {
                SharedScreen.HOME -> SharedHome(model, profile)
                SharedScreen.LISTINGS -> SharedListings(model)
                SharedScreen.DETAILS -> SharedDetails(model)
                SharedScreen.MEDIA -> SharedMedia(model)
                SharedScreen.LOCATION -> SharedLocation(model)
                SharedScreen.PREVIEW -> SharedPreview(model)
                SharedScreen.STATES -> SharedStates(model)
                SharedScreen.APPLICATIONS -> SharedApplications(model)
                SharedScreen.APPLICATION -> SharedApplication(model)
                SharedScreen.INTERESTS -> SharedInterests(model)
                SharedScreen.PREFERENCES -> SharedPreferenceEditor(model)
                SharedScreen.PREFERENCE_SUMMARY -> SharedPreferenceSummary(model)
                SharedScreen.DISCOVERY -> SharedDiscovery(model)
                SharedScreen.RENTER_PROFILE -> SharedRenterProfile(model)
                SharedScreen.INBOX -> SharedInbox(model)
                SharedScreen.CHAT -> SharedConversation(model, profile)
                SharedScreen.PAYMENTS -> SharedPayments(model)
                SharedScreen.PLANS -> PlansPanel(model::boostOffers, model::planCheckout, model::reconcileBoost,
                    model.listings.filter { it.status == "PUBLISHED" }.map { it.id to it.title },
                    model::redeemIncludedBoost) { model.go(SharedScreen.PLANS) }
                SharedScreen.PROFILE -> {
                    AccountProfile(profile, model.verification?.status ?: "NOT_STARTED", model.busy, auth,
                        documents = model.documents,
                        actions = listOf(
                            ProfileAction("My listings", Icons.Outlined.Home) { model.go(SharedScreen.LISTINGS) },
                            ProfileAction("Plans & boosts", Icons.Outlined.Bolt) { model.go(SharedScreen.PLANS) },
                            ProfileAction("Boost a listing", Icons.Outlined.RocketLaunch) { model.go(SharedScreen.LISTINGS) },
                            ProfileAction("Payment history & payouts", Icons.Outlined.Payments) { model.go(SharedScreen.PAYMENTS) }),
                        onRefreshDocuments = model::refreshVerification,
                        onVerification = { model.go(SharedScreen.VERIFICATION) },
                        onLogout = { model.clear(); auth.logout() })
                }
                SharedScreen.VERIFICATION -> Unit
            }
        }
    }
}

@Composable private fun SharedHome(model: SublessorViewModel, profile: MyProfile) {
    val draft = model.unfinishedDraft
    LaunchedEffect(model.listings) { model.refreshUnfinishedDraft() }
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(draft?.id) { if (draft != null && DraftPrompt.firstTime(draft.id)) asking = true }
    if (asking && draft != null) DraftChoiceDialog(draft.title, model.busy,
        { asking = false; model.edit(draft) }, { asking = false; model.newListing() }, { asking = false })
    PartnerDashboard(role = "Sublessor", plansOffers = model::boostOffers, verification = model.verification?.status ?: profile.verificationStatus ?: "NOT_STARTED",
        active = if (model.workspaceLoaded) model.listings.count { it.status == "PUBLISHED" } else null,
        drafts = if (model.workspaceLoaded) model.listings.count { it.status == "DRAFT" } else null,
        pending = if (model.workspaceLoaded) model.applications.count { it.status in listOf("SUBMITTED", "UNDER_REVIEW") } else null,
        applications = model.applications, applicationsLoaded = model.workspaceLoaded, listingTitle = { id -> model.listings.firstOrNull { it.id == id }?.title }, busy = model.busy,
        onPlans = { model.go(SharedScreen.PLANS) },
        onCreate = { if (draft != null) asking = true else model.newListing() }, onApplications = { model.go(SharedScreen.APPLICATIONS) },
        onBoost = { model.go(SharedScreen.LISTINGS) }, onApplication = model::openApplication,
        onMessages = { model.go(SharedScreen.INBOX) }, onPayments = { model.go(SharedScreen.PAYMENTS) },
        onVerify = { model.go(SharedScreen.VERIFICATION) })
    SharedLink("Roommate interests", !model.busy) { model.go(SharedScreen.INTERESTS) }
    SharedLink("Roommate preferences", !model.busy) { model.go(SharedScreen.PREFERENCE_SUMMARY) }
}

@Composable private fun SharedDetails(model: SublessorViewModel) {
    SharedHeading("Make room for someone new", "Step 1 of 4 · Shared-space details")
    SharedInput("Listing title", model.form.title, !model.busy) { model.form = model.form.copy(title = it) }
    SharedCard {
        SharedText("Room type", true)
        Row {
            listOf("SPARE_ROOM" to "Spare room", "SERVANT_QUARTER" to "Servant quarter").forEach { (value, title) ->
                FilterChip(selected = model.form.spaceType == value, onClick = { model.form = model.form.copy(spaceType = value) }, enabled = !model.busy, label = { Text(title, fontSize = 12.sp) })
                Spacer(Modifier.width(6.dp))
            }
        }
    }
    SharedInput("Monthly rent · KSh", model.form.rent, !model.busy, number = true) { model.form = model.form.copy(rent = it) }
    SharedInput("Deposit · KSh", model.form.deposit, !model.busy, number = true) { model.form = model.form.copy(deposit = it) }
    SharedInput("Current household", model.form.currentHousehold, !model.busy) { model.form = model.form.copy(currentHousehold = it) }
    SharedInput("Total occupants", model.form.occupants, !model.busy, number = true) { model.form = model.form.copy(occupants = it) }
    SharedInput("People per room", model.form.peoplePerRoom, !model.busy, number = true) { model.form = model.form.copy(peoplePerRoom = it) }
    SharedInput("Household lifestyle", model.form.lifestyle, !model.busy, lines = 2) { model.form = model.form.copy(lifestyle = it) }
    SharedInput("Shared home description", model.form.description, !model.busy, lines = 3) { model.form = model.form.copy(description = it) }
    ListingCountDropdown("Bathrooms", model.form.bathrooms, 1..20, !model.busy) { model.form = model.form.copy(bathrooms = it) }
    SharedInput("Available from · YYYY-MM-DD", model.form.available, !model.busy) { model.form = model.form.copy(available = it) }
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(model.form.furnished, { model.form = model.form.copy(furnished = it) }, enabled = !model.busy); SharedText("Furnished") }
    Gap(10)
    AmenitySelector(model.amenityOptions, model.selectedAmenityIds, !model.busy, model::toggleAmenity)
    SharedButton("Save and continue", !model.busy) { model.saveDraft(true) }
    TextButton({ model.saveDraft(false) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
}

@Composable private fun SharedMedia(model: SublessorViewModel) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.uploadMedia(resolver, it) }
    val queue = remember { mutableStateListOf<android.net.Uri>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> queue.clear(); queue.addAll(uris) }
    var deleting by remember { mutableStateOf<OwnerMedia?>(null) }
    MediaStepHeader(2, 4, "Show the shared space", "Bright, honest photos of the bedroom and common areas bring the right roommates.")
    LocalPhoto(model.media.firstNotNullOfOrNull { model.photos[it.id] })
    Gap(4)
    var photoLimit by remember { mutableIntStateOf(2) }
    LaunchedEffect(Unit) {
        try { val o = model.boostOffers(); photoLimit = if (o.activePhotoLimit < 0) Int.MAX_VALUE else o.activePhotoLimit }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { }
    }
    val photoCount = model.media.count { it.mediaType == "IMAGE" }
    if (photoCount >= photoLimit) {
        SharedText("Your plan allows $photoLimit photos per listing. Upgrade your plan to add more.", true)
        SharedLink("View plans", !model.busy) { model.go(SharedScreen.PLANS) }
    }
    // Photos chosen together upload one after another; stops on any failure so Retry still works.
    LaunchedEffect(queue.size, model.busy, model.pendingMedia, photoCount) {
        if (queue.isEmpty() || model.busy) return@LaunchedEffect
        if (model.pendingMedia != null || photoCount >= photoLimit) { queue.clear(); return@LaunchedEffect }
        model.uploadMedia(resolver, queue.removeAt(0))
    }
    if (queue.isNotEmpty()) { SharedText("Uploading photos… ${queue.size} more waiting", true) }
    PhotoAddCard(photoCount, photoLimit, !model.busy && queue.isEmpty() && model.pendingMedia == null && photoCount < photoLimit) {
        photoPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
    }
    if (model.media.none { it.mediaType == "VIDEO" }) VideoAddCard(false, false, !model.busy && model.pendingMedia == null, { picker.launch(arrayOf("video/mp4")) }, {})
    if (photoCount == 0) PhotoTipsCard()
    model.media.forEach { value -> SharedCard {
        LocalPhoto(model.photos[value.id])
        SharedText(if (value.mediaType == "VIDEO") "Shared-home video tour" else "Selected photo")
        SharedText("${value.sizeBytes / 1024} KB · ${if (value.confirmed) "Upload confirmed" else "Confirmation required"}", true)
        if (!value.confirmed && model.pendingMedia == null) SharedLink("Confirm stored upload", !model.busy) { model.confirmStoredMedia(value) }
        SharedLink("Remove / delete ${if (value.mediaType == "IMAGE") "photo" else "video"}", !model.busy) { deleting = value }
    } }
    if (model.pendingMedia != null) SharedButton("Retry current upload", !model.busy) { model.retryMedia(resolver) }
    SharedButton("Continue to private location", !model.busy, model::openLocation)
    TextButton({ model.go(SharedScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
    deleting?.let { value -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete selected media?") },
        text = { Text("Your other photos remain.") }, confirmButton = { TextButton({ deleting = null; model.deleteMedia(value) }, enabled = !model.busy) { Text("Confirm delete", color = Color(0xFFAB4B3F)) } },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
}

@Composable private fun SharedLocation(model: SublessorViewModel) {
    var area by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.areaLabel ?: "") }
    var pin by remember(model.selected?.id, model.location) { mutableStateOf(model.location?.let { LatLng(it.latitude.toDouble(), it.longitude.toDouble()) }) }
    SharedHeading("Set your private home pin", "Step 3 of 4 · Pick your county, then find your address")
    PropertyMapPicker(pin, !model.busy, initialCounty = area, onCounty = { area = it }) { pin = it }
    SharedButton("Save exact pin", !model.busy && pin != null && area.isNotBlank()) { pin?.let { model.savePin(area, it.latitude, it.longitude) } }
    TextButton({ model.go(SharedScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
}

@Composable private fun SharedPreview(model: SublessorViewModel) {
    val listing = model.selected ?: return
    SharedHeading("Ready to welcome a roommate", "Step 4 of 4 · Preview and submit")
    SharedCard {
        LocalPhoto(model.media.firstNotNullOfOrNull { model.photos[it.id] })
        if (model.verification?.status == "VERIFIED") SharedStatus("Verified Sublessor")
        SharedText(listing.title); SharedText("${model.location?.areaLabel ?: "Area not set"} · ${if (listing.spaceType == "SPARE_ROOM") "Spare room" else "Servant quarter"}", true)
        SharedText("${sharedKes(listing.monthlyRentKes)} / month"); SharedText("Exact location locked", true)
    }
    SharedCard {
        SharedText("Household snapshot", true); SharedText(listing.description)
        model.household?.let { value ->
            value.currentHousehold?.let { SharedText(it) }
            value.totalOccupants?.let { SharedText("$it people total${value.peoplePerRoom?.let { n -> " · $n per room" } ?: ""}") }
            value.lifestyle?.let { SharedText(it) }
            value.depositKes?.let { SharedText("Deposit · ${sharedKes(it)}") }
        }
        SharedText(if (listing.furnished) "Furnished" else "Unfurnished")
        SharedText("Private pin · ${if (model.location == null) "Missing" else "Saved"}")
        SharedText("Rental agreement · ${if (model.verification?.status == "VERIFIED") "Verified" else "Verification required"}")
    }
    if (model.listingReview?.moderationSubmittedAt == null) {
        SharedButton("Submit shared listing for review", !model.busy && model.verification?.status == "VERIFIED"
            && model.media.any { it.mediaType == "IMAGE" && it.confirmed } && model.location != null, model::submitListingReview)
    } else SharedNote("Under staff review\nPublication remains locked until a moderator records a decision.")
    TextButton({ model.go(SharedScreen.DETAILS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Edit shared listing") }
}

@Composable private fun SharedListings(model: SublessorViewModel) {
    var count by remember(model.listings) { mutableIntStateOf(20) }
    var selected by remember { mutableStateOf("ALL") }
    val activeListings = model.listings.filter { it.status != "ARCHIVED" }
    val draft = model.unfinishedDraft
    LaunchedEffect(model.listings) { model.refreshUnfinishedDraft() }
    var asking by remember { mutableStateOf(false) }
    if (asking && draft != null) DraftChoiceDialog(draft.title, model.busy,
        { asking = false; model.edit(draft) }, { asking = false; model.newListing() }, { asking = false })
    PartnerListingHeader("SUBLESSOR", selected, activeListings.groupingBy { it.status }.eachCount(), activeListings.size,
        model.busy, { selected = it; count = 20 }, { if (draft != null) asking = true else model.newListing() })
    val filtered = activeListings.filter { selected == "ALL" || it.status == selected }
    filtered.take(count).forEach { value ->
        val metrics = model.cardMetrics[value.id]
        PartnerListingCard(value.id, value.title, value.status, value.monthlyRentKes,
            "${value.bathroomCount} bath · ${value.spaceType.lowercase().replace('_', ' ')}",
            value.availableFrom, metrics?.viewsToday, if (model.workspaceLoaded) model.applications.count { it.listingId == value.id } else null,
            model.busy, model::listingCover, { model.viewListingApplications(value.id) }, { model.edit(value) },
            { model.updateListing(value) }, { model.removeListing(value) }) {
            ListingBoost(value.id, value.status == "PUBLISHED", metrics?.boostExpiresAt != null, model.busy,
                model::boostOffers, model::buyBoost, model::reconcileBoost) { model.go(SharedScreen.LISTINGS) }
        }
    }
    if (filtered.isEmpty()) SharedText(if (activeListings.isEmpty()) "No shared listings yet." else "No listings in this category.", true)
    if (count < filtered.size) SharedLink("Load more", true) { count += 20 }
}

@Composable private fun SharedStates(model: SublessorViewModel) {
    val listing = model.selected ?: return
    SharedHeading("Shared-listing alternatives", "Manage, publish and media states")
    if (model.verification?.status != "VERIFIED") {
        SharedNote("Verification required\nComplete Sublessor verification before publishing.")
        SharedLink("Open verification", !model.busy) { model.go(SharedScreen.VERIFICATION) }
    }
    SharedCard {
        SharedText(listing.title); SharedStatus(listing.status)
        if (listing.status == "PUBLISHED") SharedButton("Pause shared listing", !model.busy) { model.transition("pause") }
        else when (val review=model.listingReview) {
            null -> SharedButton("Refresh review status", !model.busy) { model.go(SharedScreen.STATES) }
            else -> when (review.moderationStatus) {
                "PENDING" -> if (review.moderationSubmittedAt == null) {
                    SharedLink("Edit shared listing", !model.busy) { model.go(SharedScreen.DETAILS) }
                    SharedLink("Manage media", !model.busy, model::loadMedia)
                    SharedButton("Submit shared listing for review", !model.busy && model.verification?.status == "VERIFIED"
                        && model.media.any { it.mediaType == "IMAGE" && it.confirmed } && model.location != null, model::submitListingReview)
                } else {
                    SharedNote("Under staff review\nPublication remains locked until a moderator records a decision.")
                    SharedLink("Refresh review status", !model.busy) { model.go(SharedScreen.STATES) }
                    SharedLink("Back to dashboard", !model.busy) { model.go(SharedScreen.HOME) }
                }
                "REJECTED" -> {
                    SharedNote("Changes required\n${review.moderationReason ?: "Open the listing and correct the submitted details."}")
                    SharedButton("Revise shared listing", !model.busy, model::reviseListingReview)
                }
                "APPROVED" -> {
                    SharedNote(if (listing.status == "PUBLISHED") "Approved\nYour listing is published." else "Approved\nManage publication below.")
                    if (listing.status == "DRAFT") SharedButton("Publish shared listing", !model.busy) { model.transition("publish") }
                    if (listing.status == "PAUSED") SharedButton("Resume shared listing", !model.busy) { model.transition("resume") }
                }
            }
        }
    }
    SharedLink("Back to listings", !model.busy) { model.go(SharedScreen.LISTINGS) }
}

@Composable private fun SharedApplications(model: SublessorViewModel) {
    SharedHeading("People interested in your home", "Review applications for your own listings.")
    SharedText("New ${model.applications.count { it.status == "SUBMITTED" }} · Under review ${model.applications.count { it.status == "UNDER_REVIEW" }} · Approved ${model.applications.count { it.status == "APPROVED" }}", true)
    Gap(12)
    var count by remember(model.applications) { mutableIntStateOf(20) }
    var hidden by remember { mutableStateOf(false) }
    TextButton({ hidden = !hidden }) { Text(if (hidden) "Show applications" else "Hidden applications") }
    model.applicationListingFilter?.let { id ->
        TextButton({ model.go(SharedScreen.APPLICATIONS) }, enabled = !model.busy) { Text("Show applications for all listings") }
    }
    val displayed = model.applications.filter { app -> (model.applicationListingFilter == null || app.listingId == model.applicationListingFilter) && (model.applicationNotes.firstOrNull { it.applicationId == app.id }?.hidden == true) == hidden }
    displayed.take(count).forEach { value ->
        ApplicationCard(value, model.listings.firstOrNull { it.id == value.listingId }?.title ?: "Shared home",
            model.applicationNotes.firstOrNull { it.applicationId == value.id }, model.busy,
            onView = { model.openApplication(value) }, onRestore = { model.hideApplication(value.id, false) },
            onChat = { model.openApplicationChat(value) })
    }
    if (model.applications.isEmpty()) SharedText("No applications yet.", true)
    if (count < displayed.size) SharedLink("Load more", true) { count += 20 }
}
@Composable private fun SharedApplication(model: SublessorViewModel) {
    val value = model.application ?: return
    var rejecting by remember(value.id) { mutableStateOf(false) }
    var reason by remember(value.id) { mutableStateOf("") }
    ApplicationFacts(value, model.listings.firstOrNull { it.id == value.listingId }?.title ?: "Shared home")
    SharedCard {
        if (value.status == "SUBMITTED") SharedButton("Mark under review", !model.busy) { model.decide("review") }
        if (value.status == "UNDER_REVIEW") {
            SharedButton("Approve application", !model.busy) { model.decide("approve") }
            SharedLink("Reject with reason", !model.busy) { rejecting = true }
            if (rejecting) {
                SharedInput("Reason sent to applicant", reason, !model.busy, lines = 3) { reason = it.take(500) }
                SharedButton("Confirm rejection", !model.busy && reason.isNotBlank()) { model.decide("reject", reason) }
            }
        }
    }
    val booking = model.bookings.firstOrNull { it.sourceApplicationId == value.id }
    OwnerMoveInCard(booking, model.busy, model.testDestination?.configured, { model.go(SharedScreen.PAYMENTS) },
        { booking?.let { model.confirmBookingMoveIn(it.id) } }, model::loadBookings)
}
@Composable private fun SharedInterests(model: SublessorViewModel) {
    SharedHeading("Roommate interests", "People interested in your shared spaces")
    var count by remember(model.interests) { mutableIntStateOf(20) }
    model.interests.take(count).forEach { value -> SharedCard {
        SharedText(model.listings.firstOrNull { it.id == value.sublessorListingId }?.title ?: "Shared space")
        SharedStatus(value.status)
        if (value.status in listOf("SUBMITTED", "UNDER_REVIEW")) {
            SharedLink("Accept interest", !model.busy) { model.interestAction(value, "accept") }
            SharedLink("Decline interest", !model.busy) { model.interestAction(value, "decline") }
        }
        if (value.status == "ACCEPTED") SharedLink("Open protected conversations", !model.busy) { model.go(SharedScreen.INBOX) }
    } }
    if (model.interests.isEmpty()) SharedText("No roommate interests yet.", true)
    if (count < model.interests.size) SharedLink("Load more", true) { count += 20 }
}

@Composable private fun SharedInbox(model: SublessorViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    ProtectedInboxHeader(query) { query = it }
    ArchiveTabs(model.archivedThreads.size, model.showArchived) { model.showArchived = it }
    val visible = (if (model.showArchived) model.archivedThreads else model.inboxThreads).filter { value ->
        query.isBlank() || listOfNotNull(value.counterpartyName, value.lastMessage, value.status)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    visible.forEach { value ->
        ProtectedConversationRow(
            title = value.counterpartyName ?: "Renter",
            detail = value.lastMessage ?: "Start a conversation",
            status = value.lastMessageAt?.let { kenyaTime(it, "d MMM yyyy") } ?: "",
            enabled = !model.busy, avatarUrl = value.counterpartyAvatarUrl,
            onArchive = if (model.showArchived) null else ({ model.archiveThread(value) }),
            onRestore = if (model.showArchived) ({ model.restoreThread(value) }) else null,
            onDelete = { model.deleteThread(value) }
        ) { model.openThread(value) }
    }
    if (visible.isEmpty()) SharedText(if (query.isNotBlank()) "No conversations match your search." else if (model.showArchived) "No archived conversations." else "No conversations yet.", true)
    if (model.threads?.last == false) SharedLink("Load more", !model.busy, model::nextThreads)
}
@Composable private fun SharedConversation(model: SublessorViewModel, profile: MyProfile) {
    val selectedThread = model.thread ?: return
    ProtectedConversation(
        title = selectedThread.counterpartyName ?: "Renter",
        roleLabel = "Renter",
        conversationType = "Roommate conversation",
        contextLabel = "Roommate enquiry",
        threadId = selectedThread.id,
        messages = model.messages?.items.orEmpty(),
        ownUserId = profile.id,
        connected = model.chatReady,
        awaitingEcho = model.awaitingEcho,
        threadOpen = selectedThread.status == "OPEN",
        draft = model.chatDraft,
        busy = model.busy,
        hasOlder = model.messages?.hasMore == true,
        onOlder = model::olderMessages,
        onReconnect = model::reconnect,
        onAcknowledge = model::acknowledgeMessages,
        onDraft = model::updateChat,
        onSend = model::sendMessage
    )
}
@Composable private fun SharedPayments(model: SublessorViewModel) {
    SharedHeading("Payments and payouts", "Your payment records")
    OwnerTestPayoutSetup(model.testDestination, model.busy, model::configureTestDestination)
    model.payments?.items?.forEach { value -> SharedCard {
        SharedText(value.paymentType.lowercase().replace('_', ' ')); SharedStatus(value.status)
        SharedText("${value.currency} ${BigDecimal.valueOf(value.amountMinor, 2).toPlainString()}")
        SharedText(kenyaTime(value.createdAt), true)
    } }
    if (model.payments?.items?.isEmpty() == true) SharedText("No payment records yet.", true)
    if (model.payments?.hasNext == true) SharedLink("Load more", !model.busy, model::nextPayments)
}

@Composable private fun SharedHeading(title: String, subtitle: String) {
    OwnerBanner(title, subtitle)
}
@Composable internal fun SharedCard(content: @Composable ColumnScope.() -> Unit) {
    StyledCard(null, content)
}
@Composable internal fun SharedText(text: String, muted: Boolean = false) { Text(text, color = if (muted) Muted else Ink, fontSize = 14.sp, lineHeight = 20.sp) }
@Composable internal fun SharedStatus(value: String) { StatusPill(value) }
@Composable internal fun SharedNote(text: String) { Surface(color = Sand, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
    Text(text, color = Brown, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(14.dp)) }; Gap(14) }
@Composable internal fun SharedButton(label: String, enabled: Boolean, action: () -> Unit) { Button(action, enabled = enabled,
    shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) } }
@Composable internal fun SharedLink(label: String, enabled: Boolean, action: () -> Unit) { TextButton(action, enabled = enabled,
    contentPadding = PaddingValues(0.dp)) { Text(label, color = Brown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) } }
@Composable private fun SharedInput(label: String, value: String, enabled: Boolean, number: Boolean = false, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label, fontSize = 12.sp) }, enabled = enabled, singleLine = lines == 1, minLines = lines,
        shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}
private fun sharedKes(value: Long) = "KSh " + NumberFormat.getIntegerInstance(Locale.UK).format(value)

@Composable private fun SharedPreferenceEditor(model: SublessorViewModel) {
    SharedHeading("What makes a good housemate?", "Your preference questionnaire · Renters use the same questions")
    PreferenceQuestion.entries.forEach { question ->
        var expanded by remember { mutableStateOf(false) }
        SharedCard {
            SharedText(question.title, true)
            Box {
                TextButton({ expanded = true }, enabled = !model.busy, contentPadding = PaddingValues(0.dp)) {
                    Text(question.options[model.preferenceForm.answers[question]] ?: "Select preference", color = Ink)
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(expanded, { expanded = false }) {
                    question.options.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                        model.preferenceForm = model.preferenceForm.copy(answers = model.preferenceForm.answers + (question to key)); expanded = false
                    }) }
                }
            }
        }
    }
    SharedInput("Budget from · KSh / month", model.preferenceForm.minBudget, !model.busy, number = true) { model.preferenceForm = model.preferenceForm.copy(minBudget = it) }
    SharedInput("Budget to · KSh / month", model.preferenceForm.maxBudget, !model.busy, number = true) { model.preferenceForm = model.preferenceForm.copy(maxBudget = it) }
    SharedCard { Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(model.preferenceForm.optIn, { model.preferenceForm = model.preferenceForm.copy(optIn = it) }, enabled = !model.busy)
        Column { SharedText("Opt in to roommate discovery"); SharedText("You can turn this off.", true) }
    } }
    SharedButton("Save preferences", !model.busy, model::savePreferences)
}

@Composable private fun SharedPreferenceSummary(model: SublessorViewModel) {
    val saved = model.preferences ?: return
    SharedHeading("Your roommate preferences", "${if (saved.discoveryOptIn) "You're opted in" else "Discovery is off"}")
    SharedCard { SharedText("What you value", true); PreferenceLines(saved) }
    SharedCard {
        SharedText("How preferences work", true)
        SharedText("Compare shared household preferences and monthly budgets. A conversation helps you decide whether sharing a home feels right.")
    }
    SharedButton("Discover renters", !model.busy && saved.discoveryOptIn) { model.go(SharedScreen.DISCOVERY) }
    SharedLink("Edit preferences", !model.busy) { model.go(SharedScreen.PREFERENCES) }
}

@Composable private fun PreferenceLines(saved: SharedPreferences) {
    val form = RoommateForm.from(saved)
    PreferenceQuestion.entries.forEach { q -> SharedText(q.options[form.answers[q]] ?: "Not specified") }
    SharedText("Budget · ${sharedKes(saved.budgetMinKes)}–${sharedKes(saved.budgetMaxKes)} / month", true)
}

@Composable private fun SharedDiscovery(model: SublessorViewModel) {
    SharedHeading("People you may feel at home with", "Opted-in renters · Shared preferences, not AI")
    model.discovery?.items?.forEach { person -> SharedCard {
        SharedText(person.displayName); SharedStatus("Verified Renter")
        person.preferences.areaLabel?.let { SharedText(it, true) }
        SharedText("Budget · ${sharedKes(person.preferences.budgetMinKes)}–${sharedKes(person.preferences.budgetMaxKes)}", true)
        val answers = RoommateForm.from(person.preferences).answers
        SharedText(listOf(PreferenceQuestion.QUIET, PreferenceQuestion.SLEEP, PreferenceQuestion.PETS).mapNotNull { it.options[answers[it]] }.joinToString(" · "), true)
        SharedButton("View roommate profile", !model.busy) { model.openRenter(person) }
    } }
    if (model.discovery?.items?.isEmpty() == true) SharedText("No opted-in renters available yet.", true)
    if (model.discovery?.hasNext == true) SharedLink("Load more", !model.busy, model::nextDiscovery)
}

@Composable private fun SharedRenterProfile(model: SublessorViewModel) {
    val person = model.renter ?: return
    SharedHeading("Meet ${person.displayName.substringBefore(' ')}", person.preferences.areaLabel ?: "Opted-in roommate profile")
    SharedStatus("Verified Renter"); Gap(12)
    SharedCard {
        SharedText("A little about me", true)
        person.preferences.about?.let { SharedText(it) }
        person.preferences.employment?.let { SharedText(it) }
        person.preferences.moveInDate?.let { SharedText("Move-in · $it") }
        SharedText("Budget · ${sharedKes(person.preferences.budgetMinKes)}–${sharedKes(person.preferences.budgetMaxKes)}")
    }
    SharedCard {
        SharedText("Deterministic preference comparison", true)
        val mine = model.preferences?.let { RoommateForm.from(it).answers } ?: emptyMap()
        val theirs = RoommateForm.from(person.preferences).answers
        val same = PreferenceQuestion.entries.filter { mine[it] == theirs[it] }
        SharedText(same.joinToString(" · ") { it.title }.ifBlank { "No shared answers" })
        SharedText("Different", true)
        SharedText(PreferenceQuestion.entries.filter { mine[it] != theirs[it] }.joinToString(" · ") { it.title }.ifBlank { "No different answers" })
        SharedText("A percentage is not shown until the matching weights and rounding rule are agreed.", true)
    }
    // The current backend opens chats after a renter expresses interest and the host accepts.
    SharedButton("Open protected conversations", !model.busy) { model.go(SharedScreen.INBOX) }
}

/** The same three-photo identity checklist used by every public role. */
@Composable private fun SublessorVerification(model: SublessorViewModel) {
    val resolver = LocalContext.current.contentResolver
    var correcting by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var roleProof by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<SharedEvidence?>(null) }
    var sourceFor by remember { mutableStateOf<SharedEvidence?>(null) }
    var camera by remember { mutableStateOf<SharedEvidence?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = choosing; choosing = null
        if (uri != null && type != null) model.uploadEvidence(type, resolver, uri)
    }
    val status = model.verification?.status
    val terminal = status in listOf("PENDING", "VERIFIED") || status == "REJECTED" && !correcting
    val editable = status in listOf("NOT_STARTED", "REJECTED")
    fun capture(type: SharedEvidence) {
        val stored = model.documents.firstOrNull { it.documentType == type.name }
        when {
            model.pendingEvidenceType == type.name -> model.retryEvidence()
            stored?.status == "UPLOADING" -> model.confirmStoredEvidence(stored.documentId)
            type == SharedEvidence.SELFIE -> camera = type
            else -> sourceFor = type
        }
    }
    val back = { if (!model.busy) { if (camera != null) camera = null else if (roleProof) roleProof = false else if (started && !terminal) started = false else model.go(SharedScreen.HOME) } }
    BackHandler(onBack = back)
    LaunchedEffect(status) { if (status in listOf("PENDING", "VERIFIED")) correcting = false }
    sourceFor?.let { type -> IdSourceDialog(type.title, { sourceFor = null },
        { sourceFor = null; camera = type },
        { sourceFor = null; choosing = type; picker.launch(arrayOf("image/jpeg", "image/png")) }) }
    VerificationPageBody(back, { model.go(SharedScreen.HOME) }, !model.busy, if (!started && !terminal) "Verification" else "Capture documents") {
        model.notice?.let { SharedNote(it); Gap(8) }
        when {
            terminal -> VerificationOutcomeScreen(
                role = "Sublessor", status = status!!, rejectionReason = model.verification?.rejectionReason,
                busy = model.busy, approvedAction = "Continue to listings",
                onApproved = { model.go(SharedScreen.LISTINGS) }, onCorrect = { correcting = true },
                onClose = { model.go(SharedScreen.HOME) }, onRefresh = model::refreshVerification)
            !started && editable -> VerificationIntro("Sublessor", "Confirms your identity and tenancy for a shared space.", "Rental agreement (PDF)") { started = true }
            camera != null -> {
                val type = camera!!
                Heading(if (type == SharedEvidence.SELFIE) "Capture selfie" else type.title); Gap(20)
                key(type) { EvidenceCamera(type == SharedEvidence.SELFIE, editable && !model.busy,
                    { bitmap -> camera = null; model.captureEvidence(type, bitmap) }, model::showNotice) }
                SharedLink("Cancel camera", !model.busy) { camera = null }
            }
            roleProof -> VerificationRoleDocumentStep(
                title = "Your rental agreement", subtitle = "Sublessor · Confirm your shared-space tenancy",
                label = "Rental agreement", name = model.evidenceNames[SharedEvidence.RENTAL_AGREEMENT],
                ready = sharedEvidenceReady(SharedEvidence.RENTAL_AGREEMENT, model.documents), busy = model.busy,
                guidance = "Include the tenant name, property and relevant signed pages.",
                privacy = "Only authorised verification staff can view this document.",
                choose = { choosing = SharedEvidence.RENTAL_AGREEMENT; picker.launch(arrayOf("application/pdf")) },
                next = model::submitVerification)
            else -> {
                val types = listOf(SharedEvidence.ID_FRONT, SharedEvidence.ID_BACK, SharedEvidence.SELFIE)
                IdentityCaptureChecklist(
                    role = "Sublessor", busy = model.busy, editable = editable,
                    stage = model.stage, progress = model.evidenceProgress,
                    items = types.map { type -> IdentityCaptureItem(
                        type.name,
                        when (type) { SharedEvidence.ID_FRONT -> "National ID (front)"; SharedEvidence.ID_BACK -> "National ID (back)"; else -> "Selfie" },
                        when (type) { SharedEvidence.ID_FRONT -> "Photo side, fit the card inside the frame"; SharedEvidence.ID_BACK -> "Turn the card over, all four corners visible"; else -> "Remove spectacles, face the camera" },
                        model.pendingEvidenceType != type.name && sharedEvidenceReady(type, model.documents),
                        model.busy && model.pendingEvidenceType == type.name
                    ) },
                    onCapture = { key -> capture(SharedEvidence.valueOf(key)) },
                    onContinue = { roleProof = true }
                )
            }
        }
    }
}
