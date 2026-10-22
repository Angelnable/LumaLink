package com.lumalink.g100d.ui.component

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Full-screen frosted backdrop. Colour fields are static, so the transfer UI
 * never pays for a live blur; glass surfaces above remain translucent and
 * bordered. Android 12+ receives a real blur on the colour fields.
 */
@Composable
fun LumaGlassBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val top = if (dark) Color(0xFF1E2025) else Color(0xFFFFFFFF)
    val bottom = if (dark) Color(0xFF08090B) else Color(0xFFDDE3DD)
    val blue = if (dark) Color(0xFF8FA6B8).copy(alpha = 0.27f) else Color(0xFF9DC9E6).copy(alpha = 0.78f)
    val pink = if (dark) Color(0xFFA5969A).copy(alpha = 0.18f) else Color(0xFFF0B9C0).copy(alpha = 0.63f)
    val green = if (dark) Color(0xFF9AA79D).copy(alpha = 0.15f) else Color(0xFFA9C4AE).copy(alpha = 0.54f)
    val scrim = if (dark) Color(0xC0101114) else Color(0x26FBFBF9)
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.fillMaxSize().blur(56.dp)
    } else {
        Modifier.fillMaxSize()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        Canvas(modifier = blurModifier) {
            drawCircle(
                color = blue,
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.88f, size.height * 0.04f),
            )
            drawCircle(
                color = pink,
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.02f, size.height * 0.30f),
            )
            drawCircle(
                color = green,
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.52f, size.height * 1.02f),
            )
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.9f, size.height * 0.6f),
                ),
            )
        }
        Box(Modifier.fillMaxSize().background(scrim))
        content()
    }
}
