package com.actum.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The actions an answer has written out whole so far, while the model is still writing. */
class FinishedActionsTest {

    private val answer = """{"actions":[{"intent":"EVENT","fields":{"title":"exam","date_text":"kal","time_text":"10 tay"},"source_span":"kal 10 tay exam"},""" +
        """{"intent":"TODO","fields":{"items":["rice"]},"source_span":"rice kinte hobe"},""" +
        """{"intent":"EXPENSE","fields":{"amount":500,"counterparty_or_merchant":"Rahul"},"source_span":"Rahul ke 500 dilam"}]}"""

    private fun intentsAt(length: Int) = EnvelopeParser.finished(answer.take(length)).map { it.intent }

    @Test
    fun `each action counts once its object has closed`() {
        val firstEnds = answer.indexOf("},{\"intent\":\"TODO\"") + 1
        assertEquals(emptyList<ActumIntent>(), intentsAt(firstEnds - 1))
        assertEquals(listOf(ActumIntent.EVENT), intentsAt(firstEnds))
        assertEquals(listOf(ActumIntent.EVENT, ActumIntent.TODO), intentsAt(answer.indexOf(",{\"intent\":\"EXPENSE\"")))
        assertEquals(listOf(ActumIntent.EVENT, ActumIntent.TODO, ActumIntent.EXPENSE), intentsAt(answer.length))
    }

    @Test
    fun `every prefix gives a prefix of the actions, never more`() {
        var before = 0
        for (length in 0..answer.length) {
            val now = EnvelopeParser.finished(answer.take(length)).size
            assert(now >= before) { "went back at $length" }
            before = now
        }
        assertEquals(3, before)
    }

    @Test
    fun `braces inside words are words`() {
        val tricky = """{"actions":[{"intent":"DIARY","fields":{"text":"a } and a { in my day"},"source_span":"x"}"""
        assertEquals("a } and a { in my day", EnvelopeParser.finished(tricky).single().fields["text"].toString().trim('"'))
    }

    @Test
    fun `nothing before the list starts`() {
        assertEquals(0, EnvelopeParser.finished("{\"acti").size)
        assertEquals(0, EnvelopeParser.finished("").size)
    }
}

/** Seen on the phone once the model stopped writing source spans: an action's last fields written as an object of their own. */
class StitchTest {

    @Test
    fun `fields written as an object of their own go back on the action before`() {
        val noIntent = EnvelopeParser.parse(
            """{"actions":[{"intent":"EXPLAIN","fields":{"summary":"The tank will be cleaned"}},{"fields":{"key_dates":["18 October"],"what_to_do":["Store water"]}}]}""",
        ) as Envelope.Actions
        assertEquals(listOf(ActumIntent.EXPLAIN), noIntent.actions.map { it.intent })
        assertEquals(setOf("summary", "key_dates", "what_to_do"), noIntent.actions.single().fields.keys)

        val fieldAsIntent = EnvelopeParser.parse(
            """{"actions":[{"intent":"TODO","fields":{"items":["toothpaste","shampoo"]}},{"intent":"list_name","fields":{"list_name":"shopping list"}}]}""",
        ) as Envelope.Actions
        assertEquals(listOf(ActumIntent.TODO), fieldAsIntent.actions.map { it.intent })
        assertEquals(setOf("items", "list_name"), fieldAsIntent.actions.single().fields.keys)
    }

    @Test
    fun `an unknown action is still unsupported, not stitched`() {
        val booked = EnvelopeParser.parse("""{"actions":[{"intent":"TODO","fields":{"items":["milk"]}},{"intent":"BOOK","fields":{"what":"cab"}}]}""") as Envelope.Actions
        assertEquals(listOf(ActumIntent.TODO, ActumIntent.UNSUPPORTED), booked.actions.map { it.intent })
    }

    @Test
    fun `while still being written, too`() {
        val partial = """{"actions":[{"intent":"EXPLAIN","fields":{"summary":"x"}},{"fields":{"key_dates":["18 October"]}},{"intent":"TO"""
        assertEquals(setOf("summary", "key_dates"), EnvelopeParser.finished(partial).single().fields.keys)
    }
}
