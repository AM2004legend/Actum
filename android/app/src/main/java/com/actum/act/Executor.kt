package com.actum.act

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.actum.data.Diary
import com.actum.data.DiaryEntry
import com.actum.data.DiaryVault
import com.actum.data.Expense
import com.actum.data.ListsDao
import com.actum.data.LogRecord
import com.actum.data.Marker
import com.actum.data.TodoItem
import com.actum.model.ActumIntent
import com.actum.validate.CheckedAction
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** What happened when a confirmed action ran. Said to the person as it is: never a success that was not one. */
sealed interface Outcome {
    data class Done(val what: String) : Outcome
    data class Failed(val why: String) : Outcome
    data object Skipped : Outcome
}

/**
 * Runs one action the person confirmed. Plain Android: an intent the calendar,
 * clock or contacts app finishes, or a row in Actum's own lists. The model is
 * nowhere here; every value was resolved by the validator.
 */
class Executor(
    private val lists: ListsDao,
    private val vault: DiaryVault,
    private val keystore: Diary,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Runs [action], and writes what was done to the log. Only what was done: a failure leaves no record. */
    suspend fun run(context: Context, action: CheckedAction): Outcome {
        val outcome = perform(context, action)
        if (outcome is Outcome.Done) {
            lists.addLog(record(action, outcome))
            marker(action)?.let { lists.addMarker(it) }
        }
        return outcome
    }

    /** The day an event or reminder is for, to mark in Actum's own calendar. Null for every other action. */
    private fun marker(action: CheckedAction): Marker? {
        fun <T> value(name: String, type: Class<T>): T? =
            action.fields.firstOrNull { it.name == name }?.value?.let { if (type.isInstance(it)) type.cast(it) else null }
        val date = value("date_text", LocalDate::class.java)
        val time = value("time_text", LocalTime::class.java)
        val now = System.currentTimeMillis()
        return when (action.intent) {
            ActumIntent.EVENT -> date?.let {
                Marker(kind = Marker.EVENT, title = value("title", String::class.java) ?: "Event", date = it.toString(), time = time?.toString(), createdAt = now)
            }
            ActumIntent.ALARM -> time?.let {
                // With no day said, the day the clock will next show that time.
                val rings = date ?: java.time.LocalDateTime.now(zone).let { at -> if (it > at.toLocalTime()) at.toLocalDate() else at.toLocalDate().plusDays(1) }
                Marker(kind = Marker.REMINDER, title = value("label", String::class.java) ?: "Reminder", date = rings.toString(), time = it.toString(), createdAt = now)
            }
            else -> null
        }
    }

    /** The fields as checked, as text. A diary entry's words stay out: the log is not encrypted. */
    private fun record(action: CheckedAction, outcome: Outcome.Done): LogRecord {
        val json = buildJsonObject {
            put("intent", action.intent.name)
            put("at", java.time.LocalDateTime.now(zone).withNano(0).toString())
            putJsonObject("fields") {
                if (action.intent != ActumIntent.DIARY) {
                    action.fields.forEach { put(it.name, (it.value as? List<*>)?.joinToString(", ") ?: it.value?.toString() ?: it.text) }
                }
            }
            put("source", if (action.intent == ActumIntent.DIARY) "" else action.sourceSpan)
            put("outcome", outcome.what)
        }
        return LogRecord(intent = action.intent.name, json = json.toString(), createdAt = System.currentTimeMillis())
    }

    private suspend fun perform(context: Context, action: CheckedAction): Outcome {
        fun text(name: String) = action.fields.firstOrNull { it.name == name }?.value as? String
        fun <T> value(name: String, type: Class<T>): T? =
            action.fields.firstOrNull { it.name == name }?.value?.let { if (type.isInstance(it)) type.cast(it) else null }

        return when (action.intent) {
            ActumIntent.EVENT -> {
                val date = value("date_text", LocalDate::class.java) ?: return Outcome.Failed("There is no date yet.")
                val time = value("time_text", LocalTime::class.java)
                val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, text("title"))
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, text("location"))
                    .putExtra(CalendarContract.Events.DESCRIPTION, text("notes"))
                if (time != null) {
                    val begin = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MILLIS)
                } else {
                    // An all-day event's times are midnights in UTC, whatever the phone's zone.
                    val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 24 * HOUR_MILLIS)
                }
                open(context, intent, "Opened in your calendar. Save it there.", "No calendar app on this phone.")
            }
            ActumIntent.ALARM -> {
                val time = value("time_text", LocalTime::class.java) ?: return Outcome.Failed("There is no time yet.")
                // The clock app takes a time, not a day: it rings the next time the clock
                // shows it. Asked for any other day, an alarm would ring on the wrong one,
                // so that reminder goes to the calendar instead.
                val date = value("date_text", LocalDate::class.java)
                val now = java.time.LocalDateTime.now(zone)
                val nextRing = if (time > now.toLocalTime()) now.toLocalDate() else now.toLocalDate().plusDays(1)
                if (date != null && date != nextRing) {
                    val begin = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    val event = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, text("label") ?: "Reminder")
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + HOUR_MILLIS / 4)
                    return open(
                        context, event,
                        "A clock alarm can't be set for another day, so this opened in your calendar. Save it there.",
                        "No calendar app on this phone.",
                    )
                }
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, text("label"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                open(context, intent, "Handed to your clock app.", "No clock app on this phone takes alarms.")
            }
            ActumIntent.CONTACT -> {
                val intent = Intent(ContactsContract.Intents.Insert.ACTION)
                    .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                    .putExtra(ContactsContract.Intents.Insert.NAME, text("name"))
                    .putExtra(ContactsContract.Intents.Insert.PHONE, text("phone"))
                    .putExtra(ContactsContract.Intents.Insert.NOTES, text("note"))
                open(context, intent, "Opened in your contacts. Save it there.", "No contacts app on this phone.")
            }
            ActumIntent.TODO -> {
                val items = (action.fields.firstOrNull { it.name == "items" }?.value as? List<*>).orEmpty().filterIsInstance<String>()
                if (items.isEmpty()) return Outcome.Failed("There is nothing to add.")
                val now = System.currentTimeMillis()
                lists.addTodos(items.map { TodoItem(text = it, listName = text("list_name"), createdAt = now) })
                Outcome.Done(if (items.size == 1) "Added to your to-dos." else "Added ${items.size} to your to-dos.")
            }
            ActumIntent.EXPENSE -> {
                val amount = value("amount", BigDecimal::class.java) ?: return Outcome.Failed("There is no amount yet.")
                lists.addExpense(
                    Expense(
                        amountPaise = amount.movePointRight(2).toLong(),
                        counterparty = text("counterparty_or_merchant"),
                        category = text("category") ?: "other",
                        direction = text("direction") ?: "spent",
                        date = (value("date_text", LocalDate::class.java) ?: LocalDate.now(zone)).toString(),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                Outcome.Done("Added to your expenses.")
            }
            ActumIntent.DIARY -> {
                val words = text("text") ?: return Outcome.Failed("There is nothing to write.")
                // Sealed to the password when there is one. Before that, to the phone's
                // keystore alone, and said so: it is not locked until a password is set.
                val locked = vault.seal(words)
                val sealed = locked ?: keystore.seal(words.toByteArray(Charsets.UTF_8))
                lists.addDiary(DiaryEntry(sealed = sealed, date = LocalDate.now(zone).toString(), createdAt = System.currentTimeMillis()))
                Outcome.Done(
                    if (locked != null) "Written in your diary, locked with your password."
                    else "Written in your diary. Set a password in the Diary tab to lock it.",
                )
            }
            // Nothing to run: an explanation is only shown, and its card has no Confirm.
            ActumIntent.EXPLAIN, ActumIntent.UNSUPPORTED -> Outcome.Failed("Actum can't do this yet.")
        }
    }

    private fun open(context: Context, intent: Intent, done: String, none: String): Outcome = try {
        context.startActivity(intent)
        Outcome.Done(done)
    } catch (_: ActivityNotFoundException) {
        Outcome.Failed(none)
    } catch (_: SecurityException) {
        Outcome.Failed(none)
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
    }
}
