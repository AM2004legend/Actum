package com.actum.ui.act

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actum.ui.common.Grabber
import com.actum.ui.home.monoStyle
import com.actum.ui.home.sans
import com.actum.ui.home.serif
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.AmberOnContainer
import com.actum.ui.theme.Bone
import com.actum.ui.theme.BoneDim
import com.actum.ui.theme.InkElevated
import com.actum.ui.theme.InkFieldBorder
import com.actum.ui.theme.InkOutline
import com.actum.act.Actor
import com.actum.act.Outcome
import com.actum.model.ActumIntent
import com.actum.validate.CheckedField
import com.actum.validate.FieldState
import com.actum.validate.Verdict
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What Actum made of a request: progress while Gemma reads it, then one card
 * per action, each waiting for its own Confirm. Nothing here runs anything
 * until that tap.
 */
@Composable
fun ColumnScope.ActSheet(state: Actor.State, actor: Actor, onClose: () -> Unit) {
    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            Actor.State.Idle -> Unit
            is Actor.State.Working -> {
                Text("Working on it", style = serif(26.sp, lineHeight = 30.sp))
                Asked(state.input)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AmberBright, trackColor = InkElevated)
                Text(state.step, style = sans(15.sp, BoneDim, lineHeight = 22.sp))
            }
            is Actor.State.Refused -> {
                Text("Actum can't do this yet", style = serif(26.sp, lineHeight = 30.sp))
                Asked(state.input)
                Text(state.reason, style = sans(15.sp, BoneDim, lineHeight = 22.sp))
            }
            is Actor.State.Ready -> {
                val context = LocalContext.current
                Text(if (state.cards.size == 1) "One thing to confirm" else "${state.cards.size} things to confirm", style = serif(26.sp, lineHeight = 30.sp))
                Asked(state.input)
                state.cards.forEachIndexed { index, card ->
                    // Each card rises into place a beat after the one above it.
                    val arrived = remember(card.id) { MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = arrived,
                        enter = fadeIn(tween(260, delayMillis = index * 90)) +
                            slideInVertically(tween(320, delayMillis = index * 90)) { it / 5 },
                    ) {
                    ActionCard(
                        card = card,
                        onChoose = { field, value -> actor.choose(card.id, field, value) },
                        onEdit = { field, text -> actor.edit(card.id, field, text) },
                        onConfirm = { actor.confirm(card.id, context) },
                        onSkip = { actor.skip(card.id) },
                    )
                    }
                }
                if (state.byRule) {
                    // A rule read this, not the model. If it read it wrong, the model gets the same words.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("a direct command, read without the model", style = monoStyle(11.sp, InkOutline), modifier = Modifier.weight(1f))
                        TextButton(onClick = { actor.ask(state.input, rules = false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("not right? ask Gemma", style = monoStyle(11.sp, AmberBright))
                        }
                    }
                } else {
                    Text("read on this phone in %.0f s".format(state.seconds), style = monoStyle(11.sp, InkOutline))
                }
            }
        }
        if (state !is Actor.State.Idle) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(if (state is Actor.State.Working) "Cancel" else "Done", style = sans(15.sp, AmberBright, FontWeight.SemiBold))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

