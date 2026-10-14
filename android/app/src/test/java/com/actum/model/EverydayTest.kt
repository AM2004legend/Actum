package com.actum.model

import com.actum.validate.FieldState
import com.actum.validate.Validator
import com.actum.validate.Verdict
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Commands as people really type and say them: slang and short forms, and
 * Hinglish and Benglish in Roman letters. Like the English rules, two lists:
 * what must be read, and what must still go to the model.
 */
class EverydayTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val now = LocalTime.of(14, 30)
    private val validator = Validator(today, now)

    private fun field(action: ProposedAction, name: String) = (action.fields[name] as? JsonPrimitive)?.content
    private fun items(action: ProposedAction) = (action.fields["items"] as JsonArray).map { (it as JsonPrimitive).content }

    private fun read(said: String, intent: ActumIntent): ProposedAction {
        val action = Rules.route(said)
        assertNotNull("no rule read: $said", action)
        assertEquals(said, intent, action!!.intent)
        return action
    }

    /** What the card would hold: the field's resolved value, and that it passed. */
    private fun value(said: String, name: String): Any? {
        val checked = validator.check(Rules.route(said)!!, said)
        val f = checked.fields.single { it.name == name }
        assertEquals("$said → $name ${f.state} ${f.question}", FieldState.VERIFIED, f.state)
        return f.value
    }

    // ── Slang ──────────────────────────────────────────────────────────────

    @Test
    fun `short forms of days`() {
        read("set an alarm for 7 am tmrw", ActumIntent.ALARM)
        assertEquals(today.plusDays(1), value("set an alarm for 7 am tmrw", "date_text"))
        assertEquals(today.plusDays(1), value("wake me up at 6:30 am 2moro", "date_text"))
        assertEquals(today.plusDays(1), value("alarm at 6 am next day", "date_text"))
        assertEquals(today.plusDays(2), value("alarm for 6 am day after tmrw", "date_text"))
        assertEquals(LocalTime.of(22, 0), value("remind me to call mom at 10 pm tonite", "time_text"))
    }

    @Test
    fun `stretches from now, said any way`() {
        assertEquals(LocalTime.of(14, 32), value("set an alarm 2 mins later", "time_text"))
        assertEquals(LocalTime.of(14, 35), value("remind me 5 min from now to check the oven", "time_text"))
        assertEquals("check the oven", field(read("remind me 5 min from now to check the oven", ActumIntent.ALARM), "label"))
        assertEquals(LocalTime.of(15, 0), value("wake me up in half an hour", "time_text"))
        assertEquals(LocalTime.of(14, 40), value("alarm after 10 mins", "time_text"))
    }

    @Test
    fun `fillers and sign-offs`() {
        read("yo set an alarm for 7 am pls", ActumIntent.ALARM)
        read("bro wake me up at 6 am tmrw fr", ActumIntent.ALARM)
        read("can u set an alarm for 7 am", ActumIntent.ALARM)
        read("alarm @ 7am", ActumIntent.ALARM)
        assertEquals(listOf("buy milk"), items(read("gotta buy milk", ActumIntent.TODO)))
        assertEquals(listOf("buy eggs", "buy bread"), items(read("need to buy eggs and bread", ActumIntent.TODO)))
        read("paid Rahul 500 thx", ActumIntent.EXPENSE)
    }

    // ── Hinglish ───────────────────────────────────────────────────────────

    @Test
    fun `hinglish alarms and reminders`() {
        val kal = read("kal subah 7 baje alarm laga do", ActumIntent.ALARM)
        assertEquals("kal", field(kal, "date_text"))
        assertEquals("subah 7 baje", field(kal, "time_text"))
        assertEquals(today.plusDays(1), value("kal subah 7 baje alarm laga do", "date_text"))
        assertEquals(LocalTime.of(7, 0), value("kal subah 7 baje alarm laga do", "time_text"))
        assertEquals(LocalTime.of(18, 30), value("sham saade 6 baje ka alarm lagao", "time_text"))
        read("alarm laga do kal subah 6 baje", ActumIntent.ALARM)
        read("subah 6 baje utha dena", ActumIntent.ALARM)
        assertEquals(LocalTime.of(14, 35), value("5 min baad yaad dila dena", "time_text"))
        assertEquals("mummy ko call karna hai", field(read("10 minute baad yaad dila dena ki mummy ko call karna hai", ActumIntent.ALARM), "label"))
        assertEquals(LocalTime.of(16, 30), value("2 ghante baad remind kar dena", "time_text"))
    }

    @Test
    fun `hinglish shopping, money and contacts`() {
        assertEquals(listOf("doodh", "ande"), items(read("doodh aur ande lene hai", ActumIntent.TODO)))
        assertEquals(listOf("atta", "chawal", "dal"), items(read("atta, chawal aur dal kharidna hai", ActumIntent.TODO)))
        val gave = read("Rahul ko 500 diye", ActumIntent.EXPENSE)
        assertEquals("Rahul", field(gave, "counterparty_or_merchant"))
        assertEquals("spent", field(gave, "direction"))
        assertEquals(today.minusDays(1), value("kal Rahul ko 500 diye", "date_text"))
        assertEquals("lunch", field(read("lunch pe 200 kharch kiye", ActumIntent.EXPENSE), "counterparty_or_merchant"))
        assertEquals("owed_to_me", field(read("Amit ko 1000 udhaar diye", ActumIntent.EXPENSE), "direction"))
        assertEquals("i_owe", field(read("mujhe Sourav ko 350 dene hain", ActumIntent.EXPENSE), "direction"))
        assertEquals("Sourav", field(read("mujhe Sourav ko 350 dene hain", ActumIntent.EXPENSE), "counterparty_or_merchant"))
        assertEquals("Priya", field(read("Priya ka number save karo 98300 12345", ActumIntent.CONTACT), "name"))
    }

    // ── Benglish ───────────────────────────────────────────────────────────

    @Test
    fun `benglish alarms and reminders`() {
        val kal = read("kal sokal 7 tay alarm diye dao", ActumIntent.ALARM)
        assertEquals(today.plusDays(1), value("kal sokal 7 tay alarm diye dao", "date_text"))
        assertEquals(LocalTime.of(7, 0), value("kal sokal 7 tay alarm diye dao", "time_text"))
        assertEquals("sokal 7 tay", field(kal, "time_text"))
        read("ratre 10 tay alarm dao".replace("ratre", "raate"), ActumIntent.ALARM)
        read("sokal 6 tay uthiye dio", ActumIntent.ALARM)
        assertEquals(LocalTime.of(14, 40), value("10 minute pore mone koriye dio", "time_text"))
        assertEquals("ma ke phone korte hobe", field(read("10 min pore mone koriye dio je ma ke phone korte hobe", ActumIntent.ALARM), "label"))
    }

    @Test
    fun `benglish shopping, money and contacts`() {
        assertEquals(listOf("dim", "pauruti", "makhon"), items(read("dim, pauruti ar makhon kinte hobe", ActumIntent.TODO)))
        assertEquals(listOf("chal", "dal"), items(read("chal o dal anbo", ActumIntent.TODO)))
        assertEquals("Rahul", field(read("Rahul ke 500 dilam", ActumIntent.EXPENSE), "counterparty_or_merchant"))
        assertEquals("lunch", field(read("lunch e 200 khoroch holo", ActumIntent.EXPENSE), "counterparty_or_merchant"))
        assertEquals("owed_to_me", field(read("Amit ke 1000 dhar dilam", ActumIntent.EXPENSE), "direction"))
        assertEquals("i_owe", field(read("Sourav ke 350 dite hobe", ActumIntent.EXPENSE), "direction"))
        assertEquals("Priya", field(read("Priya er number 98300 12345 save koro", ActumIntent.CONTACT), "name"))
    }

    @Test
    fun `what they read passes the same checks`() {
        for (said in listOf("Rahul ko 500 diye", "Rahul ke 500 dilam", "doodh aur ande lene hai", "Priya ka number save karo 98300 12345", "set an alarm 2 mins later")) {
            assertEquals(said, Verdict.PASS, validator.check(Rules.route(said)!!, said).verdict)
        }
    }

    // ── Still the model's ──────────────────────────────────────────────────

    @Test
    fun `negations, second things and vague times are left alone`() {
        val leftAlone = listOf(
            // negations: "na", "nahi", "mat" turn the command round
            "kal subah 7 baje alarm mat lagana",
            "kal sokal 7 tay alarm dio na",
            "doodh nahi lena hai",
            "dim kinte hobe na",
            "Rahul ko 500 nahi diye",
            // a second thing
            "kal subah 7 baje alarm laga do aur doodh lena hai",
            "Rahul ke 500 dilam ar dim kinte hobe",
            "kal 10 tay exam, rice kinte hobe, Rahul ke 500 dilam",
            "alarm laga do phir mummy ko call karna",
            // not a command
            "kal doodh lena tha",
            "maine alarm lagaya tha",
            "Rahul ne mujhe 500 diye",
            // no time a rule can be sure of
            "7 ka alarm laga do",
            "alarm laga do",
            "remind me in a bit",
            "set an alarm for later",
            "wake me up in 5",
            "remind me eod",
            // things that are not things to buy
            "Rahul ko call karna hai",
            "kal 7 baje lena hai",
            // a pronoun is not a name
            "mujhe 500 diye",
            "mera number save karo 98300 12345",
        )
        for (said in leftAlone) {
            val action = Rules.route(said)
            if (action != null) fail("a rule read \"$said\" as ${action.intent} ${action.fields}")
        }
    }

    @Test
    fun `saved text gets the same rules, a sentence at a time`() {
        val split = Pieces.split("Kal subah 7 baje alarm laga do.\nDoodh aur ande lene hai.\nMeeting ka time confirm karna hai, Rahul se puchna.")
        assertEquals(listOf(ActumIntent.ALARM, ActumIntent.TODO), split.ruled.map { it.second.intent })
        assertEquals("Meeting ka time confirm karna hai, Rahul se puchna.", split.rest)
    }
}
