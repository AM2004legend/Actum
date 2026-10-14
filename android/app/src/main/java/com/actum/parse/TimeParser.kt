package com.actum.parse

import java.time.LocalTime

/** Reads a clock time out of words in English, Hindi or Bengali: "10:30 pm", "5 baje", "sokal 10 tay". */
object TimeParser {

    private enum class Part { MORNING, AFTERNOON, EVENING, NIGHT }

    private val parts = mapOf(
        Part.MORNING to listOf("morning", "subah", "sokal", "sokale", "সকাল", "সকালে", "सुबह"),
        Part.AFTERNOON to listOf("afternoon", "dopahar", "dupur", "dupure", "দুপুর", "দুপুরে", "दोपहर"),
        Part.EVENING to listOf(
            "evening", "sham", "shaam", "bikel", "bikal", "bikele", "bikale", "sondha", "sondhay",
            "বিকাল", "বিকেল", "বিকেলে", "সন্ধ্যা", "সন্ধ্যায়", "शाम",
        ),
        Part.NIGHT to listOf("night", "tonight", "tonite", "2nite", "raat", "raate", "raat ko", "রাত", "রাতে", "रात"),
    ).mapValues { (_, words) -> Words.whole(words) }

    /** "am", "a.m." and "a.m", the last as a speech recognizer's text arrives with its full stop trimmed. */
    private const val MERIDIEM = "(am|pm|a\\.?m\\.?|p\\.?m\\.?)"
    private val half = Words.normalize("(?:(saade|sade|sare|সাড়ে|साढ़े)\\s+)?")
    private val oClock = listOf("baje", "bje", "bajey", "o'clock", "oclock", "ta", "tay", "te", "tar", "টা", "টায়", "টার", "বাজে", "बजे")

    private val clock = Regex("(?<![\\d:.])(\\d{1,2})[:.](\\d{2})(?![\\d:.])\\s*$MERIDIEM?")
    private val meridiem = Regex("(?<![\\d:.])(\\d{1,2})\\s*$MERIDIEM(?![\\p{L}])")
    private val spoken = Regex("$half(?<![\\d:.])(\\d{1,2})\\s*(?:${oClock.joinToString("|") { Regex.escape(Words.normalize(it)) }})(?![\\p{L}\\p{M}])")
    private val noon = Words.whole(listOf("noon", "midday"))
    private val midnight = Words.whole(listOf("midnight"))

    /**
     * The time in [text]. The part of the day ("sokal", "raat") is looked for
     * in [text] and then in [context], the words around it. An hour from 1 to
     * 12 with neither that nor am/pm is [Reading.Unsure]. A stretch of time
     * ("in 8 minutes") is counted from [now], and is not a time without it.
     */
    fun parse(text: String, context: String = "", now: LocalTime? = null): Reading<LocalTime> {
        val words = Words.normalize(text)
        // "in 8 minutes", "half an hour": a time only against the clock, so only when the caller gives it.
        if (now != null) fromNow(words, now)?.let { return Reading.Sure(it) }
        val part = part(words) ?: part(Words.normalize(context))

        clock.find(words)?.let { m ->
            val (hour, minute, ampm) = m.destructured
            return resolve(hour.toInt(), minute.toInt(), ampm, part)
        }
        meridiem.find(words)?.let { m ->
            val (hour, ampm) = m.destructured
            return resolve(hour.toInt(), 0, ampm, part)
        }
        spoken.find(words)?.let { m ->
            val (halfPast, hour) = m.destructured
            return resolve(hour.toInt(), if (halfPast.isEmpty()) 0 else 30, "", part)
        }
        if (noon.containsMatchIn(words)) return Reading.Sure(LocalTime.NOON)
        if (midnight.containsMatchIn(words)) return Reading.Sure(LocalTime.MIDNIGHT)
        return Reading.None
    }

    private val counts = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "fifteen" to 15.0,
        "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "forty five" to 45.0, "forty-five" to 45.0,
        "sixty" to 60.0, "ninety" to 90.0, "half a" to 0.5, "half an" to 0.5, "a couple of" to 2.0, "couple of" to 2.0,
        "a few" to 3.0,
    )
    /** By the unit's first letter: "ghanta" and "ghonta" are hours. */
    private val seconds = mapOf("h" to 3600, "g" to 3600, "m" to 60, "s" to 1)
    private val span = Regex(
        "(?<![\\p{L}\\d.])(\\d+(?:\\.\\d+)?|${counts.keys.sortedByDescending { it.length }.joinToString("|")})\\s*(?:more\\s+)?" +
            "(hours?|hrs?|ghan?ta|ghante|ghonta|minutes?|mins?|minit|seconds?|secs?)(?![\\p{L}])(\\s+and\\s+a\\s+half)?",
    )

    /**
     * A stretch of time counted from [now]: "8 more minutes", "in an hour and
     * a half", "1 hour 30 minutes". Rounded up to the whole minute, since
     * that is what a clock alarm can keep. Null when [words] names no stretch.
     */
    private fun fromNow(words: String, now: LocalTime): LocalTime? {
        val stretches = span.findAll(words).toList()
        if (stretches.isEmpty()) return null
        val total = stretches.sumOf { m ->
            val (count, unit, andHalf) = m.destructured
            val each = seconds.getValue(unit.take(1))
            ((count.toDoubleOrNull() ?: counts.getValue(count)) + if (andHalf.isEmpty()) 0.0 else 0.5) * each
        }
        if (total <= 0) return null
        val then = now.plusSeconds(Math.round(total))
        val whole = then.withSecond(0).withNano(0)
        return if (then == whole) whole else whole.plusMinutes(1)
    }

    private fun part(words: String): Part? = parts.entries.firstOrNull { it.value.containsMatchIn(words) }?.key

    private fun resolve(hour: Int, minute: Int, ampm: String, part: Part?): Reading<LocalTime> {
        if (hour > 23 || minute > 59) return Reading.None
        // 0 and 13-23 are the 24-hour clock already.
        if (hour == 0 || hour > 12) return Reading.Sure(LocalTime.of(hour, minute))
        val am = LocalTime.of(hour % 12, minute)
        val pm = LocalTime.of(hour % 12 + 12, minute)
        if (ampm.startsWith("a")) return Reading.Sure(am)
        if (ampm.startsWith("p")) return Reading.Sure(pm)
        return when (part) {
            Part.MORNING -> Reading.Sure(am)
            Part.AFTERNOON, Part.EVENING -> Reading.Sure(pm)
            // "raat 9" is the evening's; "raat 2" and "raat 12" are after midnight.
            Part.NIGHT -> Reading.Sure(if (hour in 5..11) pm else am)
            // Neither said: the likelier half of the day first.
            null -> Reading.Unsure(if (hour in 7..11) listOf(am, pm) else listOf(pm, am), "Morning or evening?")
        }
    }
}
