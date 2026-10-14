package com.actum.model

/**
 * The way people really type and say things, put back into the plain words the
 * rules know: "tmrw" is tomorrow, "next day" is tomorrow, "2 mins later" is in
 * 2 minutes, "plz" is please. Only swaps with one meaning: "in a bit", "later",
 * "eod" or a bare "in 5" are left as they are, for the model and a question.
 *
 * The rules read the plain words; the validator accepts a field copied from
 * either the person's words or these, so a card shows the person nothing
 * they did not say.
 */
object Lingo {

    fun plain(text: String): String = SWAPS.fold(text) { said, (pattern, plain) -> pattern.replace(said, plain) }

    private fun word(alternatives: String) = Regex("(?<![\\p{L}\\d])(?:$alternatives)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)

    /** Order matters: "day after tmrw" becomes "day after tomorrow" by the first swap, and stays so. */
    private val SWAPS = listOf(
        word("tmrw|tmr|tmro|tmrow|tmrrw|tmrw|tomoro|tomorow|tommorow|tommorrow|tomorrw|tomrw|2moro|2morrow|2morow|2mrw|2mro") to "tomorrow",
        word("(?:the\\s+)?next\\s+day|(?:the\\s+)?nxt\\s+day") to "tomorrow",
        word("tonite|2nite|2night|tnite|tonyt") to "tonight",
        word("2day|tday") to "today",
        // "2 mins later", "in 5 min from now", "10 minutes after": a stretch from now, said the plain way.
        Regex(
            "(?<![\\p{L}\\d])(?:in\\s+)?(\\d+(?:\\.\\d+)?|an?|half\\s+an?)\\s*(mins?|minutes?|hrs?|hours?|secs?|seconds?)\\s+(?:later|from\\s+now|after)(?![\\p{L}\\d])",
            RegexOption.IGNORE_CASE,
        ) to "in $1 $2",
        // "alarm @ 7am".
        Regex("\\s@\\s*(?=\\d)") to " at ",
        word("u") to "you",
        word("pls+|plz+|plis|pleez|pleas+e+|pleasee+") to "please",
        word("thx|thnx|thnks|ty|tysm|tq") to "thanks",
        // "gotta buy milk", "need to buy eggs": the shopping is the command.
        Regex("^(?:i\\s+)?(?:gotta|gonna|wanna|need\\s+to|needa|have\\s+to|hafta|got\\s+to|must)\\s+(?=(?:buy|purchase)\\b)", RegexOption.IGNORE_CASE) to "",
    )
}
