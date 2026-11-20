package com.makaohub.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Coloured banner that opens a screen: a big icon, a bold title and a short line under it. */
@Composable internal fun HeroBanner(color: Color, icon: ImageVector, title: String, subtitle: String = "") {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f))))) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = Color.White.copy(alpha = 0.22f), shape = CircleShape) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.padding(14.dp).size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** A label on the left and a value on the right, used for amounts. */
@Composable internal fun AmountRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (bold) Ink else Muted, fontSize = if (bold) 17.sp else 14.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, color = if (bold) Brown else Ink, fontSize = if (bold) 20.sp else 14.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

/** Rounded white card with a soft shadow for key information. */
@Composable internal fun HighlightCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBD9BF)),
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
