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
    val back = { model.go(when (model.screen) {
        SharedScreen.MEDIA -> SharedScreen.DETAILS
        SharedScreen.LOCATION -> SharedScreen.MEDIA
        SharedScreen.PREVIEW -> SharedScreen.LOCATION
        SharedScreen.APPLICATION -> SharedScreen.APPLICATIONS
        SharedScreen.CHAT -> SharedScreen.INBOX
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
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (!editor) NavigationBar(containerColor = Color.White) {
            listOf(Triple(SharedScreen.HOME, "Home", Icons.Outlined.Home), Triple(SharedScreen.LISTINGS, "Listings", Icons.Outlined.Apartment),
                Triple(SharedScreen.APPLICATIONS, "Applications", Icons.Outlined.Description), Triple(SharedScreen.INBOX, "Messages", Icons.Outlined.ChatBubbleOutline),
                Triple(SharedScreen.PROFILE, "Profile", Icons.Outlined.PersonOutline)).forEach { (target, label, icon) ->
                NavigationBarItem(selected = model.screen == target, onClick = { model.go(target) }, enabled = !model.busy,
                    icon = { Icon(icon, null, Modifier.size(21.dp)) }, label = { Text(label, fontSize = 10.sp) })
            }
        }
    }) { insets ->
        val scroll = rememberScrollState()
        LaunchedEffect(model.screen) { scroll.scrollTo(0) }
        Column(Modifier.fillMaxSize().padding(insets).statusBarsPadding().imePadding().verticalScroll(scroll).padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back, enabled = !model.busy, modifier = Modifier.size(28.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Brown, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(6.dp)); Text("makao hub", color = Brown, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                SharedScreen.PROFILE -> {
                    SharedHeading("Your profile", "Sublessor")
                    SharedCard { SharedText(profile.fullName); SharedText(profile.email, true); SharedStatus(model.verification?.status ?: "NOT_STARTED")
                        SharedLink("Verification", !model.busy) { model.go(SharedScreen.VERIFICATION) } }
                    SharedCard { SharedLink("Payment history", !model.busy) { model.go(SharedScreen.PAYMENTS) } }
                    TextButton({ model.clear(); auth.logout() }, enabled = !model.busy) { Text("Sign out") }
                }
                SharedScreen.VERIFICATION -> Unit
            }
        }
    }
}

