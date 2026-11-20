package com.makaohub.android.data

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Serializes session updates and optionally stores them encrypted on the device. */
class AuthRepository(private val api: MakaoApi, private val sessionStore: SessionStore? = null) {
    private val sessionLock = Mutex()
    private var tokens: AuthTokens? = null
    @Volatile private var inactive = false
    val wasInactive: Boolean get() = inactive || sessionStore?.wasInactive == true
    fun recordActivity(wallMillis: Long, elapsedMillis: Long) {
        if (tokens != null && !inactive) try { sessionStore?.recordActivity(wallMillis, elapsedMillis) } catch (_: Exception) { discardStoredSession() }
    }
    fun expireLocally() { inactive = true; discardStoredSession() }

    suspend fun restore(): Boolean = sessionLock.withLock {
        if (inactive) return@withLock false
        if (tokens == null) tokens = sessionStore?.read()
        inactive = false
        tokens != null
    }
    val sessionRole: String? get() = if (inactive) null else tokens?.role
    private fun discardStoredSession() { try { sessionStore?.clear() } catch (_: Exception) { } }
    private fun persistSession(value: AuthTokens) {
        try { sessionStore?.write(value) } catch (_: Exception) { discardStoredSession() }
    }

    suspend fun register(body: RegisterRequest) = api.register(body)
    /** Returns null when the account was signed in directly, or a code challenge when one is still needed. */
    suspend fun login(body: LoginRequest): OtpChallenge? = signInOutcome(api.login(body), body.expectedRole)
    suspend fun google(idToken: String, expectedRole: SignupRole): OtpChallenge? =
        signInOutcome(api.google(GoogleRequest(idToken, expectedRole)), expectedRole)

    private suspend fun signInOutcome(reply: SignInReply, expectedRole: SignupRole): OtpChallenge? {
        val access = reply.accessToken
        val refresh = reply.refreshToken
        if (!access.isNullOrEmpty() && !refresh.isNullOrEmpty()) {
            installSession(AuthTokens(access, refresh, reply.role.orEmpty(), reply.userId.orEmpty()), expectedRole)
            return null
        }
        val id = reply.challengeId ?: throw IOException("Sign-in could not start.")
        return OtpChallenge(id, reply.purpose.orEmpty(), reply.maskedEmail.orEmpty(), reply.expiresAt.orEmpty())
    }

    /** Registration returns only an OTP challenge; it does not create a session. */
    suspend fun registerGoogle(body: GoogleRegisterRequest) = api.googleRegister(body)

    suspend fun verify(challengeId: String, code: String, flow: OtpFlow, expectedRole: SignupRole) {
        val request = VerifyOtpRequest(challengeId, code)
        val response = when (flow) {
            OtpFlow.REGISTRATION -> api.verifyRegistrationOtp(request)
            OtpFlow.GOOGLE_LOGIN -> api.verifyGoogleOtp(request)
            OtpFlow.LOGIN -> api.verifyOtp(request)
            OtpFlow.REACTIVATION -> api.verifyReactivationOtp(request)
        }
        installSession(response, expectedRole)
    }

    private suspend fun installSession(response: AuthTokens, expectedRole: SignupRole) {
        // Defense against an old/misconfigured server: never install a wrong-role session.
        if (response.role != expectedRole.name) {
            sessionLock.withLock { tokens = null; discardStoredSession() }
            try { api.logout(RefreshRequest(response.refreshToken)).requireSuccess() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Revocation is best effort; these tokens are never retained locally. */ }
            throw LoginRoleMismatch()
        }
        sessionLock.withLock { discardStoredSession(); inactive = false; tokens = response; persistSession(response) }
    }

    /** One serialized refresh prevents concurrent calls replaying a rotated refresh token. */
    suspend fun profile(): MyProfile = authorized { api, header -> api.profile(header) }

    suspend fun completePhone(phone: String) = authorized { api, header ->
        api.completePhone(header, CompletePhoneRequest(phone)).requireSuccess()
    }

    suspend fun startAccountAction(email: String, action: String) = authorized { api, header ->
        api.startAccountAction(header, AccountActionStartRequest(email, action))
    }

    suspend fun confirmAccountAction(email: String, action: String, challengeId: String, code: String) =
        authorized { api, header ->
            api.confirmAccountAction(header, AccountActionConfirmRequest(email, action, challengeId, code)).requireSuccess()
        }

    suspend fun uploadAvatar(bytes: ByteArray) = authorized { api, header ->
        val capabilities = api.avatarCapabilities(header)
        check(capabilities.validation == "normalized-jpeg-png-v1")
        val ticket = api.avatarTicket(header, AvatarUploadRequest("image/jpeg"))
        val url = ticket.uploadUrl.toHttpUrl()
        check(url.isHttps && url.host.endsWith(".supabase.co") && url.username.isEmpty() && url.password.isEmpty())
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url)
                .put(bytes.toRequestBody("image/jpeg".toMediaType())).build()).execute().use { response ->
                if (!response.isSuccessful) throw MediaTransferFailed()
            }
        }
        api.confirmAvatar(header, AvatarConfirmRequest(ticket.objectPath)).requireSuccess()
    }

    suspend fun deleteAvatar() = authorized { api, header -> api.deleteAvatar(header).requireSuccess() }

    /** Reuse the same serialized session/refresh path for every protected workspace call. */
    suspend fun <T> authorized(requiredRole: String? = null, action: suspend (MakaoApi, String) -> T): T = sessionLock.withLock {
        if (inactive) throw SessionExpired()
        val current = tokens ?: throw SessionExpired()
        if (requiredRole != null && current.role != requiredRole) throw WorkspaceUnavailable()
        try {
            action(api, "Bearer ${current.accessToken}")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            // A rotated refresh token must never be replayed after an interrupted request.
            try { sessionStore?.clearForRefresh() } catch (_: Exception) { tokens = null; throw SessionExpired() }
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
            if (inactive) { tokens = null; discardStoredSession(); throw SessionExpired() }
            tokens = refreshed
            persistSession(refreshed)
            if (requiredRole != null && refreshed.role != requiredRole) throw WorkspaceUnavailable()
            try { action(api, "Bearer ${refreshed.accessToken}") }
            catch (failure: HttpException) {
                if (failure.code() == 401) { tokens = null; discardStoredSession(); throw SessionExpired() }
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
        discardStoredSession()
        if (current != null) api.logout(RefreshRequest(current.refreshToken)).requireSuccess()
    }

    /** Revoke only the expired session; never sign out a newer login. */
    suspend fun revokeInactiveSession() = sessionLock.withLock {
        if (!inactive) return@withLock
        val current = tokens
        tokens = null
        discardStoredSession()
        if (current != null) api.logout(RefreshRequest(current.refreshToken)).requireSuccess()
    }

    suspend fun forgetSession() = sessionLock.withLock { tokens = null; discardStoredSession() }

    private fun retrofit2.Response<Unit>.requireSuccess() {
        if (!isSuccessful) throw HttpException(this)
    }
}

class SessionExpired : Exception()
class WorkspaceUnavailable : Exception()
class LoginRoleMismatch : Exception()

enum class OtpFlow { REGISTRATION, LOGIN, GOOGLE_LOGIN, REACTIVATION }

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
        .callFactory { request -> (if (isEvidenceConfirmation(request.url.encodedPath) || request.url.encodedPath == "/api/v1/profile/me/avatar/confirm") evidence else normal).newCall(request) }
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
