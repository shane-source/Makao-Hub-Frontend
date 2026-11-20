package com.makaohub.android

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import okio.buffer
import retrofit2.HttpException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class OwnerScreen { HOME, VERIFICATION, LISTINGS, EDIT, MEDIA, MEDIA_MANAGE, LOCATION, PREVIEW, CHECKS, APPLICATIONS, APPLICATION, APPLICATION_DECISION, MESSAGES, CHAT, PAYMENTS, PLANS, BOOST, PROFILE }

internal fun nextMediaSortOrder(records: List<OwnerMedia>): Int {
    val used = records.mapTo(HashSet()) { it.sortOrder }
    return (0..250).firstOrNull { it !in used } ?: error("No available media position")
}

data class ListingForm(val title: String = "", val description: String = "", val propertyType: String = "APARTMENT",
    val rent: String = "", val deposit: String = "", val bedrooms: String = "1", val bathrooms: String = "1",
    val waterAvailable: Boolean? = false, val securityAvailable: Boolean? = false,
    val parkingAvailable: Boolean? = false, val balcony: Boolean? = false,
    val furnished: Boolean = false, val petsAllowed: Boolean? = false, val available: String = "") {
    fun error(): String? = when {
        title.trim().isEmpty() || title.trim().length > 150 -> "Enter a title of up to 150 characters."
        description.trim().isEmpty() || description.trim().length > 3000 -> "Enter a description of up to 3,000 characters."
        propertyType !in listOf("APARTMENT", "HOUSE", "STUDIO", "BEDSITTER", "ROOM") -> "Select a supported property type."
        rent.toLongOrNull()?.let { it > 0 } != true -> "Enter a positive monthly rent in whole KSh."
        deposit.toLongOrNull()?.let { it >= 0 } != true -> "Enter a deposit of zero or more in whole KSh."
        bedrooms.toIntOrNull() !in 0..20 -> "Bedrooms must be between 0 and 20."
        bathrooms.toIntOrNull() !in 1..20 -> "Bathrooms must be between 1 and 20."
        available.isNotBlank() && runCatching { LocalDate.parse(available) }.isFailure -> "Use YYYY-MM-DD for the availability date."
        else -> null
    }
    fun request() = ListingDraft(title.trim(), description.trim(), propertyType, rent.toLong(), bedrooms.toInt(),
        bathrooms.toInt(), furnished, available.ifBlank { null }, deposit.toLong(), waterAvailable,
        securityAvailable, parkingAvailable, balcony, petsAllowed)
    companion object { fun from(l: OwnerListing) = ListingForm(title = l.title, description = l.description,
        propertyType = l.propertyType, rent = l.monthlyRentKes.toString(), deposit = l.depositKes?.toString().orEmpty(),
        bedrooms = l.bedroomCount.toString(), bathrooms = l.bathroomCount.toString(),
        waterAvailable = l.waterAvailable, securityAvailable = l.securityAvailable,
        parkingAvailable = l.parkingAvailable, balcony = l.balcony, furnished = l.furnished,
        petsAllowed = l.petsAllowed, available = l.availableFrom ?: "") }
}

