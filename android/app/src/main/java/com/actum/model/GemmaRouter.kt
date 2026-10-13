package com.actum.model

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What one input came back as, and what it cost. */
data class Routed(
    val envelope: Envelope,
    /** The model's text, each attempt in turn: one, or two when the first was not an envelope. */
    val answers: List<String>,
    val millis: Long,
)

/**
 * Gemma on the phone, through LiteRT-LM: reads one input and proposes actions
 * from the closed list. It only proposes. Nothing here resolves a date, checks
 * a field or runs an action.
 *
 * One engine for the app, one input at a time. Each input gets a fresh
 * conversation, so nothing of one share is in the next one's context.
 */
class GemmaRouter(private val cacheDir: File) {

    enum class Processor { CPU, GPU }

    private val lock = Mutex()
    private var engine: Engine? = null
    private var running: Processor? = null

    /**
     * A conversation made ahead of time, with the instructions already read
     * into it. Reading them is most of the wait for a short request, and it
     * is the same every time, so it is done before the person asks.
     */
    private var warm: Conversation? = null

    /** Loads the model if it is not loaded on [processor] already. Returns the milliseconds it took. */
    suspend fun load(model: File, processor: Processor): Long = withContext(Dispatchers.Default) {
        lock.withLock {
            if (engine != null && running == processor) return@withLock 0L
            warm?.close()
            warm = null
            engine?.close()
            engine = null
            val started = SystemClock.elapsedRealtime()
            val config = EngineConfig(
                modelPath = model.path,
                backend = when (processor) {
                    Processor.CPU -> Backend.CPU()
                    Processor.GPU -> Backend.GPU()
                },
                cacheDir = cacheDir.path,
            )
            engine = Engine(config).also { it.initialize() }
            running = processor
            SystemClock.elapsedRealtime() - started
        }
    }

    /**
     * Reads the instructions into a fresh conversation now, so the next
     * [route] has only the person's words to read. Does nothing when one is
     * already waiting, or when the model is not loaded.
     */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = engine ?: return@withLock
            if (warm == null) warm = engine.createConversation(CONVERSATION)
        }
    }

    /**
     * The envelope for [input]. An answer that is not an envelope even after
     * repair is asked for once more, in the same conversation; a second miss is
     * [Envelope.Invalid], which the caller shows as unsupported.
     */
    suspend fun route(input: String): Routed = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = checkNotNull(engine) { "load() first" }
            val started = SystemClock.elapsedRealtime()
            val answers = mutableListOf<String>()
            val ready = warm ?: engine.createConversation(CONVERSATION)
            warm = null
            val envelope = ready.use { conversation ->
                val first = conversation.ask(input).also { answers += it }
                val parsed = Grounding.keep(EnvelopeParser.parse(first), input)
                when {
                    parsed is Envelope.Actions -> parsed
                    // A well-formed reply with nothing in it is an answer, not a slip: asking again only invites one made up.
                    parsed is Envelope.Invalid && parsed.reason in FINAL -> parsed
                    else -> Grounding.keep(EnvelopeParser.parse(conversation.ask(RETRY).also { answers += it }), input)
                }
            }
            Routed(envelope, answers, SystemClock.elapsedRealtime() - started)
        }
    }

    suspend fun close() = lock.withLock {
        warm?.close()
        warm = null
        engine?.close()
        engine = null
        running = null
    }

    private fun Conversation.ask(text: String): String = sendMessage(text).text()

    /** The answer's words only: anything on a thinking channel is left out. */
    private fun Message.text(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private companion object {
        const val RETRY = "That was not valid JSON in the required shape. Reply again with only the JSON object."

        /** Reasons a first reply is not asked for again. */
        val FINAL = setOf(EnvelopeParser.NO_ACTIONS, Grounding.NOT_ABOUT_THE_MESSAGE)

        /**
         * Short on purpose, and asks for one minified line: on the demo phone's
         * CPU every token read or written is time the person waits.
         */
        val INSTRUCTION = """
            Turn the message into actions. Reply with minified JSON on one line, no code fence, no other text:
            {"actions":[{"intent":"","fields":{},"source_span":""}]}
            One action per separate thing in the message, at most 5. Intents and fields:
            EVENT: title, date_text, time_text, location, notes. Something on a date: a meeting, an exam, a reminder for a day.
            ALARM: label, time_text, date_text. Wake or remind me at a clock time.
            EXPENSE: amount (number), counterparty_or_merchant, category (food, transport, shopping, bills, health, education, personal, other), date_text, direction (spent, owed_to_me, i_owe).
            CONTACT: name, phone, note.
            TODO: items (list of strings), list_name. Things to buy or do, with no time.
            DIARY: text. Words for my diary, copied exactly.
            EXPLAIN: summary, key_dates, what_to_do. Only when asked to explain a notice or document: restate it, add nothing, give no advice.
            UNSUPPORTED: reason. Anything else, such as booking, ordering, sending messages, playing media, or a question. Never reply with an empty list.
            Copy date_text and time_text exactly as written. Leave out fields the message does not give. source_span is the exact words for that action.
            Message: meeting Friday 5pm, buy milk, paid Amit 200
            Reply: {"actions":[{"intent":"EVENT","fields":{"title":"meeting","date_text":"Friday","time_text":"5pm"},"source_span":"meeting Friday 5pm"},{"intent":"TODO","fields":{"items":["buy milk"]},"source_span":"buy milk"},{"intent":"EXPENSE","fields":{"amount":200,"counterparty_or_merchant":"Amit","direction":"spent"},"source_span":"paid Amit 200"}]}
            Message: play some music
            Reply: {"actions":[{"intent":"UNSUPPORTED","fields":{"reason":"playing music"},"source_span":"play some music"}]}
        """.trimIndent()

        /** Greedy: the same input gives the same actions, and a preview the person can trust to repeat. */
        val CONVERSATION = ConversationConfig(
            systemInstruction = Contents.of(INSTRUCTION),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0, seed = 0),
            maxOutputToken = 400,
            // Read the instructions when the conversation is made, not when the first message arrives.
            prefillPrefaceOnInit = true,
        )
    }
}
