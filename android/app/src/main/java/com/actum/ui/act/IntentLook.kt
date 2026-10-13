package com.actum.ui.act

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.InkElevated
import com.actum.ui.theme.Look
import com.actum.model.ActumIntent
import com.actum.validate.FieldState

// Each kind of action has one colour and one icon, and wears them wherever it
// shows: its card, its Confirm button, its "done" line, its tab, its log row.
// Soft pastels on the dark ink; on light paper the same hues, deepened until
// they read as words and carry white text as a fill. Amber stays the app's
// own colour, and the colour of a question.

private fun pick(dark: Long, light: Long) = Color(if (Look.dark) dark else light)

val EventBlue get() = pick(0xFF8DB8F0, 0xFF2F6FD0)
val AlarmCoral get() = pick(0xFFF29B82, 0xFFD4532D)
val TodoMint get() = pick(0xFF86D3B0, 0xFF1C8F65)
val ExpenseGold get() = pick(0xFFE9C46A, 0xFFA87400)
val ContactLilac get() = pick(0xFFB9A4F0, 0xFF6A4FD4)
val DiaryRose get() = pick(0xFFEC9BB8, 0xFFC4407A)
val ExplainTeal get() = pick(0xFF7CCAD0, 0xFF157F89)
val RefusedGrey get() = pick(0xFF8C8076, 0xFF8A7F74)

/** Words on a fill of any of the colours above: near-black on the pastels, white on the deep ones. */
val OnIntent get() = pick(0xFF14100C, 0xFFFFFFFF)

/** A field Kotlin checked against the person's own words. */
val Verified get() = pick(0xFF8BCB8F, 0xFF2C8A4B)

/** A required field that is nowhere, or an action that failed. */
val Missing get() = pick(0xFFF08A7B, 0xFFC8432F)

fun colorOf(intent: ActumIntent): Color = when (intent) {
    ActumIntent.EVENT -> EventBlue
    ActumIntent.ALARM -> AlarmCoral
    ActumIntent.TODO -> TodoMint
    ActumIntent.EXPENSE -> ExpenseGold
    ActumIntent.CONTACT -> ContactLilac
    ActumIntent.DIARY -> DiaryRose
    ActumIntent.EXPLAIN -> ExplainTeal
    ActumIntent.UNSUPPORTED -> RefusedGrey
}

/** [color] at [share] over the lifted ink: the tint behind an icon or a "done" line. */
fun tint(color: Color, share: Float = 0.16f): Color = Color(
    red = color.red * share + InkElevated.red * (1 - share),
    green = color.green * share + InkElevated.green * (1 - share),
    blue = color.blue * share + InkElevated.blue * (1 - share),
)

/** The action's icon on a rounded square of its own colour, faint. */
@Composable
fun IntentBadge(intent: ActumIntent, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = colorOf(intent)
    Box(modifier.size(size).background(tint(color), RoundedCornerShape(size * 0.32f)), contentAlignment = Alignment.Center) {
        Glyph(Modifier.size(size * 0.56f), color) { intent(intent) }
    }
}

/** A tick, for a done line and a ticked to-do. */
@Composable
fun TickGlyph(modifier: Modifier, color: Color, weight: Float = 1.7f) = Glyph(modifier, color, weight) { tick() }

/** Three lines with their bullets: the log. */
@Composable
fun LogGlyph(modifier: Modifier, color: Color) = Glyph(modifier, color) {
    for (y in listOf(6f, 12f, 18f)) {
        line(8f, y, 19f, y)
        dot(4.5f, y)
    }
}

/** A microphone: its capsule, the cradle under it, and its stand. */
@Composable
fun MicGlyph(modifier: Modifier, color: Color) = Glyph(modifier, color) {
    box(9f, 3f, 6f, 11f, 3f)
    path { moveTo(5.5f, 11f); cubicTo(5.5f, 19.6f, 18.5f, 19.6f, 18.5f, 11f) }
    line(12f, 17.5f, 12f, 21f)
    line(9f, 21f, 15f, 21f)
}

@Composable
fun IntentGlyph(intent: ActumIntent, modifier: Modifier, color: Color) = Glyph(modifier, color) { intent(intent) }

/**
 * A field's state as a mark, not only a colour: a tick for checked against
 * the person's words, a question mark for unsure, an exclamation for missing.
 */
