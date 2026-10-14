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
import com.actum.model.EnvelopeParser
import com.actum.model.Pieces
import com.actum.model.STOPPED
import com.actum.data.Prepared
import com.actum.data.PreparedDao
import com.actum.data.Lesson
import com.actum.data.LessonDao
import com.actum.imports.Sha256
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val prepared: PreparedDao,
    private val lessons: LessonDao,
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
        /** As the model first wrote it: how the card is known again in the finished answer. */
        val original: ProposedAction = proposed,
        /** The person has chosen or typed on it: a fuller version from the model no longer replaces it. */
        val touched: Boolean = false,
    )

    sealed interface State {
        data object Idle : State
        data class Working(val input: String, val step: String) : State
        /**
         * [byRule]: read by a direct-command rule, with no model; the person can ask Gemma instead.
         * [ruled]: of a saved item's cards, how many its plain sentences gave by rule.
         * [prepared]: a saved item read in the background, before it was asked about.
         * [reading]: the model is still writing; more cards may come.
         */
        data class Ready(
            val input: String,
            val cards: List<Card>,
            val seconds: Double,
            val byRule: Boolean = false,
            val ruled: Int = 0,
            val prepared: Boolean = false,
            val reading: Boolean = false,
        ) : State

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
        // Asked again of the model, after a rule's card: the rule read it wrong. Something to learn.
        (_state.value as? State.Ready)?.takeIf { !rules && it.byRule && it.input == said }?.let {
            learn(said, Lesson.RULE_WRONG, it.cards.firstOrNull()?.action?.intent)
        }
        asked = said
        job?.cancel()
        // The person comes first: a saved item being read in the background waits.
        dropSaving()
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
                val ready = model.check() as? GemmaModel.State.Ready ?: return@launch
                router.load(ready.file, PROCESSOR, ready.kind.prompt)
                router.warmUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "warm-up failed", e)
            }
        }
    }

    /** Words being read ahead of the person's tap, the actions written out so far, and the reading. */
    private class Early(val text: String, val partial: MutableStateFlow<List<ProposedAction>>, val reading: Deferred<Routed?>)

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
        dropSaving()
        Log.i(TAG, "reading ahead: $said")
        val partial = MutableStateFlow<List<ProposedAction>>(emptyList())
        early = Early(said, partial, scope.async { readNow(said) { partial.value = it } })
    }

    /** Gives up any reading ahead: the words it was for are gone. */
    fun dropEarly() {
        val gone = early ?: return
        early = null
        gone.reading.cancel()
        router.cancel()
    }

    /**
     * Loads Gemma if need be and reads [said], telling [partial] of each action as
     * it is written. Null when the model is not on the phone, or it failed.
     */
    private suspend fun readNow(said: String, partial: ((List<ProposedAction>) -> Unit)? = null): Routed? = try {
        val ready = model.check() as? GemmaModel.State.Ready
        if (ready == null) {
            null
        } else {
            router.load(ready.file, PROCESSOR, ready.kind.prompt)
            // A long screenshot or PDF: only its opening is read, or the wait runs to minutes.
            router.route(said.take(MAX_INPUT_CHARS), partial).also {
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
            is GemmaModel.State.Wrong -> return State.Refused(said, "The ${model.chosen.label} file on this phone is not the published one.")
            is GemmaModel.State.Missing -> return State.Refused(said, "The ${model.chosen.label} model is not on this phone yet.")
        }
        _state.value = State.Working(said, if (ahead != null) "Already reading this. Almost there." else "Reading what you asked.")
        val started = SystemClock.elapsedRealtime()
        val live = ahead?.partial ?: MutableStateFlow(emptyList())
        val routed = showing(said, live, started) { ahead?.reading?.await() ?: readNow(said) { live.value = it } }
        // What the person waited, from their tap; reading that began earlier is not counted twice.
        val waited = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "${routed?.millis} ms reading, ${waited} ms waited${if (ahead != null) " (read ahead)" else ""}: ${routed?.answers?.lastOrNull()}")
        // Cards already shown while it was written stand, even if the end of the answer went wrong.
        val actions = (routed?.envelope as? Envelope.Actions)?.actions ?: live.value
        return when {
            actions.isNotEmpty() -> State.Ready(said, cardsFor(said, actions), waited / 1000.0)
            routed == null -> State.Refused(said, "The model stopped before it finished. Try again.")
            else -> State.Refused(said, "Actum couldn't make an action out of that.")
        }
    }

    /**
     * Runs [reading], and meanwhile shows each action of [partial] as a card the
     * moment it is written: the person can read, and confirm, the first while the
     * model writes the next.
     */
    private suspend fun <T> showing(said: String, partial: StateFlow<List<ProposedAction>>, started: Long, reading: suspend () -> T): T = coroutineScope {
        val shower = launch {
            partial.collect { actions ->
                if (actions.isNotEmpty()) {
                    Log.i(TAG, "card ${actions.size} shown at ${SystemClock.elapsedRealtime() - started} ms")
                    // An update, not a write: a tap on a card meanwhile is not lost.
                    _state.update { now -> State.Ready(said, cardsFor(said, actions, now), (SystemClock.elapsedRealtime() - started) / 1000.0, reading = true) }
                }
            }
        }
        try {
            reading()
        } finally {
            shower.cancel()
        }
    }

    /**
     * Cards for [actions], keeping those already on screen for [said]: a card the
     * person has touched or confirmed is never replaced, one they have not is
     * brought up to date when the model's finished answer says more, and actions
     * not shown yet are added below. Validated against today here: the model
     * never sees or computes a date.
     */
    private fun cardsFor(said: String, actions: List<ProposedAction>, base: State = _state.value): List<Card> {
        val cards = (base as? State.Ready)?.takeIf { it.input == said }?.cards.orEmpty().toMutableList()
        val validator = Validator(LocalDate.now())
        val matched = mutableSetOf<Int>()
        for (action in actions) {
            val i = cards.indices.firstOrNull { it !in matched && covers(cards[it].original, action) }
            if (i == null) {
                cards += Card(cards.size, validator.check(action, said), proposed = action)
                matched += cards.lastIndex
                continue
            }
            matched += i
            val card = cards[i]
            if (!card.touched && card.outcome == null && card.original != action) {
                cards[i] = Card(card.id, validator.check(action, said), proposed = action)
            }
        }
        return cards
    }

    /** [later] is [earlier] as the model finished writing it: the same action, with as much said or more. */
    private fun covers(earlier: ProposedAction, later: ProposedAction): Boolean =
        earlier.intent == later.intent && earlier.fields.all { (name, value) -> later.fields[name] == value }

    // ── Saved items: read once, in the background, kept ────────────────────

    /**
     * What a saved item proposes: its plain sentences read by the rules, the
     * rest by Gemma. [missing] when Gemma had something to read and could not
     * (no model, or stopped): such a reading is shown but not kept.
     */
    private class Reading(val actions: List<ProposedAction>, val ruled: Int, val millis: Long, val missing: String?, val invalid: String?)

    private class Saved(val text: String, val partial: MutableStateFlow<List<ProposedAction>>, val reading: Deferred<Reading>)

    /** A saved item being read in the background. Touched only on the main thread. */
    private var saving: Saved? = null

    /** The background reading gives way: the person has asked for something. */
    private fun dropSaving() {
        val gone = saving ?: return
        saving = null
        gone.reading.cancel()
        router.cancel()
    }

    /** What a stored reading must match: these words, read by this model with these rules. */
    fun preparedKey(text: String): String =
        "${model.chosen.id}|$PREPARED_VERSION|" + Sha256.digest(text.trim().byteInputStream())

    /**
     * **Find things to do in this**, for the saved item [memoryId] whose words are
     * [input]. Shown at once when they were read in the background already; picked
     * up when that reading is under way; read now otherwise, and kept.
     */
    fun askSaved(memoryId: String, input: String) {
        val said = input.trim()
        if (said.isEmpty()) return
        asked = null
        job?.cancel()
        dropEarly()
        val ahead = saving?.takeIf { it.text == said }
        if (ahead == null) dropSaving() else saving = null
        _state.value = State.Working(said, "Checking what this asks for…")
        job = scope.launch {
            try {
                val key = preparedKey(said)
                val stored = prepared.get(memoryId)?.takeIf { it.key == key }
                _state.value = if (stored != null) {
                    Log.i(TAG, "prepared: ${stored.envelope}")
                    shown(said, EnvelopeParser.parse(stored.envelope), stored.byRule, stored.millis / 1000.0, prepared = true)
                } else {
                    _state.value = State.Working(said, if (ahead != null) "Already reading this. Almost there." else "Reading it.")
                    val started = SystemClock.elapsedRealtime()
                    val live = ahead?.partial ?: MutableStateFlow(emptyList())
                    val reading = showing(said, live, started) {
                        ahead?.let { runCatching { it.reading.await() }.getOrNull() } ?: readSaved(said, live)
                    }
                    val waited = SystemClock.elapsedRealtime() - started
                    Log.i(TAG, "saved item: ${reading.millis} ms reading, $waited ms waited, ${reading.ruled} by rule")
                    keep(memoryId, key, reading)
                    reading.missing?.takeIf { reading.actions.isEmpty() }?.let { State.Refused(said, it) }
                        ?: shown(said, envelopeOf(reading), reading.ruled, waited / 1000.0, prepared = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
                _state.value = State.Refused(said, "The model stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Reads a saved item in the background and keeps what it proposes. False when
     * it could not now: the person is asking for something, or the model is not
     * on the phone; try again later. True when it is kept, or was already.
     */
    suspend fun prepare(memoryId: String, text: String): Boolean {
        val said = text.trim()
        if (said.isEmpty()) return true
        val key = preparedKey(said)
        if (prepared.get(memoryId)?.key == key) return true
        val mine = withContext(Dispatchers.Main) {
            if (early != null || saving != null || _state.value is State.Working) {
                null
            } else {
                val partial = MutableStateFlow<List<ProposedAction>>(emptyList())
                Saved(said, partial, scope.async { readSaved(said, partial) }).also { saving = it }
            }
        } ?: return false
        val reading = try {
            mine.reading.await()
        } catch (e: CancellationException) {
            // Given way to the person, or this worker itself stopped: either way, not now.
            currentCoroutineContext().ensureActive()
            null
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { if (saving === mine) saving = null }
        }
        if (reading == null || reading.missing != null) return false
        keep(memoryId, key, reading)
        Log.i(TAG, "prepared $memoryId in ${reading.millis} ms, ${reading.ruled} by rule: ${reading.actions.map { it.intent }}")
        return true
    }

    /** The plain sentences by rule, the rest by Gemma. Never throws for a missing model: says so in [Reading.missing]. */
    private suspend fun readSaved(said: String, partial: MutableStateFlow<List<ProposedAction>>): Reading {
        val started = SystemClock.elapsedRealtime()
        val split = Pieces.split(said)
        val ruled = split.ruled.map { it.second }
        // The rules' cards can be shown at once; the model's follow as they are written.
        partial.value = ruled
        if (split.rest.isBlank()) return Reading(ruled, ruled.size, SystemClock.elapsedRealtime() - started, null, null)
        val routed = readNow(split.rest) { partial.value = (ruled + it).take(EnvelopeParser.MAX_ACTIONS) }
        val envelope = routed?.envelope
        val missing = when {
            routed == null -> when (model.check()) {
                is GemmaModel.State.Wrong -> "The ${model.chosen.label} file on this phone is not the published one."
                is GemmaModel.State.Missing -> "The ${model.chosen.label} model is not on this phone yet."
                is GemmaModel.State.Ready -> "The model stopped before it finished. Try again."
            }
            envelope is Envelope.Invalid && envelope.reason == STOPPED -> "The model stopped before it finished. Try again."
            else -> null
        }
        // What was written out whole before the answer went wrong still counts.
        val gemma = (envelope as? Envelope.Actions)?.actions ?: partial.value.drop(ruled.size)
        val actions = (ruled + gemma).take(EnvelopeParser.MAX_ACTIONS)
        return Reading(actions, ruled.size, SystemClock.elapsedRealtime() - started, missing, (envelope as? Envelope.Invalid)?.reason)
    }

    private fun envelopeOf(reading: Reading): Envelope =
        if (reading.actions.isEmpty()) Envelope.Invalid(reading.invalid ?: EnvelopeParser.NO_ACTIONS) else Envelope.Actions(reading.actions, repaired = false)

    /** Kept unless it is incomplete; a memory deleted meanwhile takes nothing with it. */
    private suspend fun keep(memoryId: String, key: String, reading: Reading) {
        if (reading.missing != null) return
        runCatching {
            prepared.upsert(Prepared(memoryId, key, EnvelopeParser.write(reading.actions), reading.ruled, reading.millis, System.currentTimeMillis()))
        }.onFailure { Log.w(TAG, "not kept", it) }
    }

    private fun shown(said: String, envelope: Envelope, ruled: Int, seconds: Double, prepared: Boolean): State = when (envelope) {
        is Envelope.Invalid -> State.Refused(said, "Actum found nothing in this it can do.")
        is Envelope.Actions -> State.Ready(said, cardsFor(said, envelope.actions), seconds, ruled = ruled, prepared = prepared)
    }

    /** The person picked one of an unsure field's readings. */
    fun choose(cardId: Int, field: String, value: Any) = edit(cardId) { card ->
        val fields = card.action.fields.map {
            if (it.name == field) it.copy(value = value, state = FieldState.VERIFIED, question = null, choices = emptyList()) else it
        }
        card.copy(action = card.action.copy(fields = fields, verdict = Validator.verdict(card.action.intent, fields)), touched = true)
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
            card.copy(proposed = proposed, typed = typed, touched = true, action = Validator(LocalDate.now()).check(proposed, input + typed))
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
            if (outcome is Outcome.Done) learnFrom(card)
        }
    }

    // ── What the rules could learn ─────────────────────────────────────────

    /** The words of the last typed or spoken request; null after a saved item's. */
    private var asked: String? = null

    /** A confirmed card that says something about the rules: one they could have read, or read wrong. */
    private fun learnFrom(card: Card) {
        val ready = state.value as? State.Ready ?: return
        if (ready.input != asked || card.action.intent == ActumIntent.DIARY) return
        val kind = when {
            ready.byRule && card.touched -> Lesson.RULE_EDITED
            !ready.byRule && ready.cards.size == 1 && !card.touched && card.action.intent in PLAIN && Rules.route(ready.input) == null -> Lesson.SLOW
            else -> return
        }
        learn(ready.input, kind, card.action.intent)
    }

    private fun learn(words: String, kind: String, intent: ActumIntent?) {
        scope.launch {
            runCatching {
                lessons.add(Lesson(words = words, kind = kind, intent = intent?.name.orEmpty(), createdAt = System.currentTimeMillis()))
                lessons.trim()
            }.onFailure { Log.w(TAG, "lesson not kept", it) }
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

        /** Raised when the rules or the way saved items are read change, so what was kept is read again. */
        const val PREPARED_VERSION = 1
        val LISTS = setOf("items", "repeat_days", "key_dates")

        /** The actions a rule could read, were the sentence plain enough. */
        val PLAIN = setOf(ActumIntent.ALARM, ActumIntent.TODO, ActumIntent.EXPENSE, ActumIntent.CONTACT)

        /** Measured on the demo phone: the CPU answers in about half the GPU's time. */
        val PROCESSOR = GemmaRouter.Processor.CPU
    }
}
