package com.actum.parse

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Reads a date out of words in English, Hindi or Bengali, in Latin or native
 * script, against the day the caller says today is. Never the model's job.
 */
object DateParser {

    /**
     * Which way an ambiguous word leans. "kal" and "parshu" mean a day forward
     * or the same distance back; an event is ahead and money spent is behind,
     * and with neither known the reading is [Reading.Unsure].
     */
    enum class Lean { FUTURE, PAST, NONE }

    fun parse(text: String, today: LocalDate, lean: Lean = Lean.NONE): Reading<LocalDate> {
        val words = Words.normalize(text)
        return written(words, today, lean)
            ?: relative(words, today, lean)
            ?: weekday(words, today)
            ?: Reading.None
    }

    // 15/10, 15-10-2026, 15 Oct, October 15th 2026. Day first, as India writes it.

    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val monthName = "(${months.joinToString("|") { "$it|${it.take(3)}" }}|sept)"
    private val numeric = Regex("(?<![\\d/-])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}|\\d{2}))?(?![\\d/-])")
    private val dayMonth = Regex("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$monthName(?![\\p{L}])(?:,?\\s+(\\d{4}))?")
    private val monthDay = Regex("(?<![\\p{L}])$monthName\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\d)(?:,?\\s+(\\d{4}))?")

    private fun written(words: String, today: LocalDate, lean: Lean): Reading<LocalDate>? {
        numeric.find(words)?.let { m ->
            val (a, b, year) = m.destructured
            // Day first; month first only when it cannot be the other way round.
            val (day, month) = if (b.toInt() > 12 && a.toInt() <= 12) b.toInt() to a.toInt() else a.toInt() to b.toInt()
            date(day, month, year, today, lean)?.let { return Reading.Sure(it) }
        }
        dayMonth.find(words)?.let { m ->
            val (day, month, year) = m.destructured
            date(day.toInt(), monthNumber(month), year, today, lean)?.let { return Reading.Sure(it) }
        }
        monthDay.find(words)?.let { m ->
            val (month, day, year) = m.destructured
            date(day.toInt(), monthNumber(month), year, today, lean)?.let { return Reading.Sure(it) }
        }
        return null
    }

    private fun monthNumber(name: String) = months.indexOfFirst { it.startsWith(name.take(3)) } + 1

    /** With no year written: this year, or the next or last one when that is the way it leans. */
    private fun date(day: Int, month: Int, year: String, today: LocalDate, lean: Lean): LocalDate? = try {
        when {
            year.length == 4 -> LocalDate.of(year.toInt(), month, day)
            year.length == 2 -> LocalDate.of(2000 + year.toInt(), month, day)
            else -> {
                val thisYear = LocalDate.of(today.year, month, day)
                when {
                    lean == Lean.FUTURE && thisYear < today -> thisYear.plusYears(1)
                    lean == Lean.PAST && thisYear > today -> thisYear.minusYears(1)
                    else -> thisYear
                }
            }
        }
    } catch (_: DateTimeException) {
        null
    }

    // today, tomorrow, aaj, kal, parshu and their Bengali and Hindi spellings.

    private class Relative(val days: Long, val eitherWay: Boolean, words: List<String>) {
        val pattern = Words.whole(words)
    }

    /** Order matters: "day after tomorrow" before "tomorrow", "agamikal" before "kal". */
    private val relatives = listOf(
        Relative(2, false, listOf("day after tomorrow")),
        Relative(-2, false, listOf("day before yesterday")),
        Relative(1, false, listOf("tomorrow", "tmrw", "tmr", "agamikal", "agami kal", "আগামীকাল", "আগামী কাল")),
        Relative(-1, false, listOf("yesterday", "gotokal", "goto kal", "গতকাল", "গত কাল")),
        Relative(0, false, listOf("today", "tonight", "aaj", "aj", "aajke", "ajke", "আজ", "আজকে", "आज")),
        Relative(2, true, listOf("parshu", "parso", "parson", "porshu", "porshudin", "পরশু", "পরশুদিন", "परसों")),
        Relative(1, true, listOf("kal", "kaal", "kalke", "কাল", "কালকে", "कल")),
    )

    private fun relative(words: String, today: LocalDate, lean: Lean): Reading<LocalDate>? {
        val found = relatives.firstOrNull { it.pattern.containsMatchIn(words) } ?: return null
        val ahead = today.plusDays(found.days)
        if (!found.eitherWay) return Reading.Sure(ahead)
        val behind = today.minusDays(found.days)
        return when (lean) {
            Lean.FUTURE -> Reading.Sure(ahead)
            Lean.PAST -> Reading.Sure(behind)
            Lean.NONE -> Reading.Unsure(
                listOf(ahead, behind),
                if (found.days == 1L) "Tomorrow or yesterday?" else "The day after tomorrow, or the day before yesterday?",
            )
        }
    }

    // Friday, next Friday, last Friday.

    private val days = mapOf(
        DayOfWeek.MONDAY to listOf("monday", "somvar", "sombar"),
        DayOfWeek.TUESDAY to listOf("tuesday", "tue", "tues", "mangalvar", "mongolbar"),
        DayOfWeek.WEDNESDAY to listOf("wednesday", "budhvar", "budhbar"),
        DayOfWeek.THURSDAY to listOf("thursday", "thu", "thurs", "guruvar", "brihospotibar"),
        DayOfWeek.FRIDAY to listOf("friday", "fri", "shukravar", "shukrobar"),
        DayOfWeek.SATURDAY to listOf("saturday", "shanivar", "shonibar"),
        DayOfWeek.SUNDAY to listOf("sunday", "ravivar", "robibar"),
    ).mapValues { (_, names) -> Regex("(?:(next|last|this)\\s+)?${Words.whole(names).pattern}") }

    private fun weekday(words: String, today: LocalDate): Reading<LocalDate>? {
        for ((day, pattern) in days) {
            val match = pattern.find(words) ?: continue
            val coming = today.with(TemporalAdjusters.next(day))
            return when (match.groupValues[1]) {
                "last" -> Reading.Sure(today.with(TemporalAdjusters.previous(day)))
                "next" -> {
                    // Said early in the week, "next Friday" is this week's to some and the one after to others.
                    val sameWeek = coming.with(DayOfWeek.MONDAY) == today.with(DayOfWeek.MONDAY)
                    if (sameWeek) Reading.Unsure(listOf(coming, coming.plusWeeks(1)), "This coming one, or the week after?")
                    else Reading.Sure(coming)
                }
                else -> Reading.Sure(coming)
            }
        }
        return null
    }
}
