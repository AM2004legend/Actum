package com.actum.ui.act

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actum.ui.common.Grabber
import com.actum.ui.home.HomeViewModel
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
import com.actum.data.DiaryEntry
import com.actum.data.DiaryVault
import com.actum.data.Expense
import com.actum.data.LogRecord
import com.actum.data.Lesson
import com.actum.data.TodoItem
import com.actum.model.ActumIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String, val icon: ActumIntent?) {
    TODO("To-do", ActumIntent.TODO),
    CALENDAR("Calendar", ActumIntent.EVENT),
    EXPENSES("Spent", ActumIntent.EXPENSE),
    DIARY("Diary", ActumIntent.DIARY),
    LOG("Log", null),
}

/** Read each time it is drawn, not kept on the enum: the colours change with the look. */
private fun colorOf(tab: Tab): Color = when (tab) {
    Tab.TODO -> TodoMint
    Tab.CALENDAR -> EventBlue
    Tab.EXPENSES -> ExpenseGold
    Tab.DIARY -> DiaryRose
    Tab.LOG -> ExplainTeal
}

/** What confirmed actions left in Actum itself: the to-dos, the ledger, the diary, and the log of all of them. */
@Composable
fun ColumnScope.ListsSheet(viewModel: HomeViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODO) }
    Grabber(Modifier.align(Alignment.CenterHorizontally))
    val open by viewModel.todos.collectAsStateWithLifecycle()
    // One rounded bar; the tab in view is filled with its own colour, faint, and wears it.
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .background(InkElevated, RoundedCornerShape(26.dp))
            .border(1.dp, InkFieldBorder, RoundedCornerShape(26.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach {
            val on = it == tab
            val hue = colorOf(it)
            // The tab in view widens to show its name; the others are their icons.
            val weight by animateFloatAsState(if (on) 2.6f else 1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "tab width")
            val ink by animateColorAsState(if (on) hue else BoneDim, tween(200), label = "tab ink")
            val fill by animateColorAsState(if (on) tint(hue, 0.2f) else Color.Transparent, tween(200), label = "tab fill")
            Row(
                Modifier
                    .weight(weight)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(fill)
                    .clickable(onClickLabel = it.label) { tab = it },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (it.icon != null) IntentGlyph(it.icon, Modifier.size(17.dp), ink) else LogGlyph(Modifier.size(17.dp), ink)
                AnimatedVisibility(visible = on, enter = fadeIn(tween(180, delayMillis = 80)) + expandHorizontally(), exit = fadeOut(tween(80)) + shrinkHorizontally()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it.label, style = sans(13.sp, ink, FontWeight.SemiBold), maxLines = 1)
                        val left = open.count { item -> !item.done }
                        if (it == Tab.TODO && left > 0) Text("$left", style = monoStyle(11.sp, ink))
                    }
                }
            }
        }
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // One tab's content gives way to the next with a short fade, not a cut.
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
            label = "tab",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (shown) {
                    Tab.TODO -> Todos(viewModel)
                    Tab.CALENDAR -> CalendarTab(viewModel)
                    Tab.EXPENSES -> Expenses(viewModel)
                    Tab.DIARY -> DiaryTab(viewModel)
                    Tab.LOG -> Log(viewModel)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun Todos(viewModel: HomeViewModel) {
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    Text("To-dos", style = serif(26.sp, lineHeight = 30.sp))
    if (todos.isEmpty()) Empty("Nothing yet. Type “buy rice” and tap Do this.")
    todos.forEach { item: TodoItem ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            // A round tick, in a 48 dp target.
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { viewModel.setDone(item, !item.done) }, contentAlignment = Alignment.Center) {
                val fill by animateColorAsState(if (item.done) TodoMint else Color.Transparent, tween(180), label = "tick fill")
                val pop by animateFloatAsState(if (item.done) 1f else 0.86f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "tick pop")
                Box(
                    Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                        }
                        .background(fill, CircleShape)
                        .border(1.6.dp, if (item.done) TodoMint else InkOutline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.done) TickGlyph(Modifier.size(14.dp), OnIntent, weight = 2.6f)
                }
            }
            Text(
                item.text,
                style = sans(16.sp, if (item.done) BoneDim else Bone, lineHeight = 22.sp)
                    .copy(textDecoration = if (item.done) TextDecoration.LineThrough else null),
                modifier = Modifier.weight(1f),
            )
            Remove { viewModel.delete(item) }
        }
    }
}

