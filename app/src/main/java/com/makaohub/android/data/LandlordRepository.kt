package com.makaohub.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

data class LandlordSummary(val totalListings: Long, val draftListings: Long, val publishedListings: Long)
data class OwnerListing(val id: String, val title: String, val description: String, val propertyType: String,
    val monthlyRentKes: Long, val bedroomCount: Int, val bathroomCount: Int, val furnished: Boolean,
    val availableFrom: String?, val status: String, val createdAt: String, val updatedAt: String)
class ListingDraft(val title: String, val description: String, val propertyType: String,
    val monthlyRentKes: Long, val bedroomCount: Int, val bathroomCount: Int, val furnished: Boolean,
    val availableFrom: String?)
// Private location and signed URL models have no generated toString().
class OwnerLocation(val areaLabel: String, val latitude: BigDecimal, val longitude: BigDecimal)
data class OwnerMedia(val id: String, val listingId: String, val mediaType: String, val contentType: String,
    val sizeBytes: Long, val sortOrder: Int, val createdAt: String, val confirmed: Boolean = false)
class MediaUploadRequest(val mediaType: String, val contentType: String, val sizeBytes: Long, val sortOrder: Int)
class MediaUploadTicket(val mediaId: String, val mediaType: String, val contentType: String,
    val sizeBytes: Long, val sortOrder: Int, val uploadUrl: String)
data class OwnerApplication(val id: String, val listingId: String, val status: String, val createdAt: String, val updatedAt: String, val rejectionReason: String? = null)
data class OwnerPage<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long, val totalPages: Int, val hasNext: Boolean)
data class OwnerThread(val id: String, val rentalApplicationId: String, val status: String,
    val createdAt: String, val updatedAt: String, val closedAt: String?)
data class OwnerMessage(val id: String, val threadId: String, val senderId: String,
    val clientMessageId: String, val content: String, val contactMasked: Boolean, val createdAt: String)
data class OwnerMessagePage(val items: List<OwnerMessage>, val nextCursor: String?, val hasNext: Boolean)
class OwnerPayment(val id: String, val paymentType: String, val targetKind: String, val listingId: String?,
    val bookingId: String?, val amountMinor: Long, val currency: String, val status: String,
    val providerReference: String?, val createdAt: String, val completedAt: String?)
class OwnerCheckout(val paymentType: String, val listingId: String?, val bookingId: String? = null)
class MockCompletion(val providerReference: String, val outcome: String)
class MediaTransferFailed : Exception()
class OwnerVerification(val status: String, val submittedAt: String?, val reviewedAt: String?, val rejectionReason: String?)
class OwnerVerificationDocument(val documentId: String, val documentType: String, val status: String,
    val contentType: String? = null, val sizeBytes: Long? = null, val securityChecked: Boolean = false)
class VerificationUploadRequest(val documentType: String, val contentType: String, val replaceExisting: Boolean = false)
class VerificationUploadTicket(val documentId: String, val documentType: String, val contentType: String, val uploadUrl: String)

enum class OwnerEvidence(val title: String, val uploadContentType: String) {
    ID_FRONT("ID · Front", "image/jpeg"), ID_BACK("ID · Back", "image/jpeg"),
    SELFIE("Selfie photo", "image/jpeg"), OWNERSHIP_PROOF("Proof of ownership", "application/pdf")
}

internal fun validOwnerPin(latitude: Double, longitude: Double) =
    latitude.isFinite() && longitude.isFinite() && latitude in -5.0..5.5 && longitude in 33.0..42.5

internal fun verificationReady(documents: List<OwnerVerificationDocument>) =
    OwnerEvidence.entries.all { type -> evidenceReady(type, documents) }

internal fun evidenceReady(type: OwnerEvidence, documents: List<OwnerVerificationDocument>) =
    documents.any { it.documentType == type.name && it.status == "READY" && it.securityChecked
        && (type != OwnerEvidence.OWNERSHIP_PROOF || it.contentType == "application/pdf") }

internal fun signedMediaRequest(ticket: MediaUploadTicket, body: RequestBody): Request {
    return signedStorageRequest(ticket.uploadUrl, body)
}

internal fun signedVerificationRequest(ticket: VerificationUploadTicket, body: RequestBody): Request =
    signedStorageRequest(ticket.uploadUrl, body)

private fun signedStorageRequest(uploadUrl: String, body: RequestBody): Request {
    val url = uploadUrl.toHttpUrl()
    require(url.isHttps && url.host.endsWith(".supabase.co") && url.username.isEmpty() && url.password.isEmpty()
        && url.encodedPath.startsWith("/storage/v1/object/upload/sign/"))
    return Request.Builder().url(url).put(body).build()
}

