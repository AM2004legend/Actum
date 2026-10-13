package com.actum.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingTest {

    /** What the model once handed back for a question about Australia: its own instructions' example. */
    private val echo = """{"actions":[
        {"intent":"EVENT","fields":{"title":"meeting","date_text":"Friday","time_text":"5pm"},"source_span":"meeting Friday 5pm"},
        {"intent":"TODO","fields":{"items":["buy milk"]},"source_span":"buy milk"},
        {"intent":"EXPENSE","fields":{"amount":200,"counterparty_or_merchant":"Amit","direction":"spent"},"source_span":"paid Amit 200"}
    ]}"""

    private fun kept(answer: String, said: String) = Grounding.keep(EnvelopeParser.parse(answer), said)

    @Test
    fun `actions about something nobody said are dropped`() {
        val result = kept(echo, "what is the capital of Australia")
        assertEquals(Envelope.Invalid(Grounding.NOT_ABOUT_THE_MESSAGE), result)
    }

    @Test
    fun `actions about the message are kept, in any language it was written in`() {
        val said = "kal 10 tay exam, rice kinte hobe, Rahul ke 500 dilam"
        val answer = """{"actions":[
            {"intent":"EVENT","fields":{"title":"exam","date_text":"kal","time_text":"10tay"},"source_span":"kal 10 tay exam"},
            {"intent":"TODO","fields":{"items":["rice kinte hobe"]},"source_span":"rice kinte hobe"},
            {"intent":"EXPENSE","fields":{"amount":500,"counterparty_or_merchant":"Rahul","direction":"spent"},"source_span":"Rahul ke 500 dilam"}
        ]}"""
        assertEquals(3, (kept(answer, said) as Envelope.Actions).actions.size)
    }

    @Test
    fun `only the made-up one goes when the rest are real`() {
        val said = "buy milk before the shop shuts"
        val result = kept(echo, said) as Envelope.Actions
        assertEquals(listOf(ActumIntent.TODO), result.actions.map { it.intent })
    }

    @Test
    fun `a refusal and an action with no span are judged on what they can be`() {
        val refusal = """{"actions":[{"intent":"UNSUPPORTED","fields":{"reason":"booking a ride"},"source_span":""}]}"""
        assertTrue(kept(refusal, "book me a cab") is Envelope.Actions)

        val spanless = """{"actions":[{"intent":"TODO","fields":{"items":["renew passport"]}}]}"""
        assertTrue(kept(spanless, "need to renew my passport") is Envelope.Actions)
        assertTrue(kept(spanless, "what is the capital of Australia") is Envelope.Invalid)
    }
}
