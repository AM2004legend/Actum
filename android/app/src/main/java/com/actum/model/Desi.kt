package com.actum.model

import com.actum.model.Rules.MONEY
import com.actum.model.Rules.NAME
import com.actum.model.Rules.PHONE
import com.actum.model.Rules.propose

/**
 * Direct commands in Hinglish and Benglish, in Roman letters, by the same law
 * as the English rules: the whole sentence fits a pattern or nothing is read.
 * The words that turn a command round ("na", "nahi", "mat") and the ones that
 * join a second thing ("aur", "ar", "phir") are caught before these are tried,
 * except in a shopping list, whose only joins are between the things.
 *
 * Dates and times are copied as said ("kal", "subah 7 baje", "sokal 7 tay"):
 * the parsers already read them, and ask when a word could mean two things.
 */
internal object Desi {

    fun route(said: String): ProposedAction? = alarm(said) ?: expense(said) ?: contact(said)

    private fun rule(pattern: String) = Regex("^(?:$pattern)$", RegexOption.IGNORE_CASE)

    // ── Alarms and reminders ───────────────────────────────────────────────

    private const val PART = "(?:subah|subha|sham|shaam|raat(?:\\s+ko)?|dopahar|dopehar|sokal|sokale|bikel|bikele|bikale|sondhe|sondhay|sondhya|raate|rat|rate|dupur|dupure|morning|evening|night)"
    private const val HOUR = "(?:(?:saade|sade|sare)\\s+)?\\d{1,2}(?:[:.]\\d{2})?"
    /** "subah 7 baje", "sokal 7 tay", "7:30 pm", "raat 10 baje": an hour with a word that makes it a time. */
    private const val TIME = "(?:$PART\\s+)?$HOUR\\s*(?:baje|bje|bajey|tay|ta|te|am|pm|a\\.?m\\.?|p\\.?m\\.?)(?:\\s+$PART)?"
    private const val DAY = "(aaj|aj|aajke|ajke|kal|kaal|kalke|parso|parson|parshu|porshu|agamikal|today|tomorrow|tonight)"
    private const val STRETCH = "\\d+\\s*(?:min|mins|minute|minutes|minit|ghanta|ghante|ghonta|hour|hours|hr|hrs)"

    private const val SET = "(?:laga\\s+do|lagado|laga\\s+dena|laga\\s+dijiye|lagao|lagana|laga|set\\s+kar\\s+do|set\\s+kardo|set\\s+karo|set\\s+kar\\s+dena|set\\s+kar|set\\s+karna" +
        "|diye\\s+dao|diye\\s+dio|diye\\s+diyo|dao|dio|diyo|set\\s+koro|set\\s+kore\\s+dao|set\\s+kore\\s+dio|lagiye\\s+dao|lagiye\\s+dio)"
    private const val WAKE = "(?:utha\\s+dena|utha\\s+do|utha\\s+dijiye|jaga\\s+dena|jaga\\s+do|uthiye\\s+dio|uthiye\\s+diyo|uthiye\\s+dao|uthiye\\s+deo|tule\\s+dio|tule\\s+diyo|tule\\s+dao)"
    private const val REMIND = "(?:yaad\\s+dila\\s+dena|yaad\\s+dila\\s+do|yaad\\s+dila\\s+dijiye|yaad\\s+dilana|remind\\s+kar\\s+dena|remind\\s+kar\\s+do|remind\\s+karna|remind\\s+karo" +
        "|mone\\s+koriye\\s+dio|mone\\s+koriye\\s+diyo|mone\\s+koriye\\s+dao|mone\\s+koriye\\s+deo|remind\\s+koro|remind\\s+kore\\s+dio|remind\\s+kore\\s+dao)"

