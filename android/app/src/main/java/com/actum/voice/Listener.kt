package com.actum.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Speech to words, on the phone. Uses only Android's on-device recognizer
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer]), which by its contract
 * does the recognition on the device. Where there is none (before Android 12,
 * or with no speech pack installed) this says so and hears nothing: it never
 * falls back to the ordinary recognizer, which may send audio to a server.
 *
 * Actum has no network of its own either way. The recognizer is a system
 * service; it is the one part of this that is not Actum's own code.
 *
 * Main thread only, as the recognizer requires.
 */
class Listener(private val context: Context) {

    sealed interface State {
        data object Idle : State

        /** [heard] is everything so far, earlier takes included; [level] is the voice's loudness, 0 to 1. */
        data class Listening(val heard: String, val level: Float) : State

        /** Listening is over. [text] may be empty when nothing was made out. */
        data class Heard(val text: String) : State

        /** [canFetch]: Android can be asked to install the speech pack (it does the downloading, not Actum). */
        data class Problem(val reason: String, val canFetch: Boolean = false) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var recognizer: SpeechRecognizer? = null

    /**
     * Counts recognizers made. A recognizer that was let go still reports
     * ("disconnected") a moment later; its number no longer matches, and what
     * it says is ignored instead of landing on the take that replaced it.
     */
    private var take = 0

    /** What earlier takes heard, when the person asked to add more. */
    private var kept = ""
    private var language = LANGUAGES.first()

    /** Starts a take. With [adding], what is heard is added after [soFar] instead of replacing it. */
    fun start(adding: Boolean = false, soFar: String = "") {
        kept = if (adding) soFar.trim() else ""
        language = LANGUAGES.first()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            _state.value = State.Problem("This phone has no on-device speech recognition, so Actum can't listen without sending your voice somewhere. It won't do that.")
            return
        }
        _state.value = State.Listening(kept, 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) chooseLanguageThenListen() else listen()
    }

    /**
     * Asks the recognizer which speech packs are on the phone, and listens in
     * the first English one. With none, says so and offers to ask Android for
     * it, instead of trying languages until one fails differently.
     */
    private fun chooseLanguageThenListen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        release()
        val mine = ++take
        val asking = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = asking
        asking.checkRecognitionSupport(
            request(),
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if (mine != take) return
                    val installed = support.installedOnDeviceLanguages
                    Log.i(TAG, "speech packs installed=$installed pending=${support.pendingOnDeviceLanguages}")
                    val english = LANGUAGES.firstOrNull { it in installed } ?: installed.firstOrNull { it.startsWith("en") }
                    when {
                        english != null -> {
                            language = english
                            listen()
                        }
                        support.pendingOnDeviceLanguages.any { it.startsWith("en") } -> {
                            release()
                            _state.value = State.Problem("Android is still installing the English speech pack. Try again in a minute.")
                        }
                        else -> {
                            release()
                            _state.value = State.Problem(NO_PACK, canFetch = true)
                        }
                    }
                }

                // The question itself failed: listen anyway, and let the take say what is wrong.
                override fun onError(error: Int) {
                    if (mine == take) listen()
                }
            },
        )
    }

    /**
     * Listens with the recognizer already held, if there is one. Letting one
     * go and making another in the same breath drops the new one's connection
     * to the service too ("server disconnected"), so one is kept per sheet.
     */
    private fun listen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mine = ++take
        val held = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it }
        held.setRecognitionListener(Hearing(mine))
        _state.value = State.Listening(kept, 0f)
        held.startListening(request())
    }

    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)

    /** The person is done talking: what was heard so far becomes the result. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Asks Android to install the Indian English speech pack. Android downloads it; Actum cannot. */
    fun fetchPack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        release()
        language = LANGUAGES.first()
        val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        created.triggerModelDownload(request())
        created.destroy()
        _state.value = State.Problem("Asked Android for the English speech pack. It downloads in the background; try again once it has installed.")
    }

    /** The person said no to the microphone. */
    fun refused() {
        _state.value = State.Problem("Actum isn't allowed to use the microphone. You can allow it in the phone's settings for Actum.")
    }

    /** Lets go of the microphone. Call when the sheet closes. */
    fun release() {
        take++
        recognizer?.destroy()
        recognizer = null
    }

    fun reset() {
        release()
        _state.value = State.Idle
    }

    private fun joined(now: String) = listOf(kept, now.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun best(results: Bundle?) = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    /** One recognizer's reports, heeded only while it is still the one listening. */
    private inner class Hearing(private val mine: Int) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (mine != take) return
            val now = _state.value as? State.Listening ?: return
            // Reported in decibels, roughly -2 for silence to 10 for a raised voice.
            _state.value = now.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (mine != take) return
            val now = _state.value as? State.Listening ?: return
            val words = best(partialResults)
            if (words.isNotEmpty()) _state.value = now.copy(heard = joined(words))
        }

        override fun onResults(results: Bundle?) {
            if (mine != take) return
            release()
            _state.value = State.Heard(joined(best(results)))
        }

        override fun onError(error: Int) {
            if (mine != take) return
            Log.i(TAG, "recognizer error $error in $language")
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                // Indian English first; a phone without that pack may still have another English.
                val next = LANGUAGES.getOrNull(LANGUAGES.indexOf(language) + 1)
                if (next != null) {
                    language = next
                    listen()
                    return
                }
            }
            release()
            _state.value = when (error) {
                // Nothing said, or nothing made out: not a failure, just an empty take.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> State.Heard(kept)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    State.Problem(NO_PACK, canFetch = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> State.Problem("Actum isn't allowed to use the microphone.")
                SpeechRecognizer.ERROR_AUDIO -> State.Problem("The microphone is busy. Is a call or another recorder using it?")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> State.Problem("The phone's speech recognizer is busy. Try again in a moment.")
                else -> State.Problem("The phone's speech recognizer stopped (code $error). Try again.")
            }
        }
    }

    private companion object {
        const val TAG = "ActumVoice"
        val LANGUAGES = listOf("en-IN", "en-US", "en-GB")
        const val NO_PACK = "The English speech pack for offline recognition isn't on this phone yet. " +
            "Android can install it; that download is the phone's, not Actum's."
    }
}
