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
        auth.verify("challenge", "123456", OtpFlow.LOGIN); server.takeRequest()
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
}
