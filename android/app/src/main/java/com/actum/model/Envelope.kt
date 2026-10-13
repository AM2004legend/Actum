package com.actum.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** The closed action list. Nothing outside it exists; an unknown name becomes [UNSUPPORTED]. */
enum class ActumIntent { EVENT, ALARM, EXPENSE, CONTACT, TODO, DIARY, EXPLAIN, UNSUPPORTED }

/** One action as the model proposed it. Its fields are unchecked: validation is Kotlin's, later. */
data class ProposedAction(
    val intent: ActumIntent,
    val confidence: Double,
    val fields: JsonObject,
    val sourceSpan: String,
)

sealed interface Envelope {
    /** [repaired] when the model's text was not valid JSON as it came. */
    data class Actions(val actions: List<ProposedAction>, val repaired: Boolean) : Envelope

    /** Not an envelope, even after repair. */
    data class Invalid(val reason: String) : Envelope
}

/** Reads the model's answer into the envelope: as it is, or after [repair]. */
object EnvelopeParser {

    const val MAX_ACTIONS = 5
    const val NO_ACTIONS = "no actions"

    private const val SOURCE_SPAN = "\"source_span\""
    private val ENVELOPE_KEYS = setOf("intent", "confidence", "fields", "source_span")

    private val SOLE_FIELD = mapOf(ActumIntent.DIARY to "text", ActumIntent.UNSUPPORTED to "reason")

    private val json = Json { isLenient = true }

    fun parse(answer: String): Envelope {
        val strict = runCatching { Json.parseToJsonElement(answer.trim()) }.getOrNull()
        val element = strict
            ?: runCatching { json.parseToJsonElement(repair(answer)) }.getOrNull()
            ?: return Envelope.Invalid("not JSON")
        val list = when {
            element is JsonObject && element["actions"] is JsonArray -> element["actions"] as JsonArray
            // The envelope left off: a bare list of actions, or one action alone.
            element is JsonArray -> element
            element is JsonObject && "intent" in element -> JsonArray(listOf(element))
            else -> return Envelope.Invalid("no actions list")
        }
        val actions = list.mapNotNull(::action).take(MAX_ACTIONS)
        if (actions.isEmpty()) return Envelope.Invalid(NO_ACTIONS)
        return Envelope.Actions(actions, repaired = strict == null)
    }

    private fun action(element: JsonElement): ProposedAction? {
        val item = element as? JsonObject ?: return null
        val name = (item["intent"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val intent = ActumIntent.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val fields = when (val given = item["fields"]) {
            is JsonObject -> given
            // The one field written bare, where the intent has only one: "fields":"the words".
            is JsonPrimitive -> SOLE_FIELD[intent]?.let { name -> buildJsonObject { put(name, given.content) } }
            else -> null
        } ?: JsonObject(emptyMap())
        // Fields closed too soon, and the rest written beside it ("fields":{"summary":…},"key_dates":[…]):
        // anything on the action that is not the envelope's own is a field that fell out.
        val strays = item.filterKeys { it !in ENVELOPE_KEYS }
        val allFields = if (strays.isEmpty()) fields else JsonObject(fields + strays)
        return ProposedAction(
            intent = intent ?: ActumIntent.UNSUPPORTED,
            confidence = ((item["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.0).coerceIn(0.0, 1.0),
            fields = if (intent != null) allFields else buildJsonObject { put("reason", "unknown action \"$name\"") },
            sourceSpan = (item["source_span"] as? JsonPrimitive)?.content.orEmpty(),
        )
    }

    /**
     * What small models get wrong around otherwise good JSON: a code fence or
     * words around it, a trailing comma, a bracket too many, an answer cut off
     * before its brackets closed. Returns the first JSON value in [answer], mended.
     */
    fun repair(answer: String): String {
        val start = answer.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return answer
        val text = answer.substring(start)
        val out = StringBuilder()
        val open = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (i in text.indices) {
            val c = text[i]
            if (inString) {
                out.append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> {
                    // The model's commonest slip: it closes the action a step early, and writes
                    // source_span after it, among the actions. A source_span is only ever an
                    // action's own, so the action just closed is opened again to take it.
                    if (open.lastOrNull() == '[' && text.startsWith(SOURCE_SPAN, i)) {
                        val comma = out.indexOfLast { !it.isWhitespace() }
                        val brace = if (comma > 0 && out[comma] == ',') (comma - 1 downTo 0).firstOrNull { !out[it].isWhitespace() } else null
                        if (brace != null && out[brace] == '}') {
                            out.setLength(brace)
                            out.append(',')
                            open.addLast('{')
                        }
                    }
                    inString = true
                    out.append(c)
                }
                '{', '[' -> {
                    open.addLast(c)
                    out.append(c)
                }
                '}', ']' -> {
                    // One bracket too many ("}}}" where two were open): a closer that
                    // does not match what is open is the model's slip, and is left out.
                    val opener = if (c == '}') '{' else '['
                    if (open.lastOrNull() != opener) continue
                    dropTrailingComma(out)
                    open.removeLast()
                    out.append(c)
                    if (open.isEmpty()) return out.toString()
                }
                else -> out.append(c)
            }
        }
        if (inString) out.append('"')
        while (open.isNotEmpty()) {
            dropTrailingComma(out)
            out.append(if (open.removeLast() == '{') '}' else ']')
        }
        return out.toString()
    }

    private fun dropTrailingComma(out: StringBuilder) {
        val last = out.indexOfLast { !it.isWhitespace() }
        if (last >= 0 && out[last] == ',') out.setLength(last)
    }
}
