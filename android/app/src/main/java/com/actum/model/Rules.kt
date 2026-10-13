package com.actum.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The short way round the model, for commands so direct that a pattern can
 * read them: "set an alarm for 7 am", "buy milk", "paid Rahul 500".
 *
 * A rule either fits the whole sentence or does not speak. There is no
 * partial match and no best guess: a sentence with a second clause, a
 * negation, a question, or a word the pattern does not account for is left
 * to Gemma. The bar is that a rule is never wrong, not that it catches much.
 *
 * What a rule returns is a proposal like the model's, and goes through the
 * same validator and the same Confirm. Pure Kotlin, and tested sentence by
 * sentence, including every sentence it must leave alone.
 */
object Rules {

    /** The action [input] plainly asks for, or null when it is not that plain. */
    fun route(input: String): ProposedAction? {
        val said = tidy(input)
        if (said.isEmpty()) return null
        // The diary takes whatever follows its name, at any length, so it is tried before the other checks.
        diary(said)?.let { return it }
        if (said.split(Regex("\\s+")).size > MAX_WORDS) return null
        // A list is the one place "and" and commas belong.
        shopping(said)?.let { return it }
        if (CLAUSES.containsMatchIn(said)) return null
        return alarm(said) ?: expense(said) ?: contact(said) ?: todo(said)
    }

    // ── Tidying ────────────────────────────────────────────────────────────

    private val OPENERS = Regex("^(?:(?:hey|hi|ok|okay|please|actum|kindly)[,\\s]+)+", RegexOption.IGNORE_CASE)
    private val ASKING = Regex("^(?:can|could|will|would)\\s+you\\s+(?:please\\s+)?", RegexOption.IGNORE_CASE)
    private val CLOSERS = Regex("(?:[,\\s]+(?:please|thanks|thank you))+$", RegexOption.IGNORE_CASE)

    /** Greetings, "please" and the full stop go; the words between are left exactly as said. */
    private fun tidy(input: String): String {
        var said = input.trim().trimEnd('.', '!').trim()
        said = OPENERS.replace(said, "")
        // "can you set an alarm for 7 am?" is a command; any other question is not one a rule answers.
        if (ASKING.containsMatchIn(said)) said = ASKING.replace(said, "").trimEnd('?').trim()
        return CLOSERS.replace(said, "").trim()
    }

