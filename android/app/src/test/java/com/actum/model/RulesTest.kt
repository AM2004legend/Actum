package com.actum.model

import com.actum.ui.gate.TestSet
import com.actum.validate.Validator
import com.actum.validate.Verdict
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The rules, sentence by sentence. Two lists matter equally: what a rule must
 * read, and what it must leave to the model. A sentence added to either list
 * after a slip on the phone stays here for good.
 */
class RulesTest {

    private fun field(action: ProposedAction, name: String) = (action.fields[name] as? JsonPrimitive)?.content

    private fun items(action: ProposedAction) = (action.fields["items"] as JsonArray).map { (it as JsonPrimitive).content }

    private fun read(said: String, intent: ActumIntent): ProposedAction {
        val action = Rules.route(said)
        assertNotNull("no rule read: $said", action)
        assertEquals(said, intent, action!!.intent)
        return action
    }

    // ── What a rule must read ──────────────────────────────────────────────

    @Test
    fun alarms() {
        assertEquals("7 am", field(read("set an alarm for 7 am", ActumIntent.ALARM), "time_text"))
        assertEquals("6:30 pm", field(read("Set alarm at 6:30 pm", ActumIntent.ALARM), "time_text"))
        assertEquals("6 am", field(read("wake me up at 6 am", ActumIntent.ALARM), "time_text"))
        assertEquals("8 more minutes", field(read("set an alarm for 8 more minutes", ActumIntent.ALARM), "time_text"))
        assertEquals("20 minutes", field(read("alarm in 20 minutes", ActumIntent.ALARM), "time_text"))
        assertEquals("an hour", field(read("Hey, wake me in an hour please", ActumIntent.ALARM), "time_text"))
        assertEquals("7 o'clock in the morning", field(read("set an alarm for 7 o'clock in the morning", ActumIntent.ALARM), "time_text"))
        assertEquals("5 am", field(read("can you set an alarm for 5 am?", ActumIntent.ALARM), "time_text"))

        val dated = read("set an alarm for 6 am tomorrow", ActumIntent.ALARM)
        assertEquals("6 am", field(dated, "time_text"))
        assertEquals("tomorrow", field(dated, "date_text"))
        assertEquals("tomorrow", field(read("wake me up tomorrow at 5:45 am", ActumIntent.ALARM), "date_text"))
    }

    @Test
    fun reminders() {
        val soon = read("remind me in 10 minutes", ActumIntent.ALARM)
        assertEquals("10 minutes", field(soon, "time_text"))
        assertNull(field(soon, "label"))

        val labelled = read("remind me in half an hour to check the oven", ActumIntent.ALARM)
        assertEquals("half an hour", field(labelled, "time_text"))
        assertEquals("check the oven", field(labelled, "label"))

        val at = read("remind me to call dad at 9 pm", ActumIntent.ALARM)
        assertEquals("call dad", field(at, "label"))
        assertEquals("9 pm", field(at, "time_text"))

        val tomorrow = read("remind me to call the dentist tomorrow at 4pm", ActumIntent.ALARM)
        assertEquals("call the dentist", field(tomorrow, "label"))
        assertEquals("tomorrow", field(tomorrow, "date_text"))
    }

    @Test
    fun `things to buy`() {
        assertEquals(listOf("buy milk"), items(read("buy milk", ActumIntent.TODO)))
        assertEquals(listOf("buy eggs", "buy bread", "buy butter"), items(read("buy eggs, bread and butter", ActumIntent.TODO)))
        assertEquals(listOf("buy 2 kg rice"), items(read("Buy 2 kg rice", ActumIntent.TODO)))

        val listed = read("add toothpaste and shampoo to the shopping list", ActumIntent.TODO)
        assertEquals(listOf("toothpaste", "shampoo"), items(listed))
        assertEquals("shopping", field(listed, "list_name"))
        assertEquals(listOf("renew passport"), items(read("todo: renew passport", ActumIntent.TODO)))
    }

    @Test
    fun money() {
        val who = read("paid Rahul 500", ActumIntent.EXPENSE)
        assertEquals("500", field(who, "amount"))
        assertEquals("Rahul", field(who, "counterparty_or_merchant"))
        assertEquals("spent", field(who, "direction"))
        assertEquals("Rahul", field(read("paid Rahul 500 for lunch", ActumIntent.EXPENSE), "counterparty_or_merchant"))

        val on = read("spent 250 on auto", ActumIntent.EXPENSE)
        assertEquals("250", field(on, "amount"))
        assertEquals("auto", field(on, "counterparty_or_merchant"))
        assertEquals("1,200", field(read("paid Rs 1,200 for the electricity bill", ActumIntent.EXPENSE), "amount"))

        val lent = read("lent Priya 2000 yesterday", ActumIntent.EXPENSE)
        assertEquals("owed_to_me", field(lent, "direction"))
        assertEquals("yesterday", field(lent, "date_text"))
        assertEquals("i_owe", field(read("I owe Sourav 350 for the movie tickets", ActumIntent.EXPENSE), "direction"))
    }

