package com.makaohub.android

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale

enum class Page { SPLASH, OFFLINE, ENTRY, WELCOME, HOMES, PEACE, ROLE, REGISTER, LOGIN, OTP, FORGOT, RESET, ACCOUNT }

/** Keeps requests alive through rotation, without persisting credentials in saved state. */
class AuthViewModel(
    internal val repository: AuthRepository = AuthRepository(createMakaoApi(BuildConfig.API_BASE_URL)),
    internal val offlineHomes: OfflineHomes? = null,
    private val launchMemory: LaunchMemory? = null
) : ViewModel() {
    var page by mutableStateOf(Page.SPLASH); private set
    var role by mutableStateOf(SignupRole.RENTER); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var challenge by mutableStateOf<OtpChallenge?>(null); private set
    var profile by mutableStateOf<MyProfile?>(null); private set
    var startVerificationAfterRegistration by mutableStateOf(false); private set
    var accountActionChallenge by mutableStateOf<OtpChallenge?>(null); private set
    var resetEmail by mutableStateOf(""); private set
    var offlineSession by mutableStateOf(false); private set
    private var otpFlow = OtpFlow.LOGIN
    private var activeAction: Job? = null
    private var lastActivityWall = 0L
    private var lastActivityElapsed = 0L

    /** Times are supplied by the activity so tests do not depend on the device clock. */
    fun noteUserActivity(wallMillis: Long, elapsedMillis: Long) {
        if (!((page == Page.ACCOUNT && profile != null) || (page == Page.OFFLINE && offlineSession))) return
        if (lastActivityWall != 0L && sessionInactive(lastActivityWall, lastActivityElapsed, wallMillis, elapsedMillis)) {
            signOutForInactivity(); return
        }
        lastActivityWall = wallMillis
        lastActivityElapsed = elapsedMillis
        repository.recordActivity(wallMillis, elapsedMillis)
    }

    fun checkInactivity(wallMillis: Long, elapsedMillis: Long) {
        if (((page == Page.ACCOUNT && profile != null) || (page == Page.OFFLINE && offlineSession)) && lastActivityWall != 0L &&
            sessionInactive(lastActivityWall, lastActivityElapsed, wallMillis, elapsedMillis)) signOutForInactivity()
    }

    /** Keeps the sign-in screen on the role this person was using (landlord, renter or sublessor). */
    private fun keepRoleOf(name: String?) { name?.let { n -> SignupRole.entries.firstOrNull { it.name == n }?.let { role = it } } }

    /** Called when someone signs in successfully, so the next launch knows they are an existing user. */
    fun rememberSignedInUser() { profile?.role?.let { launchMemory?.remember(it) } }

    private fun signOutForInactivity() {
        keepRoleOf(profile?.role)
        repository.expireLocally()
        activeAction?.cancel()
        lastActivityWall = 0L; lastActivityElapsed = 0L
        profile = null; challenge = null; accountActionChallenge = null
        busy = false
        val wasOffline = page == Page.OFFLINE
        offlineSession = false; page = if (wasOffline) Page.OFFLINE else Page.LOGIN
        message = "You were signed out after two minutes of inactivity. Please sign in again."
        viewModelScope.launch {
            try { repository.revokeInactiveSession() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Local credentials were already cleared. */ }
        }
    }
    val reactivationPending get() = challenge?.purpose == "REACTIVATION"
    val phoneCompletionRequired get() = profile?.let {
        it.role in setOf("RENTER", "LANDLORD", "SUBLESSOR") && it.phoneNumber.isNullOrBlank()
    } == true

    /** Timer and tap share this guard; a late completion must not replace another page. */
    fun finishSplash() {
        if (page != Page.SPLASH || busy) return
        restoreOrShowOffline()
    }

    fun enterOffline() {
        if (repository.sessionRole == "RENTER" && offlineHomes != null) { profile = null; offlineSession = true; page = Page.OFFLINE }
    }

    fun retryOffline() { if (page == Page.OFFLINE && !busy) restoreOrShowOffline() }

    private fun restoreOrShowOffline() {
        perform(onError = { error ->
            if (offlineHomes != null && repository.sessionRole != null &&
                (error is java.io.IOException || error is HttpException && error.code() >= 500)) {
                offlineSession = true; page = Page.OFFLINE; true
            } else false
        }) {
            if (repository.restore()) {
                page = Page.ACCOUNT
                profile = repository.profile()
                offlineSession = false
                profile?.role?.let { name -> SignupRole.entries.firstOrNull { it.name == name }?.let { role = it } }
            } else if (offlineHomes?.unavailable() == true) {
                offlineSession = false
                page = Page.OFFLINE
                if (repository.wasInactive) message = "You were signed out after two minutes of inactivity. Please sign in again."
            } else if (repository.wasInactive) {
                keepRoleOf(launchMemory?.lastRole)
                page = Page.LOGIN
                message = "You were signed out after two minutes of inactivity. Please sign in again."
            } else if (launchMemory?.knownUser == true) {
                keepRoleOf(launchMemory.lastRole) // an existing user goes to sign in, as the role they used last
                page = Page.LOGIN
            } else page = Page.WELCOME // a brand new user sees the walkthroughs
        }
    }

    fun navigate(target: Page) {
        if (busy) return
        message = null
        if (page == Page.OTP && target != Page.OTP) challenge = null
        page = target
    }
    fun selectRole(value: SignupRole) {
        if (!busy) {
            if (value != role) challenge = null
            role = value
        }
    }
    fun showMessage(value: String) { message = value }

    fun register(name: String, email: String, phone: String, password: String, confirm: String, consent: Boolean) {
        val problem = registrationError(name, email, phone, password, confirm, consent)
        if (problem != null) { message = problem; return }
        perform {
            challenge = repository.register(RegisterRequest(name.trim(), normalizeEmail(email), normalizePhone(phone), password, role, consent))
            otpFlow = OtpFlow.REGISTRATION
            page = Page.OTP
        }
    }
    fun login(email: String, password: String) {
        if (!validEmail(email) || password.isBlank()) { message = "Enter your email and password."; return }
        perform {
            val pending = repository.login(LoginRequest(normalizeEmail(email), password, role))
            if (pending == null) { enterAccount(); return@perform }
            challenge = pending
            otpFlow = if (pending.purpose == "REACTIVATION") OtpFlow.REACTIVATION else OtpFlow.LOGIN
            page = Page.OTP
        }
    }
    fun googleLogin(idToken: String) = perform(onError = { error ->
        if (error is HttpException && error.code() == 409
            && error.problemCode() == "GOOGLE_REGISTRATION_REQUIRED") {
            page = Page.ROLE
            message = "Choose your role, then create your account."
            true
        } else false
    }) {
        val pending = repository.google(idToken, role)
        if (pending == null) { enterAccount(); return@perform }
        challenge = pending
        otpFlow = if (pending.purpose == "REACTIVATION") OtpFlow.REACTIVATION else OtpFlow.GOOGLE_LOGIN
        page = Page.OTP
    }

    /** A signed-in reply needs no code: go straight to the account. */
    private suspend fun enterAccount() {
        challenge = null
        startVerificationAfterRegistration = false
        page = Page.ACCOUNT
        profile = repository.profile()
    }
    fun googleRegister(idToken: String, name: String, phone: String, consent: Boolean) {
        if (!validPhone(phone)) { message = "Enter a Kenyan mobile number, such as 0712345678."; return }
        perform {
        challenge = repository.registerGoogle(GoogleRegisterRequest(idToken, name.trim(), role, consent, normalizePhone(phone)))
        otpFlow = OtpFlow.REGISTRATION
        page = Page.OTP
        }
    }
    fun verify(code: String) {
        val pending = challenge ?: return
        if (!code.matches(Regex("[0-9]{6}"))) { message = "Enter the six-digit verification code."; return }
        perform {
            repository.verify(pending.challengeId, code, otpFlow, role)
            challenge = null
            startVerificationAfterRegistration = otpFlow == OtpFlow.REGISTRATION
            page = Page.ACCOUNT
            profile = repository.profile()
        }
    }
    fun forgot(email: String) {
        if (!validEmail(email)) { message = "Enter a valid email address."; return }
        perform {
            repository.forgot(normalizeEmail(email))
            resetEmail = normalizeEmail(email)
            page = Page.RESET
            message = "If an eligible account exists, a code will be sent to its account email."
        }
    }
    fun reset(code: String, password: String, confirm: String) {
        val problem = passwordError(password, confirm)
        if (problem != null) { message = problem; return }
        if (!code.matches(Regex("[0-9]{6}"))) { message = "Enter the six-digit code."; return }
        perform {
            repository.reset(resetEmail, code, password)
            resetEmail = ""
            page = Page.LOGIN
            message = "Password updated. Sign in with your new password."
        }
    }
    fun reloadProfile() = perform { profile = repository.profile() }

    fun consumeRegistrationRedirect() { startVerificationAfterRegistration = false }

    fun completePhone(phone: String) {
        if (!phoneCompletionRequired) return
        if (!validPhone(phone)) { message = "Enter a Kenyan mobile number, such as 0712345678."; return }
        perform {
            repository.completePhone(normalizePhone(phone))
            profile = repository.profile()
        }
    }

    fun uploadAvatar(resolver: ContentResolver, uri: Uri) = perform(onError = { error ->
        when {
            error is HttpException && error.code() == 404 -> { message = "Profile photo upload needs the updated Makao server."; true }
            error is VerificationImageRejected -> { message = "Choose a readable JPEG, PNG, WebP or HEIC photo under 25 MB and 100 megapixels."; true }
            error is MediaTransferFailed -> { message = "The photo could not be uploaded. Check your connection and try again."; true }
            error is HttpException && error.code() == 400 -> { message = "The photo could not be processed. Choose another photo and try again."; true }
            else -> false
        }
    }) {
        val bytes = withContext(Dispatchers.IO) { VerificationImages.fromUri(resolver, uri, forAvatar = true) }
        try {
            if (bytes.size > 5_242_880) { message = "Choose a smaller profile photo."; return@perform }
            repository.uploadAvatar(bytes)
            profile = repository.profile()
            message = "Profile photo updated."
        } finally { bytes.fill(0) }
    }

    fun deleteAvatar() = perform(onError = { error ->
        if (error is HttpException && error.code() == 404) { message = "Removing a photo needs the updated Makao server."; true }
        else false
    }) {
        repository.deleteAvatar()
        profile = repository.profile()
        message = "Profile photo removed."
    }

    fun clearAccountAction() { accountActionChallenge = null; message = null }

    fun startAccountAction(email: String, action: String) {
        if (!validEmail(email)) { message = "Enter your account email address."; return }
        accountActionChallenge = null
        perform(onError = ::accountActionBlocked) {
            accountActionChallenge = repository.startAccountAction(normalizeEmail(email), action)
        }
    }

    fun confirmAccountAction(email: String, action: String, code: String) {
        val pending = accountActionChallenge ?: return
        if (!code.matches(Regex("[0-9]{6}"))) { message = "Enter the six-digit email code."; return }
        perform(onError = ::accountActionBlocked) {
            repository.confirmAccountAction(normalizeEmail(email), action, pending.challengeId, code)
            repository.forgetSession()
            accountActionChallenge = null
            profile = null
            page = Page.LOGIN
            message = if (action == "DELETE")
                "Deletion request confirmed. Sign-in is disabled while staff review it."
            else "Account deactivated. Sign in and confirm reactivation when you are ready."
        }
    }

    private fun accountActionBlocked(error: Exception): Boolean {
        if (error !is HttpException || error.code() != 409) return false
        message = "Resolve any active booking, held payment, pending payout or dispute before changing your account status."
        return true
    }

    fun logout() = perform {
        lastActivityWall = 0L; lastActivityElapsed = 0L
        try { repository.logout() }
        finally { profile = null; challenge = null; page = Page.LOGIN }
    }

    /** Disable repeated taps and only advance after the backend accepts the request. */
    private fun perform(onError: (Exception) -> Boolean = { false }, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        activeAction = viewModelScope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (error is SessionExpired) { keepRoleOf(profile?.role); profile = null; page = Page.LOGIN }
                if (error is LoginRoleMismatch) { profile = null; challenge = null; page = Page.LOGIN }
                if (!onError(error)) message = friendlyError(error)
            }
            finally { busy = false }
        }
    }
}

