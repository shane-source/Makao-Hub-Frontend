package com.makaohub.android.ui

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makaohub.android.R
import com.makaohub.android.data.SignupRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Brown = Color(0xFF573B2D)
val Cream = Color(0xFFFAF7F1)
val Ink = Color(0xFF403B36)
val Muted = Color(0xFF887F74)
val Border = Color(0xFFE7E0D6)
val Sand = Color(0xFFEDE2D7)

/** Native system bars stay native; all app-owned content follows the supplied reference. */
@Composable
fun MakaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Brown, onPrimary = Color.White,
        background = Cream, surface = Cream, onSurface = Ink, onBackground = Ink,
        outline = Border), content = content)
}

/** Scrollable on small screens and when the keyboard or large accessibility fonts are used. */
@Composable
fun PageBody(onBack: () -> Unit, enabled: Boolean = true, showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Brown, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("makao hub", color = Brown, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable fun Heading(text: String) {
    Text(text, color = Brown, fontSize = 28.sp, lineHeight = 33.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
}
@Composable fun BodyCopy(text: String, muted: Boolean = false) {
    Text(text, color = if (muted) Muted else Ink, fontSize = 15.sp, lineHeight = 22.sp)
}
@Composable fun Gap(height: Int = 20) { Spacer(Modifier.height(height.dp)) }

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun SkipButton(onSkip: () -> Unit) {
    Gap(20)
    TextButton(onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Skip walkthrough", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The exact photos are decoded from their regions in the user's supplied board.
 * No text, phone bezel or system status bar from the board is rendered as UI. */
@Suppress("DEPRECATION")
@Composable
private fun ReferencePhoto(index: Int, aspect: Float, description: String) {
    val resources = LocalContext.current.resources
    val bitmap by produceState<ImageBitmap?>(null, index) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.walkthrough_reference).use { stream ->
                val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
                try {
                    // Coordinates measured in the supplied 3488 × 2108 source.
                    val rect = when (index) {
                        0 -> Rect(48, 397, 776, 980)
                        1 -> Rect(936, 511, 1665, 1173)
                        else -> Rect(1824, 397, 2553, 899)
                    }
                    decoder.decodeRegion(rect, BitmapFactory.Options()).asImageBitmap()
                } finally { decoder.recycle() }
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(17.dp))
    bitmap?.let { Image(it, description, modifier, contentScale = ContentScale.Crop) }
        ?: Box(modifier.background(Sand))
}

@Composable private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}
@Composable fun Note(text: String, payment: Boolean = false) {
    Surface(color = if (payment) Color(0xFFFAEECB) else Sand, shape = RoundedCornerShape(12.dp)) {
        Text(text, color = if (payment) Color(0xFF9D640B) else Brown, fontSize = 13.sp,
            lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}
@Composable private fun ProgressLabel(step: Int) {
    Gap(20)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(true, null, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(5.dp))
        Text("WALKTHROUGH · $step OF 2", color = Brown, fontSize = 12.sp)
    }
}

@Composable
fun WelcomeScreen(onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    PageBody(onBack) {
        Heading("A safer place to start")
        Gap(22)
        BodyCopy("A home is more than an address.", muted = true)
        Gap(20)
        ReferencePhoto(0, 728f / 583, "Sunlit living room with plants and a balcony")
        Gap(20)
        WhiteCard {
            Text("Welcome to Makao Hub", fontSize = 17.sp, color = Brown)
            Gap(28)
            BodyCopy("Find a home that fits your life\nWhole homes, spare rooms and new beginnings.")
            Gap(24)
            BodyCopy("Pay with peace of mind\nClear payment states and move-in protection.")
        }
        Gap(20)
        Text("Made for renters, landlords and sublessors in Kenya.", fontSize = 12.sp, color = Ink)
        Gap(20)
        PrimaryButton("Continue walkthrough", onClick = onNext)
        SkipButton(onSkip)
    }
}

@Composable
fun HomesWalkthrough(onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    PageBody(onBack) {
        Heading("Find a home that fits your life")
        Gap(22)
        BodyCopy("From a place of your own to a room with good company.", muted = true)
        Gap(20)
        ReferencePhoto(1, 729f / 662, "Bright bedroom with warm bedding and a desk")
        ProgressLabel(1)
        Gap(24)
        BodyCopy("Explore verified whole homes and shared spaces in Nairobi, Mombasa and beyond. Clear rent and household details help you compare.")
        Gap(20)
        Note("Your search, your pace\nPublic listings show an approximate area. You choose when to unlock more.")
        Gap(20)
        PrimaryButton("Next", onClick = onNext)
        SkipButton(onSkip)
    }
}

@Composable
fun PeaceWalkthrough(onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    PageBody(onBack) {
        Heading("Pay with peace of mind")
        Gap(22)
        BodyCopy("Understand every step before you commit.", muted = true)
        Gap(20)
        ReferencePhoto(2, 729f / 502, "Comfortable living room opening onto a balcony")
        ProgressLabel(2)
        Gap(24)
        WhiteCard {
            Text("A safer starting point", fontSize = 17.sp, color = Brown)
            Gap(28)
            BodyCopy("Verified people\nRole-based checks help build trust.")
            Gap(24)
            BodyCopy("Protected communication\nPersonal contact stays masked in chat.")
            Gap(24)
            BodyCopy("Clear payment progress\nSee held, released and dispute statuses.")
        }
        Gap(20)
        Note("TEST / MOCK PAYMENT\nNo real money moves. This checkout simulates a payment state.", payment = true)
        Gap(20)
        PrimaryButton("Choose my role", onClick = onNext)
        SkipButton(onSkip)
    }
}

@Composable
fun RoleScreen(role: SignupRole, onSelect: (SignupRole) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    PageBody(onBack) {
        Heading("How will you use Makao Hub?")
        Gap(22)
        BodyCopy("Choose one role to begin. You can’t select more than one.", muted = true)
        Gap(20)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SignupRole.entries.forEach { item ->
                val selected = role == item
                Surface(color = if (selected) Sand else Color.White, shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brown else Border),
                    modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { onSelect(item) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(item.title, fontSize = 14.sp)
                        }
                        Gap(4)
                        BodyCopy(item.description)
                    }
                }
            }
        }
        Gap(20)
        Note("Your role stays with your account\nSaved by the server when you create your account. Switching roles later requires re-verification.")
        Gap(20)
        Text("Staff access is invite-only, on the website. It is not part of public signup.", fontSize = 12.sp, lineHeight = 18.sp)
        Gap(20)
        PrimaryButton("Continue as ${role.title}", onClick = onNext)
    }
}
