package com.makaohub.android

import com.google.gson.JsonParser
import com.makaohub.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

/** Exercises real ViewModel navigation and Retrofit with synthetic local responses only. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginRoleFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var model: AuthViewModel
    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }
    private fun challenge() = respond("""{"challengeId":"synthetic-challenge","purpose":"LOGIN","maskedEmail":"••••@example.invalid","expiresAt":"2030-01-01T00:00:00Z"}""")
    private suspend fun idle() { withTimeout(5000) { while (model.busy) delay(10) } }
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer(); server.start()
        model = AuthViewModel(AuthRepository(createMakaoApi(server.url("/").toString())))
        model.navigate(Page.LOGIN)
    }
    @After fun close() { server.shutdown(); Dispatchers.resetMain() }

    @Test fun passwordAndGoogleRoleMismatchNeverOpenOtpOrDashboard() = runBlocking {
        for (role in SignupRole.entries) {
            for (google in listOf(false, true)) {
                model.selectRole(role)
                respond("""{"code":"ROLE_MISMATCH"}""", 403)
                if (google) model.googleLogin("synthetic-token")
                else model.login("synthetic@example.invalid", "synthetic-password")
                idle()
                assertEquals(Page.LOGIN, model.page)
                assertNull(model.challenge); assertNull(model.profile)
                assertTrue(model.message!!.contains("selected role"))
                assertEquals(role.name, JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject["expectedRole"].asString)
            }
        }
        assertEquals(6, server.requestCount)
    }

    @Test fun matchingRoleOpensItsWorkspaceAfterOtp() = runBlocking {
        model.selectRole(SignupRole.LANDLORD)
        challenge()
        model.login("synthetic@example.invalid", "synthetic-password"); idle()
        assertEquals(Page.OTP, model.page); assertNull(model.profile)
        server.takeRequest()
        respond("""{"accessToken":"synthetic-access","refreshToken":"synthetic-refresh","role":"LANDLORD","userId":"synthetic"}""")
        respond("""{"id":"synthetic","fullName":"Synthetic","email":"synthetic@example.invalid","role":"LANDLORD","accountStatus":"ACTIVE","verificationStatus":"PENDING"}""")
        model.verify("123456"); idle()
        assertEquals(Page.ACCOUNT, model.page)
        assertEquals("LANDLORD", model.profile!!.role)
        assertEquals("PENDING", model.profile!!.verificationStatus)
        assertNull(model.challenge)
        assertEquals(3, server.requestCount)
    }

    @Test fun oldServerWrongRoleTokensReturnToSignInWithoutProfileRequest() = runBlocking {
        model.selectRole(SignupRole.RENTER)
        challenge(); model.googleLogin("synthetic-token"); idle()
        respond("""{"accessToken":"synthetic-access","refreshToken":"synthetic-refresh","role":"LANDLORD","userId":"synthetic"}""")
        server.enqueue(MockResponse().setResponseCode(204))
        model.verify("123456"); idle()
        assertEquals(Page.LOGIN, model.page)
        assertNull(model.profile); assertNull(model.challenge)
        assertTrue(model.message!!.contains("selected role"))
        assertEquals(3, server.requestCount) // Google request, OTP verification, rejected-session revocation only.
    }

    @Test fun selectingAnotherRoleInvalidatesThePreviousOtpChallenge() = runBlocking {
        challenge(); model.login("synthetic@example.invalid", "synthetic-password"); idle()
        assertNotNull(model.challenge)
        model.selectRole(SignupRole.SUBLESSOR)
        assertNull(model.challenge)
        model.verify("123456"); idle()
        assertEquals(1, server.requestCount)
        assertNull(model.profile)
    }
}