@Composable
fun StateMark(state: FieldState, modifier: Modifier = Modifier) {
    val color = when (state) {
        FieldState.VERIFIED -> Verified
        FieldState.UNSURE -> AmberBright
        FieldState.MISSING -> Missing
    }
    Box(modifier.size(20.dp).background(tint(color, 0.22f), CircleShape), contentAlignment = Alignment.Center) {
        Glyph(Modifier.size(12.dp), color, weight = 2.4f) {
            when (state) {
                FieldState.VERIFIED -> tick()
                FieldState.UNSURE -> {
                    path { moveTo(8.5f, 9f); cubicTo(8.5f, 4f, 15.5f, 4f, 15.5f, 8.5f); cubicTo(15.5f, 11.5f, 12f, 11.5f, 12f, 14.5f) }
                    dot(12f, 19f, 1.5f)
                }
                FieldState.MISSING -> {
                    line(12f, 5f, 12f, 14f)
                    dot(12f, 19f, 1.5f)
                }
            }
        }
    }
}

/** Icons are drawn on a 24-unit square with round-ended strokes, like the rest of the app's glyphs. */
private class Pen(val scope: DrawScope, val u: Float, val color: Color, val stroke: Stroke) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        scope.drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke.width, StrokeCap.Round)

    fun circle(x: Float, y: Float, r: Float) = scope.drawCircle(color, r * u, Offset(x * u, y * u), style = stroke)

    fun dot(x: Float, y: Float, r: Float = 1.1f) = scope.drawCircle(color, r * u, Offset(x * u, y * u))

    fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
        scope.drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), stroke)

    /** A path given in the 24-unit square. */
    fun path(build: Path.() -> Unit) = scope.scale(u, u, pivot = Offset.Zero) {
        drawPath(Path().apply(build), color, style = Stroke(stroke.width / u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun tick() = path { moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f) }

    fun intent(intent: ActumIntent) {
        when (intent) {
        // A calendar page with a tick on it.
        ActumIntent.EVENT -> {
            box(4f, 5.5f, 16f, 14.5f, 3f)
            line(4f, 10f, 20f, 10f)
            line(8.5f, 3.5f, 8.5f, 7.5f)
            line(15.5f, 3.5f, 15.5f, 7.5f)
            path { moveTo(9f, 15f); lineTo(11f, 17f); lineTo(15f, 12.5f) }
        }
        // A clock with its two bells.
        ActumIntent.ALARM -> {
            circle(12f, 13f, 7f)
            path { moveTo(12f, 9.5f); lineTo(12f, 13f); lineTo(14.4f, 14.6f) }
            line(5f, 4.5f, 3f, 6.5f)
            line(19f, 4.5f, 21f, 6.5f)
        }
        // A list, two of its lines ticked.
        ActumIntent.TODO -> {
            path { moveTo(4f, 7f); lineTo(5.6f, 8.6f); lineTo(8.5f, 5.5f) }
            path { moveTo(4f, 13.5f); lineTo(5.6f, 15.1f); lineTo(8.5f, 12f) }
            dot(5.3f, 19f)
            for (y in listOf(7f, 13.5f, 19f)) line(12f, y, 20f, y)
        }
        // The rupee sign.
        ActumIntent.EXPENSE -> {
            line(8f, 5f, 17f, 5f)
            line(8f, 9f, 17f, 9f)
            path { moveTo(8f, 5f); cubicTo(14.6f, 5f, 14.6f, 13f, 8f, 13f); lineTo(14f, 19.5f) }
        }
        ActumIntent.CONTACT -> {
            circle(12f, 9f, 3.6f)
            path { moveTo(5f, 19.5f); cubicTo(6f, 15.9f, 9f, 14.5f, 12f, 14.5f); cubicTo(15f, 14.5f, 18f, 15.9f, 19f, 19.5f) }
        }
        // A bound book with a clasp.
        ActumIntent.DIARY -> {
            box(5.5f, 4f, 13f, 16.5f, 2.5f)
            line(9f, 4f, 9f, 20.5f)
            line(12.5f, 9f, 15.5f, 9f)
            circle(14f, 14.5f, 1.2f)
        }
        // A page with a folded corner.
        ActumIntent.EXPLAIN -> {
            path { moveTo(6f, 4.5f); lineTo(14f, 4.5f); lineTo(18f, 8.5f); lineTo(18f, 19.5f); lineTo(6f, 19.5f); close() }
            line(9f, 12f, 15f, 12f)
            line(9f, 15.5f, 13f, 15.5f)
        }
        ActumIntent.UNSUPPORTED -> {
            circle(12f, 12f, 7.5f)
            line(7f, 17f, 17f, 7f)
        }
        }
    }
}

@Composable
private fun Glyph(modifier: Modifier, color: Color, weight: Float = 1.7f, draw: Pen.() -> Unit) {
    Canvas(modifier) {
        val u = minOf(size.width, size.height) / 24f
        if (u <= 0f) return@Canvas
        Pen(this, u, color, Stroke(weight * u, cap = StrokeCap.Round, join = StrokeJoin.Round)).draw()
    }
}
