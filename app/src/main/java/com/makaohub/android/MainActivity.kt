package com.makaohub.android

import android.os.Bundle
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
import com.makaohub.android.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT))
        setContent { MakaoApp() }
    }
}

/** Routes only through the current ViewModel; backend responses own authenticated state. */
@Composable
private fun MakaoApp(model: AuthViewModel = viewModel()) {
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
                    delay(2500L)
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
                if (profile?.role == "LANDLORD") LandlordWorkspace(model, profile)
                else if (profile?.role == "SUBLESSOR") SublessorWorkspace(model, profile)
                else if (profile?.role == "RENTER") RenterWorkspace(model, profile)
                else AccountScreen(model)
            }
        }
    }
}

@Composable private fun Splash(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brown)) {
        // The owner's artwork includes the logo/headline; preserve it without cropping.
        Image(
            painter = painterResource(R.drawable.makao_splash_screen),
            contentDescription = "Makao Hub. Find Your Dream Home.",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
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