@Composable
private fun Asked(input: String) {
    Text("“$input”", style = sans(15.sp, BoneDim, lineHeight = 22.sp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(
    card: Actor.Card,
    onChoose: (field: String, value: Any) -> Unit,
    onEdit: (field: String, text: String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    val action = card.action
    // The field being rewritten, and what is typed so far. One at a time.
    var editing by remember(card.id) { mutableStateOf<String?>(null) }
    var draft by remember(card.id) { mutableStateOf(TextFieldValue("")) }
    val editable = card.outcome == null && action.intent != ActumIntent.UNSUPPORTED && action.intent != ActumIntent.EXPLAIN
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkElevated, RoundedCornerShape(18.dp))
            .border(1.dp, InkFieldBorder, RoundedCornerShape(18.dp))
            .animateContentSize(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val hue = colorOf(action.intent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IntentBadge(action.intent)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title(action.intent), style = sans(17.sp, Bone, FontWeight.SemiBold))
                Text(where(action.intent), style = monoStyle(11.sp, hue))
            }
        }
        action.fields.forEach { field ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StateMark(field.state, Modifier.padding(top = 1.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label(field.name) + if (editable && editing != field.name) "  ·  tap to change" else "", style = monoStyle(11.sp, BoneDim))
                    if (editing == field.name) {
                        val save = {
                            onEdit(field.name, draft.text)
                            editing = null
                        }
                        // Opens with the keyboard up, and the field scrolled clear of it.
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = sans(16.sp, Bone, lineHeight = 22.sp),
                            singleLine = field.name != "text",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { save() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberBright, unfocusedBorderColor = InkOutline, cursorColor = AmberBright,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        TextButton(onClick = save, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Save", style = sans(14.sp, AmberBright, FontWeight.SemiBold))
                        }
                    } else {
                        Text(
                            if (field.state == FieldState.MISSING) "missing" else shown(field),
                            style = sans(16.sp, if (field.state == FieldState.MISSING) Missing else Bone, lineHeight = 22.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = editable) {
                                    // The cursor after the last word, where a correction usually goes.
                                    draft = TextFieldValue(field.text, TextRange(field.text.length))
                                    editing = field.name
                                },
                        )
                    }
                    field.question?.let { Text(it, style = sans(13.sp, AmberBright, lineHeight = 18.sp)) }
                    if (field.choices.isNotEmpty() && card.outcome == null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            field.choices.forEach { choice ->
                                OutlinedButton(
                                    onClick = { onChoose(field.name, choice) },
                                    border = BorderStroke(1.dp, AmberBright),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(pretty(choice), style = sans(14.sp, AmberBright, FontWeight.Medium)) }
                            }
                        }
                    }
                }
            }
        }
        when (val outcome = card.outcome) {
            null -> when {
                action.intent == ActumIntent.UNSUPPORTED ->
                    Text("Actum can't do this yet. Nothing was done.", style = sans(14.sp, BoneDim, lineHeight = 20.sp))
                action.intent == ActumIntent.EXPLAIN ->
                    Text("Restated from what you shared, by a model on this phone. It can be wrong, and it is not advice.", style = sans(14.sp, BoneDim, lineHeight = 20.sp))
                action.verdict == Verdict.FAIL ->
                    Text("Something needed is missing, so this can't be done.", style = sans(14.sp, Missing, lineHeight = 20.sp))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A field with readings to pick from is settled first; any other amber field is the person's to accept.
                    // A field Kotlin could not read at all has nothing to act on: it is rewritten first.
                    val unread = action.fields.any { it.state == FieldState.UNSURE && it.value == null }
                    val open = action.fields.any { it.choices.isNotEmpty() } || editing != null || unread
                    Button(
                        onClick = onConfirm,
                        enabled = !open,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = hue, contentColor = OnIntent,
                            disabledContainerColor = tint(hue, 0.28f), disabledContentColor = BoneDim,
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Text(
                            if (editing != null) "Save first" else if (unread) "Tap the amber field to fix it" else if (open) "Pick one above" else verb(action.intent),
                            style = sans(15.sp, if (open) BoneDim else OnIntent, FontWeight.SemiBold),
                        )
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text("Skip", style = sans(15.sp, BoneDim, FontWeight.Medium))
                    }
                }
            }
            is Outcome.Done -> Strip(outcome.what, hue, done = true)
            is Outcome.Failed -> Strip(outcome.why, Missing, done = false)
            Outcome.Skipped -> Text("Skipped. Nothing was done.", style = sans(14.sp, BoneDim, lineHeight = 20.sp))
        }
    }
}

/** What became of a confirmed card: a tinted line in the action's colour with a tick, or in red without one. */
@Composable
private fun Strip(words: String, color: Color, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(tint(color, 0.14f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (done) {
            // The tick lands with a small bounce: the one moment the card celebrates.
            val landed = remember { Animatable(0.3f) }
            LaunchedEffect(Unit) { landed.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            TickGlyph(
                Modifier.size(18.dp).graphicsLayer {
                    scaleX = landed.value
                    scaleY = landed.value
                },
                color,
            )
        }
        Text(words, style = sans(14.sp, color, FontWeight.Medium, lineHeight = 20.sp))
    }
}

/** Where a confirmed card goes, said before it is confirmed. */
private fun where(intent: ActumIntent) = when (intent) {
    ActumIntent.EVENT -> "opens in your calendar"
    ActumIntent.ALARM -> "goes to your clock app"
    ActumIntent.EXPENSE -> "goes in your expenses"
    ActumIntent.CONTACT -> "opens in your contacts"
    ActumIntent.TODO -> "goes on your to-do list"
    ActumIntent.DIARY -> "goes in your locked diary"
    ActumIntent.EXPLAIN -> "restated, nothing is run"
    ActumIntent.UNSUPPORTED -> "nothing is done"
}

/** The Confirm button names what the tap does. */
private fun verb(intent: ActumIntent) = when (intent) {
    ActumIntent.EVENT -> "Add to calendar"
    ActumIntent.ALARM -> "Set alarm"
    ActumIntent.EXPENSE -> "Add expense"
    ActumIntent.CONTACT -> "Add contact"
    ActumIntent.TODO -> "Add to-do"
    ActumIntent.DIARY -> "Write in diary"
    else -> "Confirm"
}

private fun title(intent: ActumIntent) = when (intent) {
    ActumIntent.EVENT -> "Calendar event"
    ActumIntent.ALARM -> "Alarm"
    ActumIntent.EXPENSE -> "Expense"
    ActumIntent.CONTACT -> "Contact"
    ActumIntent.TODO -> "To-do"
    ActumIntent.DIARY -> "Diary entry"
    ActumIntent.EXPLAIN -> "Explanation"
    ActumIntent.UNSUPPORTED -> "Not something Actum does"
}

private fun label(name: String) = when (name) {
    "date_text" -> "date"
    "time_text" -> "time"
    "counterparty_or_merchant" -> "who"
    else -> name.replace('_', ' ')
}

private val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

private fun pretty(value: Any?): String = when (value) {
    is LocalDate -> value.format(day)
    is LocalTime -> value.format(clock)
    is List<*> -> value.joinToString(", ")
    null -> ""
    else -> value.toString()
}

/** The person's words, and beside them what Kotlin resolved them to when that says more. */
private fun shown(field: CheckedField): String {
    val resolved = pretty(field.value)
    return when {
        field.value is LocalDate || field.value is LocalTime ->
            if (field.choices.isNotEmpty()) field.text else "$resolved  ·  “${field.text}”"
        resolved.isNotEmpty() -> resolved
        else -> field.text
    }
}
