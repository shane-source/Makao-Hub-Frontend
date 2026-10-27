package com.makaohub.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import retrofit2.HttpException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class SharedScreen { HOME, VERIFICATION, LISTINGS, DETAILS, MEDIA, LOCATION, PREVIEW, STATES, INTERESTS, PREFERENCES, PREFERENCE_SUMMARY, DISCOVERY, RENTER_PROFILE, APPLICATIONS, APPLICATION, INBOX, CHAT, PAYMENTS, PROFILE }

data class SharedForm(val title: String = "", val description: String = "", val spaceType: String = "SPARE_ROOM",
    val rent: String = "", val bathrooms: String = "1", val furnished: Boolean = false, val available: String = "",
    val deposit: String = "", val currentHousehold: String = "", val occupants: String = "", val peoplePerRoom: String = "",
    val lifestyle: String = "", val amenities: Set<String> = emptySet()) {
    fun error(): String? = when {
        title.trim().isEmpty() || title.trim().length > 150 -> "Enter a listing title of up to 150 characters."
        description.trim().isEmpty() || description.trim().length > 3000 -> "Describe the shared home in up to 3,000 characters."
        spaceType !in listOf("SPARE_ROOM", "SERVANT_QUARTER") -> "Choose spare room or servant quarter."
        rent.toLongOrNull()?.let { it > 0 } != true -> "Enter monthly rent in whole KSh."
        bathrooms.toIntOrNull() !in 1..20 -> "Enter a bathroom count from 1 to 20."
        available.isNotBlank() && runCatching { LocalDate.parse(available) }.isFailure -> "Use YYYY-MM-DD for the available date."
        deposit.isNotBlank() && deposit.toLongOrNull()?.let { it >= 0 } != true -> "Enter a non-negative deposit in whole KSh."
        currentHousehold.length > 600 || lifestyle.length > 600 -> "Keep household descriptions within 600 characters."
        occupants.isNotBlank() && occupants.toIntOrNull() !in 1..20 -> "Enter 1 to 20 household occupants."
        peoplePerRoom.isNotBlank() && peoplePerRoom.toIntOrNull() !in 1..10 -> "Enter 1 to 10 people per room."
        else -> null
    }
    fun request() = SharedListingRequest(title.trim(), description.trim(), spaceType, rent.toLong(), bathrooms.toInt(), furnished, available.ifBlank { null })
    fun household() = SharedHousehold(deposit.toLongOrNull(), currentHousehold.trim().ifBlank { null }, occupants.toIntOrNull(), peoplePerRoom.toIntOrNull(), lifestyle.trim().ifBlank { null }, amenities)
    fun withHousehold(value: SharedHousehold) = copy(deposit = value.depositKes?.toString() ?: "", currentHousehold = value.currentHousehold ?: "",
        occupants = value.totalOccupants?.toString() ?: "", peoplePerRoom = value.peoplePerRoom?.toString() ?: "", lifestyle = value.lifestyle ?: "", amenities = value.amenities)
    companion object { fun from(value: SharedListing) = SharedForm(value.title, value.description, value.spaceType,
        value.monthlyRentKes.toString(), value.bathroomCount.toString(), value.furnished, value.availableFrom ?: "") }
}