    /** Groups: day, time. */
    private val alarms = listOf(
        // "kal subah 7 baje alarm laga do", "kal sokal 7 tay alarm diye dao", "7 baje ka alarm lagao"
        rule("(?:$DAY\\s+)?($TIME)\\s+(?:ka\\s+|ki\\s+|ke\\s+liye\\s+|er\\s+|e\\s+|te\\s+|pe\\s+|par\\s+)?alarm\\s+$SET"),
        // "subah 6 baje utha dena", "kal sokal 6 tay uthiye dio"
        rule("(?:$DAY\\s+)?($TIME)\\s+(?:pe\\s+|par\\s+|e\\s+|te\\s+)?$WAKE"),
    )
    /** "alarm laga do kal subah 7 baje". Groups: day, time. */
    private val alarmFirst = rule("alarm\\s+$SET\\s+(?:$DAY\\s+)?($TIME)")
    /** "5 min baad yaad dila dena", "10 minute pore mone koriye dio ki/je …". Groups: stretch, what. */
    private val remindIn = rule("($STRETCH)\\s+(?:baad|bad|me|mein|pore|por|porey|bade)\\s+$REMIND(?:\\s+(?:ki|ke|je)\\s+(.+))?")

    private fun alarm(said: String): ProposedAction? {
        for (pattern in alarms + alarmFirst) {
            val (day, time) = pattern.matchEntire(said)?.destructured ?: continue
            return propose(ActumIntent.ALARM, said, "time_text" to time, "date_text" to day)
        }
        remindIn.matchEntire(said)?.let { m ->
            val (stretch, what) = m.destructured
            return propose(ActumIntent.ALARM, said, "time_text" to stretch, "label" to what)
        }
        return null
    }

    // ── Shopping ───────────────────────────────────────────────────────────

    private const val GET = "(?:lena\\s+hai|lene\\s+hai|lene\\s+hain|lana\\s+hai|lane\\s+hai|lane\\s+hain|lani\\s+hai|kharidna\\s+hai|kharidne\\s+hai|kharidne\\s+hain|le\\s+aana|le\\s+ana|le\\s+aao|mangwana\\s+hai|mangana\\s+hai" +
        "|kinte\\s+hobe|kinbo|kine\\s+anbo|kine\\s+ante\\s+hobe|ante\\s+hobe|anbo|nite\\s+hobe|kine\\s+aano|kine\\s+ano|kine\\s+nebo)"
    /** "doodh aur ande lene hai", "dim, pauruti ar makhon kinte hobe". Group: the things. */
    private val buy = rule("(?:(?:mujhe|amake|amar)\\s+)?(.+?)\\s+$GET")
    private val JOIN = Regex("\\s*,\\s*(?:(?:aur|ar|aar|and|o)\\s+)?|\\s+(?:aur|ar|aar|and|o)\\s+", RegexOption.IGNORE_CASE)

    /** A "thing" with any of these in it is a person, a time, a second command or a refusal. */
    private val NOT_A_THING = Regex(
        "\\b(?:ko|ne|se|ka|ki|ke|er|diye|diya|dilam|baje|bje|tay|alarm|nahi|nahin|na|nai|mat|kal|kaal|aaj|aj|parso|call|phone|karna|karo|korte|korbo|hai|hain|hobe|phir|fir|bhi|kintu|lekin)\\b" +
            "|\\d+\\s*(?:baje|tay|ta)\\b|[?:;]",
        RegexOption.IGNORE_CASE,
    )

