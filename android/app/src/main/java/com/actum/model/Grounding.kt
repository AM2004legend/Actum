package com.actum.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Whether what the model proposed is about what the person said. A small
 * model, asked twice, will sometimes hand back the example from its own
 * instructions as if it were the answer: well-formed actions for a meeting
 * nobody mentioned. Those are dropped here, before anything is shown.
 *
 * The test is plain: most of an action's own words (its source span, or its
 * fields when it gave none) must be words of the message. Pure Kotlin.
 */
object Grounding {

    const val NOT_ABOUT_THE_MESSAGE = "not about the message"

    /** [envelope] without the actions that are not about [input]; invalid when none is left. */
    fun keep(envelope: Envelope, input: String): Envelope {
        if (envelope !is Envelope.Actions) return envelope
        val said = words(input).toSet()
        val grounded = envelope.actions.filter { isAbout(it, said) }
        return if (grounded.isEmpty()) Envelope.Invalid(NOT_ABOUT_THE_MESSAGE) else envelope.copy(actions = grounded)
    }

    private fun isAbout(action: ProposedAction, said: Set<String>): Boolean {
        // A refusal's reason is the model's own words, not the person's.
        if (action.intent == ActumIntent.UNSUPPORTED) return true
        val own = words(action.sourceSpan).ifEmpty { action.fields.values.flatMap(::wordsOf) }
        if (own.isEmpty()) return true
        return own.count { it in said } * 2 >= own.size
    }

    private fun wordsOf(value: JsonElement): List<String> = when (value) {
        is JsonPrimitive -> words(value.content)
        is JsonArray -> value.flatMap(::wordsOf)
        else -> emptyList()
    }

    /** Lower-cased runs of letters and digits; one-letter words say too little to count. */
    private fun words(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.filter { it.length > 1 }.toList()
}
