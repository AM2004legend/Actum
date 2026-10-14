package com.actum.model

/**
 * Finds, in the person's words, the part an action came from, when the model
 * was not asked to copy it out: the clause that shares the most words with the
 * action's fields. Writing that span out was a fifth of the model's answer,
 * every word of it time the person waited; finding it here takes no time.
 *
 * The validator reads it as before: for a time's "morning" or "raat", for an
 * amount the model left out, for a date it did not copy.
 */
object Spans {

    /** [actions] with every empty source span filled from [input]. */
    fun fill(actions: List<ProposedAction>, input: String): List<ProposedAction> =
        actions.map { if (it.sourceSpan.isBlank()) it.copy(sourceSpan = locate(it, input)) else it }

    fun locate(action: ProposedAction, input: String): String {
        val own = action.fields.values.flatMap { words(it.toString()) }.toSet()
        val clauses = BREAK.split(input).map { it.trim() }.filter { it.isNotEmpty() }
        if (own.isEmpty() || clauses.size < 2) return input.trim()
        val best = clauses.maxBy { clause -> words(clause).count { it in own } }
        return if (words(best).none { it in own }) input.trim() else best
    }

    /** Where one thing said ends and the next begins: punctuation, a new line, a joining word. */
    private val BREAK = Regex("[,;!?]\\s*|\\.\\s+|\\n+|\\s+(?:and|also|then|aur|ar|phir|tarpor|tarpore)\\s+", RegexOption.IGNORE_CASE)

    private fun words(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.filter { it.length > 1 }.toList()
}