    fun shopping(said: String): ProposedAction? {
        val things = buy.matchEntire(said)?.groupValues?.get(1) ?: return null
        val items = things.split(JOIN).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty() || items.any { it.split(Regex("\\s+")).size > MAX_ITEM_WORDS || NOT_A_THING.containsMatchIn(it) }) return null
        return propose(ActumIntent.TODO, said, "items" to items, "list_name" to "shopping")
    }

    // ── Money ──────────────────────────────────────────────────────────────

    private const val WHEN = "(?:(aaj|aj|kal|kaal|gotokal|yesterday|today)\\s+)?"
    private const val ME = "(?:(?:mujhe|maine|main|ami|amake|amar)\\s+)?"

    private val gave = rule("$WHEN$ME$NAME\\s+ko\\s+$MONEY\\s+(?:diye|diya|de\\s+diye|de\\s+diya|bheje|bhej\\s+diye|pay\\s+kiye|pay\\s+kiya|pay\\s+kar\\s+diye)")
    private val gaveB = rule("$WHEN$ME$NAME\\s*(?:ke|k)\\s+$MONEY\\s+(?:dilam|dilaam|diyechi|diye\\s+dilam|pathalam|pathiyechi|pay\\s+korlam|pay\\s+korechi)")
    private val spentOn = rule("$WHEN(.+?)\\s+(?:pe|par|mein|me|ke\\s+liye)\\s+$MONEY\\s+(?:kharch|kharcha|kharche)\\s+(?:kiye|kiya|hue|hua|ho\\s+gaye|ho\\s+gaya)")
    private val spentOnB = rule("$WHEN(.+?)(?:\\s+|-)(?:e|te|er\\s+jonno|r\\s+jonno)\\s+$MONEY\\s+(?:khoroch|khorcha|khoros)\\s+(?:holo|hoyeche|korlam|korechi|hoye\\s+gelo)")
    private val lent = rule("$WHEN$ME$NAME\\s+(?:ko\\s+)?$MONEY\\s+(?:udhaar|udhar|udhar\\s+me)\\s+(?:diye|diya|de\\s+diye)")
    private val lentB = rule("$WHEN$ME$NAME\\s*(?:ke|k)\\s+$MONEY\\s+(?:dhar|dhaar)\\s+(?:dilam|diyechi|dilaam)")
    private val owe = rule("$ME$NAME\\s+ko\\s+$MONEY\\s+(?:dene\\s+hai|dene\\s+hain|lautane\\s+hai|lautane\\s+hain|wapas\\s+dene\\s+hain)")
    private val oweB = rule("$ME$NAME\\s*(?:ke|k)\\s+$MONEY\\s+(?:dite\\s+hobe|ferot\\s+dite\\s+hobe)")

    private val NOT_A_NAME = setOf("mujhe", "maine", "main", "ami", "amake", "amar", "tumi", "tumhe", "use", "usko", "ise", "isko", "sabko", "kisi", "me", "i")

    private fun expense(said: String): ProposedAction? {
        for ((pattern, direction) in listOf(gave to "spent", gaveB to "spent", lent to "owed_to_me", lentB to "owed_to_me")) {
            val (day, who, amount) = pattern.matchEntire(said)?.destructured ?: continue
            if (who.lowercase() in NOT_A_NAME) return null
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to who, "direction" to direction, "date_text" to day)
        }
        for (pattern in listOf(owe, oweB)) {
            val (who, amount) = pattern.matchEntire(said)?.destructured ?: continue
            if (who.lowercase() in NOT_A_NAME) return null
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to who, "direction" to "i_owe")
        }
        for (pattern in listOf(spentOn, spentOnB)) {
            val (day, what, amount) = pattern.matchEntire(said)?.destructured ?: continue
            if (what.split(Regex("\\s+")).size > MAX_ITEM_WORDS) return null
            return propose(ActumIntent.EXPENSE, said, "amount" to amount, "counterparty_or_merchant" to what, "direction" to "spent", "date_text" to day)
        }
        return null
    }

    // ── Contacts ───────────────────────────────────────────────────────────

    private const val SAVE = "save\\s+(?:karo|kar\\s+do|kar\\s+lo|kardo|karlo|koro|kore\\s+rakho|kore\\s+dao|kore\\s+nao)"
    /** "Priya ka number save karo 98300 12345", "Priya er number 98300 12345 save koro". */
    private val saveNumber = listOf(
        rule("$NAME\\s+(?:ka|ki|ke|er|r)\\s+(?:phone\\s+|mobile\\s+)?number\\s+$SAVE\\s+$PHONE"),
        rule("$NAME\\s+(?:ka|ki|ke|er|r)\\s+(?:phone\\s+|mobile\\s+)?number\\s+$PHONE\\s+$SAVE"),
    )

    private fun contact(said: String): ProposedAction? {
        for (pattern in saveNumber) {
            val (name, phone) = pattern.matchEntire(said)?.destructured ?: continue
            if (name.lowercase() in NOT_A_NAME + setOf("mera", "amar", "tumhara", "tomar", "uska", "or")) return null
            return propose(ActumIntent.CONTACT, said, "name" to name, "phone" to phone)
        }
        return null
    }

    private const val MAX_ITEM_WORDS = 4
}
