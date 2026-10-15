package com.makaohub.android

import com.google.gson.JsonParser
import com.makaohub.android.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/** Real Retrofit HTTP requests against a local test server: no emails or real accounts. */
class AuthContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MakaoApi
    private lateinit var repository: AuthRepository
    private val challenge = """{"challengeId":"test-challenge","purpose":"LOGIN","maskedEmail":"••••@example.test","expiresAt":"2030-01-01T00:00:00Z"}"""
    private val profile = """{"id":"test-user","fullName":"Test Account","email":"test@example.test","role":"SUBLESSOR","accountStatus":"ACTIVE","verificationStatus":"PENDING"}"""
    private fun tokens(access: String, refresh: String) = """{"accessToken":"$access","refreshToken":"$refresh","role":"SUBLESSOR","userId":"test-user"}"""
    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }
    @Before fun setup() {
        server = MockWebServer()
        server.start()
        api = createMakaoApi(server.url("/").toString())
        repository = AuthRepository(api)
    }
    @After fun close() { server.shutdown() }

    @Test fun signupSendsRoleAndConsentThenRequiresOtpBeforeStoringSession() = runBlocking {
        respond(challenge, 201)
        repository.register(RegisterRequest("Test Account", "test@example.test", "0712345678",
            "example-password", SignupRole.SUBLESSOR, true))
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/register", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("SUBLESSOR", json["role"].asString)
        assertTrue(json["termsAndPrivacyAccepted"].asBoolean)
        assertEquals(setOf("fullName", "email", "phoneNumber", "password", "role", "termsAndPrivacyAccepted"), json.keySet())
        assertFalse(SignupRole.entries.any { it.name == "ADMIN" })
        try { repository.profile(); fail("Signup must not authorize profile access before OTP") }
        catch (_: SessionExpired) { }
        assertEquals(1, server.requestCount)
        respond(tokens("signup-access", "signup-refresh"))
        repository.verify("test-challenge", "123456", OtpFlow.REGISTRATION)
        assertEquals("/api/v1/auth/register/verify-otp", server.takeRequest().path)
        respond(profile)
        repository.profile()
        assertEquals("Bearer signup-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun loginSendsNoRoleAndDoesNotCreateSessionBeforeOtp() = runBlocking {
        respond(challenge)
        val result = repository.login(LoginRequest("test@example.test", "example-password"))
        assertEquals("••••@example.test", result.maskedEmail)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("email", "password"), json.keySet())
        try { repository.profile(); fail("Login alone must not authorize profile access") }
        catch (_: SessionExpired) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun failedOtpNeverGrantsSession() = runBlocking {
        respond("{}", 401)
        try { repository.verify("test-challenge", "123456", OtpFlow.LOGIN); fail("Must reject invalid OTP") }
        catch (error: HttpException) { assertEquals(401, error.code()) }
        try { repository.profile(); fail("Profile must remain unauthenticated") }
        catch (_: SessionExpired) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun verifiedSessionUsesBearerTokenAndServerRole() = runBlocking {
        respond(tokens("test-access", "test-refresh"))
        repository.verify("test-challenge", "123456", OtpFlow.LOGIN)
        val verify = server.takeRequest()
        assertEquals("/api/v1/auth/login/verify-otp", verify.path)
        val json = JsonParser.parseString(verify.body.readUtf8()).asJsonObject
        assertEquals("test-challenge", json["challengeId"].asString)
        respond(profile)
        assertEquals("SUBLESSOR", repository.profile().role)
        assertEquals("Bearer test-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun concurrentProfileRequestsRotateRefreshTokenOnlyOnce() = runBlocking {
        respond(tokens("old-access", "old-refresh"))
        repository.verify("test-challenge", "123456", OtpFlow.LOGIN)
        server.takeRequest()
        respond("{}", 401)
        respond(tokens("new-access", "new-refresh"))
        respond(profile)
        respond(profile)
        listOf(async { repository.profile() }, async { repository.profile() }).awaitAll()
        val requests = List(4) { server.takeRequest() }
        assertEquals(1, requests.count { it.path == "/api/v1/auth/refresh" })
        val refresh = requests.single { it.path == "/api/v1/auth/refresh" }
        assertEquals("old-refresh", JsonParser.parseString(refresh.body.readUtf8()).asJsonObject["refreshToken"].asString)
        assertEquals(2, requests.count { it.getHeader("Authorization") == "Bearer new-access" })
    }

    @Test fun forgotAndResetUseExistingBackendContracts() = runBlocking {
        respond("""{"message":"If an account exists, a code will be sent."}""", 202)
        repository.forgot("test@example.test")
        assertEquals("/api/v1/auth/forgot-password", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(204))
        repository.reset("test@example.test", "123456", "example-password")
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/forgot-password/reset", request.path)
        assertEquals(setOf("email", "code", "newPassword"), JsonParser.parseString(request.body.readUtf8()).asJsonObject.keySet())
    }

    @Test fun logoutClearsLocalSessionEvenWhenServerFails() = runBlocking {
        respond(tokens("access", "refresh"))
        repository.verify("test-challenge", "123456", OtpFlow.LOGIN)
        respond("{}", 500)
        try { repository.logout(); fail("Server failure must be reported") }
        catch (_: HttpException) { }
        try { repository.profile(); fail("Local session must be cleared") }
        catch (_: SessionExpired) { }
        assertEquals(2, server.requestCount)
    }

    @Test fun registrationValidationMatchesBackendLimits() {
        assertNull(passwordError("12345678", "12345678"))
        assertNotNull(passwordError("1234567", "1234567"))
        assertNotNull(passwordError(" ".repeat(8), " ".repeat(8)))
        assertNotNull(passwordError("a".repeat(73), "a".repeat(73)))
        assertNotNull(passwordError("é".repeat(40), "é".repeat(40)))
        assertNotNull(registrationError("Test", "test@example.test", "0712345678", "example-password", "example-password", false))
        assertNull(registrationError("Test", "test@example.test", "+254 712 345 678", "example-password", "example-password", true))
    }

    @Test fun failedEmailRequestIsNotRetriedAndCannotGrantASession() = runBlocking {
        respond("""{"code":"OTP_PROVIDER_CONFIGURATION","detail":"private provider content"}""", 503)
        try {
            repository.register(RegisterRequest("Test Account", "test@example.test", "0712345678",
                "example-password", SignupRole.RENTER, true))
            fail("Must report the provider failure")
        } catch (error: HttpException) {
            assertTrue(friendlyError(error).contains("contact support"))
        }
        try { repository.profile(); fail("Failed email issuance must leave the app unauthenticated") }
        catch (_: SessionExpired) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun errorsUseSafeCodesAndNeverDisplayProviderDetails() = runBlocking {
        for (code in listOf("OTP_PROVIDER_CONFIGURATION", "OTP_PROVIDER_RATE_LIMITED", "OTP_PROVIDER_UNAVAILABLE", "UNKNOWN")) {
            respond("""{"code":"$code","detail":"private provider content"}""", 503)
            try { repository.login(LoginRequest("test@example.test", "example-password")); fail("Must fail") }
            catch (error: HttpException) {
                val message = friendlyError(error)
                assertFalse(message.contains("private provider content"))
                when (code) {
                    "OTP_PROVIDER_CONFIGURATION" -> assertTrue(message.contains("contact support"))
                    "OTP_PROVIDER_RATE_LIMITED" -> assertTrue(message.contains("sending limit"))
                    "OTP_PROVIDER_UNAVAILABLE" -> assertTrue(message.contains("check your inbox"))
                    else -> assertEquals("The service is temporarily unavailable. Please try again.", message)
                }
            }
        }
        respond("<html>private provider content</html>", 503)
        try { repository.login(LoginRequest("test@example.test", "example-password")); fail("Must fail") }
        catch (error: HttpException) {
            assertEquals("The service is temporarily unavailable. Please try again.", friendlyError(error))
        }
    }
}