@Composable private fun SharedHome(model: SublessorViewModel, profile: MyProfile) {
    SharedHeading("Good company starts here", "Karibu, ${profile.fullName.substringBefore(' ')}. A clear view of your shared home.")
    SharedStatus(if (model.verification?.status == "VERIFIED") "Verified Sublessor" else model.verification?.status ?: "NOT_STARTED"); Gap(16)
    if (model.verification?.status != "VERIFIED") {
        SharedNote("Complete your ID, live photo and rental agreement verification.")
        SharedButton("Verify as Sublessor", !model.busy) { model.go(SharedScreen.VERIFICATION) }; Gap(16)
    }
    SharedCard {
        SharedText("Your shared spaces", true)
        if (model.listings.isEmpty()) SharedText("No shared spaces yet.", true)
        model.listings.take(5).forEach { listing -> SharedLink(listing.title, !model.busy) { model.edit(listing) }; SharedText("${listing.status.lowercase().replaceFirstChar { it.uppercase() }} · ${sharedKes(listing.monthlyRentKes)} / month", true) }
    }
    SharedButton("Create shared listing", !model.busy && model.verification?.status == "VERIFIED", model::newListing); Gap(18)
    SharedCard {
        SharedText("People and progress", true)
        SharedLink("Roommate interests · View", !model.busy) { model.go(SharedScreen.INTERESTS) }
        SharedText("${model.interests.size} interests", true)
        SharedLink("Roommate preferences · Discover", !model.busy) { model.go(SharedScreen.PREFERENCE_SUMMARY) }
        SharedLink("Applications · Review", !model.busy) { model.go(SharedScreen.APPLICATIONS) }
        SharedText("${model.applications.count { it.status in listOf("SUBMITTED", "UNDER_REVIEW") }} waiting for review", true)
        SharedLink("Messages · Open", !model.busy) { model.go(SharedScreen.INBOX) }
        SharedLink("Test payments · View", !model.busy) { model.go(SharedScreen.PAYMENTS) }
    }
    SharedNote("Keep your verification current\nYour rental agreement stays private.")
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
    SharedInput("Bathrooms", model.form.bathrooms, !model.busy, number = true) { model.form = model.form.copy(bathrooms = it) }
    SharedInput("Available from · YYYY-MM-DD", model.form.available, !model.busy) { model.form = model.form.copy(available = it) }
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(model.form.furnished, { model.form = model.form.copy(furnished = it) }, enabled = !model.busy); SharedText("Furnished") }
    listOf("WATER" to "Water", "WIFI" to "Wi-Fi", "SECURITY" to "Security", "PARKING" to "Parking", "PETS" to "Pets").chunked(2).forEach { group ->
        Row { group.forEach { (key, label) -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(key in model.form.amenities, { checked -> model.form = model.form.copy(amenities = if (checked) model.form.amenities + key else model.form.amenities - key) }, enabled = !model.busy)
            SharedText(label)
        } } }
    }
    SharedNote("Shared space, not a whole unit\nChoose a spare room or servant quarter when seeking a roommate.")
    SharedButton("Save and continue", !model.busy) { model.saveDraft(true) }
    TextButton({ model.saveDraft(false) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
}

@Composable private fun SharedMedia(model: SublessorViewModel) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) model.uploadMedia(resolver, it) }
    var deleting by remember { mutableStateOf<OwnerMedia?>(null) }
    SharedHeading("Show the shared space", "Step 2 of 4 · Bedroom and common areas")
    LocalPhoto(model.media.firstNotNullOfOrNull { model.photos[it.id] })
    SharedText("${model.media.count { it.mediaType == "IMAGE" }} photos · ${model.media.count { it.mediaType == "VIDEO" }} video", true); Gap(10)
    SharedLink("Add photos", !model.busy && model.pendingMedia == null) { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }
    if (model.media.none { it.mediaType == "VIDEO" }) SharedLink("Add video walkthrough", !model.busy && model.pendingMedia == null) { picker.launch(arrayOf("video/mp4")) }
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
    SharedHeading("Set your private home pin", "Step 3 of 4 · Your household address stays private")
    SharedInput("Public approximate area", area, !model.busy) { area = it }
    PropertyMapPicker(pin, !model.busy, showDeveloperDetails = false) { pin = it }
    SharedNote("Not shown in roommate discovery\nOnly the approximate area is public. Exact access is controlled by the server.")
    SharedButton("Save exact pin", !model.busy && pin != null) { pin?.let { model.savePin(area, it.latitude, it.longitude) } }
    TextButton({ model.go(SharedScreen.LISTINGS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Save draft") }
}

@Composable private fun SharedPreview(model: SublessorViewModel) {
    val listing = model.selected ?: return
    SharedHeading("Ready to welcome a roommate", "Step 4 of 4 · Preview and publish")
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
    SharedButton("Publish shared listing", !model.busy && model.verification?.status == "VERIFIED") { model.transition("publish") }
    TextButton({ model.go(SharedScreen.DETAILS) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Edit shared listing") }
}

@Composable private fun SharedListings(model: SublessorViewModel) {
    SharedHeading("Your shared spaces", "Manage your rooms and availability")
    SharedButton("Create shared listing", !model.busy && model.verification?.status == "VERIFIED", model::newListing); Gap(16)
    var count by remember(model.listings) { mutableIntStateOf(20) }
    model.listings.take(count).forEach { value -> SharedCard {
        SharedText(value.title); SharedStatus(value.status); SharedText("${sharedKes(value.monthlyRentKes)} / month", true)
        SharedLink(if (value.status == "DRAFT") "Continue draft" else "Manage listing", !model.busy) { model.edit(value) }
    } }
    if (model.listings.isEmpty()) SharedText("No shared listings yet.", true)
    if (count < model.listings.size) SharedLink("Load more", true) { count += 20 }
}

@Composable private fun SharedStates(model: SublessorViewModel) {
    val listing = model.selected ?: return
    SharedHeading("Shared-listing alternatives", "Manage, publish and media states")
    SharedCard {
        SharedText(listing.title); SharedStatus(listing.status)
        when (listing.status) {
            "DRAFT" -> { SharedLink("Edit shared listing", !model.busy) { model.go(SharedScreen.DETAILS) }; SharedLink("Manage media", !model.busy, model::loadMedia)
                SharedButton("Publish shared listing", !model.busy && model.verification?.status == "VERIFIED") { model.transition("publish") } }
            "PUBLISHED" -> SharedButton("Pause shared listing", !model.busy) { model.transition("pause") }
            "PAUSED" -> SharedButton("Resume shared listing", !model.busy && model.verification?.status == "VERIFIED") { model.transition("resume") }
        }
    }
}

@Composable private fun SharedApplications(model: SublessorViewModel) {
    SharedHeading("Shared-home applications", "People interested in your shared home")
    var count by remember(model.applications) { mutableIntStateOf(20) }
    model.applications.take(count).forEach { value -> SharedCard {
        SharedText(model.listings.firstOrNull { it.id == value.listingId }?.title ?: "Shared-home application")
        SharedStatus(value.status); SharedLink("Review application", !model.busy) { model.openApplication(value) }
    } }
    if (model.applications.isEmpty()) SharedText("No applications yet.", true)
    if (count < model.applications.size) SharedLink("Load more", true) { count += 20 }
}
@Composable private fun SharedApplication(model: SublessorViewModel) {
    val value = model.application ?: return
    var rejecting by remember(value.id) { mutableStateOf(false) }
    var reason by remember(value.id) { mutableStateOf("") }
    SharedHeading("Review application", model.listings.firstOrNull { it.id == value.listingId }?.title ?: "Shared home")
    SharedCard {
        SharedStatus(value.status)
        SharedText("Submitted · ${value.createdAt.substringBefore('T')}", true)
        if (value.status == "SUBMITTED") SharedButton("Mark under review", !model.busy) { model.decide("review") }
        if (value.status == "UNDER_REVIEW") {
            SharedButton("Approve application", !model.busy) { model.decide("approve") }
            SharedLink("Reject with reason", !model.busy) { rejecting = true }
            if (rejecting) {
                SharedInput("Reason sent to applicant", reason, !model.busy, lines = 3) { reason = it.take(500) }
                SharedButton("Confirm rejection", !model.busy && reason.isNotBlank()) { model.decide("reject", reason) }
            }
        }
        value.rejectionReason?.let { SharedText(it) }
    }
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
    SharedHeading("Roommate conversations", "Your shared-home messages")
    model.threads?.content?.forEach { value -> SharedCard { SharedText("Roommate conversation"); SharedStatus(value.status)
        SharedLink("Open conversation", !model.busy) { model.openThread(value) } } }
    if (model.threads?.content?.isEmpty() == true) SharedText("No conversations yet.", true)
    if (model.threads?.last == false) SharedLink("Load more", !model.busy, model::nextThreads)
}
@Composable private fun SharedConversation(model: SublessorViewModel, profile: MyProfile) {
    SharedHeading("Roommate conversation", if (model.chatReady) "Connected" else "Disconnected")
    if (model.messages?.hasMore == true) SharedLink("Load older messages", !model.busy, model::olderMessages)
    model.messages?.items?.sortedWith(compareBy<OwnerMessage> { it.createdAt }.thenBy { it.id })?.forEach { message -> SharedCard {
        SharedText(if (message.senderId == profile.id) "You" else "Renter", true); SharedText(message.content)
        if (message.contactMasked) SharedStatus("Flagged · Contact details masked")
    } }
    if (model.thread?.status == "OPEN") {
        SharedInput("Message", model.chatDraft, model.chatReady && !model.awaitingEcho, lines = 3, onChange = model::updateChat)
        SharedButton(if (model.awaitingEcho) "Sending…" else "Send message", model.chatReady && !model.awaitingEcho && model.chatDraft.isNotBlank(), model::sendMessage)
        if (!model.chatReady) SharedLink("Reconnect", !model.busy, model::reconnect)
    }
}
@Composable private fun SharedPayments(model: SublessorViewModel) {
    SharedHeading("Shared-space test payments", "Your payment records")
    SharedNote("TEST / MOCK PAYMENT\nNo real money moves.")
    model.payments?.items?.forEach { value -> SharedCard {
        SharedText(value.paymentType.lowercase().replace('_', ' ')); SharedStatus(value.status)
        SharedText("${value.currency} ${BigDecimal.valueOf(value.amountMinor, 2).toPlainString()}")
        SharedText(value.createdAt.substringBefore('T'), true)
    } }
    if (model.payments?.items?.isEmpty() == true) SharedText("No payment records yet.", true)
    if (model.payments?.hasNext == true) SharedLink("Load more", !model.busy, model::nextPayments)
}

@Composable private fun SharedHeading(title: String, subtitle: String) {
    Text(title, color = Brown, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    Gap(9); Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 19.sp); Gap(20)
}
@Composable internal fun SharedCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }; Gap(14)
}
@Composable internal fun SharedText(text: String, muted: Boolean = false) { Text(text, color = if (muted) Muted else Ink, fontSize = 14.sp, lineHeight = 20.sp) }
@Composable internal fun SharedStatus(value: String) { Text(value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
    color = if (value in listOf("VERIFIED", "PUBLISHED", "APPROVED", "Verified Sublessor")) Color(0xFF417653) else Muted, fontSize = 12.sp) }
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
    SharedNote("Shared preferences\nUse household preferences to start a conversation and discuss expectations.")
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
    SharedNote("You control discovery\nOnly opted-in renters appear. Private identity documents, exact addresses and contact details are excluded.")
    SharedButton("Discover renters", !model.busy && saved.discoveryOptIn) { model.go(SharedScreen.DISCOVERY) }
    SharedLink("Edit preferences", !model.busy) { model.go(SharedScreen.PREFERENCES) }
}

@Composable private fun PreferenceLines(saved: SharedPreferences) {
    val form = RoommateForm.from(saved)
    PreferenceQuestion.entries.forEach { q -> SharedText(q.options[form.answers[q]] ?: "Not specified") }
    SharedText("Budget · ${sharedKes(saved.budgetMinKes)}–${sharedKes(saved.budgetMaxKes)} / month", true)
}

@Composable private fun SharedDiscovery(model: SublessorViewModel) {
    SharedHeading("People you may feel at home with", "Opted-in renters · Shared preferences")
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
        SharedText("Shared preferences", true)
        val mine = model.preferences?.let { RoommateForm.from(it).answers } ?: emptyMap()
        val theirs = RoommateForm.from(person.preferences).answers
        val same = PreferenceQuestion.entries.filter { mine[it] == theirs[it] }
        SharedText(same.joinToString(" · ") { it.title }.ifBlank { "No shared answers" })
        SharedText("Different", true)
        SharedText(PreferenceQuestion.entries.filter { mine[it] != theirs[it] }.joinToString(" · ") { it.title }.ifBlank { "No different answers" })
    }
    SharedNote("A public profile, not an ID file\nPrivate identity evidence is never shared.")
    // The current backend opens chats after a renter expresses interest and the host accepts.
    SharedButton("Open protected conversations", !model.busy) { model.go(SharedScreen.INBOX) }
}

