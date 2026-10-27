package com.makaohub.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

data class SharedListing(val id: String, val title: String, val description: String, val spaceType: String,
    val monthlyRentKes: Long, val bathroomCount: Int, val furnished: Boolean, val availableFrom: String?,
    val status: String, val createdAt: String, val updatedAt: String)
class SharedListingRequest(val title: String, val description: String, val spaceType: String,
    val monthlyRentKes: Long, val bathroomCount: Int, val furnished: Boolean, val availableFrom: String?)
data class SharedInterest(val id: String, val sublessorListingId: String, val status: String, val createdAt: String, val updatedAt: String)
data class SharedThread(val id: String, val roommateInterestId: String, val status: String, val chatUnlocked: Boolean,
    val createdAt: String, val updatedAt: String, val closedAt: String?)
data class SharedThreadPage(val content: List<SharedThread>, val number: Int, val totalPages: Int, val last: Boolean)
data class SharedMessagePage(val items: List<OwnerMessage>, val nextCursor: String?, val hasMore: Boolean)
class SharedHousehold(val depositKes: Long?, val currentHousehold: String?, val totalOccupants: Int?, val peoplePerRoom: Int?, val lifestyle: String?, val amenities: Set<String>)
class SharedPreferences(val quietSocial: String, val sleepSchedule: String, val cleanliness: String, val cooking: String, val guests: String,
    val smoking: String, val pets: String, val budgetMinKes: Long, val budgetMaxKes: Long, val discoveryOptIn: Boolean,
    val areaLabel: String? = null, val about: String? = null, val employment: String? = null, val moveInDate: String? = null)
class SharedPublicProfile(val userId: String, val displayName: String, val preferences: SharedPreferences)
class SharedDiscoveryPage(val items: List<SharedPublicProfile>, val page: Int, val size: Int, val totalElements: Long, val hasNext: Boolean)
class SharedRejection(val reason: String)

enum class SharedEvidence(val title: String, val mime: String) {
    ID_FRONT("National ID front", "image/jpeg"), ID_BACK("National ID back", "image/jpeg"),
    SELFIE("Live photo", "image/jpeg"), RENTAL_AGREEMENT("Rental agreement", "application/pdf")
}
internal fun sharedEvidenceReady(type: SharedEvidence, documents: List<OwnerVerificationDocument>) =
    documents.any { it.documentType == type.name && it.status == "READY" && it.securityChecked
        && (type != SharedEvidence.RENTAL_AGREEMENT || it.contentType == "application/pdf") }
internal fun sharedVerificationReady(documents: List<OwnerVerificationDocument>) = SharedEvidence.entries.all { sharedEvidenceReady(it, documents) }

