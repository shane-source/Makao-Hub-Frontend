package com.makaohub.android.data

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Owns the in-memory session. No password, OTP or JWT is written to disk or logs. */
class AuthRepository(private val api: MakaoApi) {
    private val sessionLock = Mutex()
    private var tokens: AuthTokens? = null

    suspend fun register(body: RegisterRequest) = api.register(body)
    suspend fun login(body: LoginRequest) = api.login(body)
    suspend fun google(idToken: String, expectedRole: SignupRole) = api.google(GoogleRequest(idToken, expectedRole))

    /** Registration returns only an OTP challenge; it does not create a session. */
    suspend fun registerGoogle(body: GoogleRegisterRequest) = api.googleRegister(body)

    suspend fun verify(challengeId: String, code: String, flow: OtpFlow, expectedRole: SignupRole) {
        val request = VerifyOtpRequest(challengeId, code)
        val response = when (flow) {
            OtpFlow.REGISTRATION -> api.verifyRegistrationOtp(request)
            OtpFlow.GOOGLE_LOGIN -> api.verifyGoogleOtp(request)
            OtpFlow.LOGIN -> api.verifyOtp(request)
        }
        // Defense against an old/misconfigured server: never install a wrong-role session.
        if (response.role != expectedRole.name) {
            sessionLock.withLock { tokens = null }
            try { api.logout(RefreshRequest(response.refreshToken)).requireSuccess() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Revocation is best effort; these tokens are never retained locally. */ }
            throw LoginRoleMismatch()
        }
        sessionLock.withLock { tokens = response }
    }

    /** One serialized refresh prevents concurrent calls replaying a rotated refresh token. */
    suspend fun profile(): MyProfile = authorized { api, header -> api.profile(header) }

    /** Reuse the same serialized session/refresh path for every protected workspace call. */
    suspend fun <T> authorized(requiredRole: String? = null, action: suspend (MakaoApi, String) -> T): T = sessionLock.withLock {
        val current = tokens ?: throw SessionExpired()
        if (requiredRole != null && current.role != requiredRole) throw WorkspaceUnavailable()
        try {
            action(api, "Bearer ${current.accessToken}")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val refreshed = try {
                api.refresh(RefreshRequest(current.refreshToken))
            } catch (cancel: CancellationException) {
                tokens = null
                throw cancel
            } catch (failure: Exception) {
                // A lost response can hide a successful rotation. Never replay the old token.
                tokens = null
                throw SessionExpired()
            }
            tokens = refreshed
            if (requiredRole != null && refreshed.role != requiredRole) throw WorkspaceUnavailable()
            try { action(api, "Bearer ${refreshed.accessToken}") }
            catch (failure: HttpException) {
                if (failure.code() == 401) { tokens = null; throw SessionExpired() }
                throw failure
            }
        }
    }

    suspend fun forgot(email: String) = api.forgotPassword(ForgotPasswordRequest(email))
    suspend fun reset(email: String, code: String, password: String) =
        api.resetPassword(ResetPasswordRequest(email, code, password)).requireSuccess()

    suspend fun logout() = sessionLock.withLock {
        val current = tokens
        tokens = null
        if (current != null) api.logout(RefreshRequest(current.refreshToken)).requireSuccess()
    }

    private fun retrofit2.Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }
}

class SessionExpired : Exception()
class WorkspaceUnavailable : Exception()
class LoginRoleMismatch : Exception()

enum class OtpFlow { REGISTRATION, LOGIN, GOOGLE_LOGIN }

/** No HTTP body logger and no automatic retries of OTP/payment-like POST requests. */
private val evidenceConfirmPath = Regex("^/api/v1/(landlord|sublessor|renter)/verification/documents/[^/]+/confirm$")
internal fun isEvidenceConfirmation(path: String) = evidenceConfirmPath.matches(path)

fun createMakaoApi(baseUrl: String): MakaoApi {
    val normal = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    // Bounded server download + scan + normalization can exceed the ordinary request budget.
    val evidence = normal.newBuilder().readTimeout(75, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()
    return Retrofit.Builder().baseUrl(baseUrl)
        .callFactory { request -> (if (isEvidenceConfirmation(request.url.encodedPath)) evidence else normal).newCall(request) }
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(MakaoApi::class.java)
}

/** Fixed messages avoid displaying raw server errors, tokens or document information. */
fun friendlyError(error: Exception): String = when (error) {
    is LoginRoleMismatch -> "This account does not match the selected role. Go back and choose your account’s role."
    is SessionExpired -> "Your session has expired. Please sign in again."
    is WorkspaceUnavailable -> "Switch to the required account role to open this workspace."
    is HttpException -> when (error.problemCode()) {
        "ROLE_MISMATCH" -> friendlyError(LoginRoleMismatch())
        "OTP_PROVIDER_CONFIGURATION" -> "We couldn't send your code. Please contact support to restore email verification."
        "OTP_PROVIDER_RATE_LIMITED" -> "Email verification has reached a sending limit. Please wait before requesting another code."
        "OTP_PROVIDER_UNAVAILABLE" -> "Email verification is unavailable right now. If you requested a code, check your inbox before trying again."
        else -> when (error.code()) {
        400 -> "Please check your details. The code may be incorrect or expired."
        401 -> "We couldn't verify your details. Check your email, password or code."
        403 -> "Your account cannot perform this action. Contact support if this continues."
        409 -> "An account or request already exists. Try signing in instead."
        429 -> "Too many attempts. Please wait before trying again."
        else -> "The service is temporarily unavailable. Please try again."
        }
    }
    is IOException -> "We couldn't connect. Check your connection and that the Makao server is running."
    else -> "Something went wrong. Please try again."
}

/** Only allowlisted codes affect the UI. Raw server/provider details are never shown. */
internal fun HttpException.problemCode(): String? = runCatching {
    response()?.errorBody()?.use { body ->
        body.charStream().use { reader ->
            val text = buildString {
                while (length <= 4096) {
                    val char = reader.read()
                    if (char == -1) break
                    append(char.toChar())
                }
            }
            if (text.length > 4096) null
            else JsonParser.parseString(text).asJsonObject.get("code")?.asString
        }
    }
}.getOrNull()