/** ID front/back, a camera selfie and a private agreement PDF; READY never means identity approval. */
@Composable private fun SublessorVerification(model: SublessorViewModel) {
    val resolver = LocalContext.current.contentResolver
    var step by rememberSaveable { mutableIntStateOf(2) }
    var correcting by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<SharedEvidence?>(null) }
    var camera by remember { mutableStateOf<SharedEvidence?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val type = choosing; choosing = null
        if (uri != null && type != null) model.uploadEvidence(type, resolver, uri)
    }
    fun choose(type: SharedEvidence) { choosing = type; picker.launch(if (type == SharedEvidence.RENTAL_AGREEMENT) arrayOf("application/pdf") else arrayOf("image/jpeg", "image/png")) }
    val status = model.verification?.status
    val terminal = status in listOf("PENDING", "VERIFIED") || status == "REJECTED" && !correcting
    val back = { if (!model.busy) { if (camera != null) camera = null else if (!terminal && step > 2) step-- else model.go(SharedScreen.HOME) } }
    BackHandler(onBack = back)
    LaunchedEffect(status) { if (status in listOf("PENDING", "VERIFIED")) correcting = false }
    PageBody(back, !model.busy) {
        model.notice?.let { SharedNote(it) }
        if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(12) }
        when {
            terminal -> {
                Heading(when (status) { "VERIFIED" -> "Verification Approved!"; "REJECTED" -> "Verification Rejected"; else -> "Documents Under Review" }); Gap(20)
                SharedCard { SharedText(when (status) {
                    "VERIFIED" -> "Your Sublessor role is verified."
                    "REJECTED" -> model.verification?.rejectionReason ?: "Please contact support for the required corrections."
                    else -> "Your ID, live photo and rental agreement are under review."
                }) }
                when (status) {
                    "VERIFIED" -> SharedButton("Continue to your shared home", !model.busy) { model.go(SharedScreen.HOME) }
                    "REJECTED" -> SharedButton("Re-submit documents", !model.busy) { correcting = true; step = 2 }
                    else -> SharedButton("Close & Back to Main", !model.busy) { model.go(SharedScreen.HOME) }
                }
                SharedLink("Refresh status", !model.busy, model::refreshVerification)
            }
            else -> {
                SharedText(if (step <= 4) "Step $step of 4" else "Review submission", true); Gap(10)
                Heading(when (step) { 2 -> "Upload National ID"; 3 -> "Capture Live Photo"; 4 -> "Rental Agreement"; else -> "Review Submission" }); Gap(20)
                if (camera != null) {
                    val type = camera!!
                    EvidenceCamera(type == SharedEvidence.SELFIE, !model.busy, { bitmap -> camera = null; model.captureEvidence(type, bitmap) }, model::showNotice)
                } else when (step) {
                    2 -> listOf(SharedEvidence.ID_FRONT, SharedEvidence.ID_BACK).forEach { type ->
                        SharedCard {
                            SharedText(type.title); SharedText(if (sharedEvidenceReady(type, model.documents)) "Uploaded" else "Required", true)
                            SharedLink("Take ID Photo", !model.busy) { camera = type }
                            SharedLink("Choose from gallery", !model.busy) { choose(type) }
                        }
                    }
                    3 -> SharedCard {
                        SharedText("Use good lighting and keep your face visible.")
                        SharedText(if (sharedEvidenceReady(SharedEvidence.SELFIE, model.documents)) "Live photo uploaded" else "Live photo required", true)
                        SharedLink("Capture Live Photo", !model.busy) { camera = SharedEvidence.SELFIE }
                    }
                    4 -> SharedCard {
                        SharedText("Upload your rental agreement.")
                        SharedText("PDF only · Up to 10 MB", true)
                        SharedLink("Choose agreement PDF", !model.busy) { choose(SharedEvidence.RENTAL_AGREEMENT) }
                    }
                    else -> SharedEvidence.entries.forEach { type -> SharedCard { SharedText(type.title); SharedText(if (sharedEvidenceReady(type, model.documents)) "Uploaded" else "Required", true) } }
                }
                model.documents.filter { it.status == "UPLOADING" && model.pendingEvidence == null }.forEach { value ->
                    SharedLink("Confirm ${value.documentType.lowercase().replace('_', ' ')}", !model.busy) { model.confirmStoredEvidence(value.documentId) }
                }
                if (model.pendingEvidence != null) SharedButton("Retry current document", !model.busy, model::retryEvidence)
                else if (camera == null) SharedButton(if (step == 5) "Submit Verification" else "Continue", !model.busy && when (step) {
                    2 -> sharedEvidenceReady(SharedEvidence.ID_FRONT, model.documents) && sharedEvidenceReady(SharedEvidence.ID_BACK, model.documents)
                    3 -> sharedEvidenceReady(SharedEvidence.SELFIE, model.documents)
                    4 -> sharedEvidenceReady(SharedEvidence.RENTAL_AGREEMENT, model.documents)
                    else -> sharedVerificationReady(model.documents)
                }) { if (step == 5) model.submitVerification() else step++ }
            }
        }
    }
}