internal fun normalizeEmail(value: String) = value.trim().lowercase(Locale.ROOT)
internal fun normalizePhone(value: String) = value.replace(Regex("[\\s-]"), "")
internal fun validEmail(value: String) = value.trim().length <= 320 &&
    value.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
internal fun passwordError(password: String, confirm: String): String? = when {
    password.isBlank() || password.length !in 8..72 || password.toByteArray(Charsets.UTF_8).size > 72 ->
        "Use at least 8 characters and no more than 72 UTF-8 bytes for your password."
    password != confirm -> "Your passwords do not match."
    else -> null
}
internal fun registrationError(name: String, email: String, phone: String, password: String,
    confirm: String, consent: Boolean): String? = when {
    name.trim().length !in 2..150 -> "Enter your full name (2–150 characters)."
    !validEmail(email) -> "Enter a valid email address."
    !validPhone(phone) -> "Enter a Kenyan mobile number, such as 0712345678."
    !consent -> "Accept the Terms of Service and Privacy Policy to continue."
    else -> passwordError(password, confirm)
}
internal fun validPhone(phone: String) = normalizePhone(phone).matches(Regex("^(?:\\+254|254|0)[17][0-9]{8}$"))
internal fun localKenyanPhone(phone: String): String? {
    val value = normalizePhone(phone)
    val local = when {
        value.startsWith("+254") -> "0" + value.drop(4)
        value.startsWith("254") -> "0" + value.drop(3)
        else -> value
    }
    return local.takeIf { it.matches(Regex("^0[17][0-9]{8}$")) }
}
