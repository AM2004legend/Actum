package com.actum.parse

/** Indian mobile numbers, written the ways people write them, as +91 and ten digits. */
object PhoneParser {

    private val inText = Regex("(?<![\\d+])(?:\\+?91[\\s-]?|0)?[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)")
    private val mobile = Regex("(?:\\+91|0091|91|0)?([6-9]\\d{9})")

    /** Every mobile number in [text], normalized. */
    fun find(text: String): List<String> =
        inText.findAll(Words.normalize(text)).mapNotNull { (normalize(it.value) as? Reading.Sure)?.value }.toList()

    /**
     * [raw] as a number to save. An Indian mobile is [Reading.Sure]; another
     * plausible number (a landline, another country) is [Reading.Unsure], kept
     * as written for the person to confirm; anything else is not a number.
     */
    fun normalize(raw: String): Reading<String> {
        val compact = Words.normalize(raw).filter { it.isDigit() || it == '+' }
        mobile.matchEntire(compact)?.let { return Reading.Sure("+91" + it.groupValues[1]) }
        val digits = compact.count { it.isDigit() }
        val plusOnlyFirst = compact.lastIndexOf('+') <= 0
        return if (digits in 7..15 && plusOnlyFirst) {
            Reading.Unsure(listOf(compact), "This is not an Indian mobile number. Is it right?")
        } else {
            Reading.None
        }
    }
}
