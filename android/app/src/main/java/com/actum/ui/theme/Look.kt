package com.actum.ui.theme

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Which of the two grounds is showing: dark ink or light paper. The person's
 * choice, kept between launches; dark until they choose. Snapshot state, so
 * everything drawn with the palette's names redraws the moment it changes.
 */
object Look {
    var dark by mutableStateOf(true)
        private set

    private const val FILE = "look"
    private const val KEY = "dark"

    fun load(context: Context) {
        dark = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun toggle(context: Context) {
        dark = !dark
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, dark).apply()
    }
}

/**
 * The switch between the two: a sun while it is dark, a moon while it is
 * light, each showing where a tap goes. A 48 dp target around a small mark.
 */
@Composable
fun LookToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val color = BoneDim
    val dark = Look.dark
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { Look.toggle(context) }
            .semantics { contentDescription = if (dark) "Switch to light" else "Switch to dark" },
        contentAlignment = Alignment.Center,
    ) {
        // Offscreen, so the moon's bite is cut out of the moon and not out of what is behind it.
        Canvas(Modifier.size(20.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val u = size.minDimension / 24f
            val centre = Offset(12f * u, 12f * u)
            if (dark) {
                drawCircle(color, 4.2f * u, centre, style = Stroke(1.8f * u))
                for (i in 0 until 8) {
                    val angle = Math.PI / 4 * i
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(color, centre + direction * (7.4f * u), centre + direction * (9.8f * u), 1.8f * u, StrokeCap.Round)
                }
            } else {
                drawCircle(color, 8.2f * u, centre)
                drawCircle(color, 7.2f * u, Offset(16.2f * u, 8.6f * u), blendMode = BlendMode.Clear)
            }
        }
    }
}