/** All identities come from the shared authenticated session, never a Sublessor ID supplied by a form. */
class SublessorRepository(private val auth: AuthRepository) {
    suspend fun verification() = auth.authorized("SUBLESSOR") { api, h -> api.sublessorVerification(h) }
    suspend fun documents() = auth.authorized("SUBLESSOR") { api, h -> api.sublessorDocuments(h) }
    suspend fun ticket(type: SharedEvidence, replace: Boolean = false) = auth.authorized("SUBLESSOR") { api, h ->
        api.sublessorEvidenceTicket(h, VerificationUploadRequest(type.name, type.mime, replace)) }
    suspend fun confirmEvidence(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.confirmSublessorEvidence(h, id).requireSuccess() }
    suspend fun submitVerification() = auth.authorized("SUBLESSOR") { api, h -> api.submitSublessorVerification(h) }
    suspend fun listings() = auth.authorized("SUBLESSOR") { api, h -> api.sublessorListings(h) }
    suspend fun save(id: String?, body: SharedListingRequest) = auth.authorized("SUBLESSOR") { api, h ->
        if (id == null) api.createSharedListing(h, body) else api.updateSharedListing(h, id, body) }
    suspend fun transition(id: String, action: String) = auth.authorized("SUBLESSOR") { api, h ->
        require(action in listOf("publish", "pause", "resume", "archive")); api.sharedListingAction(h, id, action) }
    suspend fun review(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.sublessorListingReview(h, id) }
    suspend fun submitReview(id: String) = auth.authorized("SUBLESSOR") { api, h ->
        val response=api.submitSublessorListingReview(h,id)
        if (!response.isSuccessful) throw HttpException(response)
        api.sublessorListingReview(h,id)
    }
    suspend fun reviseReview(id: String) = auth.authorized("SUBLESSOR") { api, h ->
        val response=api.reviseSublessorListingReview(h,id)
        if (!response.isSuccessful) throw HttpException(response)
        api.sublessorListingReview(h,id)
    }
    suspend fun location(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.sharedLocation(h, id) }
    suspend fun saveLocation(id: String, value: OwnerLocation) = auth.authorized("SUBLESSOR") { api, h -> api.saveSharedLocation(h, id, value) }
    suspend fun household(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.sharedHousehold(h, id) }
    suspend fun saveHousehold(id: String, value: SharedHousehold) = auth.authorized("SUBLESSOR") { api, h -> api.saveSharedHousehold(h, id, value) }
    suspend fun preferences() = auth.authorized("SUBLESSOR") { api, h -> api.sharedPreferences(h) }
    suspend fun savePreferences(value: SharedPreferences) = auth.authorized("SUBLESSOR") { api, h -> api.saveSharedPreferences(h, value) }
    suspend fun discovery(page: Int = 0) = auth.authorized("SUBLESSOR") { api, h -> api.sharedDiscovery(h, page) }
    suspend fun renterProfile(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.sharedRenterProfile(h, id) }
    suspend fun media(id: String) = auth.authorized("SUBLESSOR") { api, h -> api.sharedMedia(h, id) }
    suspend fun mediaTicket(id: String, body: MediaUploadRequest) = auth.authorized("SUBLESSOR") { api, h -> api.sharedMediaTicket(h, id, body) }
    suspend fun confirmMedia(id: String, media: String) = auth.authorized("SUBLESSOR") { api, h -> api.confirmSharedMedia(h, id, media) }
    suspend fun deleteMedia(id: String, media: String) = auth.authorized("SUBLESSOR") { api, h -> api.deleteSharedMedia(h, id, media).requireSuccess() }
    suspend fun applications() = auth.authorized("SUBLESSOR") { api, h -> api.sharedApplications(h) }
    suspend fun decide(id: String, action: String, reason: String? = null) = auth.authorized("SUBLESSOR") { api, h ->
        require(action in listOf("review", "approve", "reject"))
        if (action == "reject") { require(!reason.isNullOrBlank() && reason.length <= 500); api.rejectSharedApplication(h, id, SharedRejection(reason.trim())) }
        else api.sharedApplicationAction(h, id, action) }
    suspend fun interests() = auth.authorized("SUBLESSOR") { api, h -> api.roommateInterests(h) }
    suspend fun interestAction(id: String, action: String) = auth.authorized("SUBLESSOR") { api, h ->
        require(action in listOf("review", "accept", "decline")); api.roommateInterestAction(h, id, action) }
    suspend fun threads(page: Int) = auth.authorized("SUBLESSOR") { api, h -> api.roommateThreads(h, page) }
    suspend fun messages(id: String, cursor: String? = null) = auth.authorized("SUBLESSOR") { api, h -> api.roommateMessages(h, id, cursor) }
    suspend fun payments(page: Int) = auth.authorized("SUBLESSOR") { api, h -> api.ownerPayments(h, page) }
    suspend fun connect(events: (ChatEvent) -> Unit): RentalChatSocket = auth.authorized("SUBLESSOR") { _, h ->
        RentalChatSocket(com.makaohub.android.BuildConfig.API_BASE_URL, h, ChatChannel.ROOMMATE, events).also { it.connect() } }

    private val transferClient = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    suspend fun transfer(ticket: MediaUploadTicket, body: RequestBody) = withContext(Dispatchers.IO) {
        transferClient.newCall(signedMediaRequest(ticket, body)).execute().use { if (!it.isSuccessful) throw MediaTransferFailed() }
    }
    suspend fun transferEvidence(ticket: VerificationUploadTicket, body: RequestBody) = withContext(Dispatchers.IO) {
        transferClient.newCall(signedVerificationRequest(ticket, body)).execute().use { if (!it.isSuccessful) throw MediaTransferFailed() }
    }
    private fun retrofit2.Response<Unit>.requireSuccess() { if (!isSuccessful) throw HttpException(this) }
}
