package com.actum.ui.capture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import com.actum.ui.common.Mark
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.actum.R
import com.actum.data.Memory
import com.actum.data.MemoryType
import com.actum.ui.common.DocumentGlyph
import com.actum.ui.home.monoStyle
import com.actum.ui.home.sans
import com.actum.ui.home.serif
import com.actum.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.roundToInt

sealed class CaptureState {
    object Saving : CaptureState()
    /** [memories] saved of the [total] offered: fewer when some could not be read or kept. */
    data class Saved(val memories: List<Memory>, val total: Int = memories.size) : CaptureState() {
        val count: Int get() = memories.size
    }
    data class Failed(val reason: String) : CaptureState()
    object Removed : CaptureState()
}

/**
 * The sheet a capture shows over the app the share came from.
 * One transition drives the scrim and the sheet both ways:
 * in over 420 ms on CubicBezierEasing(0.22, 1, 0.36, 1), out over 240 ms FastOutLinearIn.
 * [onDismiss] runs only once the exit has played and two frames have drawn.
 */
@Composable
fun CaptureSheet(
    state: CaptureState,
    note: String,
    onNoteChange: (String) -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showNoteField by remember { mutableStateOf(false) }
    // Not while saving: a shared content:// URI is readable only while the activity lives.
    val canClose = state !is CaptureState.Saving

    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { shown.targetState = true }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            shown.targetState = false
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            snapshotFlow { shown.isIdle && !shown.currentState }.first { it }
            withFrameNanos { }
            withFrameNanos { } // finish only after the exit has played and two frames have drawn
            onDismiss()
        }
    }

    val shownFraction by rememberTransition(shown, label = "capture sheet").animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
            } else {
                tween(durationMillis = 240, easing = FastOutLinearInEasing)
            }
        },
        label = "shown",
    ) { if (it) 1f else 0f }

    BackHandler {
        if (canClose) close()
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val flingVelocity = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    val dismissThreshold = with(LocalDensity.current) { 96.dp.toPx() }

    suspend fun settleDrag(velocity: Float) {
        val goes = sheetHeight > 0 && (
            velocity > flingVelocity ||
                (velocity >= -flingVelocity && dragOffset > dismissThreshold)
        )
        val target = if (goes) sheetHeight.toFloat() else 0f
        animate(dragOffset, target, initialVelocity = velocity) { value, _ -> dragOffset = value }
        if (goes) close()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim: CaptureScrim at alpha 0.62, full size, its alpha the sheet's shown fraction;
        // a tap dismisses (when dismissible).
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val dragged = if (sheetHeight > 0) (dragOffset / sheetHeight).coerceIn(0f, 1f) else 0f
                    alpha = SCRIM_ALPHA * shownFraction * (1f - dragged)
                }
                .background(CaptureScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canClose && !closing,
                ) { close() }
        )

        // The sheet: full width at bottom, top corners 24dp, background surface (Ink),
        // 1dp hairline in outlineVariant (InkBorder) along top edge only (both corner arcs and the line between).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = canClose && !closing,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    },
                    onDragStopped = { velocity -> settleDrag(velocity) },
                )
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        val hidden = (1f - shownFraction) * placeable.height
                        placeable.placeRelative(0, (hidden + dragOffset).roundToInt())
                    }
                }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val half = strokeWidth / 2f
                    val r = 24.dp.toPx()
                    if (size.width > 2 * r) {
                        val hairlinePath = Path().apply {
                            moveTo(half, r)
                            arcTo(
                                rect = Rect(half, half, 2 * r - half, 2 * r - half),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false,
                            )
                            lineTo(size.width - r, half)
                            arcTo(
                                rect = Rect(size.width - 2 * r + half, half, size.width - half, 2 * r - half),
                                startAngleDegrees = 270f,
                                sweepAngleDegrees = 90f,
                                forceMoveTo = false,
                            )
                        }
                        drawPath(hairlinePath, color = InkBorder, style = Stroke(width = strokeWidth))
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* swallow touches */ }
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Grabber: centred, bottom padding 14, 36×4dp, radius 2, outlineVariant.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                }

                // Content: a Column with animateContentSize(tween(320)), spacing 18
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(tween(320)),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Header: Row, centred, spacing 14
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SavedMark(
                            play = state is CaptureState.Saved,
                            size = 42.dp,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val titleText = when (state) {
                                is CaptureState.Saving -> stringResource(R.string.capture_saving)
                                is CaptureState.Saved -> when {
                                    state.count < state.total -> stringResource(
                                        R.string.capture_saved_some,
                                        state.count,
                                        state.total
                                    )
                                    state.count == 1 -> stringResource(R.string.capture_saved)
                                    else -> stringResource(R.string.capture_saved_many, state.count)
                                }
                                is CaptureState.Removed -> stringResource(R.string.capture_removed)
                                is CaptureState.Failed -> state.reason
                            }
                            Text(
                                text = titleText,
                                style = serif(
                                    size = 23.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 27.sp,
                                ),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (state is CaptureState.Saved) {
                                val kind = SheetText.kindLine(state.memories)
                                if (kind.isNotEmpty()) {
                                    Text(
                                        text = kind,
                                        style = monoStyle(12.sp, color = MaterialTheme.colorScheme.outline),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    when (state) {
                        is CaptureState.Saving -> {
                            // Saving: nothing more
                        }

                        is CaptureState.Saved -> {
                            // Preview (compact while note is being written)
                            SavedPreview(
                                memories = state.memories,
                                isCompact = showNoteField,
                            )

                            // Note
                            if (!showNoteField) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showNoteField = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("+", style = monoStyle(15.sp, color = AmberBright))
                                    Text(
                                        stringResource(R.string.capture_add_note),
                                        style = monoStyle(12.sp, color = MaterialTheme.colorScheme.outline),
                                    )
                                }
                            } else {
                                val focusRequester = remember { FocusRequester() }
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                ) {
                                    BasicTextField(
                                        value = note,
                                        onValueChange = onNoteChange,
                                        textStyle = sans(
                                            size = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 21.sp,
                                        ),
                                        cursorBrush = SolidColor(AmberBright),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        maxLines = 5,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        decorationBox = { innerTextField ->
                                            if (note.isEmpty()) {
                                                Text(
                                                    stringResource(R.string.capture_note_hint),
                                                    style = sans(
                                                        size = 15.sp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        lineHeight = 21.sp,
                                                    ),
                                                )
                                            }
                                            innerTextField()
                                        },
                                    )
                                }
                            }

                            // Buttons: Row, spacing 10, of two equal buttons: Undo (secondary) and Done (primary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = onUndo,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.capture_undo),
                                        style = sans(
                                            15.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            weight = FontWeight.SemiBold,
                                        ),
                                    )
                                }
                                Button(
                                    onClick = close,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberBright,
                                        contentColor = AmberOnContainer,
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.capture_done),
                                        style = sans(
                                            15.sp,
                                            color = AmberOnContainer,
                                            weight = FontWeight.SemiBold,
                                        ),
                                    )
                                }
                            }
                        }

                        is CaptureState.Failed -> {
                            // Failed: one full-width Close (secondary)
                            Button(
                                onClick = close,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text(
                                    stringResource(R.string.capture_close),
                                    style = sans(
                                        15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        weight = FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }

                        is CaptureState.Removed -> {
                            LaunchedEffect(Unit) {
                                delay(800)
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The mark in the sheet's header: faint while the save is under way, and once
 * it is saved it arrives, growing to full size with a small bounce.
 */
@Composable
fun SavedMark(
    play: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (play) {
            arrival.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        } else {
            arrival.snapTo(0f)
        }
    }
    Mark(
        modifier
            .size(size)
            .graphicsLayer {
                val p = arrival.value
                alpha = 0.35f + 0.65f * p.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * p
                scaleY = 0.8f + 0.2f * p
            },
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Preview cards
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SavedPreview(
    memories: List<Memory>,
    isCompact: Boolean,
) {
    val first = memories.firstOrNull() ?: return
    when {
        memories.size > 1 -> TileRow(memories)
        first.type == MemoryType.IMAGE -> SinglePhoto(first, isCompact)
        first.type == MemoryType.PDF -> PdfCard(first, isCompact)
        first.type == MemoryType.LINK -> LinkCard(first)
        else -> TextPreview(first, isCompact)
    }
}

/** One picture: height = width × aspect clamped 160–300dp (220 until loaded; 110 while compact). */
@Composable
private fun SinglePhoto(
    memory: Memory,
    isCompact: Boolean,
) {
    val uri = memory.localUri ?: return
    var ratio by remember(uri) { mutableStateOf<Float?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val height = if (isCompact) {
                110.dp
            } else {
                ratio?.let { (maxWidth / it).coerceIn(160.dp, 300.dp) } ?: 220.dp
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(uri))
                    .build(),
                contentDescription = stringResource(R.string.capture_picture),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                onSuccess = { success ->
                    val size = success.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(InkMedia),
            )
        }
        val caption = memory.rawText?.trim()?.takeIf { it.isNotEmpty() }
        if (caption != null && !isCompact) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = caption,
                style = sans(
                    14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

/** Several files: up to 3 square tiles sharing width, DocumentGlyph 34×44 for PDF, "+N" on last tile. */
@Composable
private fun TileRow(memories: List<Memory>) {
    val tiles = memories.take(MAX_TILES)
    val more = memories.size - tiles.size
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEachIndexed { index, memory ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val uri = memory.localUri
                if (memory.type == MemoryType.IMAGE && uri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(Uri.parse(uri))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DocumentGlyph(
                        modifier = Modifier.size(width = 34.dp, height = 44.dp),
                    )
                }
                if (index == tiles.lastIndex && more > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+$more",
                            style = sans(
                                20.sp,
                                color = Color.White,
                                weight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** A PDF: padding 16, spacing 12: Row: DocumentGlyph 40×52, title in 16sp Medium over "pdf · size". */
@Composable
private fun PdfCard(
    memory: Memory,
    isCompact: Boolean,
) {
    val context = LocalContext.current
    val size = remember(memory.localUri) {
        memory.localUri?.let { Uri.parse(it).path }?.let { File(it).length() }?.takeIf { it > 0 }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentGlyph(
                modifier = Modifier.size(width = 40.dp, height = 52.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = memory.title ?: stringResource(R.string.capture_document),
                    style = sans(
                        16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Medium,
                        lineHeight = 21.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    "pdf",
                    size?.let { Formatter.formatShortFileSize(context, it) },
                ).joinToString(" · ")
                Text(
                    text = subtitle,
                    style = monoStyle(11.sp, color = MaterialTheme.colorScheme.outline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val caption = memory.rawText?.trim()?.takeIf { it.isNotEmpty() }
        if (caption != null && !isCompact) {
            Text(
                text = caption,
                style = sans(
                    14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A link: padding 16, spacing 8: 7dp primary dot + host, headline, URL in mono 11sp outline. */
@Composable
private fun LinkCard(memory: Memory) {
    val card = SheetText.linkCard(memory.rawText, memory.title)
    if (card == null) {
        TextPreview(memory, isCompact = false)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(AmberBright, CircleShape),
            )
            Text(
                text = card.host,
                style = monoStyle(12.sp, color = AmberBright),
                maxLines = 1,
            )
        }
        val headline = card.headline ?: memory.rawText ?: ""
        if (headline.isNotEmpty()) {
            Text(
                text = headline,
                style = sans(
                    16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    lineHeight = 22.sp,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.headline != null) {
            Text(
                text = card.url,
                style = monoStyle(11.sp, color = MaterialTheme.colorScheme.outline),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Text: padding 16, text in 16sp lineHeight 23 onSurface, at most 6 lines (2 while compact). */
@Composable
private fun TextPreview(
    memory: Memory,
    isCompact: Boolean,
) {
    val text = memory.rawText ?: ""
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = text.take(600),
            style = sans(
                16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 23.sp,
            ),
            maxLines = if (isCompact) 2 else 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A downward fling this fast closes the sheet from anywhere. */
private val FLING_VELOCITY = 125.dp

/** The scrim's dim when the sheet is fully up. */
private const val SCRIM_ALPHA = 0.62f

/** Several files show this many tiles at most, "+N" on the last for the rest. */
private const val MAX_TILES = 3