    @Test
    fun contacts() {
        val whose = read("save Ankit's number 9830012345", ActumIntent.CONTACT)
        assertEquals("Ankit", field(whose, "name"))
        assertEquals("9830012345", field(whose, "phone"))

        val under = read("save the number 94740618282828 under the name hero", ActumIntent.CONTACT)
        assertEquals("hero", field(under, "name"))
        assertEquals("94740618282828", field(under, "phone"))
        assertEquals("Dr Sen", field(read("add contact Dr Sen 98765 43210", ActumIntent.CONTACT), "name"))
    }

    @Test
    fun `the diary keeps every word, at any length`() {
        val entry = read("diary: felt tired today, but the walk by the lake helped and I did not check my phone once", ActumIntent.DIARY)
        assertEquals("felt tired today, but the walk by the lake helped and I did not check my phone once", field(entry, "text"))
        assertEquals("I finally finished the chapter", field(read("Dear diary, I finally finished the chapter", ActumIntent.DIARY), "text"))
    }

    // ── What a rule must leave to the model ────────────────────────────────

    @Test
    fun `anything less than plain is left alone`() {
        val leftAlone = listOf(
            // a second thing said
            "paid Rahul 500 and remind me to call him at 6",
            "buy milk and call mom",
            "set an alarm for 7 am and buy bread",
            "wake me at 5 am, flight to Delhi on 2 November at 7:40 am",
            // remarks, not commands
            "I forgot to buy milk yesterday",
            "the alarm didn't ring this morning",
            "I should set an alarm",
            "did I buy milk",
            "what is the capital of Australia",
            // negations and conditions
            "don't set an alarm for 7 am",
            "set an alarm for 7 am if it rains",
            // not enough said for a rule to be sure
            "set an alarm",
            "set an alarm for 7",
            "alarm for dinner",
            "wake me up when september ends",
            "remind me to call mom",
            "buy milk tomorrow",
            "buy mom a present before Friday",
            "paid the bill",
            "paid 1200 rupees electricity bill",
            "save my number 9830012345",
            "save this number",
            // alarms with more to them than a time
            "set an alarm for 7:15 pm to take medicine",
            "alarm at 5:30 am every weekday for gym",
            // events and everything else are the model's
            "dentist appointment on 20 October at 11am",
            "meeting with the bank manager Monday 3pm",
            "need to renew my passport",
            "pick up the parcel from the post office",
            "book me a cab to the airport",
            "order a pizza from Dominos",
            "send a WhatsApp message to Riya saying I'm late",
            "diary of a wimpy kid",
            "",
        )
        for (said in leftAlone) {
            val action = Rules.route(said)
            if (action != null) fail("a rule read \"$said\" as ${action.intent} ${action.fields}")
        }
    }

    @Test
    fun `on the accuracy set a rule is right or silent, never wrong`() {
        var read = 0
        for ((sentence, expected) in TestSet.sentences) {
            val action = Rules.route(sentence) ?: continue
            assertEquals("a rule misread: $sentence", expected, listOf(action.intent))
            read++
        }
        println("RULES_READ $read of ${TestSet.sentences.size}")
        // Not a target, a tripwire: if the rules stop reading the plain sentences, this says so.
        if (read < 8) fail("the rules read only $read of ${TestSet.sentences.size} sentences")
    }

    // ── And what a rule reads must pass the same checks as the model's ─────

    @Test
    fun `a rule's proposal goes through the validator like any other`() {
        val validator = Validator(LocalDate.of(2026, 10, 13), LocalTime.of(20, 43, 30))
        fun verdict(said: String) = validator.check(Rules.route(said)!!, said)

        val alarm = verdict("set an alarm for 8 more minutes")
        assertEquals(Verdict.PASS, alarm.verdict)
        assertEquals(LocalTime.of(20, 52), alarm.fields.single { it.name == "time_text" }.value)

        assertEquals(Verdict.PASS, verdict("paid Rahul 500").verdict)
        assertEquals(Verdict.PASS, verdict("buy eggs, bread and butter").verdict)
        assertEquals(Verdict.PASS, verdict("save Ankit's number 9830012345").verdict)
        assertEquals(Verdict.PASS, verdict("remind me to call the dentist tomorrow at 4pm").verdict)
        // Seven o'clock, morning or evening? The rule read it; the validator still asks.
        assertEquals(Verdict.UNCERTAIN, verdict("set an alarm for 7 o'clock").verdict)
        // Fourteen digits is not a phone number a rule can vouch for.
        assertEquals(Verdict.UNCERTAIN, verdict("save the number 94740618282828 under the name hero").verdict)
    }
}
