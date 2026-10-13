package com.actum.parse

import java.math.BigDecimal

/** Finds money amounts in words: "₹500", "Rs. 1,500", "500 taka", "5k", "2 lakh". Rupees only. */
object AmountParser {

    /** [marked] when the words said it was money (₹, Rs, rupees, taka, /-), not a bare number. */
    data class Amount(val value: BigDecimal, val marked: Boolean)

    private val pattern = Regex(
        "(₹|(?<![\\p{L}])(?:rs\\.?|inr)(?![\\p{L}]))?\\s*" +
            "(?<![\\d.,:/-])(\\d[\\d,]*(?:\\.\\d+)?)(?![\\d:]|/\\d)" +
            "(?:\\s*(k|lakhs?|lacs?|hazar|hajar|hazaar|crores?)(?![\\p{L}]))?" +
            "(?:\\s*(/-|(?:rupees?|rupaye|rupay|rupiya|taka|tk|bucks)(?![\\p{L}])|টাকা|रुपये|रुपए))?",
    )

    private val scales = mapOf(
        "k" to 1_000, "hazar" to 1_000, "hajar" to 1_000, "hazaar" to 1_000,
        "lakh" to 100_000, "lac" to 100_000, "crore" to 10_000_000,
    )

    /** Every number in [text] that could be an amount, in the order written. */
    fun find(text: String): List<Amount> = pattern.findAll(Words.normalize(text)).mapNotNull { m ->
        val (before, digits, scale, after) = m.destructured
        val number = digits.replace(",", "").toBigDecimalOrNull() ?: return@mapNotNull null
        val times = scales[scale.removeSuffix("s")] ?: 1
        // 500, not 5E+2: whole rupees at scale 0, so equal amounts are equal BigDecimals.
        val value = (number * times.toBigDecimal()).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        Amount(value, marked = before.isNotEmpty() || after.isNotEmpty())
    }.toList()

    /** One amount from a field's own text ("500", "₹1,500", "5k"), or null when it is not one positive amount. */
    fun parse(text: String): BigDecimal? = find(text).singleOrNull()?.value?.takeIf { it.signum() > 0 }
}
