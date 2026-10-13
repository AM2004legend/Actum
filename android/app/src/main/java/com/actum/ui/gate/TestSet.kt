package com.actum.ui.gate

import com.actum.model.ActumIntent
import com.actum.model.ActumIntent.ALARM
import com.actum.model.ActumIntent.CONTACT
import com.actum.model.ActumIntent.DIARY
import com.actum.model.ActumIntent.EVENT
import com.actum.model.ActumIntent.EXPENSE
import com.actum.model.ActumIntent.EXPLAIN
import com.actum.model.ActumIntent.TODO
import com.actum.model.ActumIntent.UNSUPPORTED

/**
 * The sentences the accuracy number is measured on: English, as people type
 * them, each with the actions it should become. A sentence counts as right
 * only when the model returns exactly these intents, no more and no fewer.
 * None of them is in the model's prompt.
 */
object TestSet {
    val sentences: List<Pair<String, List<ActumIntent>>> = listOf(
        "dentist appointment on 20 October at 11am" to listOf(EVENT),
        "team lunch next Friday 1pm at Peter Cat" to listOf(EVENT),
        "mom's birthday on 3 Nov" to listOf(EVENT),
        "project demo tomorrow 10:30 am in lab 2" to listOf(EVENT),
        "parent teacher meeting Saturday 9 am" to listOf(EVENT),
        "wake me up at 6 am" to listOf(ALARM),
        "set an alarm for 7:15 pm to take medicine" to listOf(ALARM),
        "alarm at 5:30 am every weekday for gym" to listOf(ALARM),
        "spent 250 on auto to the station" to listOf(EXPENSE),
        "paid 1200 rupees electricity bill" to listOf(EXPENSE),
        "lent Priya 2000 yesterday" to listOf(EXPENSE),
        "I owe Sourav 350 for the movie tickets" to listOf(EXPENSE),
        "Rs 499 for Netflix subscription" to listOf(EXPENSE),
        "save Ankit's number 9830012345" to listOf(CONTACT),
        "new contact: Dr Sen, 98765 43210, cardiologist" to listOf(CONTACT),
        "buy eggs, bread and butter" to listOf(TODO),
        "need to renew my passport" to listOf(TODO),
        "add toothpaste and shampoo to the shopping list" to listOf(TODO),
        "pick up the parcel from the post office" to listOf(TODO),
        "diary: felt tired today but the walk by the lake helped" to listOf(DIARY),
        "dear diary, I finally finished the chapter I was stuck on" to listOf(DIARY),
        "explain this notice: The society water tank will be cleaned on 18 October. Supply will be off from 10 am to 2 pm. Store water in advance." to listOf(EXPLAIN),
        "book me a cab to the airport" to listOf(UNSUPPORTED),
        "order a pizza from Dominos" to listOf(UNSUPPORTED),
        "send a WhatsApp message to Riya saying I'm late" to listOf(UNSUPPORTED),
        "what is the capital of Australia" to listOf(UNSUPPORTED),
        "meeting with the bank manager Monday 3pm, and buy stamps" to listOf(EVENT, TODO),
        "paid 80 for tea and remind me to call dad at 9 pm" to listOf(EXPENSE, ALARM),
        "exam on 12 November at 10 am, buy graph paper, paid 300 for the admit card" to listOf(EVENT, TODO, EXPENSE),
        "wake me at 5 am, flight to Delhi on 2 November at 7:40 am" to listOf(ALARM, EVENT),
    )
}
