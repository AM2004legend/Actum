package com.actum.act

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.actum.model.Envelope
import com.actum.model.GemmaModel
import com.actum.model.GemmaRouter
import com.actum.model.ProposedAction
import com.actum.model.Routed
import com.actum.model.Rules
import com.actum.model.ActumIntent
import com.actum.validate.CheckedAction
import com.actum.validate.CheckedField
import com.actum.validate.Verdict
import com.actum.validate.FieldState
import com.actum.validate.Validator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * One request from the person, from their words to actions done: Gemma
 * proposes, the validator checks, a card per action waits for a tap, and only
 * a confirmed card runs. Lives in the app, so a rotation or a sheet closing
 * does not lose a request the phone spent half a minute on.
 */
class Actor(
    private val model: GemmaModel,
    private val router: GemmaRouter,
    private val executor: Executor,
    private val scope: CoroutineScope,
) {
    /** One proposed action and what became of it; [outcome] null while it waits for the person. */
    data class Card(
        val id: Int,
        val action: CheckedAction,
        val outcome: Outcome? = null,
        /** What the model proposed, with the person's edits written over it. */
        val proposed: ProposedAction,
        /** What the person typed into fields: their words too, when a field is checked against what was said. */
        val typed: String = "",
    )

    sealed interface State {
        data object Idle : State
        data class Working(val input: String, val step: String) : State
        /** [byRule]: read by a direct-command rule, with no model; the person can ask Gemma instead. */
        data class Ready(val input: String, val cards: List<Card>, val seconds: Double, val byRule: Boolean = false) : State

        /** Nothing can be done with [input]; [reason] says why, plainly. */
        data class Refused(val input: String, val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var job: Job? = null

    /**
     * [rules] off sends [input] straight to Gemma: for a saved picture, PDF or note,
     * which is never a direct command, and when the person asks for a second reading.
     */
    fun ask(input: String, rules: Boolean = true) {
        val said = input.trim()
        if (said.isEmpty()) return
        job?.cancel()
        // The same words already being read, from before the tap: picked up, not started again.
        val ahead = early?.takeIf { it.text == said }
        if (ahead == null) dropEarly() else early = null
        _state.value = State.Working(said, "Checking the model…")
        job = scope.launch {
            try {
                _state.value = (if (rules) direct(said) else null) ?: work(said, ahead)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
                _state.value = State.Refused(said, "The model stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Gets Gemma ready before it is asked: loads the model and reads the
     * instructions in. Called when the person starts typing or opens the
     * microphone, so the wait they see later is for their own words only.
     * Quietly does nothing if the model is not on the phone.
     */
    fun warmUp() {
        scope.launch {
            try {
                val file = (model.check() as? GemmaModel.State.Ready)?.file ?: return@launch
                router.load(file, PROCESSOR)
                router.warmUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "warm-up failed", e)
            }
        }
    }

    /** Words being read ahead of the person's tap, and the reading. */
    private class Early(val text: String, val reading: Deferred<Routed?>)

    /** Touched only on the main thread, by [readAhead] and [ask]. */
    private var early: Early? = null

    /**
     * Starts reading [input] before the person taps **Do this**: while they
     * pause typing, or as soon as their speech has been turned into words.
     * If they then ask with the same words, the reading is already part done;
     * if the words change, it is stopped and the new ones are read instead.
     * Nothing is shown and nothing is done with it until they ask.
     *
     * Not for direct commands, which a rule reads instantly anyway, nor for a
     * word or two typed into search, which is most likely a search.
     */
    fun readAhead(input: String) {
        val said = input.trim()
        if (said.split(Regex("\\s+")).size < MIN_WORDS_AHEAD || Rules.route(said) != null) {
            dropEarly()
            return
        }
        if (early?.text == said) return
        dropEarly()
        Log.i(TAG, "reading ahead: $said")
        early = Early(said, scope.async { readNow(said) })
    }

    /** Gives up any reading ahead: the words it was for are gone. */
    fun dropEarly() {
        val gone = early ?: return
        early = null
        gone.reading.cancel()
        router.cancel()
    }

    /** Loads Gemma if need be and reads [said]. Null when the model is not on the phone, or it failed. */
    private suspend fun readNow(said: String): Routed? = try {
        val file = (model.check() as? GemmaModel.State.Ready)?.file
        if (file == null) {
            null
        } else {
            router.load(file, PROCESSOR)
            // A long screenshot or PDF: only its opening is read, or the wait runs to minutes.
            router.route(said.take(MAX_INPUT_CHARS)).also {
                // Ready for the next request while this one's cards are being read.
                scope.launch { runCatching { router.warmUp() } }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "reading failed", e)
        null
    }

    /** A direct command, read by a rule in no time at all. Null for everything else, which is Gemma's. */
    private fun direct(said: String): State? {
        val proposed = Rules.route(said) ?: return null
        Log.i(TAG, "rule: ${proposed.intent} ${proposed.fields}")
        val card = Card(0, Validator(LocalDate.now()).check(proposed, said), proposed = proposed)
        return State.Ready(said, listOf(card), seconds = 0.0, byRule = true)
    }

    private suspend fun work(said: String, ahead: Early?): State {
        when (model.check()) {
            is GemmaModel.State.Ready -> Unit
            is GemmaModel.State.Wrong -> return State.Refused(said, "The model file on this phone is not the published one.")
            is GemmaModel.State.Missing -> return State.Refused(said, "The Gemma model is not on this phone yet.")
        }
        _state.value = State.Working(said, if (ahead != null) "Already reading this. Almost there." else "Reading what you asked.")
        val started = SystemClock.elapsedRealtime()
        val routed = ahead?.reading?.await() ?: readNow(said)
            ?: return State.Refused(said, "The model stopped before it finished. Try again.")
        // What the person waited, from their tap; reading that began earlier is not counted twice.
        val waited = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "${routed.millis} ms reading, ${waited} ms waited${if (ahead != null) " (read ahead)" else ""}: ${routed.answers.lastOrNull()}")
        return when (val envelope = routed.envelope) {
            is Envelope.Invalid -> State.Refused(said, "Actum couldn't make an action out of that.")
            is Envelope.Actions -> {
                // Today is the phone's, read here: the model never sees or computes a date.
                val validator = Validator(LocalDate.now())
                val cards = envelope.actions.mapIndexed { i, action -> Card(i, validator.check(action, said), proposed = action) }
                State.Ready(said, cards, waited / 1000.0)
            }
        }
    }

    /** The person picked one of an unsure field's readings. */
    fun choose(cardId: Int, field: String, value: Any) = edit(cardId) { card ->
        val fields = card.action.fields.map {
            if (it.name == field) it.copy(value = value, state = FieldState.VERIFIED, question = null, choices = emptyList()) else it
        }
        card.copy(action = card.action.copy(fields = fields, verdict = Validator.verdict(card.action.intent, fields)))
    }

    /**
     * The person rewrote a field. Their words are a claim like the model's
     * were: the whole action goes through the validator again, so a date they
     * type is resolved by the same parser and shows the same states.
     */
    fun edit(cardId: Int, field: String, text: String) {
        val input = (state.value as? State.Ready)?.input ?: return
        edit(cardId) { card ->
            val value = if (field in LISTS) {
                JsonArray(text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
            } else {
                JsonPrimitive(text.trim())
            }
            val proposed = card.proposed.copy(fields = JsonObject(card.proposed.fields + (field to value)))
            val typed = card.typed + "\n" + text
            card.copy(proposed = proposed, typed = typed, action = Validator(LocalDate.now()).check(proposed, input + typed))
        }
    }

    fun skip(cardId: Int) = edit(cardId) { it.copy(outcome = Outcome.Skipped) }

    /** The tap that makes it happen. [context] is the activity's: the intents open other apps over it. */
    fun confirm(cardId: Int, context: Context) {
        val card = (state.value as? State.Ready)?.cards?.firstOrNull { it.id == cardId } ?: return
        if (card.outcome != null) return
        scope.launch {
            val outcome = try {
                executor.run(context, card.action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "action failed", e)
                Outcome.Failed("That didn't work: ${e.message ?: e.javaClass.simpleName}")
            }
            edit(cardId) { it.copy(outcome = outcome) }
        }
    }

    /**
     * Writes [words] in the diary as they stand, with no model in between:
     * they are the person's own, and the tap that sent them here was the
     * confirmation. Goes through the same executor, so it is sealed and
     * logged like any diary card. [done] is called on the main thread.
     */
    fun writeDiary(words: String, context: Context, done: (Outcome) -> Unit) {
        scope.launch {
            val entry = CheckedAction(
                intent = ActumIntent.DIARY,
                fields = listOf(CheckedField("text", words, FieldState.VERIFIED, words)),
                verdict = Verdict.PASS,
                sourceSpan = "",
            )
            val outcome = try {
                executor.run(context, entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "diary failed", e)
                Outcome.Failed("That didn't work: ${e.message ?: e.javaClass.simpleName}")
            }
            withContext(Dispatchers.Main) { done(outcome) }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = State.Idle
    }

    private fun edit(cardId: Int, change: (Card) -> Card) = _state.update { state ->
        if (state is State.Ready) state.copy(cards = state.cards.map { if (it.id == cardId) change(it) else it }) else state
    }

    private companion object {
        const val TAG = "ActumAct"
        const val MAX_INPUT_CHARS = 1_200

        /** Fewer typed words than this is most likely a search, and is not read ahead. */
        const val MIN_WORDS_AHEAD = 3
        val LISTS = setOf("items", "repeat_days", "key_dates")

        /** Measured on the demo phone: the CPU answers in about half the GPU's time. */
        val PROCESSOR = GemmaRouter.Processor.CPU
    }
}