    /** Any sign of a second thing being said: these go to the model. */
    private val CLAUSES = Regex(
        // A comma between digits is a number's own ("1,200"), not a break in the sentence.
        "(?<!\\d),|,(?!\\d)|[;?]|\\.\\s|\\b(?:and|but|then|also|or|if|unless|because|not|never|no|don't|dont|didn't|didnt|won't|wont|can't|cant|isn't|wasn't|forgot|should|maybe)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun rule(pattern: String) = Regex("^(?:$pattern)$", RegexOption.IGNORE_CASE)

    private fun propose(intent: ActumIntent, said: String, vararg fields: Pair<String, Any?>): ProposedAction {
        val given = fields.mapNotNull { (name, value) ->
            when (value) {
                null, "" -> null
                is List<*> -> name to JsonArray(value.map { JsonPrimitive(it.toString()) })
                else -> name to JsonPrimitive(value.toString().trim())
            }
        }
        return ProposedAction(intent, confidence = 1.0, fields = JsonObject(given.toMap()), sourceSpan = said)
    }

    // ── Alarms and reminders ───────────────────────────────────────────────

    private const val COUNT = "(?:\\d+(?:\\.\\d+)?|an?|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|forty[- ]five|half an?|a couple of)"
    private const val STRETCH = "$COUNT\\s*(?:more\\s+)?(?:hours?|hrs?|minutes?|mins?)(?:\\s+and\\s+a\\s+half)?(?:\\s+\\d+\\s*(?:minutes?|mins?))?(?:\\s+from\\s+now)?"

    /** Only times that say enough to be read: "7:15", "7 pm", "7 o'clock". A bare "7" is left to the model. */
    private const val CLOCK = "(?:\\d{1,2}[:.]\\d{2}(?:\\s*(?:am|pm|a\\.m\\.|p\\.m\\.))?|\\d{1,2}\\s*(?:am|pm|a\\.m\\.|p\\.m\\.)|\\d{1,2}\\s+o'?clock|noon|midnight)"
    private const val PART = "(?:\\s+(?:in\\s+the\\s+(?:morning|afternoon|evening)|at\\s+night|tonight))?"
    private const val DAY = "(today|tomorrow|tonight)"

    /** A clock time with a day before or after it, or a stretch from now. Groups: time, day | day, time | stretch | stretch. */
    private const val WHEN = "(?:(?:at|for)\\s+)?($CLOCK$PART)(?:\\s+$DAY)?|$DAY\\s+(?:at|for)\\s+($CLOCK$PART)|in\\s+($STRETCH)|(?:for|after)\\s+($STRETCH)"

    private val alarms = listOf(
        rule("(?:set|put|create|add|make)\\s+(?:up\\s+)?(?:an?\\s+|the\\s+|my\\s+)?alarm\\s+(?:$WHEN)"),
        rule("alarm\\s+(?:$WHEN)"),
        rule("wake\\s+me(?:\\s+up)?\\s+(?:$WHEN)"),
    )
    private val remindIn = rule("remind\\s+me\\s+in\\s+($STRETCH)(?:\\s+to\\s+(.+))?")
    private val remindTo = rule("remind\\s+me\\s+to\\s+(.+?)\\s+(?:in\\s+($STRETCH)|at\\s+($CLOCK$PART)(?:\\s+$DAY)?|$DAY\\s+at\\s+($CLOCK$PART))")

    private fun alarm(said: String): ProposedAction? {
        for (pattern in alarms) {
            val g = pattern.matchEntire(said)?.groupValues ?: continue
            val time = listOf(g[1], g[4], g[5], g[6]).first { it.isNotEmpty() }
            val day = listOf(g[2], g[3]).firstOrNull { it.isNotEmpty() }
            return propose(ActumIntent.ALARM, said, "time_text" to time, "date_text" to day)
        }
        remindIn.matchEntire(said)?.let { m ->
            val (stretch, what) = m.destructured
            return propose(ActumIntent.ALARM, said, "time_text" to stretch, "label" to what)
        }
        remindTo.matchEntire(said)?.let { m ->
            val (what, stretch, clock, dayAfter, dayBefore, clockAfterDay) = m.destructured
            val time = listOf(stretch, clock, clockAfterDay).first { it.isNotEmpty() }
            return propose(ActumIntent.ALARM, said, "label" to what, "time_text" to time, "date_text" to dayAfter.ifEmpty { dayBefore })
        }
        return null
    }

    // ── Money ──────────────────────────────────────────────────────────────

    private const val MONEY = "(?:rs\\.?\\s*|₹\\s*|inr\\s*)?(\\d[\\d,]*(?:\\.\\d+)?k?)(?:\\s*(?:rupees?|rs|bucks|/-))?"
    private const val NAME = "([\\p{L}][\\p{L}.'’-]*(?:\\s+[\\p{L}][\\p{L}.'’-]*)?)"
    private const val DATED = "(?:\\s+(today|yesterday))?"

    private val spentOn = rule("(?:paid|spent|i paid|i spent)\\s+$MONEY\\s+(?:for|on|at|to)\\s+(?:the\\s+|an?\\s+)?(.+?)$DATED")
    private val paidWho = rule("(?:paid|i paid)\\s+$NAME\\s+$MONEY(?:\\s+(?:for|on)\\s+.+?)?$DATED")
    private val lent = rule("(?:lent|i lent)\\s+$NAME\\s+$MONEY$DATED")
    private val owe = rule("i\\s+owe\\s+$NAME\\s+$MONEY(?:\\s+(?:for|on)\\s+.+?)?")

    private fun expense(said: String): ProposedAction? {
        spentOn.matchEntire(said)?.let { m ->
            val (amount, what, day) = m.destructured
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to what, "direction" to "spent", "date_text" to day)
        }
        paidWho.matchEntire(said)?.let { m ->
            val (who, amount, day) = m.destructured
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to who, "direction" to "spent", "date_text" to day)
        }
        lent.matchEntire(said)?.let { m ->
            val (who, amount, day) = m.destructured
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to who, "direction" to "owed_to_me", "date_text" to day)
        }
        owe.matchEntire(said)?.let { m ->
            val (who, amount) = m.destructured
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to who, "direction" to "i_owe")
        }
        return null
    }

    // ── Contacts ───────────────────────────────────────────────────────────

    private const val PHONE = "(\\+?\\d[\\d\\s-]{5,18}\\d)"
    private val saveWhose = rule("save\\s+$NAME\\s+(?:phone\\s+|mobile\\s+)?number\\s+(?:as\\s+|is\\s+)?$PHONE")
    private val saveAs = rule("save\\s+(?:the\\s+|this\\s+)?(?:phone\\s+|mobile\\s+)?number\\s+$PHONE\\s+(?:as|for|under(?:\\s+the\\s+name)?)\\s+$NAME")
    private val newContact = rule("(?:add|new|save)\\s+contact:?\\s+$NAME\\s+$PHONE")

    private fun contact(said: String): ProposedAction? {
        (saveWhose.matchEntire(said) ?: newContact.matchEntire(said))?.let { m ->
            val (name, phone) = m.destructured
            // "Ankit's number": the name is Ankit.
            val whose = name.removeSuffix("'s").removeSuffix("’s")
            // "save my number", "save this number": no name was given.
            if (whose.lowercase() in setOf("my", "the", "this", "that", "his", "her", "their", "a")) return null
            return propose(ActumIntent.CONTACT, said, "name" to whose, "phone" to phone)
        }
        saveAs.matchEntire(said)?.let { m ->
            val (phone, name) = m.destructured
            return propose(ActumIntent.CONTACT, said, "name" to name, "phone" to phone)
        }
        return null
    }

    // ── To-dos ─────────────────────────────────────────────────────────────

    /** Words that make an "item" a second command, a person or a time, not a thing to buy. */
    private val NOT_AN_ITEM = Regex(
        "^(?:call|pay|paid|remind|set|send|book|email|text|go|meet|wake|order|save|tell|ask|check|i|we|it|me|us|him|her|them|then|also|up|out|back|ready|in|into|to|rid)\\b" +
            "|\\b(?:at|on|by|before|after|from|tomorrow|today|tonight|yesterday|am|pm|o'?clock|not|but|if)\\b|[?:;]",
        RegexOption.IGNORE_CASE,
    )
    private val buy = rule("(buy|purchase)\\s+(?:some\\s+|a\\s+|an\\s+|the\\s+)?(.+)")
    private val addToList = rule("add\\s+(.+?)\\s+to\\s+(?:my\\s+|the\\s+)?(?:(shopping|grocery|groceries|to-?do)\\s+)?list")
    private val todoPlain = rule("(?:to-?do|to\\s+do|task):?\\s+(.+)")

    /** "buy eggs, bread and butter": each thing its own item, only when every one of them is plainly a thing. */
    private fun shopping(said: String): ProposedAction? {
        val bought = buy.matchEntire(said)
        val listed = if (bought == null) addToList.matchEntire(said) ?: return null else null
        val things = bought?.groupValues?.get(2) ?: listed!!.groupValues[1]
        val items = things.split(Regex("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty() || items.any { it.split(Regex("\\s+")).size > MAX_ITEM_WORDS || NOT_AN_ITEM.containsMatchIn(it) }) return null
        // "buy milk" stays "buy milk" on the list; a thing added to a named list is just the thing.
        val verb = bought?.groupValues?.get(1)?.lowercase()
        return propose(
            ActumIntent.TODO, said,
            "items" to if (verb != null) items.map { "$verb $it" } else items,
            "list_name" to listed?.groupValues?.get(2),
        )
    }

    private fun todo(said: String): ProposedAction? =
        todoPlain.matchEntire(said)?.let { propose(ActumIntent.TODO, said, "items" to listOf(it.groupValues[1])) }

    // ── Diary ──────────────────────────────────────────────────────────────

    private val diaryEntry = Regex("^(?:dear\\s+)?diary\\s*[:,-]\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Everything after "diary:" as it was said. Only with the mark after the word: "diary of a wimpy kid" is not an entry. */
    private fun diary(said: String): ProposedAction? =
        diaryEntry.matchEntire(said)?.let { propose(ActumIntent.DIARY, said, "text" to it.groupValues[1]) }

    private const val MAX_WORDS = 14
    private const val MAX_ITEM_WORDS = 4
}
