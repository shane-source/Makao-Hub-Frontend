package com.makaohub.android.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val PREFS = "makao_celebrated"

/**
 * Shows a short celebration the first time something good happens, once per item.
 * With [seed] on, whatever already exists the first time this runs is marked as seen without a
 * celebration, so people who updated the app are not shown old news.
 */
@Composable
fun CelebrateNew(group: String, ids: List<String>, seed: Boolean, title: String, body: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val seededKey = "seeded:$group"
    if (seed && !prefs.getBoolean(seededKey, false)) {
        SideEffect {
            prefs.edit().apply { ids.forEach { putBoolean("$group:$it", true) }; putBoolean(seededKey, true) }.apply()
        }
        return
    }
    val next = ids.firstOrNull { !prefs.getBoolean("$group:$it", false) } ?: return
    var open by remember(group, next) { mutableStateOf(true) }
    // Marked as seen straight away, so it can never repeat, even if the app is closed mid-way.
    LaunchedEffect(group, next) { prefs.edit().putBoolean("$group:$next", true).apply() }
    if (open) Dialog(onDismissRequest = { open = false }) { CelebrationCard(title, body) { open = false } }
}

private class Piece(val x: Float, val delay: Float, val size: Float, val color: Color, val drift: Float)

@Composable
private fun CelebrationCard(title: String, body: String, onDone: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    val pieces = remember {
        val colors = listOf(Color(0xFFE9A23B), Color(0xFF417653), Color(0xFFC8503C), Color(0xFF573B2D), Color(0xFFF1C27D))
        List(30) { i ->
            Piece(Random.nextFloat(), Random.nextFloat() * 0.4f, 3f + Random.nextFloat() * 3f,
                colors[i % colors.size], (Random.nextFloat() - 0.5f) * 0.2f)
        }
    }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        fall.animateTo(1f, tween(2400, easing = LinearEasing))
    }

    Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                pieces.forEach { p ->
                    val t = ((fall.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                    if (t > 0f && t < 1f) {
                        drawCircle(p.color, radius = p.size.dp.toPx(),
                            center = Offset((p.x + p.drift * t) * size.width, -20f + t * (size.height + 40f)))
                    }
                }
            }
            Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF417653), modifier = Modifier.scale(pop.value)) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White,
                        modifier = Modifier.padding(18.dp).size(44.dp))
                }
                Text(title, color = Brown, fontFamily = FontFamily.Serif, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(body, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brown)) { Text("Continue") }
            }
        }
    }
}
