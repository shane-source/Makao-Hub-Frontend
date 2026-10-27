package com.makaohub.android

import com.google.gson.JsonParser
import com.makaohub.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import okhttp3.RequestBody.Companion.toRequestBody

/** Retrofit contract tests use a local server; no users, SMS, storage or payments are changed. */
class LandlordContractTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: AuthRepository
    private lateinit var owner: LandlordRepository
    private val listing = """{"id":"listing-1","title":"Sunlit apartment","description":"A quiet whole unit","propertyType":"APARTMENT","monthlyRentKes":45000,"depositKes":45000,"bedroomCount":2,"bathroomCount":2,"waterAvailable":true,"securityAvailable":true,"parkingAvailable":true,"balcony":true,"furnished":false,"petsAllowed":false,"status":"DRAFT","availableFrom":"2026-11-01","createdAt":"2026-10-13T12:00:00Z","updatedAt":"2026-10-13T12:00:00Z"}"""
    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }
    private suspend fun session(role: String = "LANDLORD") {
        respond("""{"accessToken":"test-access","refreshToken":"test-refresh","role":"$role","userId":"owner-1"}""")
        auth.verify("challenge", "123456", OtpFlow.LOGIN, SignupRole.valueOf(role))
        server.takeRequest()
    }
    @Before fun setup() {
        server = MockWebServer(); server.start()
        auth = AuthRepository(createMakaoApi(server.url("/").toString()))
        owner = LandlordRepository(auth)
    }
    @After fun close() { server.shutdown() }

    @Test fun draftUsesBackendFieldsIntegerKesAndTheAuthenticatedOwner() = runBlocking {
        session(); respond(listing, 201)
        val result = owner.save(null, ListingDraft("Sunlit apartment", "A quiet whole unit", "APARTMENT", 45000, 2, 2, false,
            "2026-11-01", 45000, waterAvailable = true, securityAvailable = true, parkingAvailable = true, balcony = true))
        assertEquals("listing-1", result.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method); assertEquals("/api/v1/landlord/listings/drafts", request.path)
        assertEquals("Bearer test-access", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(45000L, body["monthlyRentKes"].asLong)
        assertEquals(45000L, body["depositKes"].asLong)
        assertEquals(setOf("title", "description", "propertyType", "monthlyRentKes", "bedroomCount", "bathroomCount",
            "furnished", "availableFrom", "depositKes", "waterAvailable", "securityAvailable", "parkingAvailable",
            "balcony", "petsAllowed"), body.keySet())
        assertFalse(body.has("landlordId"))
    }

    @Test fun privateLocationAndDestructiveActionsUseAuthenticatedOwnedRoutes() = runBlocking {
        session()
        respond("""{"areaLabel":"Kilimani, Nairobi","latitude":-1.2921,"longitude":36.7824}""")
        owner.saveLocation("listing-1", OwnerLocation("Kilimani, Nairobi", BigDecimal("-1.2921"), BigDecimal("36.7824")))
        val request = server.takeRequest()
        assertEquals("PUT", request.method); assertEquals("/api/v1/landlord/listings/listing-1/location", request.path)
        assertFalse(request.path!!.contains("36.7824"))
        assertEquals("Bearer test-access", request.getHeader("Authorization"))
        assertEquals(BigDecimal("-1.2921"), JsonParser.parseString(request.body.readUtf8()).asJsonObject["latitude"].asBigDecimal)
        server.enqueue(MockResponse().setResponseCode(204))
        owner.remove("listing-1", "media-1")
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method); assertEquals("/api/v1/landlord/listings/listing-1/media/media-1", delete.path)
        server.enqueue(MockResponse().setResponseCode(204))
        owner.deleteDraft("listing-1")
        val deleteDraft = server.takeRequest()
        assertEquals("DELETE", deleteDraft.method); assertEquals("/api/v1/landlord/listings/listing-1", deleteDraft.path)
        assertEquals("Bearer test-access", deleteDraft.getHeader("Authorization"))
    }

    @Test fun renterCannotCallLandlordApiAndFailedAccessDoesNotInventListings() = runBlocking {
        session("RENTER")
        try { owner.listings(); fail("Wrong active role must be rejected") } catch (_: WorkspaceUnavailable) { }
        assertEquals(1, server.requestCount)
        session(); respond("{}", 403)
        try { owner.listings(); fail("Server permission failure must be preserved") }
        catch (e: retrofit2.HttpException) { assertEquals(403, e.code()) }
    }

    @Test fun ownerWritesRefreshOnlyAfterExplicitUnauthorizedResponse() = runBlocking {
        session(); respond("{}", 401)
        respond("""{"accessToken":"new-access","refreshToken":"new-refresh","role":"LANDLORD","userId":"owner-1"}""")
        respond(listing)
        owner.transition("listing-1", "publish")
        assertEquals("/api/v1/landlord/listings/listing-1/publish", server.takeRequest().path)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals(4, server.requestCount)
    }

    @Test fun decisionsUseOnlyApplicationIdAndPaymentsUseStableKeysAndServerPrices() = runBlocking {
        session()
        respond("""{"id":"application-1","listingId":"listing-1","status":"APPROVED","createdAt":"2026-10-13T12:00:00Z","updatedAt":"2026-10-13T12:00:00Z"}""")
        assertEquals("APPROVED", owner.decide("application-1", "approve").status)
        assertEquals("/api/v1/landlord/applications/application-1/approve", server.takeRequest().path)
        respond("""{"id":"application-2","listingId":"listing-1","status":"REJECTED","rejectionReason":"Move-in date is unavailable.","createdAt":"2026-10-13T12:00:00Z","updatedAt":"2026-10-13T12:00:00Z"}""")
        assertEquals("Move-in date is unavailable.", owner.decide("application-2", "reject", "Move-in date is unavailable.").rejectionReason)
        val rejection = server.takeRequest()
        assertEquals("/api/v1/landlord/applications/application-2/reject", rejection.path)
        assertEquals("Move-in date is unavailable.", JsonParser.parseString(rejection.body.readUtf8()).asJsonObject["reason"].asString)
        val receipt = """{"id":"payment-1","paymentType":"BOOST","targetKind":"LANDLORD_LISTING","listingId":"listing-1","amountMinor":50000,"currency":"KES","status":"PENDING","providerReference":"test-provider-reference","createdAt":"2026-10-13T12:00:00Z"}"""
        respond(receipt)
        assertEquals(50000L, owner.checkout("BOOST", "listing-1", "stable-attempt-key").amountMinor)
        val request = server.takeRequest()
        assertEquals("stable-attempt-key", request.getHeader("Idempotency-Key"))
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertFalse(json.has("amountMinor")); assertFalse(json.has("payerUserId"))
        respond(receipt.replace("PENDING", "SUCCEEDED"))
        assertEquals("SUCCEEDED", owner.complete("test-provider-reference", "SUCCESS").status)
        assertEquals("/api/v1/payments/mock/completions", server.takeRequest().path)
    }

    @Test fun historiesRequestBoundedPagesAndKeepMaskedMessageContent() = runBlocking {
        session()
        respond("""{"items":[],"page":2,"size":20,"totalElements":42,"totalPages":3,"hasNext":false}""")
        assertFalse(owner.payments(2).hasNext)
        assertEquals("/api/v1/payments?page=2&size=20", server.takeRequest().path)
        respond("""{"items":[{"id":"message-1","threadId":"thread-1","senderId":"renter-1","clientMessageId":"client-1","content":"Contact [masked]","contactMasked":true,"createdAt":"2026-10-13T12:00:00Z"}],"nextCursor":null,"hasNext":false}""")
        assertTrue(owner.messages("thread-1", "older-cursor").items.single().contactMasked)
        assertEquals("/api/v1/landlord/rental-chat/threads/thread-1/messages?cursor=older-cursor&limit=50", server.takeRequest().path)
    }

    @Test fun formRejectsUnsupportedFieldsAndInvalidMoneyWithoutRounding() {
        val good = ListingForm("Sunlit apartment", "A quiet whole unit", rent = "45000", deposit = "45000", available = "2026-11-01")
        assertNull(good.error())
        assertNotNull(good.copy(rent = "45000.50").error())
        assertNotNull(good.copy(rent = "9223372036854775808").error())
        assertNotNull(good.copy(deposit = "-1").error())
        assertNotNull(good.copy(bedrooms = "21").error())
        assertNotNull(good.copy(bathrooms = "0").error())
        assertNotNull(good.copy(available = "2026-02-30").error())
        assertNotNull(good.copy(propertyType = "SUBLESSOR_ROOM").error())
    }

    @Test fun mediaPositionsReuseGapsWithoutCollidingAfterDeletion() {
        fun record(order: Int) = OwnerMedia("media-$order", "listing-1", "IMAGE", "image/jpeg", 20, order, "2026-10-13T12:00:00Z")
        assertEquals(0, nextMediaSortOrder(emptyList()))
        assertEquals(1, nextMediaSortOrder(listOf(record(0), record(2))))
        assertEquals(0, nextMediaSortOrder(listOf(record(1), record(2))))
    }

    @Test fun signedUploadsNeverReceiveMakaoAuthorizationOrAcceptUntrustedDestinations() {
        fun ticket(url: String) = MediaUploadTicket("media-1", "IMAGE", "image/jpeg", 20, 0, url)
        val request = signedMediaRequest(ticket("https://project.supabase.co/storage/v1/object/upload/sign/media/file?token=synthetic"), byteArrayOf(1).toRequestBody())
        assertEquals("PUT", request.method)
        assertNull(request.header("Authorization"))
        for (url in listOf("http://project.supabase.co/storage/v1/object/upload/sign/media/file",
            "https://project.supabase.co.evil.invalid/storage/v1/object/upload/sign/media/file",
            "https://project.supabase.co/not-an-upload")) {
            try { signedMediaRequest(ticket(url), byteArrayOf(1).toRequestBody()); fail("Untrusted destination must be rejected") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun verificationUsesOwnedRoutesAndNeverApprovesLocally() = runBlocking {
        session()
        respond("""{"status":"NOT_STARTED"}""")
        assertEquals("NOT_STARTED", owner.verification().status)
        assertEquals("/api/v1/landlord/verification", server.takeRequest().path)
        respond("""[{"documentId":"document-1","documentType":"ID_FRONT","status":"READY"}]""")
        assertFalse(verificationReady(owner.verificationDocuments()))
        assertEquals("/api/v1/landlord/verification/documents", server.takeRequest().path)
        respond("""{"documentId":"document-2","documentType":"ID_BACK","contentType":"image/jpeg","uploadUrl":"https://project.supabase.co/storage/v1/object/upload/sign/docs/file?token=synthetic"}""")
        val ticket = owner.verificationTicket(OwnerEvidence.ID_BACK)
        assertEquals("document-2", ticket.documentId)
        val request = server.takeRequest()
        assertEquals("Bearer test-access", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("documentType", "contentType", "replaceExisting"), body.keySet())
        assertEquals("ID_BACK", body["documentType"].asString)
        server.enqueue(MockResponse().setResponseCode(204))
        owner.confirmVerification(ticket.documentId)
        assertEquals("/api/v1/landlord/verification/documents/document-2/confirm", server.takeRequest().path)
        respond("""{"status":"PENDING","submittedAt":"2026-10-14T12:00:00Z"}""")
        assertEquals("PENDING", owner.submitVerification().status)
        assertEquals("/api/v1/landlord/verification/submit", server.takeRequest().path)
    }

    @Test fun verificationRequiresAllFourReadyTypesAndRejectsNonOwnerSessions() = runBlocking {
        val ready = OwnerEvidence.entries.map { OwnerVerificationDocument(it.name, it.name, "READY", it.uploadContentType, securityChecked = true) }
        assertTrue(verificationReady(ready))
        assertFalse(verificationReady(ready.dropLast(1)))
        assertFalse(verificationReady(ready.map { OwnerVerificationDocument(it.documentId, it.documentType, "UPLOADING", it.contentType) }))
        assertFalse(verificationReady(ready.map { OwnerVerificationDocument(it.documentId, it.documentType, "READY", "image/jpeg") }))
        session("RENTER")
        try { owner.verification(); fail("Wrong role must not request verification data") } catch (_: WorkspaceUnavailable) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun verificationTransferOmitsJwtAndMapPinsRejectInvalidValues() {
        assertTrue(validVerificationImageDimensions(4000, 3000))
        assertFalse(validVerificationImageDimensions(0, 3000))
        assertFalse(validVerificationImageDimensions(-1, 3000))
        assertFalse(validVerificationImageDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(validVerificationImageDimensions(10000, 10000))
        val ticket = VerificationUploadTicket("document-1", "ID_FRONT", "image/jpeg",
            "https://project.supabase.co/storage/v1/object/upload/sign/docs/file?token=synthetic")
        assertNull(signedVerificationRequest(ticket, byteArrayOf(1).toRequestBody()).header("Authorization"))
        assertTrue(validOwnerPin(-1.2921, 36.8219))
        assertFalse(validOwnerPin(Double.NaN, 36.8219))
        assertFalse(validOwnerPin(-1.2921, Double.POSITIVE_INFINITY))
        assertFalse(validOwnerPin(51.5, -0.12))
        for (url in listOf("http://project.supabase.co/storage/v1/object/upload/sign/docs/file",
            "https://project.supabase.co.evil.invalid/storage/v1/object/upload/sign/docs/file",
            "https://user:pass@project.supabase.co/storage/v1/object/upload/sign/docs/file")) {
            try { signedVerificationRequest(VerificationUploadTicket("id", "SELFIE", "image/jpeg", url), byteArrayOf(1).toRequestBody())
                fail("Untrusted storage URL") } catch (_: IllegalArgumentException) { }
        }
    }
}
