package com.makaohub.android

import com.makaohub.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LandlordVerificationFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: AuthRepository
    private lateinit var model: LandlordViewModel
    private var expired = false
    private fun respond(json: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(json))
    }
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer(); server.start()
        auth = AuthRepository(createMakaoApi(server.url("/").toString()))
        respond("""{"accessToken":"synthetic","refreshToken":"synthetic-refresh","role":"LANDLORD","userId":"owner"}""")
        auth.verify("challenge", "123456", OtpFlow.LOGIN, SignupRole.LANDLORD); server.takeRequest()
        model = LandlordViewModel(LandlordRepository(auth)) { expired = true }
    }
    @After fun close() { model.clearPrivateState(); server.shutdown(); Dispatchers.resetMain() }
    private suspend fun waitForWork() {
        withTimeout(5000) { while (model.busy) delay(10) }
    }
    @Test fun unverifiedOwnerStartsWithEvidenceAndSubmissionStaysPending() = runBlocking {
        respond("""{"status":"NOT_STARTED"}""")
        respond(OwnerEvidence.entries.joinToString(prefix = "[", postfix = "]") {
            """{"documentId":"${it.name}","documentType":"${it.name}","status":"READY","contentType":"${it.uploadContentType}","securityChecked":true}"""
        })
        model.start(); waitForWork()
        assertEquals(OwnerScreen.VERIFICATION, model.screen)
        assertEquals("/api/v1/landlord/verification", server.takeRequest().path)
        assertEquals("/api/v1/landlord/verification/documents", server.takeRequest().path)
        respond("""{"status":"PENDING"}""")
        model.submitVerification(); waitForWork()
        assertEquals("PENDING", model.verificationStatus("VERIFIED"))
        assertEquals("/api/v1/landlord/verification/submit", server.takeRequest().path)
        assertFalse(expired)
    }
    @Test fun missingEvidenceCannotBeSubmittedAndLogoutClearsReadiness() = runBlocking {
        respond("""{"status":"NOT_STARTED"}"""); respond("[]")
        model.start(); waitForWork()
        model.submitVerification(); waitForWork()
        assertEquals(3, server.requestCount) // Includes the synthetic authentication setup request.
        assertTrue(model.notice!!.contains("ID front"))
        model.clearPrivateState()
        assertNull(model.verification)
        assertTrue(model.verificationDocuments.isEmpty())
        assertNull(model.verificationPendingId)
        assertTrue(model.evidenceNames.isEmpty())
    }
    @Test fun verifiedOwnerOpensTheExistingDashboard() = runBlocking {
        respond("""{"status":"VERIFIED"}""")
        respond("""{"totalListings":0,"draftListings":0,"publishedListings":0}""")
        respond("[]"); respond("[]")
        model.start(); waitForWork()
        assertEquals(OwnerScreen.HOME, model.screen)
        assertEquals("VERIFIED", model.verificationStatus(null))
        assertEquals(5, server.requestCount) // Includes the synthetic authentication setup request.
    }
    @Test fun pendingOwnerCannotOpenCreationFromAnotherListingButton() = runBlocking {
        respond("""{"status":"PENDING"}"""); respond("[]")
        model.start(); waitForWork()
        respond("""{"status":"PENDING"}"""); respond("[]")
        model.newListing(); waitForWork()
        assertEquals(OwnerScreen.VERIFICATION, model.screen)
        assertEquals("PENDING", model.verificationStatus(null))
        assertFalse(expired)
    }

    private suspend fun pendingDashboard() {
        respond("""{"status":"PENDING"}"""); respond("[]")
        model.start(); waitForWork()
        respond("""{"status":"PENDING"}"""); respond("[]")
        respond("""{"totalListings":0,"draftListings":0,"publishedListings":0}""")
        respond("[]"); respond("[]")
        model.go(OwnerScreen.HOME); waitForWork()
        assertEquals(OwnerScreen.HOME, model.screen)
    }

    @Test fun approvalFromServerReplacesThePendingDashboardWithTheApprovedOutcome() = runBlocking {
        pendingDashboard()
        respond("""{"status":"VERIFIED","reviewedAt":"2026-10-21T06:00:00Z"}""")
        model.refreshReviewStatus(); waitForWork()
        assertEquals("VERIFIED", model.verification?.status)
        assertEquals(OwnerScreen.VERIFICATION, model.screen)
        assertEquals("2026-10-21T06:00:00Z", model.verification?.reviewedAt)
        val requests=server.requestCount
        model.refreshReviewStatus(); waitForWork()
        assertEquals(requests, server.requestCount) // No further polling after the decision.
    }

    @Test fun rejectionFromServerDisplaysTheReasonWithoutApprovingOrChangingTheAccount() = runBlocking {
        pendingDashboard()
        respond("""{"status":"REJECTED","reviewedAt":"2026-10-21T06:00:00Z","rejectionReason":"ID photo is unreadable"}""")
        model.refreshReviewStatus(); waitForWork()
        assertEquals("REJECTED", model.verification?.status)
        assertEquals("ID photo is unreadable", model.verification?.rejectionReason)
        assertEquals(OwnerScreen.VERIFICATION, model.screen)
        assertFalse(expired)
    }

    @Test fun acknowledgementAndFailedRefreshNeverPretendTheRoleWasApproved() = runBlocking {
        pendingDashboard()
        respond("""{"status":"PENDING"}""")
        model.refreshReviewStatus(); waitForWork()
        assertEquals("PENDING", model.verification?.status)
        assertEquals(OwnerScreen.HOME, model.screen)
        respond("{}", 503)
        model.refreshReviewStatus(); waitForWork()
        assertEquals("PENDING", model.verification?.status)
        assertEquals(OwnerScreen.HOME, model.screen)
        assertFalse(model.notice.orEmpty().contains("scanner"))
    }

    @Test fun listingIsSubmittedReviewedAndOnlyThenPublished() = runBlocking {
        val draft="""{"id":"listing-1","title":"Test home","description":"Synthetic","propertyType":"HOUSE","monthlyRentKes":10000,"bedroomCount":1,"bathroomCount":1,"furnished":false,"status":"DRAFT","createdAt":"2026-10-21T06:00:00Z","updatedAt":"2026-10-21T06:00:00Z"}"""
        respond("""{"status":"VERIFIED"}""")
        respond("""{"totalListings":1,"draftListings":1,"publishedListings":0}""")
        respond("[$draft]"); respond("[]")
        model.start(); waitForWork()
        repeat(4) { server.takeRequest() }

        respond("""{"id":"listing-1","status":"DRAFT","moderation_status":"PENDING","updated_at":"2026-10-21T06:00:00Z"}""")
        model.manage(model.listings.single()); waitForWork()
        assertEquals(OwnerScreen.EDIT,model.screen)
        assertEquals("/api/v1/landlord/listings/listing-1/review",server.takeRequest().path)

        respond("""{"id":"listing-1","status":"DRAFT","moderation_status":"PENDING","updated_at":"2026-10-21T06:00:00Z"}""")
        respond("""[{"id":"photo","listingId":"listing-1","mediaType":"IMAGE","contentType":"image/jpeg","sizeBytes":3,"sortOrder":0,"createdAt":"2026-10-21T06:00:00Z","confirmed":true}]""")
        respond("""{"areaLabel":"Test area","latitude":-1.29,"longitude":36.82}""")
        model.go(OwnerScreen.CHECKS); waitForWork()
        assertEquals("/api/v1/landlord/listings/listing-1/review",server.takeRequest().path)
        assertEquals("/api/v1/landlord/listings/listing-1/media",server.takeRequest().path)
        assertEquals("/api/v1/landlord/listings/listing-1/location",server.takeRequest().path)
        val beforeBlockedPublish=server.requestCount
        model.listingAction("publish"); waitForWork()
        assertEquals(beforeBlockedPublish,server.requestCount)
        assertTrue(model.notice.orEmpty().contains("approval"))

        respond("{}")
        respond("""{"id":"listing-1","status":"DRAFT","moderation_status":"PENDING","moderation_submitted_at":"2026-10-21T07:00:00Z","updated_at":"2026-10-21T07:00:00Z"}""")
        model.submitListingReview(); waitForWork()
        assertEquals(OwnerScreen.CHECKS, model.screen)
        assertEquals("/api/v1/landlord/listings/listing-1/submit-review",server.takeRequest().path)
        assertEquals("/api/v1/landlord/listings/listing-1/review",server.takeRequest().path)
        assertNotNull(model.listingReview?.moderationSubmittedAt)

        respond("""{"id":"listing-1","status":"DRAFT","moderation_status":"APPROVED","moderation_reviewed_at":"2026-10-21T08:00:00Z","updated_at":"2026-10-21T08:00:00Z"}""")
        respond("""[{"id":"photo","listingId":"listing-1","mediaType":"IMAGE","contentType":"image/jpeg","sizeBytes":3,"sortOrder":0,"createdAt":"2026-10-21T06:00:00Z","confirmed":true}]""")
        respond("""{"areaLabel":"Test area","latitude":-1.29,"longitude":36.82}""")
        model.go(OwnerScreen.CHECKS); waitForWork()
        repeat(3) { server.takeRequest() }
        assertEquals("APPROVED",model.listingReview?.moderationStatus)

        val published=draft.replace("\"DRAFT\"","\"PUBLISHED\"")
        respond(published)
        respond("""{"id":"listing-1","status":"PUBLISHED","moderation_status":"APPROVED","moderation_reviewed_at":"2026-10-21T08:00:00Z","updated_at":"2026-10-21T08:01:00Z"}""")
        respond("[$published]")
        model.listingAction("publish"); waitForWork()
        assertEquals("/api/v1/landlord/listings/listing-1/publish",server.takeRequest().path)
        assertEquals("/api/v1/landlord/listings/listing-1/review",server.takeRequest().path)
        assertEquals("/api/v1/landlord/listings",server.takeRequest().path)
        assertEquals("PUBLISHED",model.selected?.status)
        assertFalse(expired)
    }

    @Test fun mediaManagementNeedsASelectedRecordAndDeletionFailureKeepsItRetryable() = runBlocking {
        val listing = OwnerListing("listing-1", "Test home", "Synthetic", "HOUSE", 10000,
            1, 1, false, null, "DRAFT", "2026-10-21T06:00:00Z", "2026-10-21T06:00:00Z")
        model.edit(listing)
        model.manageMedia("unknown")
        assertEquals(OwnerScreen.EDIT, model.screen)
        assertNull(model.selectedMediaId)
        respond("""[{"id":"photo","listingId":"listing-1","mediaType":"IMAGE","contentType":"image/jpeg","sizeBytes":3,"sortOrder":0,"createdAt":"2026-10-21T06:00:00Z","confirmed":true}]""")
        model.loadMedia(); waitForWork(); server.takeRequest()
        val count = server.requestCount
        model.manageMedia("photo")
        assertEquals(OwnerScreen.MEDIA_MANAGE, model.screen)
        assertEquals("photo", model.selectedMediaId)
        assertEquals(count, server.requestCount) // Opening confirmation never sends DELETE.
        respond("{}", 503)
        model.removeMedia("photo"); waitForWork()
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(OwnerScreen.MEDIA_MANAGE, model.screen)
        assertEquals("photo", model.media.single().id)
        respond("", 204); respond("{}", 503)
        model.removeMedia("photo"); waitForWork()
        // The deletion succeeded even though refreshing the remaining media failed.
        assertEquals(OwnerScreen.MEDIA, model.screen)
        assertNull(model.selectedMediaId)
        assertTrue(model.media.isEmpty())
        assertFalse(expired)
    }

    @Test fun openingDecisionDoesNotApproveAndServerFailureNeverShowsSuccess() = runBlocking {
        val app = OwnerApplication("application-1", "listing-1", "SUBMITTED",
            "2026-10-21T06:00:00Z", "2026-10-21T06:00:00Z")
        model.viewApplication(app)
        val count = server.requestCount
        model.go(OwnerScreen.APPLICATION_DECISION)
        assertEquals(count, server.requestCount)
        assertEquals("SUBMITTED", model.selectedApplication?.status)
        respond("{}", 409)
        model.decision("approve"); waitForWork()
        assertEquals("/api/v1/landlord/applications/application-1/approve", server.takeRequest().path)
        assertEquals("SUBMITTED", model.selectedApplication?.status)
        assertEquals(OwnerScreen.APPLICATION_DECISION, model.screen)
        assertNotNull(model.notice)
        assertFalse(expired)
    }

    @Test fun aRejectedIdCanBeReplacedWithoutLosingTheRetryWhenANewTicketFails() = runBlocking {
        respond("""{"status":"NOT_STARTED"}""")
        respond("""[{"documentId":"old","documentType":"ID_FRONT","status":"READY","contentType":"image/jpeg","securityChecked":true}]""")
        model.start(); waitForWork(); server.takeRequest(); server.takeRequest()
        fun ticket(id: String) = respond("""{"documentId":"$id","documentType":"ID_FRONT","contentType":"image/jpeg","uploadUrl":"http://invalid.test/not-storage"}""")
        suspend fun select(bytes: ByteArray, name: String) {
            try { model.beginEvidence(OwnerEvidence.ID_FRONT, bytes, name); fail("Unsafe storage URL must be rejected") }
            catch (_: IllegalArgumentException) { /* No document leaves this synthetic test. */ }
        }
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5)
        try {
            ticket("first"); select(first, "wrong-id.jpg")
            assertEquals("first", model.verificationPendingId)
            assertEquals("wrong-id.jpg", model.evidenceNames[OwnerEvidence.ID_FRONT])
            assertFalse(evidenceReady(OwnerEvidence.ID_FRONT, model.verificationDocuments))
            assertTrue(com.google.gson.JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject["replaceExisting"].asBoolean)

            respond("{}", 503)
            val rejected = byteArrayOf(8, 9)
            try { model.beginEvidence(OwnerEvidence.ID_FRONT, rejected, "not-accepted.jpg"); fail("Ticket should fail") }
            catch (error: retrofit2.HttpException) { assertEquals(503, error.code()) }
            server.takeRequest()
            assertEquals("first", model.verificationPendingId)
            assertEquals("wrong-id.jpg", model.evidenceNames[OwnerEvidence.ID_FRONT])
            assertArrayEquals(byteArrayOf(1, 2, 3), first)
            assertArrayEquals(byteArrayOf(0, 0), rejected)

            ticket("second"); select(second, "correct-id.jpg")
            assertEquals("second", model.verificationPendingId)
            assertEquals("correct-id.jpg", model.evidenceNames[OwnerEvidence.ID_FRONT])
            assertArrayEquals(byteArrayOf(0, 0, 0), first)
            assertArrayEquals(byteArrayOf(4, 5), second)
        } finally { model.clearPrivateState() }
        assertArrayEquals(byteArrayOf(0, 0), second)
        assertNull(model.verificationPendingId)
        assertTrue(model.evidenceNames.isEmpty())
    }
}
