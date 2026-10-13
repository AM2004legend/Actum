package com.actum.ui.gate

import android.util.Log
import com.actum.model.Envelope
import com.actum.model.GemmaModel
import com.actum.model.GemmaRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Milestone M1's measurement: the fixed sentence through Gemma, [RUNS] times,
 * with the time to each complete answer. Every line shown is also logged under
 * [TAG], so `adb logcat -s ActumM1` carries the same report.
 */
class ModelGate(
    private val model: GemmaModel,
    private val router: GemmaRouter,
    private val scope: CoroutineScope,
) {
    data class Run(val millis: Long, val outcome: String, val passed: Boolean)

    data class State(
        val busy: Boolean = false,
        val status: String = "",
        val runs: List<Run> = emptyList(),
        val summary: String? = null,
        val answer: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun measure(processor: GemmaRouter.Processor) {
        if (_state.value.busy) return
        _state.value = State(busy = true)
        scope.launch {
            try {
                run(processor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
                say("Failed on $processor: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun run(processor: GemmaRouter.Processor) {
        say("Checking the model file…")
        val file = when (val found = model.check()) {
            is GemmaModel.State.Ready -> found.file
            is GemmaModel.State.Wrong -> return say("Model file ${found.file.path} ${found.why}")
            is GemmaModel.State.Missing ->
                return say("No model on this phone. Push ${GemmaModel.FILE_NAME} to:\n" + found.places.last())
        }
        say("Loading Gemma on $processor…")
        val loaded = router.load(file, processor)
        Log.i(TAG, "loaded on $processor in $loaded ms")
        repeat(RUNS) { index ->
            say("Loaded in ${seconds(loaded)}. Run ${index + 1} of $RUNS on $processor…")
            // As in the app: the instructions are read in ahead of the request, and that is not counted.
            val warming = android.os.SystemClock.elapsedRealtime()
            router.warmUp()
            Log.i(TAG, "warm-up ${android.os.SystemClock.elapsedRealtime() - warming} ms")
            val routed = router.route(SENTENCE)
            val envelope = routed.envelope
            val outcome = when (envelope) {
                is Envelope.Actions -> buildString {
                    append(envelope.actions.joinToString(" ") { it.intent.name })
                    if (envelope.repaired) append(" · repaired")
                    if (routed.answers.size > 1) append(" · retried")
                }
                is Envelope.Invalid -> "invalid: ${envelope.reason}"
            }
            val passed = envelope is Envelope.Actions && envelope.actions.size == EXPECTED_ACTIONS
            Log.i(TAG, "run ${index + 1}: ${routed.millis} ms, $outcome\n${routed.answers.joinToString("\n---\n")}")
            _state.update {
                it.copy(runs = it.runs + Run(routed.millis, outcome, passed), answer = routed.answers.last())
            }
        }
        val runs = _state.value.runs
        val times = runs.map { it.millis }.sorted()
        val summary = "median ${seconds(times[times.size / 2])} · worst ${seconds(times.last())} · " +
            "${runs.count { it.passed }} of ${runs.size} runs gave $EXPECTED_ACTIONS actions"
        Log.i(TAG, "$processor: load ${seconds(loaded)}, $summary")
        _state.update { it.copy(status = "Loaded on $processor in ${seconds(loaded)}.", summary = summary) }
    }

    /**
     * The accuracy number: every sentence of [TestSet] once, right only when
     * the intents returned are exactly the ones expected. Logged a line a
     * sentence, so the misses can be read, then the total.
     */
    fun evaluate() {
        if (_state.value.busy) return
        _state.value = State(busy = true)
        scope.launch {
            try {
                val file = (model.check() as? GemmaModel.State.Ready)?.file ?: return@launch say("No model on this phone.")
                router.load(file, GemmaRouter.Processor.CPU)
                val times = mutableListOf<Long>()
                var right = 0
                TestSet.sentences.forEachIndexed { index, (sentence, expected) ->
                    say("Sentence ${index + 1} of ${TestSet.sentences.size}…")
                    router.warmUp()
                    val routed = router.route(sentence)
                    val got = (routed.envelope as? Envelope.Actions)?.actions?.map { it.intent }.orEmpty()
                    val ok = got.sortedBy { it.name } == expected.sortedBy { it.name }
                    if (ok) right++
                    times += routed.millis
                    Log.i(TAG, "eval ${index + 1} ${if (ok) "ok  " else "MISS"} ${routed.millis} ms got=$got want=$expected :: $sentence :: ${routed.answers.last()}")
                    _state.update { it.copy(runs = it.runs + Run(routed.millis, "${if (ok) "ok" else "miss"} · $sentence", ok)) }
                }
                val sorted = times.sorted()
                val summary = "$right of ${times.size} sentences right · median ${seconds(sorted[sorted.size / 2])} · worst ${seconds(sorted.last())}"
                Log.i(TAG, "eval total: $summary")
                _state.update { it.copy(status = "Test set done.", summary = summary) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "eval failed", e)
                say("Failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun say(status: String) {
        Log.i(TAG, status)
        _state.update { it.copy(status = status) }
    }

    private fun seconds(millis: Long) = "%.1f s".format(millis / 1000.0)

    companion object {
        const val TAG = "ActumM1"
        const val SENTENCE = "kal 10 tay exam, rice kinte hobe, Rahul ke 500 dilam"
        const val RUNS = 5
        const val EXPECTED_ACTIONS = 3
    }
}