@Composable
private fun Expenses(viewModel: HomeViewModel) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    Text("Expenses", style = serif(26.sp, lineHeight = 30.sp))
    if (expenses.isEmpty()) {
        Empty("Nothing yet. Type “paid Rahul 500” and tap Do this.")
    } else {
        val spent = expenses.filter { it.direction == "spent" }.sumOf { it.amountPaise }
        Text("${rupees(spent)} spent in all", style = monoStyle(12.sp, BoneDim))
    }
    expenses.forEach { expense: Expense ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    listOfNotNull(rupees(expense.amountPaise), expense.counterparty).joinToString("  ·  "),
                    style = sans(16.sp, Bone, FontWeight.Medium),
                )
                Text(
                    listOf(day(expense.date), direction(expense.direction), expense.category).joinToString(" · "),
                    style = monoStyle(11.sp, BoneDim),
                )
            }
            Remove { viewModel.delete(expense) }
        }
    }
}

/**
 * The diary, shut until its password opens it. Set here the first time, and
 * changed here later. While this tab is on screen the window is marked secure,
 * so neither the entries nor a password being typed reach a screenshot or the
 * recent-apps picture. Leaving the tab shuts the diary again.
 */
@Composable
private fun DiaryTab(viewModel: HomeViewModel) {
    val entries by viewModel.diary.collectAsStateWithLifecycle()
    val hasPassword by viewModel.diaryHasPassword.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    var reader by remember { mutableStateOf<DiaryVault.Reader?>(null) }
    var changing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    Text("Diary", style = serif(26.sp, lineHeight = 30.sp))
    val open = reader
    when {
        !hasPassword -> {
            var first by remember { mutableStateOf("") }
            var second by remember { mutableStateOf("") }
            Empty(
                "Choose a password for your diary. Entries are locked with it and can't be read without it" +
                    if (entries.isEmpty()) "." else "; the ${entries.size} already written will be locked too.",
            )
            Text("If you forget it, the entries can't be recovered. Actum keeps no copy and has no reset.", style = sans(14.sp, AmberBright, lineHeight = 20.sp))
            Password(first, { first = it }, "password, at least ${DiaryVault.MIN_LENGTH} characters")
            Password(second, { second = it }, "the same again")
            Act(if (busy) "Locking…" else "Set password", enabled = !busy) {
                problem = when {
                    first.length < DiaryVault.MIN_LENGTH -> "Use at least ${DiaryVault.MIN_LENGTH} characters."
                    first != second -> "The two don't match."
                    else -> null
                }
                if (problem == null) {
                    busy = true
                    viewModel.setDiaryPassword(first) {
                        reader = it
                        busy = false
                    }
                }
            }
        }
        open == null -> {
            var typed by remember { mutableStateOf("") }
            Empty(
                if (entries.isEmpty()) "Nothing written yet. Type “diary: ” and what happened, and tap Do this."
                else "${entries.size} ${if (entries.size == 1) "entry" else "entries"}, locked with your password.",
            )
            Password(typed, { typed = it }, "diary password")
            Act(if (busy) "Opening…" else "Unlock", enabled = !busy && typed.isNotEmpty()) {
                busy = true
                problem = null
                viewModel.unlockDiary(typed) {
                    busy = false
                    reader = it
                    if (it == null) problem = "That's not the password."
                }
            }
        }
        changing -> {
            var old by remember { mutableStateOf("") }
            var first by remember { mutableStateOf("") }
            var second by remember { mutableStateOf("") }
            Empty("Change the diary's password. Your entries stay as they are.")
            Password(old, { old = it }, "current password")
            Password(first, { first = it }, "new password, at least ${DiaryVault.MIN_LENGTH} characters")
            Password(second, { second = it }, "the new one again")
            Act(if (busy) "Changing…" else "Change password", enabled = !busy) {
                problem = when {
                    first.length < DiaryVault.MIN_LENGTH -> "Use at least ${DiaryVault.MIN_LENGTH} characters."
                    first != second -> "The two new ones don't match."
                    else -> null
                }
                if (problem == null) {
                    busy = true
                    viewModel.changeDiaryPassword(old, first) { changed ->
                        busy = false
                        if (changed) {
                            changing = false
                            note = "Password changed."
                        } else {
                            problem = "The current password isn't right. Nothing was changed."
                        }
                    }
                }
            }
            Link("cancel") {
                changing = false
                problem = null
            }
        }
        else -> {
            note?.let { Text(it, style = sans(14.sp, Verified, lineHeight = 20.sp)) }
            if (entries.isEmpty()) Empty("Nothing written yet.")
            entries.forEach { entry: DiaryEntry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day(entry.date), style = monoStyle(11.sp, BoneDim))
                        val words = remember(entry.id, entry.sealed) { open.open(entry.sealed) }
                        Text(
                            words ?: "This entry can't be opened.",
                            style = sans(16.sp, if (words == null) Missing else Bone, lineHeight = 24.sp),
                        )
                    }
                    Remove { viewModel.delete(entry) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Link("change password") {
                    changing = true
                    note = null
                }
                Link("lock") { reader = null }
            }
        }
    }
    problem?.let { Text(it, style = sans(14.sp, Missing, lineHeight = 20.sp)) }
}