/** Every owner call shares the existing JWT/refresh lock. No landlord ID comes from the UI. */
class LandlordRepository(private val auth: AuthRepository) {
    suspend fun verification() = auth.authorized("LANDLORD") { api, h -> api.landlordVerification(h) }
    suspend fun verificationDocuments() = auth.authorized("LANDLORD") { api, h -> api.landlordVerificationDocuments(h) }
    suspend fun verificationTicket(type: OwnerEvidence, replaceExisting: Boolean = false) = auth.authorized("LANDLORD") { api, h ->
        api.landlordVerificationUpload(h, VerificationUploadRequest(type.name, type.uploadContentType, replaceExisting))
    }
    suspend fun confirmVerification(id: String) = auth.authorized("LANDLORD") { api, h ->
        val response = api.confirmLandlordVerificationDocument(h, id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
    suspend fun submitVerification() = auth.authorized("LANDLORD") { api, h -> api.submitLandlordVerification(h) }
    suspend fun transferVerification(ticket: VerificationUploadTicket, body: RequestBody) = withContext(Dispatchers.IO) {
        transferClient.newCall(signedVerificationRequest(ticket, body)).execute().use {
            if (!it.isSuccessful) throw MediaTransferFailed()
        }
    }
    suspend fun summary() = auth.authorized("LANDLORD") { api, h -> api.landlordDashboard(h) }
    suspend fun listings() = auth.authorized("LANDLORD") { api, h -> api.landlordListings(h) }
    suspend fun save(id: String?, draft: ListingDraft) = auth.authorized("LANDLORD") { api, h ->
        if (id == null) api.createListing(h, draft) else api.updateListing(h, id, draft)
    }
    suspend fun transition(id: String, action: String) = auth.authorized("LANDLORD") { api, h ->
        when (action) { "publish" -> api.publishListing(h, id); "pause" -> api.pauseListing(h, id)
            "resume" -> api.resumeListing(h, id); else -> error("Unsupported listing action") }
    }
    suspend fun location(id: String) = auth.authorized("LANDLORD") { api, h -> api.ownerLocation(h, id) }
    suspend fun saveLocation(id: String, location: OwnerLocation) = auth.authorized("LANDLORD") { api, h -> api.saveLocation(h, id, location) }
    suspend fun media(id: String) = auth.authorized("LANDLORD") { api, h -> api.ownerMedia(h, id) }
    suspend fun uploadTicket(id: String, request: MediaUploadRequest) = auth.authorized("LANDLORD") { api, h -> api.mediaUpload(h, id, request) }
    suspend fun confirm(id: String, media: String) = auth.authorized("LANDLORD") { api, h -> api.confirmMedia(h, id, media) }
    suspend fun remove(id: String, media: String) = auth.authorized("LANDLORD") { api, h ->
        val result = api.deleteMedia(h, id, media)
        if (!result.isSuccessful) throw retrofit2.HttpException(result)
    }
    suspend fun applications() = auth.authorized("LANDLORD") { api, h -> api.landlordApplications(h) }
    suspend fun decide(id: String, action: String) = auth.authorized("LANDLORD") { api, h ->
        when(action) { "review" -> api.reviewApplication(h, id); "approve" -> api.approveApplication(h, id)
            "reject" -> api.rejectApplication(h, id); else -> error("Unsupported application action") }
    }
    suspend fun threads(page: Int = 0) = auth.authorized("LANDLORD") { api, h -> api.landlordThreads(h, page) }
    suspend fun messages(id: String, cursor: String? = null) = auth.authorized("LANDLORD") { api, h -> api.landlordMessages(h, id, cursor) }
    suspend fun payments(page: Int = 0) = auth.authorized("LANDLORD") { api, h -> api.ownerPayments(h, page) }
    suspend fun checkout(type: String, listingId: String?, key: String) = auth.authorized("LANDLORD") { api, h -> api.ownerCheckout(h, key, OwnerCheckout(type, listingId)) }
    suspend fun complete(reference: String, outcome: String) = auth.authorized("LANDLORD") { api, h -> api.completeOwnerMock(h, MockCompletion(reference, outcome)) }
    suspend fun connectChat(baseUrl: String, events: (ChatEvent) -> Unit): RentalChatSocket {
        auth.profile() // Refresh an expired access token before authenticating the socket.
        return auth.authorized("LANDLORD") { _, h -> RentalChatSocket(baseUrl, h, events = events).also { it.connect() } }
    }

    /** Separate client: Makao JWTs are never attached to signed Supabase upload requests. */
    suspend fun transfer(ticket: MediaUploadTicket, body: RequestBody) = withContext(Dispatchers.IO) {
        val request = signedMediaRequest(ticket, body)
        transferClient.newCall(request).execute().use { if (!it.isSuccessful) throw MediaTransferFailed() }
    }
    private val transferClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
}
