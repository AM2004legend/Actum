package com.actum.validate

import com.actum.model.ActumIntent
import com.actum.model.Lingo
import com.actum.model.ProposedAction
import com.actum.parse.AmountParser
import com.actum.parse.DateParser
import com.actum.parse.PhoneParser
import com.actum.parse.Reading
import com.actum.parse.TimeParser
import com.actum.parse.Words
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Green, amber, red on a preview card. */
enum class FieldState { VERIFIED, UNSURE, MISSING }

/**
 * One field after checking. [text] is what the card shows; [value] is what a
 * handler would use (a LocalDate, LocalTime, BigDecimal, String or list of
 * strings), null when there is none yet; [question] is what to ask when unsure.
 */
data class CheckedField(
    val name: String,
    val text: String,
    val state: FieldState,
    val value: Any? = null,
    val question: String? = null,
    /** When unsure between a few readings: each of them, likeliest first, for the person to pick. */
    val choices: List<Any> = emptyList(),
)

/** PASS can be confirmed; UNCERTAIN asks first; FAIL does nothing. */
enum class Verdict { PASS, UNCERTAIN, FAIL }

data class CheckedAction(
    val intent: ActumIntent,
    val fields: List<CheckedField>,
    val verdict: Verdict,
    val sourceSpan: String,
)

/**
 * Decides whether what the model proposed can be acted on. The model's fields
 * are claims: each is read again here by the parsers and checked against the
 * person's own words, and dates and times are resolved here against [today]
 * and, for "in 8 minutes", against [now].
 */
class Validator(private val today: LocalDate, private val now: LocalTime = LocalTime.now()) {

    fun check(action: ProposedAction, input: String): CheckedAction {
        val draft = Draft(action, input)
        when (action.intent) {
            ActumIntent.EVENT -> {
                draft.text("title", required = true)
                draft.date("date_text", required = true, lean = DateParser.Lean.FUTURE)
                draft.time("time_text", required = false)
                draft.text("location")
                draft.text("notes")
            }
            ActumIntent.ALARM -> {
                draft.text("label")
                draft.time("time_text", required = true)
                draft.date("date_text", required = false, lean = DateParser.Lean.FUTURE)
                draft.list("repeat_days", required = false)
            }
            ActumIntent.EXPENSE -> {
                draft.amount()
                draft.text("counterparty_or_merchant")
                draft.choice("category", CATEGORIES, otherwise = "other")
                draft.choice("direction", DIRECTIONS, otherwise = "spent")
                draft.date("date_text", required = false, lean = DateParser.Lean.PAST, absentIsToday = true)
            }
            ActumIntent.CONTACT -> {
                draft.text("name", required = true)
                draft.phone()
                draft.text("note")
            }
            ActumIntent.TODO -> {
                draft.list("items", required = true)
                draft.text("list_name")
            }
            ActumIntent.DIARY -> draft.text("text", required = true)
            ActumIntent.EXPLAIN -> {
                draft.text("summary")
                draft.list("key_dates", required = false)
                draft.text("what_to_do")
            }
            ActumIntent.UNSUPPORTED -> draft.text("reason")
        }
        return CheckedAction(action.intent, draft.fields, verdict(action.intent, draft.fields), action.sourceSpan)
    }