enum class PreferenceQuestion(val title: String, val options: Map<String, String>) {
    QUIET("Quiet / social", linkedMapOf("QUIET" to "Mostly quiet", "SOCIAL" to "Social", "BALANCED" to "A balance of both")),
    SLEEP("Sleep schedule", linkedMapOf("EARLY" to "Early riser", "NIGHT" to "Night owl", "FLEXIBLE" to "Flexible")),
    CLEAN("Cleanliness", linkedMapOf("DAILY" to "Daily shared-space tidy-up", "WEEKLY" to "Weekly tidy-up")),
    COOK("Cooking", linkedMapOf("REGULAR" to "Regular cooking", "OCCASIONAL" to "Occasional cooking", "NONE" to "No cooking")),
    GUESTS("Guests", linkedMapOf("ASK_FIRST" to "Ask first", "OCCASIONAL" to "Occasional guests", "FREQUENT" to "Frequent guests")),
    SMOKE("Smoking", linkedMapOf("NO_INDOOR" to "No indoor smoking", "OUTSIDE_ONLY" to "Outside only", "SMOKER" to "Smoking household")),
    PETS("Pets", linkedMapOf("NONE" to "No pets", "PETS_OK" to "Pets welcome", "HAS_PETS" to "Pets in the home"))
}
data class RoommateForm(val answers: Map<PreferenceQuestion,String> = emptyMap(), val minBudget: String = "", val maxBudget: String = "", val optIn: Boolean = false) {
    fun error(): String? = when {
        PreferenceQuestion.entries.any { answers[it] !in it.options.keys } -> "Answer each preference question."
        minBudget.toLongOrNull()?.let { it > 0 } != true || maxBudget.toLongOrNull()?.let { it >= (minBudget.toLongOrNull() ?: Long.MAX_VALUE) } != true -> "Enter a valid monthly budget range."
        else -> null
    }
    fun request(previous: SharedPreferences? = null) = SharedPreferences(answers.getValue(PreferenceQuestion.QUIET),answers.getValue(PreferenceQuestion.SLEEP),answers.getValue(PreferenceQuestion.CLEAN),
        answers.getValue(PreferenceQuestion.COOK),answers.getValue(PreferenceQuestion.GUESTS),answers.getValue(PreferenceQuestion.SMOKE),answers.getValue(PreferenceQuestion.PETS),minBudget.toLong(),maxBudget.toLong(),optIn,
        previous?.areaLabel,previous?.about,previous?.employment,previous?.moveInDate)
    companion object { fun from(p: SharedPreferences) = RoommateForm(mapOf(PreferenceQuestion.QUIET to p.quietSocial,PreferenceQuestion.SLEEP to p.sleepSchedule,
        PreferenceQuestion.CLEAN to p.cleanliness,PreferenceQuestion.COOK to p.cooking,PreferenceQuestion.GUESTS to p.guests,PreferenceQuestion.SMOKE to p.smoking,PreferenceQuestion.PETS to p.pets),p.budgetMinKes.toString(),p.budgetMaxKes.toString(),p.discoveryOptIn) }
}

/** Separate Sublessor workspace; it never inherits a Landlord's verification or data. */
class SublessorViewModel(private val repository: SublessorRepository, private val expired: () -> Unit) : ViewModel() {
    var screen by mutableStateOf(SharedScreen.HOME); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var verification by mutableStateOf<OwnerVerification?>(null); private set
    var documents by mutableStateOf<List<OwnerVerificationDocument>>(emptyList()); private set
    var listings by mutableStateOf<List<SharedListing>>(emptyList()); private set
    var selected by mutableStateOf<SharedListing?>(null); private set
    var listingReview by mutableStateOf<OwnerListingReview?>(null); private set
    var form by mutableStateOf(SharedForm())
    var media by mutableStateOf<List<OwnerMedia>>(emptyList()); private set
    var location by mutableStateOf<OwnerLocation?>(null); private set
    var household by mutableStateOf<SharedHousehold?>(null); private set
    var preferences by mutableStateOf<SharedPreferences?>(null); private set
    var preferenceForm by mutableStateOf(RoommateForm())
    var discovery by mutableStateOf<SharedDiscoveryPage?>(null); private set
    var renter by mutableStateOf<SharedPublicProfile?>(null); private set
    var applications by mutableStateOf<List<OwnerApplication>>(emptyList()); private set
    var application by mutableStateOf<OwnerApplication?>(null); private set
    var interests by mutableStateOf<List<SharedInterest>>(emptyList()); private set
    var threads by mutableStateOf<SharedThreadPage?>(null); private set
    var thread by mutableStateOf<SharedThread?>(null); private set
    var messages by mutableStateOf<SharedMessagePage?>(null); private set
    var payments by mutableStateOf<OwnerPage<OwnerPayment>?>(null); private set
    var chatReady by mutableStateOf(false); private set
    var awaitingEcho by mutableStateOf(false); private set
    var chatDraft by mutableStateOf(""); private set
    val photos = mutableStateMapOf<String, Uri>()
    val previews = mutableStateMapOf<SharedEvidence, ByteArray>()
    var stage by mutableStateOf<String?>(null); private set
    var pendingEvidence by mutableStateOf<String?>(null); private set
    var evidenceProgress by mutableStateOf<Int?>(null); private set
    val evidenceNames = mutableStateMapOf<SharedEvidence, String>()
    val pendingEvidenceType: String? get() = evidenceTicket?.documentType
    var pendingMedia by mutableStateOf<String?>(null); private set
    private var evidenceTicket: VerificationUploadTicket? = null
    private var evidenceBytes: ByteArray? = null
    private var evidenceTransferred = false
    private var mediaTicket: MediaUploadTicket? = null
    private var mediaUri: Uri? = null
    private var mediaTransferred = false
    private var socket: RentalChatSocket? = null
    private var chatGeneration = 0L
    private var messageId: String? = null
    private var selectedRenterId: String? = null
    private var work: Job? = null

