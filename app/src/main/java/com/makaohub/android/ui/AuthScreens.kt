package com.makaohub.android.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.makaohub.android.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

@Composable private fun RequestState(model: AuthViewModel) {
    if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Gap(12) }
    model.message?.let { Note(it); Gap(16) }
}

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit,
    password: Boolean = false, type: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type),
        trailingIcon = if (password) {{
            IconButton({ visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "Hide password" else "Show password")
            }
        }} else null,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
}

@Composable fun RegisterScreen(model: AuthViewModel, back: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    PageBody(back, !model.busy) {
        Heading("Create your account")
        Gap(16); BodyCopy("Join Makao Hub as a ${model.role.title}.", muted = true); Gap()
        RequestState(model)
        Field("Full name", name, { name = it }, enabled = !model.busy)
        Field("Email address", email, { email = it }, type = KeyboardType.Email, enabled = !model.busy)
        Field("Phone number", phone, { phone = it }, type = KeyboardType.Phone, enabled = !model.busy)
        Field("Password", password, { password = it }, password = true, enabled = !model.busy)
        Field("Confirm password", confirm, { confirm = it }, password = true, enabled = !model.busy)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consent, { consent = it }, enabled = !model.busy)
            Text("I accept the Terms of Service and Privacy Policy.", modifier = Modifier.weight(1f))
        }
        Gap(12)
        PrimaryButton("Create account", !model.busy) {
            model.register(name, email, phone, password, confirm, consent)
        }
        Gap(12)
        GoogleButton(model, validate = {
            when {
                name.trim().length !in 2..150 -> "Enter your full name before continuing with Google."
                !validPhone(phone) -> "Enter a Kenyan mobile number before continuing with Google."
                !consent -> "Accept the Terms of Service and Privacy Policy to continue."
                else -> null
            }
        }) { model.googleRegister(it, name, phone, consent) }
        TextButton({ model.navigate(Page.LOGIN) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable fun LoginScreen(model: AuthViewModel, back: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    PageBody(back, !model.busy) {
        Heading("Welcome back")
        Gap(16); BodyCopy("Log in to manage your homes and matches.", muted = true); Gap()
        RequestState(model)
        Field("Email address", email, { email = it }, type = KeyboardType.Email, enabled = !model.busy)
        Field("Password", password, { password = it }, password = true, enabled = !model.busy)
        TextButton({ model.navigate(Page.FORGOT) }, enabled = !model.busy, modifier = Modifier.align(Alignment.End)) {
            Text("Forgot password?")
        }
        PrimaryButton("Sign in", !model.busy) { model.login(email, password) }
        Gap(12)
        GoogleButton(model, onToken = model::googleLogin)
        TextButton({ model.navigate(Page.ROLE) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Don’t have an account? Sign up")
        }
    }
}

@Composable fun OtpScreen(model: AuthViewModel, back: () -> Unit) {
    var code by remember { mutableStateOf("") }
    PageBody(back, !model.busy) {
        Heading("Verify your email")
        Gap(20)
        BodyCopy("Enter the six-digit code sent to ${model.challenge?.maskedEmail.orEmpty()}. Check your inbox and spam folder.", muted = true)
        Gap(16)
        RequestState(model)
        Field("6-digit verification code", code, { code = it.filter { c -> c in '0'..'9' }.take(6) },
            type = KeyboardType.NumberPassword, enabled = !model.busy)
        PrimaryButton("Verify email", !model.busy && code.length == 6) { model.verify(code) }
        Gap(20)
        BodyCopy("Didn’t receive a code? Return to sign in and request a new one.", muted = true)
        // The backend has no resend endpoint; never render a fake resend success.
        TextButton(back, enabled = !model.busy) { Text("Return to sign in") }
        val expiry = model.challenge?.expiresAt
        if (expiry != null && runCatching { Instant.parse(expiry).isBefore(Instant.now()) }.getOrDefault(false)) {
            Note("This code has expired. Return to sign in to request a new one.")
        }
    }
}

@Composable fun ForgotScreen(model: AuthViewModel, back: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    PageBody(back, !model.busy) {
        Heading("Let’s get you back in.")
        Gap(20); BodyCopy("Enter your account email to request a password reset code.", muted = true); Gap()
        RequestState(model)
        Field("Account email", email, { email = it }, type = KeyboardType.Email, enabled = !model.busy)
        PrimaryButton("Send reset code", !model.busy) { model.forgot(email) }
    }
}

@Composable fun ResetScreen(model: AuthViewModel, back: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    PageBody(back, !model.busy) {
        Heading("A fresh start.")
        Gap(20); BodyCopy("Use the six-digit code sent to your account email. Check your inbox and spam folder.", muted = true); Gap()
        RequestState(model)
        Field("Verification code", code, { code = it.filter { c -> c in '0'..'9' }.take(6) },
            type = KeyboardType.NumberPassword, enabled = !model.busy)
        Field("New password", password, { password = it }, password = true, enabled = !model.busy)
        Field("Confirm password", confirm, { confirm = it }, password = true, enabled = !model.busy)
        PrimaryButton("Save new password", !model.busy) { model.reset(code, password, confirm) }
    }
}

/** Displays a real profile response rather than fabricated listings after login. */
@Composable fun AccountScreen(model: AuthViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PageBody({}, !model.busy, showBack = false) {
        Heading("Your account")
        Gap(20)
        RequestState(model)
        model.profile?.let { profile ->
            BodyCopy(profile.fullName)
            BodyCopy(profile.email, muted = true)
            Gap(20)
            Note("Active role: ${profile.role}\nAccount: ${profile.accountStatus}\nVerification: ${profile.verificationStatus ?: "Not started"}")
            Gap(20)
            BodyCopy("Your account details are loaded from Makao Hub.", muted = true)
        }
        Gap(20)
        PrimaryButton("Refresh profile", !model.busy, model::reloadProfile)
        TextButton(onClick = {
            model.logout()
            scope.launch {
                try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* API session clearing is independent of credential provider availability. */ }
            }
        }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable private fun GoogleButton(model: AuthViewModel, validate: () -> String? = { null }, onToken: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requesting by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val problem = validate()
        if (problem != null) { model.showMessage(problem); return@OutlinedButton }
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            model.showMessage("Google sign-in needs this app’s Google client configuration before it can be used.")
            return@OutlinedButton
        }
        requesting = true
        scope.launch {
            try { onToken(getGoogleToken(context)) }
            catch (_: GetCredentialCancellationException) { model.showMessage("Google sign-in was cancelled.") }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.showMessage("Google sign-in could not complete. Check your Google account and try again.") }
            finally { requesting = false }
        }
    }, enabled = !model.busy && !requesting, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
        Text(if (requesting) "Connecting to Google…" else "Continue with Google")
    }
}

/** Android obtains the Google token; Spring Boot verifies it and creates the session. */
private suspend fun getGoogleToken(context: Context): String {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
