package com.makaohub.android

import com.google.gson.JsonParser
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SublessorContractTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: AuthRepository
    private lateinit var repository: SublessorRepository
    private fun response(json: String, code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(json)) }
    private suspend fun login(role: String = "SUBLESSOR") {
        response("""{"accessToken":"synthetic","refreshToken":"synthetic-refresh","role":"$role","userId":"owner"}""")
        auth.verify("synthetic-challenge", "123456", OtpFlow.LOGIN, SignupRole.valueOf(role)); server.takeRequest()
    }
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); server = MockWebServer(); server.start(); auth = AuthRepository(createMakaoApi(server.url("/").toString())); repository = SublessorRepository(auth) }
    @After fun close() { server.shutdown(); Dispatchers.resetMain() }

    @Test fun landlordSessionCannotRequestSublessorData() = runBlocking {
        login("LANDLORD")
        assertThrows(WorkspaceUnavailable::class.java) { runBlocking { repository.listings() } }
        assertEquals(1, server.requestCount)
    }
    @Test fun draftsUseSublessorContractAndNeverWholeUnitTypes() = runBlocking {
        login()
        response("""{"id":"room","title":"Spare room","description":"Quiet home","spaceType":"SPARE_ROOM","monthlyRentKes":18000,"bathroomCount":1,"furnished":true,"status":"DRAFT","createdAt":"2026-10-15T06:00:00Z","updatedAt":"2026-10-15T06:00:00Z"}""", 201)
        val form = SharedForm("Spare room", "Quiet home", rent = "18000")
        assertNull(form.error())
        assertEquals("room", repository.save(null, form.request()).id)
        val request = server.takeRequest()
        assertEquals("/api/v1/sublessor/listings", request.path); assertEquals("POST", request.method)
        assertEquals("Bearer synthetic", request.getHeader("Authorization"))
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("SPARE_ROOM", json["spaceType"].asString)
        assertEquals(18000L, json["monthlyRentKes"].asLong)
        assertFalse(json.has("sublessorId")); assertFalse(json.has("propertyType"))
        assertNotNull(form.copy(spaceType = "APARTMENT").error())
        assertNotNull(form.copy(rent = "18000.50").error())
        assertNotNull(form.copy(rent = "9223372036854775808").error())
    }
    @Test fun agreementUsesItsOwnRoleRoutesAndCannotUseOwnershipEvidence() = runBlocking {
        login()
        response("""{"documentId":"agreement","documentType":"RENTAL_AGREEMENT","contentType":"application/pdf","uploadUrl":"https://synthetic.supabase.co/storage/v1/object/upload/sign/docs/unused"}""")
        repository.ticket(SharedEvidence.RENTAL_AGREEMENT, true)
        val request = server.takeRequest()
        assertEquals("/api/v1/sublessor/verification/documents/upload-url", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("RENTAL_AGREEMENT", body["documentType"].asString); assertTrue(body["replaceExisting"].asBoolean)
        val ready = SharedEvidence.entries.map { OwnerVerificationDocument(it.name, it.name, "READY", it.mime, securityChecked = true) }
        assertTrue(sharedVerificationReady(ready))
        assertFalse(sharedVerificationReady(ready.dropLast(1)))
        assertFalse(sharedVerificationReady(ready.map { OwnerVerificationDocument(it.documentId, it.documentType, "READY", it.contentType) }))
        assertFalse(sharedVerificationReady(ready.dropLast(1) + OwnerVerificationDocument("owner", "OWNERSHIP_PROOF", "READY", "application/pdf", securityChecked = true)))
        assertTrue(isEvidenceConfirmation("/api/v1/sublessor/verification/documents/id/confirm"))
    }
    @Test fun springThreadPagesAndCursorHistoriesUseTheirActualJsonShapes() = runBlocking {
        login(); response("""{"content":[],"number":1,"totalPages":2,"last":true}""")
        assertTrue(repository.threads(1).last)
        assertEquals("/api/v1/sublessor/roommate-chat/threads?page=1&size=20", server.takeRequest().path)
        response("""{"items":[],"nextCursor":"cursor-2","hasMore":true}""")
        assertTrue(repository.messages("thread", "cursor-1").hasMore)
        assertEquals("/api/v1/sublessor/roommate-chat/threads/thread/messages?cursor=cursor-1&limit=50", server.takeRequest().path)
    }
    @Test fun unverifiedSublessorEntersVerificationAndCannotCreateListing() = runBlocking {
        login(); response("""{"status":"NOT_STARTED"}"""); response("[]")
        val model = SublessorViewModel(repository) { fail("Session should remain valid") }
        model.start(); withTimeout(5000) { while (model.busy) delay(10) }
        assertEquals(SharedScreen.VERIFICATION, model.screen)
        response("""{"status":"NOT_STARTED"}"""); response("[]")
        model.newListing(); withTimeout(5000) { while (model.busy) delay(10) }
        assertEquals(SharedScreen.VERIFICATION, model.screen)
        assertEquals("NOT_STARTED", model.verification?.status)
        model.clear(); assertNull(model.verification); assertTrue(model.documents.isEmpty())
    }
    @Test fun householdAndDiscoveryUseOwnedRoutesAndBoundedPages() = runBlocking {
        login()
        response("""{"depositKes":18000,"currentHousehold":"One adult","totalOccupants":2,"peoplePerRoom":1,"lifestyle":"Quiet evenings","amenities":["WIFI"]}""")
        assertEquals(18000L, repository.saveHousehold("room", SharedHousehold(18000,"One adult",2,1,"Quiet evenings",setOf("WIFI"))).depositKes)
        val write=server.takeRequest()
        assertEquals("/api/v1/sublessor/listings/room/household",write.path); assertEquals("PUT",write.method)
        assertFalse(JsonParser.parseString(write.body.readUtf8()).asJsonObject.has("sublessorId"))
        response("""{"items":[],"page":2,"size":20,"totalElements":40,"hasNext":false}""")
        assertFalse(repository.discovery(2).hasNext)
        assertEquals("/api/v1/sublessor/roommate-discovery?page=2&size=20",server.takeRequest().path)
    }
    @Test fun sharedListingUsesTheSameServerModerationContract() = runBlocking {
        login()
        response("""{"id":"room","status":"DRAFT","moderation_status":"REJECTED","moderation_reason":"Photo needs correction","moderation_reviewed_at":"2026-10-21T08:00:00Z","updated_at":"2026-10-21T08:00:00Z"}""")
        val rejected=repository.review("room")
        assertEquals("REJECTED",rejected.moderationStatus)
        assertEquals("Photo needs correction",rejected.moderationReason)
        assertEquals("/api/v1/sublessor/listings/room/review",server.takeRequest().path)

        response("{}")
        response("""{"id":"room","status":"DRAFT","moderation_status":"PENDING","updated_at":"2026-10-21T08:01:00Z"}""")
        val revised=repository.reviseReview("room")
        assertEquals("/api/v1/sublessor/listings/room/revise-review",server.takeRequest().path)
        assertEquals("/api/v1/sublessor/listings/room/review",server.takeRequest().path)
        assertEquals("PENDING",revised.moderationStatus)
        assertNull(revised.moderationSubmittedAt)

        response("{}")
        response("""{"id":"room","status":"DRAFT","moderation_status":"PENDING","moderation_submitted_at":"2026-10-21T08:02:00Z","updated_at":"2026-10-21T08:02:00Z"}""")
        val submitted=repository.submitReview("room")
        assertEquals("/api/v1/sublessor/listings/room/submit-review",server.takeRequest().path)
        assertEquals("/api/v1/sublessor/listings/room/review",server.takeRequest().path)
        assertNotNull(submitted.moderationSubmittedAt)
    }
    @Test fun preferencesRequireExplicitValidAnswersBudgetAndOptIn() {
        assertNotNull(RoommateForm().error())
        val good=RoommateForm(PreferenceQuestion.entries.associateWith { it.options.keys.first() },"15000","22000",true)
        assertNull(good.error()); assertTrue(good.request().discoveryOptIn)
        assertNotNull(good.copy(maxBudget="14000").error())
        assertNotNull(good.copy(minBudget="15000.50").error())
        assertNotNull(good.copy(maxBudget="9223372036854775808").error())
        assertNotNull(good.copy(answers=good.answers+(PreferenceQuestion.QUIET to "UNSUPPORTED")).error())
        assertFalse(good.copy(optIn=false).request().discoveryOptIn)
        val form=SharedForm("Room","Description",rent="18000")
        assertNotNull(form.copy(deposit="-1").error()); assertNull(form.copy(deposit="0").error())
    }

    @Test fun pendingVerificationCannotStartAReplacement() = runBlocking {
        login(); response("""{"status":"PENDING"}"""); response("[]")
        val model = SublessorViewModel(repository) { fail("Session should remain valid") }
        try {
            model.start(); withTimeout(5000) { while (model.busy) delay(10) }
            val bytes = byteArrayOf(1, 2)
            model.beginEvidence(SharedEvidence.ID_FRONT, bytes)
            assertArrayEquals(byteArrayOf(0, 0), bytes)
            assertNull(model.pendingEvidence)
            assertEquals(3, server.requestCount) // Authentication, verification and document metadata only.
        } finally { model.clear() }
    }

    @Test fun closingPendingVerificationLoadsOnlyTheOwnedDraftsAndKeepsCreationLocked() = runBlocking {
        login(); response("""{"status":"PENDING"}"""); response("[]")
        val model = SublessorViewModel(repository) { fail("Session should remain valid") }
        suspend fun idle() { withTimeout(5000) { while (model.busy) delay(10) } }
        try {
            model.start(); idle(); server.takeRequest(); server.takeRequest()
            response("""{"status":"PENDING"}"""); response("[]")
            response("""[{"id":"draft","title":"Own spare room","description":"Quiet home","spaceType":"SPARE_ROOM","monthlyRentKes":18000,"bathroomCount":1,"furnished":true,"status":"DRAFT","createdAt":"2026-10-15T06:00:00Z","updatedAt":"2026-10-15T06:00:00Z"}]""")
            model.go(SharedScreen.HOME); idle()
            assertEquals(SharedScreen.HOME, model.screen)
            assertEquals("PENDING", model.verification?.status)
            assertEquals("Own spare room", model.listings.single().title)
            assertEquals("/api/v1/sublessor/verification", server.takeRequest().path)
            assertEquals("/api/v1/sublessor/verification/documents", server.takeRequest().path)
            assertEquals("/api/v1/sublessor/listings", server.takeRequest().path)
            assertEquals(6, server.requestCount) // No applications/interests calls for pending dashboard.
            response("""{"status":"PENDING"}"""); response("[]")
            model.newListing(); idle()
            assertEquals(SharedScreen.VERIFICATION, model.screen)
            assertNull(model.selected)
        } finally { model.clear() }
    }

    @Test fun failedEvidenceCanBeReplacedWithoutLosingTheRetryOnTicketFailure() = runBlocking {
        login(); response("""{"status":"NOT_STARTED"}""")
        response("""[{"documentId":"old","documentType":"RENTAL_AGREEMENT","status":"READY","contentType":"application/pdf","securityChecked":true}]""")
        val model = SublessorViewModel(repository) { fail("Session should remain valid") }
        model.start(); withTimeout(5000) { while (model.busy) delay(10) }
        server.takeRequest(); server.takeRequest()
        fun ticket(id: String) = response("""{"documentId":"$id","documentType":"RENTAL_AGREEMENT","contentType":"application/pdf","uploadUrl":"http://invalid.test/not-storage"}""")
        suspend fun select(bytes: ByteArray) {
            try { model.beginEvidence(SharedEvidence.RENTAL_AGREEMENT, bytes); fail("Unsafe storage URL must be rejected") }
            catch (_: IllegalArgumentException) { /* No real document or external storage request. */ }
        }
        val first = byteArrayOf(1, 2, 3)
        val second = byteArrayOf(4, 5, 6)
        try {
            ticket("first"); select(first)
            assertEquals("first", model.pendingEvidence)
            assertFalse(sharedEvidenceReady(SharedEvidence.RENTAL_AGREEMENT, model.documents))
            assertTrue(JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject["replaceExisting"].asBoolean)

            response("{}", 503)
            val rejected = byteArrayOf(7, 8)
            try { model.beginEvidence(SharedEvidence.RENTAL_AGREEMENT, rejected); fail("Ticket should fail") }
            catch (e: retrofit2.HttpException) { assertEquals(503, e.code()) }
            server.takeRequest()
            assertEquals("first", model.pendingEvidence)
            assertArrayEquals(byteArrayOf(1, 2, 3), first)
            assertArrayEquals(byteArrayOf(0, 0), rejected)

            ticket("second"); select(second)
            assertEquals("second", model.pendingEvidence)
            assertTrue(JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject["replaceExisting"].asBoolean)
            assertArrayEquals(byteArrayOf(0, 0, 0), first)
            assertArrayEquals(byteArrayOf(4, 5, 6), second)
            assertFalse(sharedVerificationReady(model.documents))
        } finally { model.clear() }
        assertArrayEquals(byteArrayOf(0, 0, 0), second)
        assertNull(model.pendingEvidence)
    }
}
