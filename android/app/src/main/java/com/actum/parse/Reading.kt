package com.actum.parse

import java.text.Normalizer

/** What a parser made of some words: one value, several it cannot choose between, or nothing. */
sealed interface Reading<out T> {
    data class Sure<T>(val value: T) : Reading<T>

    /** [candidates] most likely first; [question] is the one thing to ask the person. */
    data class Unsure<T>(val candidates: List<T>, val question: String) : Reading<T>

    data object None : Reading<Nothing>
}

internal object Words {
    /**
     * Lower case, with Bengali and Devanagari digits as 0-9, so one set of
     * patterns reads all three scripts. NFC first: letters such as য় and ढ़ have
     * two encodings, and a pattern typed in one would miss text in the other.
     */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in Normalizer.normalize(text, Normalizer.Form.NFC)) {
            append(
                when (c) {
                    in '০'..'৯' -> '0' + (c - '০')
                    in '०'..'९' -> '0' + (c - '०')
                    else -> c.lowercaseChar()
                },
            )
        }
    }

    /**
     * Any of [words] standing alone. Letters and combining marks both count as
     * part of a word: in Bengali and Devanagari a vowel sign is a mark, and
     * \b would end the word before it.
     */
    fun whole(words: List<String>): Regex =
        Regex("(?<![\\p{L}\\p{M}])(?:${words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(normalize(it)) }})(?![\\p{L}\\p{M}])")
}