@Composable
private fun Password(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, style = monoStyle(13.sp, InkOutline)) },
        textStyle = sans(16.sp, Bone),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        // Password type: the keyboard neither suggests nor learns what is typed.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberBright, unfocusedBorderColor = InkOutline, cursorColor = AmberBright),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Act(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AmberBright, contentColor = AmberOnContainer),
        modifier = Modifier.heightIn(min = 52.dp),
    ) { Text(label, style = sans(15.sp, AmberOnContainer, FontWeight.SemiBold)) }
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(label, style = monoStyle(12.sp, BoneDim), modifier = Modifier.clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 4.dp))
}

@Composable
private fun Log(viewModel: HomeViewModel) {
    val records by viewModel.log.collectAsStateWithLifecycle()
    var words by rememberSaveable { mutableStateOf("") }
    Text("Log", style = serif(26.sp, lineHeight = 30.sp))
    Text("every action you confirmed, as the record Actum kept", style = monoStyle(11.sp, BoneDim))
    OutlinedTextField(
        value = words,
        onValueChange = {
            words = it
            viewModel.setLogFilter(it)
        },
        placeholder = { Text("find a word", style = monoStyle(13.sp, InkOutline)) },
        textStyle = sans(16.sp, Bone),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberBright, unfocusedBorderColor = InkOutline, cursorColor = AmberBright),
        modifier = Modifier.fillMaxWidth(),
    )
    if (records.isEmpty()) Empty(if (words.isBlank()) "Nothing confirmed yet." else "No record holds that.")
    records.forEach { record: LogRecord ->
        val intent = ActumIntent.entries.firstOrNull { it.name == record.intent } ?: ActumIntent.UNSUPPORTED
        // The record as kept, shown when its row is tapped.
        var raw by remember(record.id) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clickable { raw = !raw }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntentBadge(intent, size = 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(summary(record), style = sans(15.sp, Bone, lineHeight = 21.sp))
                Text("${record.intent.lowercase()} · ${moment(record.createdAt)}", style = monoStyle(11.sp, colorOf(intent)))
                if (raw) Text(record.json, style = monoStyle(11.sp, BoneDim, lineHeight = 17.sp))
            }
        }
    }
    Learning(viewModel)
}

/**
 * What the rules could learn from your requests: kept on this phone only, read
 * by whoever improves Actum's rules, and forgotten when asked.
 */
@Composable
private fun Learning(viewModel: HomeViewModel) {
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    if (lessons.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text("Actum is learning", style = serif(22.sp, lineHeight = 26.sp))
    Text("requests that took the model but could be instant, and direct commands read wrong · kept on this phone only", style = monoStyle(11.sp, BoneDim, lineHeight = 16.sp))
    lessons.take(SHOWN_LESSONS).forEach { lesson ->
        val why = when (lesson.kind) {
            Lesson.SLOW -> "could be instant"
            Lesson.RULE_WRONG -> "read wrong, asked Gemma"
            else -> "read, then corrected"
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lesson.words, style = sans(15.sp, Bone, lineHeight = 21.sp))
            Text("$why · ${lesson.intent.lowercase()} · ${moment(lesson.createdAt)}", style = monoStyle(11.sp, InkOutline))
        }
    }
    Text("forget these", style = monoStyle(12.sp, AmberBright), modifier = Modifier.clickable { viewModel.forgetLessons() }.padding(vertical = 16.dp, horizontal = 4.dp))
}

private const val SHOWN_LESSONS = 20

/** A record's fields in a line, read back out of its JSON. A diary's record has none, by design. */
private fun summary(record: LogRecord): String {
    val fields = runCatching { Json.parseToJsonElement(record.json).jsonObject["fields"]?.jsonObject }.getOrNull()
    val words = fields?.values.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    return if (words.isEmpty()) "Diary entry · words kept out of the log" else words.joinToString(" · ")
}

@Composable
private fun Empty(words: String) {
    Text(words, style = sans(15.sp, BoneDim, lineHeight = 22.sp))
}

@Composable
private fun Remove(onClick: () -> Unit) {
    Text(
        "remove",
        style = monoStyle(11.sp, InkOutline),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 16.dp),
    )
}

private fun rupees(paise: Long): String {
    val whole = "%,d".format(Locale.ENGLISH, paise / 100)
    return if (paise % 100 == 0L) "₹$whole" else "₹$whole.%02d".format(paise % 100)
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val momentFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

private fun day(iso: String) = runCatching { LocalDate.parse(iso).format(dayFormat) }.getOrDefault(iso)

private fun moment(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(momentFormat)

private fun direction(value: String) = when (value) {
    "owed_to_me" -> "owed to me"
    "i_owe" -> "I owe"
    else -> "spent"
}
