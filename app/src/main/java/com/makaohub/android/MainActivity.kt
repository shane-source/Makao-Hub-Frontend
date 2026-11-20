package com.makaohub.android

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.platform.LocalContext
import com.makaohub.android.data.AndroidSessionStore
import com.makaohub.android.data.OfflineHomes
import com.makaohub.android.data.AuthRepository
import com.makaohub.android.data.createMakaoApi
import com.makaohub.android.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var checkoutReturnSignal by mutableIntStateOf(0)
    internal var activityListener: (() -> Unit)? = null

    override fun onUserInteraction() {
        super.onUserInteraction()
        activityListener?.invoke()
    }

    override fun onResume() {
        super.onResume()
        activityListener?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT))
        if (isPaystackReturnUri(intent?.dataString)) checkoutReturnSignal++
        setContent {
            CompositionLocalProvider(LocalCheckoutReturnSignal provides checkoutReturnSignal) { MakaoApp() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isPaystackReturnUri(intent.dataString)) checkoutReturnSignal++
    }
}

/** Routes only through the current ViewModel; backend responses own authenticated state. */
@Composable
private fun MakaoApp() {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(AuthRepository(createMakaoApi(BuildConfig.API_BASE_URL), AndroidSessionStore(context)), OfflineHomes(context, BuildConfig.API_BASE_URL), LaunchMemory(context)) as T
    } }
    val model: AuthViewModel = viewModel(factory = factory)
    val activity = LocalContext.current as? MainActivity
    DisposableEffect(activity, model) {
        val listener = { model.noteUserActivity(System.currentTimeMillis(), SystemClock.elapsedRealtime()) }
        activity?.activityListener = listener
        onDispose { if (activity?.activityListener === listener) activity.activityListener = null }
    }
    LaunchedEffect(model.profile?.id, model.profile?.role) { model.rememberSignedInUser() }
    LaunchedEffect(model.page, model.profile?.id, model.offlineSession) {
        if ((model.page == Page.ACCOUNT && model.profile != null) || (model.page == Page.OFFLINE && model.offlineSession)) {
            model.noteUserActivity(System.currentTimeMillis(), SystemClock.elapsedRealtime())
            while (true) {
                delay(1_000)
                model.checkInactivity(System.currentTimeMillis(), SystemClock.elapsedRealtime())
            }
        }
    }
    val back = {
        model.navigate(when (model.page) {
            Page.WELCOME -> Page.ENTRY
            Page.HOMES -> Page.WELCOME
            Page.PEACE -> Page.HOMES
            Page.ROLE -> Page.PEACE
            Page.REGISTER -> Page.ROLE
            Page.LOGIN -> Page.ROLE
            Page.OTP, Page.FORGOT -> Page.LOGIN
            Page.RESET -> Page.FORGOT
            else -> Page.ENTRY
        })
    }
    BackHandler(enabled = model.page !in listOf(Page.SPLASH, Page.ENTRY, Page.ACCOUNT)) { back() }
    MakaoTheme {
        when (model.page) {
            Page.SPLASH -> {
                LaunchedEffect(Unit) {
                    delay(600L)
                    model.finishSplash()
                }
                Box(Modifier.fillMaxSize().clickable(
                    onClickLabel = "Continue to welcome",
                    role = Role.Button,
                    onClick = model::finishSplash
                )) {
                    Splash(modifier = Modifier.fillMaxSize())
                }
            }
            Page.OFFLINE -> OfflineRenterDashboard(model)
            Page.ENTRY -> EntryScreen { model.navigate(Page.WELCOME) }
            Page.WELCOME -> WelcomeScreen(back, { model.navigate(Page.HOMES) }, { model.navigate(Page.ROLE) })
            Page.HOMES -> HomesWalkthrough(back, { model.navigate(Page.PEACE) }, { model.navigate(Page.ROLE) })
            Page.PEACE -> PeaceWalkthrough(back, { model.navigate(Page.ROLE) }, { model.navigate(Page.ROLE) })
            Page.ROLE -> RoleScreen(model.role, model::selectRole, back) { model.navigate(Page.REGISTER) }
            Page.REGISTER -> RegisterScreen(model, back)
            Page.LOGIN -> LoginScreen(model, back)
            Page.OTP -> OtpScreen(model, back)
            Page.FORGOT -> ForgotScreen(model, back)
            Page.RESET -> ResetScreen(model, back)
            Page.ACCOUNT -> {
                val profile = model.profile
                if (model.phoneCompletionRequired) PhoneCompletionScreen(model)
                else if (profile != null && profile.role in setOf("LANDLORD", "SUBLESSOR", "RENTER")) {
                    NotificationHost(model, profile) {
                        when (profile.role) {
                            "LANDLORD" -> LandlordWorkspace(model, profile)
                            "SUBLESSOR" -> SublessorWorkspace(model, profile)
                            "RENTER" -> RenterWorkspace(model, profile)
                        }
                    }
                } else if (profile == null && model.message == null) SigningInLoader()
                else AccountScreen(model)
            }
        }
    }
}

/** Shown while the profile loads after sign-in or sign-up, so no account/log out screen flashes. */
@Composable private fun SigningInLoader() {
    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(color = Brown)
        }
    }
}

@Composable private fun Splash(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brown)) {
        // Fill every screen shape. Anchored to the top so the logo and headline always stay visible;
        // taller or wider phones only trim the lawn at the bottom.
        Image(
            painter = painterResource(R.drawable.makao_splash_screen),
            contentDescription = "Makao Hub. Find Your Dream Home.",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.Alignment.TopCenter
        )
    }
}

@Composable private fun EntryScreen(onCreate: () -> Unit) {
    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Image(painterResource(R.drawable.makao_splash), "Welcome home",
                modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Crop)
            Heading("Welcome home.")
            BodyCopy("Your space. Your people.", muted = true)
            PrimaryButton("Explore Makao Hub", onClick = onCreate)
        }
    }
}