    private inner class Draft(private val action: ProposedAction, input: String) {
        val fields = mutableListOf<CheckedField>()
        /** The person's words, and the same with their slang made plain: a rule copies from the second. */
        private val said = squash(input) + "\n" + squash(Lingo.plain(input))
        private val span = action.sourceSpan

        private fun raw(name: String): String = when (val element = action.fields[name]) {
            null, JsonNull -> ""
            is JsonPrimitive -> element.content.trim()
            else -> element.toString()
        }

        /** The model was told to copy dates and times as written; one that is not in the input was made up. */
        private fun copied(text: String) = squash(text) in said

        fun text(name: String, required: Boolean = false) {
            val text = raw(name)
            when {
                text.isNotEmpty() -> fields += CheckedField(name, text, FieldState.VERIFIED, text)
                required -> fields += CheckedField(name, "", FieldState.MISSING)
            }
        }

        fun list(name: String, required: Boolean) {
            val items = when (val element: JsonElement? = action.fields[name]) {
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                is JsonPrimitive -> listOf(element.content.trim())
                else -> emptyList()
            }.filter { it.isNotEmpty() }
            when {
                items.isNotEmpty() -> fields += CheckedField(name, items.joinToString(", "), FieldState.VERIFIED, items)
                required -> fields += CheckedField(name, "", FieldState.MISSING)
            }
        }

        fun choice(name: String, allowed: Set<String>, otherwise: String) {
            val text = raw(name).lowercase()
            fields += when (text) {
                "" -> CheckedField(name, otherwise, FieldState.VERIFIED, otherwise)
                in allowed -> CheckedField(name, text, FieldState.VERIFIED, text)
                else -> CheckedField(name, otherwise, FieldState.UNSURE, otherwise, "\"$text\" is not one of the choices.")
            }
        }

        fun date(name: String, required: Boolean, lean: DateParser.Lean, absentIsToday: Boolean = false) {
            val text = raw(name)
            if (text.isEmpty()) {
                // Left out by the model, but the person's words for this action may still hold one.
                when (val found = DateParser.parse(span, today, lean)) {
                    is Reading.Sure ->
                        fields += CheckedField(name, span, FieldState.UNSURE, found.value, "Is this the date?")
                    is Reading.Unsure ->
                        fields += CheckedField(name, span, FieldState.UNSURE, found.candidates.first(), found.question, found.candidates)
                    Reading.None -> when {
                        absentIsToday -> fields += CheckedField(name, "today", FieldState.VERIFIED, today)
                        required -> fields += CheckedField(name, "", FieldState.MISSING)
                    }
                }
                return
            }
            fields += when (val read = DateParser.parse(text, today, lean)) {
                is Reading.Sure ->
                    if (copied(text)) CheckedField(name, text, FieldState.VERIFIED, read.value)
                    else CheckedField(name, text, FieldState.UNSURE, read.value, NOT_IN_MESSAGE)
                is Reading.Unsure -> CheckedField(name, text, FieldState.UNSURE, read.candidates.first(), read.question, read.candidates)
                Reading.None -> CheckedField(name, text, FieldState.UNSURE, null, "Which date is this?")
            }
        }

        fun time(name: String, required: Boolean) {
            val text = raw(name)
            if (text.isEmpty()) {
                if (required) fields += CheckedField(name, "", FieldState.MISSING)
                return
            }
            fields += when (val read = TimeParser.parse(text, context = span, now = now)) {
                is Reading.Sure ->
                    if (copied(text)) CheckedField(name, text, FieldState.VERIFIED, read.value)
                    else CheckedField(name, text, FieldState.UNSURE, read.value, NOT_IN_MESSAGE)
                is Reading.Unsure -> CheckedField(name, text, FieldState.UNSURE, read.candidates.first(), read.question, read.candidates)
                Reading.None -> CheckedField(name, text, FieldState.UNSURE, null, "What time is this?")
            }
        }

        /** The model's amount must be a number the person wrote; otherwise theirs is offered instead. */
        fun amount() {
            val text = raw("amount")
            val claimed = AmountParser.parse(text)
            val written = AmountParser.find(span) + AmountParser.find(said)
            val inSpan = AmountParser.find(span)
            val inWords: BigDecimal? = inSpan.singleOrNull { it.marked }?.value ?: inSpan.singleOrNull()?.value
            fields += when {
                claimed != null && written.any { it.value.compareTo(claimed) == 0 } ->
                    CheckedField("amount", claimed.toPlainString(), FieldState.VERIFIED, claimed)
                claimed != null ->
                    CheckedField("amount", claimed.toPlainString(), FieldState.UNSURE, claimed, NOT_IN_MESSAGE)
                inWords != null ->
                    CheckedField("amount", inWords.toPlainString(), FieldState.UNSURE, inWords, "Is this the amount?")
                else -> CheckedField("amount", text, FieldState.MISSING)
            }
        }

        fun phone() {
            val text = raw("phone")
            fields += when (val read = PhoneParser.normalize(text)) {
                is Reading.Sure ->
                    if (read.value in PhoneParser.find(said)) CheckedField("phone", read.value, FieldState.VERIFIED, read.value)
                    else CheckedField("phone", read.value, FieldState.UNSURE, read.value, NOT_IN_MESSAGE)
                is Reading.Unsure -> CheckedField("phone", text, FieldState.UNSURE, read.candidates.first(), read.question)
                Reading.None -> CheckedField("phone", text, FieldState.MISSING)
            }
        }
    }

    companion object {
        /** Worked out again whenever the person settles a field. */
        fun verdict(intent: ActumIntent, fields: List<CheckedField>): Verdict = when {
            intent == ActumIntent.UNSUPPORTED -> Verdict.FAIL
            fields.any { it.state == FieldState.MISSING } -> Verdict.FAIL
            fields.any { it.state == FieldState.UNSURE } -> Verdict.UNCERTAIN
            else -> Verdict.PASS
        }

        private const val NOT_IN_MESSAGE = "This is not in what you shared. Is it right?"
        private val CATEGORIES = setOf("food", "transport", "shopping", "bills", "health", "education", "personal", "other")
        private val DIRECTIONS = setOf("spent", "owed_to_me", "i_owe")

        private fun squash(text: String) = Words.normalize(text).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