/** Keeps private owner data in memory and only reflects states confirmed by the server. */
class LandlordViewModel(private val repository: LandlordRepository, private val sessionExpired: () -> Unit) : ViewModel() {
    var screen by mutableStateOf(OwnerScreen.HOME); private set
    var busy by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null); private set
    var summary by mutableStateOf<LandlordSummary?>(null); private set
    var verification by mutableStateOf<OwnerVerification?>(null); private set
    var verificationDocuments by mutableStateOf<List<OwnerVerificationDocument>>(emptyList()); private set
    private var verificationTicket: VerificationUploadTicket? = null
    private var verificationBytes: ByteArray? = null
    private var verificationTransferred = false
    var verificationPendingId by mutableStateOf<String?>(null); private set
    var evidenceStage by mutableStateOf<String?>(null); private set
    var evidenceProgress by mutableStateOf<Int?>(null); private set
    val pendingEvidenceType: String? get() = verificationTicket?.documentType
    val evidencePreviews = mutableStateMapOf<OwnerEvidence, ByteArray>()
    val evidenceNames = mutableStateMapOf<OwnerEvidence, String>()
    var listings by mutableStateOf<List<OwnerListing>>(emptyList()); private set
    var applications by mutableStateOf<List<OwnerApplication>>(emptyList()); private set
    var applicationNotes by mutableStateOf<List<ApplicationNote>>(emptyList()); private set
    var applicationsLoaded by mutableStateOf(false); private set
    var applicationListingFilter by mutableStateOf<String?>(null); private set
    suspend fun listingCover(id: String) = repository.cover(id)
    var selected by mutableStateOf<OwnerListing?>(null); private set
    var listingReview by mutableStateOf<OwnerListingReview?>(null); private set
    var form by mutableStateOf(ListingForm())
    var amenityOptions by mutableStateOf<List<AmenityOption>>(emptyList()); private set
    var selectedAmenityIds by mutableStateOf<Set<String>>(emptySet()); private set
    fun toggleAmenity(id:String, checked:Boolean) { selectedAmenityIds = if (checked) selectedAmenityIds + id else selectedAmenityIds - id }
    private suspend fun loadAmenities(id:String?) { amenityOptions=repository.amenities(); selectedAmenityIds=id?.let { repository.selectedAmenities(it).map { a -> a.id }.toSet() } ?: emptySet() }
    var media by mutableStateOf<List<OwnerMedia>>(emptyList()); private set
    var selectedMediaId by mutableStateOf<String?>(null); private set
    var mediaUploadError by mutableStateOf<String?>(null); private set
    var location by mutableStateOf<OwnerLocation?>(null); private set
    var selectedApplication by mutableStateOf<OwnerApplication?>(null); private set
    var bookings by mutableStateOf<List<OwnerBooking>>(emptyList()); private set
    var threads by mutableStateOf<OwnerPage<OwnerThread>?>(null); private set
    var chatStates by mutableStateOf<Map<String, ChatThreadState>>(emptyMap()); private set
    var showArchived by mutableStateOf(false)
    private fun stateOf(t: OwnerThread) = chatStates[ChatStates.key(ChatStates.RENTAL, t.id)]
    private fun placement(t: OwnerThread) = ChatStates.placement(stateOf(t), t.lastMessageAt)
    val inboxThreads: List<OwnerThread> get() = threads?.items.orEmpty().filter { placement(it) == ChatStates.ACTIVE }
    val archivedThreads: List<OwnerThread> get() = threads?.items.orEmpty().filter { placement(it) == ChatStates.ARCHIVED }
    private suspend fun loadStates() {
        try { chatStates = repository.chatStates().associateBy { ChatStates.key(it.kind, it.threadId) } } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    /** Threads and the person's archive state arrive together so the inbox never flashes archived chats. */
    private suspend fun loadInbox(page: Int = 0) = coroutineScope {
        val list = async { repository.threads(page) }
        val states = async { loadStates() }
        states.await(); threads = list.await()
    }
    private fun changeState(t: OwnerThread, state: String, done: String) = run { repository.setChatState(ChatStates.RENTAL, t.id, state); loadStates(); notice = done }
    fun archiveThread(t: OwnerThread) = changeState(t, ChatStates.ARCHIVED, "Conversation archived. Find it under Archived.")
    fun restoreThread(t: OwnerThread) = changeState(t, ChatStates.ACTIVE, "Conversation moved back to your inbox.")
    fun deleteThread(t: OwnerThread) = changeState(t, ChatStates.DELETED, "Conversation deleted for you. The other person still has it.")
    var thread by mutableStateOf<OwnerThread?>(null); private set
    var messages by mutableStateOf<OwnerMessagePage?>(null); private set
    var payments by mutableStateOf<OwnerPage<OwnerPayment>?>(null); private set
    var testDestination by mutableStateOf<OwnerTestDestinationStatus?>(null); private set
    var checkout by mutableStateOf<OwnerPayment?>(null); private set
    var cardMetrics by mutableStateOf<Map<String, ListingCardMetrics>>(emptyMap()); private set
    suspend fun boostOffers() = repository.offers()
    suspend fun buyBoost(code: String, listingId: String, key: String) = repository.offerCheckout(code, listingId, key)
    suspend fun reconcileBoost(reference: String) = repository.reconcileOffer(reference)
    suspend fun planCheckout(code: String, key: String) = repository.planCheckout(code, key)
    suspend fun redeemIncludedBoost(listingId: String) { repository.redeemIncludedBoost(listingId) }
    var checkoutType by mutableStateOf("PLAN_UPGRADE"); private set
    var chatReady by mutableStateOf(false); private set
    var chatDraft by mutableStateOf(""); private set
    val localPhotos = mutableStateMapOf<String, Uri>()
    private var upload: MediaUploadTicket? = null
    private var uploadUri: Uri? = null
    private var uploaded = false
    private var replacingMediaId: String? = null
    val mediaUploadPending: Boolean get() = upload != null
    var pendingConfirmationId by mutableStateOf<String?>(null); private set
    private val live = LiveChat(viewModelScope, listOf(ChatChannel.RENTAL), { _, events -> repository.connectChat(BuildConfig.API_BASE_URL, events) }) { _, event -> viewModelScope.launch { liveEvent(event) } }
    private var threadRefresh: Job? = null
    private var clientMessageId: String? = null
    var awaitingEcho by mutableStateOf(false); private set
    private var checkoutKey = UUID.randomUUID().toString()
    private var checkoutListingId: String? = null
    private var work: Job? = null
    private var generation = 0L

    fun go(target: OwnerScreen) {
        if (busy) return
        if (target == OwnerScreen.APPLICATIONS) applicationListingFilter = null
        if (target != OwnerScreen.CHAT) disconnectChat()
        screen = target; notice = null
        when(target) {
            OwnerScreen.HOME -> refresh()
            OwnerScreen.VERIFICATION -> loadVerification()
            OwnerScreen.LISTINGS -> run(keepCache = true) {
                if (isFresh("listings")) return@run
                listings = repository.listings()
                cardMetrics = emptyMap()
                try { cardMetrics = repository.cardMetrics().associateBy { it.listingId } }
                catch (e: HttpException) { if (e.code() != 404 && e.code() != 503) throw e }
                markFresh("listings")
            }
            OwnerScreen.APPLICATIONS -> run(keepCache = true) {
                if (isFresh("applications")) return@run
                applications = repository.applications(); applicationNotes = repository.notes(); applicationsLoaded = true
                markFresh("applications")
            }
            OwnerScreen.MESSAGES -> loadThreads()
            OwnerScreen.PAYMENTS -> loadPayments()
            OwnerScreen.CHECKS -> run {
                val id=requireNotNull(selected).id
                listingReview = repository.review(id)
                media = repository.media(id)
                location = try { repository.location(id) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
            }
            else -> Unit
        }
    }
    fun start() = run {
        // Load the workspace together with the verification check. An unverified owner's early result is dropped;
        // if the early load fails, the normal load below repeats it so errors surface as before.
        coroutineScope {
            val early = async {
                try { Triple(repository.summary(), repository.listings(), repository.applications()) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
            verification = repository.verification()
            if (verification?.status != "VERIFIED") {
                early.cancel()
                verificationDocuments = repository.verificationDocuments()
                screen = OwnerScreen.VERIFICATION
            } else {
                val loaded = early.await()
                if (loaded != null) {
                    summary = loaded.first; listings = loaded.second; applications = loaded.third
                    applicationsLoaded = true
                } else refreshWorkspace()
            }
        }
    }
    fun verificationStatus(fallback: String?) = verification?.status ?: fallback ?: "NOT_STARTED"
    /** Newest draft that was never sent for staff review; drafts already awaiting review are not "unfinished". */
    var unfinishedDraft by mutableStateOf<OwnerListing?>(null); private set
    fun refreshUnfinishedDraft() {
        val drafts = listings.filter { it.status == "DRAFT" }.sortedByDescending { it.updatedAt }.take(5)
        if (drafts.isEmpty()) { unfinishedDraft = null; return }
        viewModelScope.launch {
            unfinishedDraft = drafts.firstOrNull { draft ->
                try { repository.review(draft.id).let { it.moderationStatus == "PENDING" && it.moderationSubmittedAt == null } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
            }
        }
    }
    fun loadVerification() = run {
        verification = repository.verification()
        verificationDocuments = repository.verificationDocuments()
    }
    /** Reads only the owner's saved decision; acknowledging a document never approves the role. */
    fun refreshReviewStatus() {
        if (busy || verification?.status != "PENDING" || screen !in listOf(OwnerScreen.HOME, OwnerScreen.VERIFICATION)) return
        run {
            val latest = repository.verification()
            verification = latest
            if (latest.status in listOf("VERIFIED", "REJECTED")) screen = OwnerScreen.VERIFICATION
        }
    }
    fun uploadEvidence(type: OwnerEvidence, resolver: ContentResolver, uri: Uri) = run {
        // Keep display names only in this session; never persist or log document names.
        val displayName = withContext(Dispatchers.IO) { evidenceDisplayName(resolver, uri) }
        evidenceStage = if (type == OwnerEvidence.OWNERSHIP_PROOF) "Reading PDF" else "Preparing image"
        val bytes = withContext(Dispatchers.IO) {
            if (type == OwnerEvidence.OWNERSHIP_PROOF) VerificationDocuments.fromUri(resolver, uri)
            else VerificationImages.fromUri(resolver, uri)
        }
        beginEvidence(type, bytes, displayName)
    }
    fun uploadSelfie(bitmap: Bitmap) = uploadCameraEvidence(OwnerEvidence.SELFIE, bitmap)
    fun uploadCameraEvidence(type: OwnerEvidence, bitmap: Bitmap) {
        // CameraX transfers ownership of this private in-memory bitmap to the model.
        // If another operation is active, release it immediately instead of leaking it.
        if (busy) {
            bitmap.recycle()
            return
        }
        run {
            try {
                if (type == OwnerEvidence.OWNERSHIP_PROOF) return@run
                val displayName = when (type) {
                    OwnerEvidence.ID_FRONT -> "national_id_front.jpg"
                    OwnerEvidence.ID_BACK -> "national_id_back.jpg"
                    else -> "selfie_capture.jpg"
                }
                evidenceStage = "Preparing photo"
                val bytes = withContext(Dispatchers.IO) { VerificationImages.fromBitmap(bitmap) }
                beginEvidence(type, bytes, displayName)
            } finally {
                bitmap.recycle()
            }
        }
    }
    internal suspend fun beginEvidence(type: OwnerEvidence, bytes: ByteArray, displayName: String? = null) {
        if (verification?.status !in listOf("NOT_STARTED", "REJECTED")) {
            bytes.fill(0); notice = "Refresh your verification status before uploading."; return
        }
        // A new file needs a new immutable object, even after a failed or uncertain confirmation.
        // Keep the previous file retryable until the replacement ticket succeeds.
        val ticket = try { repository.verificationTicket(type, true) }
        catch (failure: Exception) { bytes.fill(0); throw failure }
        if (ticket.documentType != type.name || ticket.contentType != type.uploadContentType) {
            bytes.fill(0); notice = "The saved upload uses a different format. Contact support before replacing it."; return
        }
        verificationBytes?.fill(0)
        evidencePreviews.remove(type)?.fill(0)
        evidenceNames.remove(type)
        displayName?.let { evidenceNames[type] = it }
        verificationDocuments = verificationDocuments.filterNot { it.documentType == type.name }
        verificationTicket = ticket; verificationBytes = bytes
        verificationTransferred = false; verificationPendingId = ticket.documentId; evidenceProgress = null
        // Only the live-photo screen needs a thumbnail; ID screens deliberately expose no preview.
        if (type == OwnerEvidence.SELFIE) withContext(Dispatchers.IO) { VerificationImages.thumbnail(bytes) }?.let { evidencePreviews[type] = it }
        finishEvidenceUpload()
    }
    fun retryEvidence() = run {
        val previous = verificationTicket ?: return@run
        if (!verificationTransferred) {
            // A user-requested retry renews an expired signed URL; never silently retry a transfer.
            val renewed = repository.verificationTicket(OwnerEvidence.valueOf(previous.documentType))
            if (renewed.documentId != previous.documentId || renewed.documentType != previous.documentType
                || renewed.contentType != previous.contentType) {
                notice = "The saved upload changed. Refresh verification before continuing."; return@run
            }
            verificationTicket = renewed
        }
        finishEvidenceUpload()
    }
    private suspend fun finishEvidenceUpload() {
        val ticket = verificationTicket ?: return
        if (!verificationTransferred) {
            evidenceStage = "Uploading file"
            repository.transferVerification(ticket, EvidenceUploadBody(requireNotNull(verificationBytes), ticket.contentType.toMediaType()) {
                if (verificationPendingId == ticket.documentId) evidenceProgress = it
            })
            verificationTransferred = true
        }
        evidenceStage = if (ticket.contentType == "application/pdf") "Security-checking PDF" else "Confirming image"
        confirmEvidence(ticket.documentId)
        verificationBytes?.fill(0)
        verificationTicket = null; verificationBytes = null
        verificationTransferred = false; verificationPendingId = null
        evidenceProgress = null
        notice = null
    }
    fun confirmSavedEvidence(id: String) = run {
        evidenceStage = "Checking stored file"
        confirmEvidence(id)
        if (verificationPendingId == id) {
            verificationBytes?.fill(0)
            verificationTicket = null; verificationBytes = null; verificationTransferred = false; verificationPendingId = null
            evidenceProgress = null
        }
        notice = null
    }
    private suspend fun confirmEvidence(id: String) {
        try { repository.confirmVerification(id) }
        catch (failure: HttpException) {
            if (failure.code() != 409) throw failure
            // Only an owned READY record can resolve an uncertain previous confirmation.
            val records = repository.verificationDocuments()
            if (records.none { it.documentId == id && it.status == "READY" && it.securityChecked }) throw failure
        }
        verificationDocuments = repository.verificationDocuments()
    }
    fun submitVerification() = run {
        if (!verificationReady(verificationDocuments)) {
            notice = "Upload and confirm ID front, ID back, selfie and ownership proof first."; return@run
        }
        verification = repository.submitVerification()
        notice = null
    }
    fun refresh() = run {
        verification = repository.verification()
        if (verification?.status == "PENDING") verificationDocuments = repository.verificationDocuments()
        refreshWorkspace()
    }
    private suspend fun refreshWorkspace() {
        coroutineScope {
            val s = async { repository.summary() }
            val l = async { repository.listings() }
            val a = async { repository.applications() }
            summary = s.await(); listings = l.await(); applications = a.await()
        }
        applicationsLoaded = true
    }
    fun newListing() {
        if (busy) return
        if (verification?.status == "PENDING") { go(OwnerScreen.VERIFICATION); return }
        selected = null; form = ListingForm(); resetEditor(); run { loadAmenities(null); screen = OwnerScreen.EDIT }
    }
    fun edit(listing: OwnerListing) {
        if (busy) return
        selected = listing; form = ListingForm.from(listing); resetEditor(); run { loadAmenities(listing.id); screen = OwnerScreen.EDIT }
    }
    fun updateListing(listing: OwnerListing) {
        if (busy) return
        selected = listing; resetEditor()
        run {
            val review = repository.review(listing.id)
            if (listing.status != "DRAFT" || review.moderationStatus != "PENDING" || review.moderationSubmittedAt != null)
                repository.reviseReview(listing.id)
            listings = repository.listings()
            selected = listings.first { it.id == listing.id }
            form = ListingForm.from(requireNotNull(selected))
            loadAmenities(listing.id)
            screen = OwnerScreen.EDIT
            notice = "Make your changes, then submit the listing for review."
        }
    }
    fun removeListing(listing: OwnerListing) = run {
        repository.transition(listing.id, "archive")
        listings = listings.filterNot { it.id == listing.id }
        summary = summary?.let { it.copy(totalListings = (it.totalListings - 1).coerceAtLeast(0),
            draftListings = (it.draftListings - if (listing.status == "DRAFT") 1 else 0).coerceAtLeast(0),
            publishedListings = (it.publishedListings - if (listing.status == "PUBLISHED") 1 else 0).coerceAtLeast(0)) }
        notice = "Listing removed."
    }
    fun manage(listing: OwnerListing) {
        if (busy) return
        selected = listing; form = ListingForm.from(listing); resetEditor()
        run {
            listingReview = repository.review(listing.id)
            loadAmenities(listing.id)
            screen = if (listing.status == "DRAFT" && listingReview?.moderationStatus == "PENDING"
                && listingReview?.moderationSubmittedAt == null) OwnerScreen.EDIT else OwnerScreen.CHECKS
            if (screen == OwnerScreen.CHECKS) {
                media = repository.media(listing.id)
                location = try { repository.location(listing.id) } catch (e: HttpException) { if (e.code() == 404) null else throw e }
            }
        }
    }
    private fun resetEditor() { notice = null; media = emptyList(); selectedMediaId = null; mediaUploadError = null; location = null; listingReview = null; upload = null; uploadUri = null; uploaded = false; replacingMediaId = null; pendingConfirmationId = null }
    fun saveDraft(next: Boolean) {
        form.error()?.let { notice = it; return }
        run {
            val names=amenityOptions.filter { it.id in selectedAmenityIds }.map { it.name }.toSet()
            form=form.copy(waterAvailable="Water" in names, securityAvailable="Security" in names,
                parkingAvailable="Parking" in names, balcony="Balcony" in names, petsAllowed="Pets allowed" in names)
            selected = repository.save(selected?.id, form.request())
            repository.saveAmenities(selected!!.id,selectedAmenityIds)
            listingReview = null
            notice = "Draft saved. It is not visible in public search."
            if (next) { media = repository.media(selected!!.id); screen = OwnerScreen.MEDIA }
        }
    }
    fun loadMedia() = run { media = repository.media(requireNotNull(selected).id); screen = OwnerScreen.MEDIA }
    fun deleteDraft(listing: OwnerListing) {
        if (listing.status != "DRAFT") {
            notice = "Only draft listings can be deleted."
            return
        }
        run {
            repository.deleteDraft(listing.id)
            listings = listings.filterNot { it.id == listing.id }
            summary = summary?.let {
                it.copy(
                    totalListings = (it.totalListings - 1).coerceAtLeast(0),
                    draftListings = (it.draftListings - 1).coerceAtLeast(0)
                )
            }
            if (selected?.id == listing.id) {
                selected = null
                form = ListingForm()
                resetEditor()
            }
            notice = "Draft deleted."
        }
    }
    fun selectMedia(id: String) {
        if (!busy && media.any { it.id == id }) selectedMediaId = id
    }
    fun manageMedia(id: String) {
        if (!busy && media.any { it.id == id }) { selectedMediaId = id; screen = OwnerScreen.MEDIA_MANAGE }
    }
    fun removeMedia(id: String) = run {
        repository.remove(requireNotNull(selected).id, id)
        listingReview = null
        localPhotos.remove(id)
        // DELETE succeeded. Do not invite a second deletion if the metadata refresh fails.
        media = media.filterNot { it.id == id }
        if (selectedMediaId == id) selectedMediaId = null
        if (screen == OwnerScreen.MEDIA_MANAGE) screen = OwnerScreen.MEDIA
        if (upload?.mediaId == id) { upload = null; uploadUri = null; uploaded = false; replacingMediaId = null; pendingConfirmationId = null }
        media = repository.media(requireNotNull(selected).id)
        mediaUploadError = null
        notice = "Media removed from this draft."
    }
    fun uploadFile(resolver: ContentResolver, uri: Uri) = startMediaUpload(resolver, uri, null)
    fun replacePhoto(resolver: ContentResolver, uri: Uri) {
        val selectedId = selectedMediaId ?: return
        val selectedMedia = media.find { it.id == selectedId } ?: return
        if (selectedMedia.mediaType != "IMAGE") {
            notice = "Only photos can be replaced here."
            return
        }
        startMediaUpload(resolver, uri, selectedId)
    }
    private fun startMediaUpload(resolver: ContentResolver, uri: Uri, replacementId: String?) = run {
        val listing = requireNotNull(selected)
        mediaUploadError = null
        val (mime, size) = withContext(Dispatchers.IO) {
            val contentType = resolver.getType(uri)?.lowercase() ?: ""
            val fileSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
            contentType to fileSize
        }
        val type = if (mime in listOf("image/jpeg", "image/png", "image/webp")) "IMAGE" else if (mime == "video/mp4") "VIDEO" else {
            notice = "Choose a JPEG, PNG, WebP image or MP4 video."; return@run
        }
        if (replacementId != null && type != "IMAGE") {
            notice = "Choose a JPEG, PNG or WebP photo as the replacement."; return@run
        }
        if (size !in 1..52428800L) { notice = "Choose a file with a known size of at most 50 MB. The server applies additional image limits."; return@run }
        if (upload != null && uploadUri != uri) {
            repository.remove(listing.id, requireNotNull(upload).mediaId)
            upload = null; uploadUri = null; uploaded = false; pendingConfirmationId = null
        }
        if (upload == null || uploadUri != uri) {
            media = repository.media(listing.id)
            upload = repository.uploadTicket(listing.id, MediaUploadRequest(type, mime, size, nextMediaSortOrder(media)))
            uploadUri = uri; uploaded = false; replacingMediaId = replacementId
        }
        transferPendingUpload(resolver)
        confirmPendingUpload()
    }
    private suspend fun transferPendingUpload(resolver: ContentResolver) {
        val ticket = upload ?: return
        val uri = uploadUri ?: return
        if (!uploaded) {
            val body = object : RequestBody() {
                override fun contentType() = ticket.contentType.toMediaType()
                override fun contentLength() = ticket.sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    resolver.openInputStream(uri)?.use { input -> input.source().buffer().use { source ->
                        var remaining = ticket.sizeBytes
                        while (remaining > 0) {
                            val count = source.read(sink.buffer, minOf(remaining, 8192))
                            if (count == -1L) throw MediaTransferFailed()
                            sink.emitCompleteSegments(); remaining -= count
                        }
                        if (!source.exhausted()) throw MediaTransferFailed()
                    } } ?: throw MediaTransferFailed()
                }
            }
            repository.transfer(ticket, body); uploaded = true; pendingConfirmationId = ticket.mediaId
        }
    }
    fun retryConfirmation() = run { confirmPendingUpload() }
    fun retryMediaUpload(resolver: ContentResolver) = run {
        mediaUploadError = null
        transferPendingUpload(resolver)
        confirmPendingUpload()
    }
    private suspend fun confirmPendingUpload() {
        val ticket = upload ?: return
        if (!uploaded) return
        val listingId = requireNotNull(selected).id
        repository.confirm(listingId, ticket.mediaId)
        listingReview = null
        replacingMediaId?.let { oldMediaId ->
            if (oldMediaId != ticket.mediaId) repository.remove(listingId, oldMediaId)
            localPhotos.remove(oldMediaId)
        }
        if (ticket.mediaType == "IMAGE") uploadUri?.let { localPhotos[ticket.mediaId] = it }
        selectedMediaId = ticket.mediaId
        upload = null; uploadUri = null; uploaded = false; replacingMediaId = null; pendingConfirmationId = null
        media = repository.media(listingId)
        mediaUploadError = null
        if (screen == OwnerScreen.MEDIA_MANAGE) screen = OwnerScreen.MEDIA
        notice = "Upload confirmed by the server."
    }
    fun openLocation() = run {
        location = try { repository.location(requireNotNull(selected).id) }
            catch (e: HttpException) { if (e.code() == 404) null else throw e }
        screen = OwnerScreen.LOCATION
    }
    fun saveLocation(area: String, latitude: String, longitude: String) {
        val lat = latitude.toBigDecimalOrNull(); val lng = longitude.toBigDecimalOrNull()
        if (area.isBlank() || area.trim().length > 150 || lat == null || lng == null
            || lat < BigDecimal("-5") || lat > BigDecimal("5.5") || lng < BigDecimal("33") || lng > BigDecimal("42.5")) {
            notice = "Enter a public neighborhood or area label and select a supported property pin."; return
        }
        run { location = repository.saveLocation(requireNotNull(selected).id, OwnerLocation(area.trim(), lat, lng)); listingReview = null; screen = OwnerScreen.PREVIEW; notice = "Private location saved." }
    }
    fun saveMapLocation(area: String, latitude: Double, longitude: Double) {
        if (!validOwnerPin(latitude, longitude)) {
            notice = "Choose a pin inside the supported Kenya map area."; return
        }
        saveLocation(area, BigDecimal.valueOf(latitude).toPlainString(), BigDecimal.valueOf(longitude).toPlainString())
    }
    fun submitListingReview() = run {
        listingReview = repository.submitReview(requireNotNull(selected).id)
        screen = OwnerScreen.CHECKS
        notice = "Listing submitted for staff review."
    }
    fun reviseListingReview() = run {
        val id=requireNotNull(selected).id
        listingReview = repository.reviseReview(id)
        listings = repository.listings()
        selected = listings.first { it.id == id }
        form = ListingForm.from(requireNotNull(selected))
        screen = OwnerScreen.EDIT
        notice = "Update the listing, then submit it for review again."
    }
    fun listingAction(action: String) {
        if (action == "publish" && listingReview?.moderationStatus != "APPROVED") {
            notice = "Staff approval is required before publication."
            return
        }
        run {
        selected = repository.transition(requireNotNull(selected).id, action)
        listingReview = repository.review(requireNotNull(selected).id)
        listings = repository.listings()
        screen = OwnerScreen.CHECKS
        notice = when(action) { "pause" -> "Listing paused. It is hidden from discovery."; "resume" -> "Published listing resumed."; else -> "Published. Exact coordinates remain protected by the server." }
        }
    }
    fun viewApplication(application: OwnerApplication) {
        if (!busy) { selectedApplication = application; notice = null; screen = OwnerScreen.APPLICATION; loadBookings() }
    }
    fun loadBookings() = run {
        bookings = try { repository.bookings() } catch (e: HttpException) { emptyList() }
        testDestination = try { repository.testDestination() } catch (e: HttpException) { testDestination }
    }
    fun confirmBookingTerms(id: String) = run {
        repository.confirmBookingTerms(id); bookings = repository.bookings(); notice = "Booking terms confirmed."
    }
    fun confirmBookingMoveIn(id: String) = run {
        repository.confirmBookingMoveIn(id, java.util.UUID.randomUUID().toString()); bookings = repository.bookings()
        notice = "Thanks! We asked your renter to confirm. Your payment is released once they answer True."
    }
    fun viewListingApplications(id: String) { if (!busy) { go(OwnerScreen.APPLICATIONS); applicationListingFilter = id } }
    fun hideApplication(id: String, hidden: Boolean) = run { repository.hide(id, hidden); applicationNotes = repository.notes() }
    fun saveApplicationRemark(id: String, value: String) = run { repository.remark(id, value); applicationNotes = repository.notes(); notice = "Remarks saved." }
    fun decideApplication(value: OwnerApplication, action: String, reason: String? = null) = run {
        if (value.status == "SUBMITTED") repository.decide(value.id, "review")
        try { repository.decide(value.id, action, reason) } finally { applications = repository.applications() }
    }
    fun openApplicationChat(value: OwnerApplication) = run {
        val selected = try { repository.conversation(value.id) } catch (e: HttpException) {
            if (e.code() == 404) { notice = "No conversation is available for this application."; return@run }; throw e
        }
        openChat(selected)
    }
    fun decision(action: String, reason: String? = null) = run {
        selectedApplication = repository.decide(requireNotNull(selectedApplication).id, action, reason)
        applications = repository.applications()
        notice = "Application ${requireNotNull(selectedApplication).status.lowercase().replace('_',' ')}. The server recorded the decision."
    }
    fun loadThreads(page: Int = 0) = run { loadInbox(page) }
    /** Quiet refresh for the tab badges; never shows a spinner or an error. */
    fun refreshBadges() { live.start(); if (busy) return; viewModelScope.launch {
        try {
            applications = repository.applications()
            // An open application follows its latest status without leaving the screen.
            if (screen == OwnerScreen.APPLICATION) selectedApplication?.let { cur -> applications.firstOrNull { it.id == cur.id }?.let { selectedApplication = it } }
            when (screen) {
                OwnerScreen.VERIFICATION -> if (verificationPendingId == null && verificationTicket == null) {
                    verification = repository.verification(); verificationDocuments = repository.verificationDocuments()
                }
                OwnerScreen.HOME -> { verification = repository.verification(); summary = repository.summary(); listings = repository.listings() }
                OwnerScreen.LISTINGS -> listings = repository.listings()
                else -> Unit
            }
            if (screen != OwnerScreen.MESSAGES && screen != OwnerScreen.CHAT) loadInbox(0)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    } }
    fun openThread(value: OwnerThread) = run { openChat(value) }
    /** The chat opens at once; the live connection and the message history load side by side. */
    private suspend fun openChat(value: OwnerThread) {
        thread = value; messages = null; screen = OwnerScreen.CHAT; awaitingEcho = false
        chatReady = value.status == "OPEN" && live.isReady(ChatChannel.RENTAL)
        if (value.status == "OPEN") live.start()
        val page = repository.messages(value.id)
        if (thread?.id != value.id) return
        messages = OwnerMessagePage(ChatStates.afterDelete((messages?.items.orEmpty() + page.items).distinctBy { it.id }, stateOf(value)), page.nextCursor, page.hasNext)
    }
    fun olderMessages() = run {
        val current = messages ?: return@run
        if (!current.hasNext) return@run
        val next = repository.messages(requireNotNull(thread).id, current.nextCursor)
        messages = OwnerMessagePage(ChatStates.afterDelete((current.items + next.items).distinctBy { it.id }, thread?.let { stateOf(it) }), next.nextCursor, next.hasNext)
    }
    fun acknowledgeMessages(ids: List<String>) = thread?.id?.let { live.socket(ChatChannel.RENTAL)?.acknowledge(it, ids) } == true
    fun reconnectChat() { chatReady = false; live.restart() }
    /** Every message from the live connection lands here, whichever screen is showing. */
    private fun liveEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Connected -> if (thread != null) chatReady = true
            is ChatEvent.Unavailable -> { chatReady = false; awaitingEcho = false }
            is ChatEvent.Message -> if (screen == OwnerScreen.CHAT && event.message.threadId == thread?.id) {
                val current = messages ?: OwnerMessagePage(emptyList(), null, false)
                messages = OwnerMessagePage((listOf(event.message) + current.items.filter { it.id != event.message.clientMessageId }).distinctBy { it.id }.take(500), current.nextCursor, current.hasNext)
            } else refreshThreads()
        }
    }
    /** A message arrived for a chat that is not open: refresh the inbox list and unread badge quietly. */
    private fun refreshThreads() {
        threadRefresh?.cancel()
        threadRefresh = viewModelScope.launch {
            delay(300)
            try { loadInbox(0) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
    fun sendMessage() {
        val text = chatDraft.trim()
        if (text.isEmpty() || text.length > 2000) return
        val threadId = thread?.id ?: return
        val id = UUID.randomUUID().toString()
        if (live.socket(ChatChannel.RENTAL)?.send(threadId, id, text) == true) {
            val current = messages ?: OwnerMessagePage(emptyList(), null, false)
            messages = OwnerMessagePage(listOf(OwnerMessage(id, threadId, "__me__", id, text, false, java.time.Instant.now().toString())) + current.items, current.nextCursor, current.hasNext)
            chatDraft = ""; clientMessageId = null
        } else { notice = "Message not sent. Reconnecting…" }
    }
    fun changeChatDraft(value: String) {
        if (awaitingEcho) return
        if (value != chatDraft) clientMessageId = null
        chatDraft = value.take(2000)
    }
    /** Leaving a chat screen only resets the screen state; the live connection stays open. */
    fun disconnectChat() { chatReady = false; awaitingEcho = false }
    fun loadPayments(page: Int = 0) = run {
        payments = repository.payments(page)
        testDestination = repository.testDestination()
    }
    fun configureTestDestination(phone: String) {
        val local = localKenyanPhone(phone)
        if (local == null) { notice = "Enter a Kenyan M-Pesa number in 07XXXXXXXX or 01XXXXXXXX format."; return }
        run {
            testDestination = repository.configureTestDestination(local, UUID.randomUUID().toString())
            notice = "Payout number saved."
        }
    }
    fun openCheckout(type: String, listingId: String? = null) {
        if (busy) return
        require(type == "PLAN_UPGRADE" && listingId == null || type == "BOOST" && !listingId.isNullOrBlank())
        checkoutType = type; checkoutListingId = listingId; checkoutKey = UUID.randomUUID().toString(); checkout = null
        notice = null; screen = if (type == "BOOST") OwnerScreen.BOOST else OwnerScreen.PLANS
    }
    fun createCheckout() = run {
        if (!BuildConfig.MOCK_PAYMENTS_ENABLED) { notice = "Checkout is unavailable right now."; return@run }
        checkout = repository.checkout(checkoutType, checkoutListingId, checkoutKey)
    }
    fun finishMock(outcome: String) = run {
        if (!BuildConfig.MOCK_PAYMENTS_ENABLED) return@run
        val reference = checkout?.providerReference ?: return@run
        checkout = repository.complete(reference, outcome)
        notice = "Payment ${checkout!!.status.lowercase()}."
    }
    fun dismissNotice() { notice = null }
    fun showNotice(value: String) { notice = value }
    // Short-lived in-memory cache: switching back to a tab within 15 seconds shows what is already loaded
    // instead of waiting for the server again. Any other action clears it so changes always show up.
    private val loadedAt = HashMap<String, Long>()
    private fun isFresh(key: String) = System.currentTimeMillis() - (loadedAt[key] ?: 0L) < 15_000
    private fun markFresh(key: String) { loadedAt[key] = System.currentTimeMillis() }

    private fun run(keepCache: Boolean = false, action: suspend () -> Unit) {
        if (busy) return
        if (!keepCache) loadedAt.clear()
        busy = true; notice = null
        work = viewModelScope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) {
                if (e is SessionExpired || e is WorkspaceUnavailable) { clearPrivateState(); sessionExpired() }
                if (screen in listOf(OwnerScreen.MEDIA, OwnerScreen.MEDIA_MANAGE)
                    && (upload != null || e is MediaTransferFailed)) {
                    mediaUploadError = "This photo could not be uploaded. Retry or replace it; your other media is saved."
                }
                notice = if (e is VerificationImageRejected || e is VerificationPdfRejected) e.message
                    else if (e is MediaTransferFailed && screen == OwnerScreen.VERIFICATION) "Document upload did not complete. Retry the current document; no approval has been granted."
                    else if (e is MediaTransferFailed) "Media upload was not completed. Your other saved media is unchanged. Select the same file to retry."
                    else if (e is HttpException) when(e.code()) {
                        400 -> "The server rejected these details. Check the file, required fields and permitted action."
                        403 -> "Your role, verification or ownership does not permit this action."
                        404 -> "The requested listing, file or application is unavailable to this account."
                        409 -> "This action conflicts with the saved state. Refresh and check the publish or decision requirements."
                        503 -> if (screen == OwnerScreen.VERIFICATION && verificationPendingId != null) "ID checking is unavailable right now. Nothing has been approved. Please try again in a few minutes." else friendlyError(e)
                        else -> friendlyError(e)
                    }
                    else friendlyError(e)
            } finally { busy = false; evidenceStage = null }
        }
    }
    fun clearPrivateState() {
        generation++; work?.cancel(); work = null; busy = false
        verificationBytes?.fill(0)
        verification = null; verificationDocuments = emptyList(); verificationTicket = null; verificationBytes = null
        verificationTransferred = false; verificationPendingId = null; evidenceProgress = null
        evidencePreviews.values.forEach { it.fill(0) }; evidencePreviews.clear(); evidenceNames.clear(); evidenceStage = null
        live.stop(); disconnectChat(); location = null; media = emptyList(); selectedMediaId = null; mediaUploadError = null; localPhotos.clear(); upload = null; uploadUri = null
        uploaded = false; replacingMediaId = null; pendingConfirmationId = null; summary = null; threads = null; chatStates = emptyMap(); showArchived = false; applicationsLoaded = false
        thread = null; messages = null; chatDraft = ""; clientMessageId = null; checkout = null; payments = null
        listings = emptyList(); applications = emptyList(); applicationNotes = emptyList(); applicationListingFilter = null; cardMetrics = emptyMap(); selected = null; listingReview = null; loadedAt.clear(); selectedApplication = null; form = ListingForm()
        checkoutListingId = null; checkoutKey = UUID.randomUUID().toString(); checkoutType = "PLAN_UPGRADE"
        notice = null; screen = OwnerScreen.HOME
    }
    override fun onCleared() { clearPrivateState() }
}
