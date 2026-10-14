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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Why an envelope is [Envelope.Invalid] when its reading was stopped part way by [GemmaRouter.cancel]. */
const val STOPPED = "stopped"

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

    /**
     * How a model is given its instructions. Either way they are read in when the
     * conversation is made, before the person asks; what differs is the chat template.
     */
    enum class Prompt {
        /** As a system instruction, examples included. Gemma 4 has a system turn. */
        SYSTEM,

        /**
         * As an opening exchange: the rules and the first example as the person's turn,
         * its answer as the model's, then the other examples likewise. Gemma 3 has no
         * system turn; its template folds a system instruction into the first message,
         * so it could not be read in until the person had asked.
         */
        TURNS,
    }

    private val lock = Mutex()
    private var engine: Engine? = null
    /** What [engine] was loaded from and on: another file or processor means loading again. */
    private var running: Pair<String, Processor>? = null
    private var conversation: ConversationConfig = conversationFor(Prompt.SYSTEM)

    /**
     * A conversation made ahead of time, with the instructions already read
     * into it. Reading them is most of the wait for a short request, and it
     * is the same every time, so it is done before the person asks.
     */
    private var warm: Conversation? = null

    /** The conversation reading right now, for [cancel]. */
    @Volatile
    private var talking: Conversation? = null

    /** Loads [model] if it is not loaded on [processor] already. Returns the milliseconds it took. */
    suspend fun load(model: File, processor: Processor, prompt: Prompt): Long = withContext(Dispatchers.Default) {
        lock.withLock {
            if (engine != null && running == model.path to processor) return@withLock 0L
            conversation = conversationFor(prompt)
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
            running = model.path to processor
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
            if (warm == null) warm = engine.createConversation(conversation)
        }
    }

    /**
     * The envelope for [input]. An answer that is not an envelope even after
     * repair is asked for once more, in the same conversation; a second miss is
     * [Envelope.Invalid], which the caller shows as unsupported.
     *
     * [partial], when given, hears each action as soon as the model has written
     * it out whole and it is about [input]: the person sees the first card while
     * the rest are still being written.
     */
    suspend fun route(input: String, partial: ((List<ProposedAction>) -> Unit)? = null): Routed = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = checkNotNull(engine) { "load() first" }
            val started = SystemClock.elapsedRealtime()
            val answers = mutableListOf<String>()
            val ready = warm ?: engine.createConversation(conversation)
            warm = null
            talking = ready
            val envelope = try {
                ready.use { conversation ->
                val first = conversation.stream(input, input, partial).also { answers += it }
                val parsed = Grounding.keep(EnvelopeParser.parse(first), input)
                when {
                    parsed is Envelope.Actions -> parsed
                    // A well-formed reply with nothing in it is an answer, not a slip: asking again only invites one made up.
                    parsed is Envelope.Invalid && parsed.reason in FINAL -> parsed
                    else -> Grounding.keep(EnvelopeParser.parse(conversation.ask(RETRY).also { answers += it }), input)
                }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stopped by [cancel], most often: the words changed while they were being read.
                Envelope.Invalid(STOPPED)
            } finally {
                talking = null
            }
            // Grounded on the model's own fields first; then each action is given the words it came from.
            val located = (envelope as? Envelope.Actions)?.let { it.copy(actions = Spans.fill(it.actions, input)) } ?: envelope
            Routed(located, answers, SystemClock.elapsedRealtime() - started)
        }
    }

    /**
     * Stops the reading under way, if any, so the next one need not wait for it. Safe from any
     * thread and without the lock: that is the point of it.
     */
    fun cancel() {
        runCatching { talking?.cancelProcess() }
    }

    suspend fun close() = lock.withLock {
        warm?.close()
        warm = null
        engine?.close()
        engine = null
        running = null
    }

    private fun Conversation.ask(text: String): String = sendMessage(text).text()

    /** As [ask], handing on the actions finished so far each time one more is. */
    private suspend fun Conversation.stream(text: String, input: String, partial: ((List<ProposedAction>) -> Unit)?): String {
        if (partial == null) return ask(text)
        val answer = StringBuilder()
        var heard = 0
        sendMessageAsync(text).collect { message ->
            val piece = message.text()
            // Each message is the next piece; were one ever the whole answer so far, it replaces it.
            if (answer.isNotEmpty() && piece.length > answer.length && piece.startsWith(answer)) answer.setLength(0)
            answer.append(piece)
            val done = EnvelopeParser.finished(answer)
            if (done.size > heard) {
                heard = done.size
                (Grounding.keep(Envelope.Actions(done, repaired = false), input) as? Envelope.Actions)?.let { partial(Spans.fill(it.actions, input)) }
            }
        }
        return answer.toString()
    }

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
        val RULES = """
            Turn the message into actions. Reply with minified JSON on one line, no code fence, no other text:
            {"actions":[{"intent":"","fields":{}}]}
            One action per separate thing in the message, at most 5. Intents and fields:
            EVENT: title, date_text, time_text, location, notes. Something on a date: a meeting, an exam, a reminder for a day.
            ALARM: label, time_text, date_text. Wake or remind me at a clock time.
            EXPENSE: amount (number), counterparty_or_merchant, category (food, transport, shopping, bills, health, education, personal, other), date_text, direction (spent, owed_to_me, i_owe).
            CONTACT: name, phone, note.
            TODO: items (list of strings), list_name. Things to buy or do, with no time.
            DIARY: text. Words for my diary, copied exactly.
            EXPLAIN: summary, key_dates, what_to_do. Only when asked to explain a notice or document: restate it, add nothing, give no advice.
            UNSUPPORTED: reason. Anything else, such as booking, ordering, sending messages, playing media, or a question. Never reply with an empty list.
            Copy date_text and time_text exactly as written. Leave out fields the message does not give.
        """.trimIndent()

        /** Each a message and the reply wanted for it. */
        val EXAMPLES = listOf(
            "meeting Friday 5pm, buy milk, paid Amit 200" to
                """{"actions":[{"intent":"EVENT","fields":{"title":"meeting","date_text":"Friday","time_text":"5pm"}},{"intent":"TODO","fields":{"items":["buy milk"]}},{"intent":"EXPENSE","fields":{"amount":200,"counterparty_or_merchant":"Amit","direction":"spent"}}]}""",
            "play some music" to
                """{"actions":[{"intent":"UNSUPPORTED","fields":{"reason":"playing music"}}]}""",
        )

        /** For [Prompt.SYSTEM]: the rules, then the examples written out. */
        val INSTRUCTION = RULES + EXAMPLES.joinToString("") { (message, reply) -> "\nMessage: $message\nReply: $reply" }

        /** For [Prompt.TURNS]: the examples as turns already taken, the rules in the first. */
        val OPENING: List<Message> = EXAMPLES.flatMapIndexed { i, (message, reply) ->
            listOf(Message.user(if (i == 0) "$RULES\n\n$message" else message), Message.model(reply))
        }

        /** Greedy: the same input gives the same actions, and a preview the person can trust to repeat. */
        val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0, seed = 0)

        fun conversationFor(prompt: Prompt) = when (prompt) {
            Prompt.SYSTEM -> ConversationConfig(
                systemInstruction = Contents.of(INSTRUCTION),
                samplerConfig = GREEDY,
                maxOutputToken = 400,
                // Read the instructions when the conversation is made, not when the first message arrives.
                prefillPrefaceOnInit = true,
            )
            Prompt.TURNS -> ConversationConfig(
                initialMessages = OPENING,
                samplerConfig = GREEDY,
                maxOutputToken = 400,
                prefillPrefaceOnInit = true,
            )
        }
    }
}