    fun start() = run {
        reloadVerification()
        if (verification?.status != "VERIFIED") screen = SharedScreen.VERIFICATION else reloadWorkspace()
    }
    private suspend fun reloadVerification() { verification = repository.verification(); documents = repository.documents() }
    private suspend fun reloadWorkspace() {
        listings = repository.listings(); applications = repository.applications(); interests = repository.interests()
    }
    fun refreshVerification() = run { reloadVerification() }
    fun go(target: SharedScreen) {
        if (busy) return
        if (target != SharedScreen.CHAT) disconnect()
        screen = target; notice = null
        run { when (target) {
            SharedScreen.HOME -> {
                reloadVerification()
                if (verification?.status == "PENDING") listings = repository.listings() else reloadWorkspace()
            }
            SharedScreen.VERIFICATION -> reloadVerification()
            SharedScreen.LISTINGS -> { verification = repository.verification(); listings = repository.listings() }
            SharedScreen.APPLICATIONS -> applications = repository.applications()
            SharedScreen.INTERESTS -> interests = repository.interests()
            SharedScreen.INBOX -> threads = repository.threads(0)
            SharedScreen.PAYMENTS -> payments = repository.payments(0)
            SharedScreen.STATES -> {
                val id=requireNotNull(selected).id
                listingReview = repository.review(id)
                media = repository.media(id)
                location = try { repository.location(id) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
            }
            SharedScreen.PREFERENCES, SharedScreen.PREFERENCE_SUMMARY -> {
                preferences = try { repository.preferences() } catch (e: HttpException) { if (e.code() == 404) null else throw e }
                preferenceForm = preferences?.let(RoommateForm::from) ?: RoommateForm()
                if (target == SharedScreen.PREFERENCE_SUMMARY && preferences == null) screen = SharedScreen.PREFERENCES
            }
            SharedScreen.DISCOVERY -> discovery = repository.discovery()
            else -> Unit
        } }
    }
    fun newListing() {
        if (busy) return
        if (verification?.status != "VERIFIED") { go(SharedScreen.VERIFICATION); return }
        selected = null; form = SharedForm(); resetMedia(); screen = SharedScreen.DETAILS
    }
    fun edit(value: SharedListing) {
        if (busy) return
        selected = value; form = SharedForm.from(value); resetMedia()
        run {
            household = repository.household(value.id); form = form.withHousehold(requireNotNull(household))
            listingReview = repository.review(value.id)
            screen = if (value.status == "DRAFT" && listingReview?.moderationStatus == "PENDING"
                && listingReview?.moderationSubmittedAt == null) SharedScreen.DETAILS else SharedScreen.STATES
            if (screen == SharedScreen.STATES) {
                media = repository.media(value.id)
                location = try { repository.location(value.id) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
            }
        }
    }
    private fun resetMedia() { media = emptyList(); location = null; household = null; listingReview = null; mediaTicket = null; mediaUri = null; mediaTransferred = false; pendingMedia = null }
    fun saveDraft(next: Boolean) {
        form.error()?.let { notice = it; return }
        run {
            selected = repository.save(selected?.id, form.request())
            household = repository.saveHousehold(selected!!.id, form.household())
            listingReview = null
            listings = repository.listings()
            if (next) { media = repository.media(selected!!.id); screen = SharedScreen.MEDIA } else screen = SharedScreen.LISTINGS
        }
    }
    fun loadMedia() = run { media = repository.media(requireNotNull(selected).id); screen = SharedScreen.MEDIA }
    fun uploadMedia(resolver: ContentResolver, uri: Uri) = run {
        if (mediaTicket != null) { notice = "Finish the current upload first."; return@run }
        val listing = requireNotNull(selected)
        val (mime, size) = withContext(Dispatchers.IO) {
            val mime = resolver.getType(uri)?.lowercase() ?: ""
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
            } ?: -1L
            mime to size
        }
        val type = when (mime) { "image/jpeg", "image/png", "image/webp" -> "IMAGE"; "video/mp4" -> "VIDEO"; else -> { notice = "Choose an image or MP4 video."; return@run } }
        if (size !in 1..52_428_800L) { notice = "Choose a file with a known size of at most 50 MB."; return@run }
        media = repository.media(listing.id)
        mediaTicket = repository.mediaTicket(listing.id, MediaUploadRequest(type, mime, size, nextMediaSortOrder(media)))
        mediaUri = uri; mediaTransferred = false; pendingMedia = mediaTicket!!.mediaId
        finishMedia(resolver)
    }
    fun retryMedia(resolver: ContentResolver) = run { finishMedia(resolver) }
    fun confirmStoredMedia(value: OwnerMedia) = run {
        repository.confirmMedia(requireNotNull(selected).id,value.id); media = repository.media(requireNotNull(selected).id)
    }
    private suspend fun finishMedia(resolver: ContentResolver) {
        val ticket = mediaTicket ?: return
        if (!mediaTransferred) {
            val uri = requireNotNull(mediaUri)
            val body = object : RequestBody() {
                override fun contentType() = ticket.contentType.toMediaType()
                override fun contentLength() = ticket.sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    resolver.openInputStream(uri)?.use { input -> input.source().buffer().use { source ->
                        var remaining = ticket.sizeBytes
                        while (remaining > 0) {
                            val count = source.read(sink.buffer, minOf(remaining, 8192))
                            if (count < 0) throw MediaTransferFailed()
                            remaining -= count; sink.emitCompleteSegments()
                        }
                        if (!source.exhausted()) throw MediaTransferFailed()
                    } } ?: throw MediaTransferFailed()
                }
            }
            repository.transfer(ticket, body); mediaTransferred = true
        }
        repository.confirmMedia(requireNotNull(selected).id, ticket.mediaId)
        listingReview = null
        if (ticket.mediaType == "IMAGE") mediaUri?.let { photos[ticket.mediaId] = it }
        mediaTicket = null; mediaUri = null; mediaTransferred = false; pendingMedia = null
        media = repository.media(requireNotNull(selected).id)
    }
    fun deleteMedia(value: OwnerMedia) = run {
        repository.deleteMedia(requireNotNull(selected).id, value.id); photos.remove(value.id)
        listingReview = null
        if (mediaTicket?.mediaId == value.id) { mediaTicket = null; mediaUri = null; mediaTransferred = false; pendingMedia = null }
        media = repository.media(requireNotNull(selected).id)
    }
    fun openLocation() = run {
        location = try { repository.location(requireNotNull(selected).id) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
        screen = SharedScreen.LOCATION
    }
    fun savePin(area: String, latitude: Double, longitude: Double) {
        if (area.trim().isEmpty() || area.trim().length > 150 || !validOwnerPin(latitude, longitude)) { notice = "Enter a public area and select the home on the map."; return }
        run {
            location = repository.saveLocation(requireNotNull(selected).id, OwnerLocation(area.trim(), BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude)))
            listingReview = null
            screen = SharedScreen.PREVIEW
        }
    }
    fun submitListingReview() = run {
        listingReview = repository.submitReview(requireNotNull(selected).id)
        notice = "Shared listing submitted for staff review."
    }
    fun reviseListingReview() = run {
        val id=requireNotNull(selected).id
        listingReview = repository.reviseReview(id)
        listings = repository.listings()
        selected = listings.first { it.id == id }
        form = SharedForm.from(requireNotNull(selected))
        household = repository.household(id)
        form = form.withHousehold(requireNotNull(household))
        screen = SharedScreen.DETAILS
        notice = "Update the shared listing, then submit it for review again."
    }
    fun transition(action: String) {
        if (action == "publish" && listingReview?.moderationStatus != "APPROVED") {
            notice = "Staff approval is required before publication."
            return
        }
        run {
        selected = repository.transition(requireNotNull(selected).id, action)
        listingReview = repository.review(requireNotNull(selected).id)
        listings = repository.listings(); screen = SharedScreen.STATES
        }
    }
    fun openApplication(value: OwnerApplication) { if (!busy) { application = value; screen = SharedScreen.APPLICATION } }
    fun decide(action: String, reason: String? = null) = run { application = repository.decide(requireNotNull(application).id, action, reason); applications = repository.applications() }
    fun interestAction(value: SharedInterest, action: String) = run { repository.interestAction(value.id, action); interests = repository.interests() }
    fun nextThreads() = run {
        val page = threads ?: return@run
        if (!page.last) { val next = repository.threads(page.number + 1); threads = SharedThreadPage((page.content + next.content).distinctBy { it.id }, next.number, next.totalPages, next.last) }
    }
    fun openThread(value: SharedThread) = run {
        disconnect(); thread = value; messages = repository.messages(value.id); screen = SharedScreen.CHAT
        if (value.status == "OPEN") connect()
    }
    fun olderMessages() = run {
        val page = messages ?: return@run
        if (page.hasMore) { val next = repository.messages(requireNotNull(thread).id, page.nextCursor)
            messages = SharedMessagePage((page.items + next.items).distinctBy { it.id }, next.nextCursor, next.hasMore) }
    }
    fun reconnect() = run { disconnect(); connect() }
    private suspend fun connect() {
        val version = ++chatGeneration
        socket = repository.connect { event -> viewModelScope.launch {
            if (version != chatGeneration) return@launch
            when (event) {
                ChatEvent.Connected -> chatReady = true
                is ChatEvent.Unavailable -> { chatReady = false; notice = event.text }
                is ChatEvent.Message -> if (event.message.threadId == thread?.id) {
                    val page = messages ?: SharedMessagePage(emptyList(), null, false)
                    messages = SharedMessagePage((page.items + event.message).distinctBy { it.id }, page.nextCursor, page.hasMore)
                    if (event.message.clientMessageId == messageId) { awaitingEcho = false; messageId = null; chatDraft = "" }
                }
            }
        } }
    }
    fun updateChat(value: String) { if (!awaitingEcho) { chatDraft = value.take(2000); messageId = null } }
    fun sendMessage() {
        if (awaitingEcho || !chatReady) return
        val id = messageId ?: UUID.randomUUID().toString().also { messageId = it }
        if (socket?.send(thread?.id ?: return, id, chatDraft) == true) awaitingEcho = true else notice = "Reconnect before sending."
    }
    fun disconnect() { chatGeneration++; socket?.close(); socket = null; chatReady = false; awaitingEcho = false }
    fun nextPayments() = run {
        val page = payments ?: return@run
        if (page.hasNext) { val next = repository.payments(page.page + 1)
            payments = OwnerPage((page.items + next.items).distinctBy { it.id }, next.page, next.size, next.totalElements, next.totalPages, next.hasNext) }
    }
    fun savePreferences() {
        preferenceForm.error()?.let { notice = it; return }
        run { preferences = repository.savePreferences(preferenceForm.request(preferences)); discovery = null; renter = null; screen = SharedScreen.PREFERENCE_SUMMARY }
    }
    fun nextDiscovery() = run {
        val current = discovery ?: return@run
        if (current.hasNext) { val next = repository.discovery(current.page + 1)
            discovery = SharedDiscoveryPage((current.items + next.items).distinctBy { it.userId },next.page,next.size,next.totalElements,next.hasNext) }
    }
    fun openRenter(value: SharedPublicProfile) = run { renter = null; selectedRenterId = value.userId; renter = repository.renterProfile(value.userId); screen = SharedScreen.RENTER_PROFILE }
    fun background() { disconnect(); discovery = null; renter = null }
    fun foreground() {
        if (busy) return
        if (screen == SharedScreen.DISCOVERY) go(SharedScreen.DISCOVERY)
        else if (screen == SharedScreen.RENTER_PROFILE) run {
            val id = selectedRenterId ?: return@run
            try { renter = repository.renterProfile(id) }
            catch (e: HttpException) { renter = null; screen = SharedScreen.DISCOVERY; throw e }
        }
    }
    fun uploadEvidence(type: SharedEvidence, resolver: ContentResolver, uri: Uri) = run {
        stage = "Preparing file"
        val (name, bytes) = withContext(Dispatchers.IO) { evidenceDisplayName(resolver, uri) to
            if (type == SharedEvidence.RENTAL_AGREEMENT) VerificationDocuments.fromUri(resolver, uri) else VerificationImages.fromUri(resolver, uri) }
        beginEvidence(type, bytes, name)
    }
    fun captureEvidence(type: SharedEvidence, bitmap: Bitmap) {
        if (busy) { bitmap.recycle(); return }
        run { try {
            require(type != SharedEvidence.RENTAL_AGREEMENT)
            val bytes = withContext(Dispatchers.IO) { VerificationImages.fromBitmap(bitmap) }
            beginEvidence(type, bytes, when (type) {
                SharedEvidence.ID_FRONT -> "national_id_front.jpg"
                SharedEvidence.ID_BACK -> "national_id_back.jpg"
                else -> "selfie_capture.jpg"
            })
        } finally { bitmap.recycle() } }
    }
    internal suspend fun beginEvidence(type: SharedEvidence, bytes: ByteArray, displayName: String? = null) {
        if (verification?.status !in listOf("NOT_STARTED", "REJECTED")) { bytes.fill(0); notice = "Refresh your verification status before uploading."; return }
        // A new camera/gallery choice explicitly replaces this type, even when local metadata is stale.
        // Keep the previous file retryable until the server accepts the replacement ticket.
        val ticket = try { repository.ticket(type, replace = true) } catch (e: Exception) { bytes.fill(0); throw e }
        if (ticket.documentType != type.name || ticket.contentType != type.mime) { bytes.fill(0); error("Unexpected evidence format") }
        evidenceBytes?.fill(0)
        previews.remove(type)?.fill(0)
        evidenceNames.remove(type)
        displayName?.let { evidenceNames[type] = it }
        documents = documents.filterNot { it.documentType == type.name }
        evidenceTicket = ticket; evidenceBytes = bytes; evidenceTransferred = false; pendingEvidence = ticket.documentId; evidenceProgress = null
        if (type == SharedEvidence.SELFIE) withContext(Dispatchers.IO) { VerificationImages.thumbnail(bytes) }?.let { previews.put(type, it)?.fill(0) }
        finishEvidence()
    }
    fun retryEvidence() = run {
        val ticket = evidenceTicket ?: return@run
        if (!evidenceTransferred) {
            val renewed = repository.ticket(SharedEvidence.valueOf(ticket.documentType))
            require(renewed.documentId == ticket.documentId && renewed.documentType == ticket.documentType && renewed.contentType == ticket.contentType)
            evidenceTicket = renewed
        }
        finishEvidence()
    }
    private suspend fun finishEvidence() {
        val ticket = evidenceTicket ?: return
        if (!evidenceTransferred) { stage = "Uploading"; repository.transferEvidence(ticket, EvidenceUploadBody(requireNotNull(evidenceBytes), ticket.contentType.toMediaType()) {
            if (pendingEvidence == ticket.documentId) evidenceProgress = it
        }); evidenceTransferred = true }
        stage = "Checking document"; confirmEvidence(ticket.documentId)
        evidenceBytes?.fill(0); evidenceBytes = null; evidenceTicket = null; evidenceTransferred = false; pendingEvidence = null; evidenceProgress = null
    }
    fun confirmStoredEvidence(id: String) = run {
        confirmEvidence(id)
        if (pendingEvidence == id) {
            evidenceBytes?.fill(0); evidenceBytes = null; evidenceTicket = null; evidenceTransferred = false; pendingEvidence = null; evidenceProgress = null
        }
    }
    private suspend fun confirmEvidence(id: String) {
        try { repository.confirmEvidence(id) } catch (e: HttpException) {
            if (e.code() != 409 || repository.documents().none { it.documentId == id && it.status == "READY" && it.securityChecked }) throw e
        }
        documents = repository.documents()
    }
    fun submitVerification() = run {
        if (!sharedVerificationReady(documents)) { notice = "Confirm both ID sides, live photo and rental agreement."; return@run }
        verification = repository.submitVerification()
    }
    fun dismissNotice() { notice = null }
    fun showNotice(value: String) { notice = value }
    private fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = null
        work = viewModelScope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) {
                if (e is SessionExpired || e is WorkspaceUnavailable) { clear(); expired() }
                else notice = when {
                    e is VerificationPdfRejected -> "Choose a rental-agreement PDF of up to 10 MB."
                    e is MediaTransferFailed -> "Upload did not complete. Retry the current file."
                    e is HttpException -> when (e.code()) {
                        400 -> "Check the file or required details."
                        403 -> "Your verification, role or access does not permit this action."
                        404 -> "This record is unavailable to your account."
                        409 -> "This action conflicts with the saved state. Refresh and try again."
                        503 -> "Document checks are unavailable. Please try again later."
                        else -> friendlyError(e)
                    }
                    else -> friendlyError(e)
                }
            } finally { busy = false; stage = null }
        }
    }
    fun clear() {
        work?.cancel(); work = null; disconnect(); evidenceBytes?.fill(0); evidenceBytes = null; evidenceTicket = null
        previews.values.forEach { it.fill(0) }; previews.clear(); evidenceNames.clear(); pendingEvidence = null; evidenceTransferred = false; evidenceProgress = null
        documents = emptyList(); verification = null; listings = emptyList(); selected = null; listingReview = null; media = emptyList(); location = null
        applications = emptyList(); application = null; interests = emptyList(); threads = null; thread = null; messages = null; payments = null
        household = null; preferences = null; preferenceForm = RoommateForm(); discovery = null; renter = null; selectedRenterId = null
        mediaTicket = null; mediaUri = null; mediaTransferred = false; pendingMedia = null; photos.clear(); chatDraft = ""; messageId = null
        busy = false; notice = null; stage = null; form = SharedForm(); screen = SharedScreen.HOME
    }
    override fun onCleared() { clear() }
}
