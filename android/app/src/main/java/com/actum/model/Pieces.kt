package com.actum.model

/**
 * A saved item's words, split so the plain parts need no model: each sentence
 * that a rule reads whole becomes that rule's action, and only the rest goes
 * to Gemma, which then has less to read and less to write.
 *
 * Careful with lines. OCR breaks lines where the picture did, mid-sentence, so
 * a line break counts as a break only when the next line plainly starts afresh:
 * a bullet or a number, a capital, or a line before it that ended a sentence.
 * Otherwise the lines are one sentence, and "I don't need to / buy eggs" stays
 * whole, which no rule reads.
 */
object Pieces {

    data class Split(
        /** Each sentence a rule read, with what it proposed. */
        val ruled: List<Pair<String, ProposedAction>>,
        /** Everything else, in order, a sentence a line. Blank when the rules read it all. */
        val rest: String,
    )

    fun split(text: String): Split {
        // A diary entry runs to the end of what was written, every sentence of it.
        Rules.route(text)?.takeIf { it.intent == ActumIntent.DIARY }?.let { return Split(listOf(text.trim() to it), "") }
        val ruled = mutableListOf<Pair<String, ProposedAction>>()
        val rest = mutableListOf<String>()
        for (sentence in sentences(text)) {
            val action = Rules.route(unmarked(sentence))
            if (action != null) ruled += sentence to action else rest += sentence
        }
        return Split(ruled, rest.joinToString("\n"))
    }

    /** The sentences of [text], line breaks inside a sentence turned to spaces. */
    fun sentences(text: String): List<String> {
        val joined = mutableListOf<StringBuilder>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                // A blank line always ends what came before it.
                if (joined.lastOrNull()?.isNotEmpty() == true) joined += StringBuilder()
                continue
            }
            val current = joined.lastOrNull()
            if (current == null || current.isEmpty() || startsAfresh(current, line)) {
                if (current != null && current.isEmpty()) current.append(line) else joined += StringBuilder(line)
            } else {
                current.append(' ').append(line)
            }
        }
        return joined.map { it.toString().trim() }.filter { it.isNotEmpty() }.flatMap { it.split(SENTENCE_END) }
            .map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun startsAfresh(before: CharSequence, line: String): Boolean =
        before.last() in ENDS || BULLET.containsMatchIn(line) || line.first().isUpperCase()

    /** The sentence without a list's bullet or number in front. */
    private fun unmarked(sentence: String) = sentence.replace(BULLET, "").trim()

    private const val ENDS = ".!?:;"
    private val BULLET = Regex("^(?:[-*•·>]+|\\d{1,2}[.)])\\s+")

    /** A full stop, ! or ? before a capital: a new sentence. Not "a.m. tomorrow", nor "Rs. 500". */
    private val SENTENCE_END = Regex("(?<=[!?]|(?<![ap]\\.m|Rs|Dr|Mr|Mrs|Ms|St|vs)\\.)\\s+(?=[A-Z])")
}
