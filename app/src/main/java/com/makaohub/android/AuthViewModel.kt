package com.makaohub.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makaohub.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

enum class Page { SPLASH, ENTRY, WELCOME, HOMES, PEACE, ROLE, REGISTER, LOGIN, OTP, FORGOT, RESET, ACCOUNT }

/** Keeps requests alive through rotation, without persisting credentials in saved state. */
class AuthViewModel : ViewModel() {
    internal val repository = AuthRepository(createMakaoApi(BuildConfig.API_BASE_URL))
    var page by mutableStateOf(Page.SPLASH); private set
    var role by mutableStateOf(SignupRole.RENTER); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var challenge by mutableStateOf<OtpChallenge?>(null); private set
    var profile by mutableStateOf<MyProfile?>(null); private set
    var resetEmail by mutableStateOf(""); private set
    private var otpFlow = OtpFlow.LOGIN

    /** Timer and tap share this guard; a late completion must not replace another page. */
    fun finishSplash() {
        if (page == Page.SPLASH) navigate(Page.ENTRY)
    }

    fun navigate(target: Page) {
        if (busy) return
        message = null
        if (page == Page.OTP && target != Page.OTP) challenge = null
        page = target
    }
    fun selectRole(value: SignupRole) { if (!busy) role = value }
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
            challenge = repository.login(LoginRequest(normalizeEmail(email), password))
            otpFlow = OtpFlow.LOGIN
            page = Page.OTP
        }
    }
    fun googleLogin(idToken: String) = perform {
        challenge = repository.google(idToken)
        otpFlow = OtpFlow.GOOGLE_LOGIN
        page = Page.OTP
    }
    fun googleRegister(idToken: String, name: String, phone: String, consent: Boolean) = perform {
        challenge = repository.registerGoogle(GoogleRegisterRequest(idToken, name.trim(), normalizePhone(phone), role, consent))
        otpFlow = OtpFlow.REGISTRATION
        page = Page.OTP
    }
    fun verify(code: String) {
        val pending = challenge ?: return
        if (!code.matches(Regex("[0-9]{6}"))) { message = "Enter the six-digit verification code."; return }
        perform {
            repository.verify(pending.challengeId, code, otpFlow)
            challenge = null
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

    fun logout() = perform {
        try { repository.logout() }
        finally { profile = null; challenge = null; page = Page.LOGIN }
    }

    /** Disable repeated taps and only advance after the backend accepts the request. */
    private fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (error is SessionExpired) { profile = null; page = Page.LOGIN }
                message = friendlyError(error)
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
