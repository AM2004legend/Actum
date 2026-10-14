# Stress Test & Benchmark Limitations Analysis

This document analyzes the complex multi-action prompt tested on the **Motorola edge 50 pro** (`gemma-4-E2B-it.litertlm` on CPU), detailing what benchmarks and expectations the model could and could not reach, along with architectural and semantic root causes.

---

## 1. Executive Summary

| Test Case | Expected Actions | Extracted Actions | Latency | Status | Primary Root Cause |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Complex Multi-Action Stress Test** (2,794 chars) | ~14 actions + 4 negative constraints | 6 actions | 34.9 s | **Partial (Truncated)** | Input cut off at character 1,200 by `MAX_INPUT_CHARS` in `Actor.kt`. |
| **Shared Expense / Debt Split** (Train Tickets) | `direction: "i_owe"`, amount: 615, counterparty: "Rahul" | `direction: "spent"`, amount: 2,460 | (part of above) | **Semantic Error** | Schema & prompt lack multi-party debt splitting logic. |
| **General Knowledge Refusal** (`TestSet` #26) | `[UNSUPPORTED]` | `[]` (empty list) | 1.5 s | **Miss (Benchmark)** | Model returns empty envelope for questions rather than `UNSUPPORTED`. |

---

## 2. The Complex Stress Test Prompt

Submitted on device at `2026-10-14 14:37:56.532` (**2,794 characters** total):

```text
Okay, so we need to sort out everything for our Darjeeling trip and the farewell party because everyone is being chaotic 😭

First, set an alarm for 6:30 AM tomorrow because our train leaves early, and remind me to call the hotel at 8 AM tomorrow to confirm the booking. Our train tickets cost ₹2,460 for four people, and Rahul paid the full amount. Split that equally among Rahul, Priya, me, and Arjun, so each person's share is ₹615. Priya already paid Rahul ₹615, and Arjun paid ₹500, so Arjun still owes Rahul ₹115. I haven't paid anything yet, so I owe Rahul ₹615. Please record that I paid Rahul ₹200 today for snacks separately, because that is an actual expense and not part of the train ticket settlement.

For the trip, buy sunscreen, ORS, tissues, and a packet of biscuits. Also remind me to pack my charger tonight. Our hotel check-in is on 20 October at 2 PM, and check-out is on 22 October at 10 AM, but don't create a calendar event for the hotel stay unless I explicitly ask you to. Just keep those details in mind for now.

Now for the farewell party on 25 October at 6 PM in the college auditorium: create a calendar event called “CSBS Farewell Party.” I need to call the decorator tomorrow at 11 AM, remind me to do that. The decorator quoted ₹3,600, which we are splitting between six organizers: me, Priya, Rahul, Arjun, Neha, and Kabir. Everyone's share is ₹600. Neha has already paid ₹1,200 toward the decoration, so that is a payment toward the shared cost, not an additional expense. Don't mark anyone else as having paid unless I tell you.

I also spent ₹850 on the farewell venue booking today and ₹420 on printing invitation cards yesterday. Record both as separate expenses. The venue booking was paid by me, but Rahul paid for the printing, so don't assume I paid that one. If you cannot correctly record an expense paid by another person, ask me instead of assigning it to me.

Add these tasks to my to-do list: collect everyone's farewell photos, finish the presentation, confirm the DJ booking, and buy a gift for our professor. I have already finished the presentation, so don't add it as an unfinished task. The DJ booking is only a possibility right now, so don't mark it as confirmed or create an event for it.

Also, save this diary entry: “Today was stressful but exciting. I finally got the trip and farewell plans somewhat organized, and I hope everyone contributes on time.”

One more thing: I was thinking about buying a new power bank, but I haven't decided yet, so don't create a purchase task. And please don't create any calendar events, alarms, expenses, or contacts beyond the specific actions I've requested above. If any detail is ambiguous or an action isn't supported, show me what you understood and ask for confirmation instead of guessing.
```

---

## 3. Detailed Request vs. Extracted Actions

### A. First Half (Characters 1 to 1,200 — Passed to Model)

| Request in Text | Model Output in Logcat (`ActumAct`) | Evaluation |
| :--- | :--- | :---: |
| *Set an alarm for 6:30 AM tomorrow* | `ALARM` (label: "train departure reminder", time: "6:30 AM", date: "tomorrow") | **PASSED** |
| *Remind me to call the hotel at 8 AM tomorrow* | `TODO` (items: ["call the hotel at 8 AM tomorrow to confirm the booking"]) | **PASSED** |
| *Train tickets ₹2,460 paid by Rahul; I owe Rahul ₹615* | `EXPENSE` (amount: 2460, direction: "spent", counterparty: "train tickets") | **FAILED** |
| *Paid Rahul ₹200 for snacks separately* | `EXPENSE` (amount: 200, direction: "spent", counterparty: "Rahul") | **PASSED** |
| *Buy sunscreen, ORS, tissues, biscuits & pack charger* | `TODO` (items: ["buy sunscreen", "ORS", "tissues", "a packet of biscuits", "pack my charger tonight"]) | **PASSED** |
| *Hotel stay 20-22 Oct: "don't create calendar event"* | *No calendar event created* | **PASSED** |
| *Farewell party 25 Oct 6 PM in auditorium* | `EVENT` (title: "CSBS Farewell Party", date: "25 October", time: "6 PM", location: "college auditorium") | **PASSED** |

### B. Second Half (Characters 1,201 to 2,794 — Dropped Before Model)

| Request in Text | Model Output | Reason Not Reached |
| :--- | :--- | :--- |
| *Call decorator tomorrow at 11 AM* | None | Truncated by `MAX_INPUT_CHARS` (1,200 chars). |
| *Decorator cost ₹3,600 / Neha paid ₹1,200* | None | Truncated by `MAX_INPUT_CHARS`. |
| *Spent ₹850 on venue booking (user paid)* | None | Truncated by `MAX_INPUT_CHARS`. |
| *₹420 printing invitation cards (Rahul paid)* | None | Truncated by `MAX_INPUT_CHARS`. |
| *To-dos: collect photos, confirm DJ, buy gift* | None | Truncated by `MAX_INPUT_CHARS`. |
| *Presentation finished: "don't add as unfinished"* | None | Truncated by `MAX_INPUT_CHARS`. |
| *Diary entry: "Today was stressful but exciting..."* | None | Truncated by `MAX_INPUT_CHARS`. |
| *Power bank: "don't create purchase task"* | None | Truncated by `MAX_INPUT_CHARS`. |

---

## 4. Root Cause Analysis

### 1. Hard Input Truncation (`MAX_INPUT_CHARS = 1_200`)
- **Code Reference:** `android/app/src/main/java/com/actum/act/Actor.kt`:
  ```kotlin
  private companion object {
      const val MAX_INPUT_CHARS = 1_200
  }
  
  // In readNow() (line 159):
  router.route(said.take(MAX_INPUT_CHARS))
  ```
- **Rationale in Code:** *"A long screenshot or PDF: only its opening is read, or the wait runs to minutes."*
- **Impact:** Any single prompt, note, or OCR text exceeding 1,200 characters silently drops the remainder.

### 2. Semantic Misattribution on Debt & Bill Splitting
- **Code Reference:** `android/app/src/main/java/com/actum/model/GemmaRouter.kt` prompt instructions:
  - The instructions only instruct the model on basic expense directions: `"spent"` (I paid), `"owed_to_me"` (lent to someone), `"i_owe"` (borrowed from someone).
  - The prompt has no examples of multi-person splitting where party A paid the vendor and the user owes party A a partial fraction.
- **Model Behavior:** The model saw `"Our train tickets cost ₹2,460"` and extracted `amount: 2460, direction: "spent"`, assigning the whole vendor payment to the user, completely missing `"Rahul paid the full amount"` and `"I owe Rahul ₹615"`.

### 3. Inference Latency on Long Inputs
- **Measurement:** Generating 6 JSON envelopes from 1,200 input characters took **34.9 seconds** on device CPU (Snapdragon 7 Gen 3).
- **Extrapolation:** If all 2,794 characters were routed at once, inference time would have exceeded **80–100 seconds**, potentially triggering Android ANRs or battery thermal throttling.

### 4. Standard Benchmark Miss (Sentence 26 of 30)
- **Prompt:** `"what is the capital of Australia"`
- **Expected:** `[UNSUPPORTED]`
- **Actual:** `[]` (empty action list, logged in 1.5 s).
- **Diagnosis:** Gemma treats purely conversational or informational queries as producing no actionable items (`[]`) rather than explicitly outputting an action with `intent: "UNSUPPORTED"`. Actum's UI handles `[]` as a refusal, but the automated test set expects `want=[UNSUPPORTED]`.

---

## 5. Architectural Recommendations for Future Model Training

1. **Section Chunking / Paging:** For inputs longer than 1,200 characters, break multi-paragraph texts by newline or sentence boundaries and route in sequential chunks rather than truncating.
2. **Fine-Tuning on Complex Splits:** In Part 3 & 4 (Gemma 3 270M fine-tuning), include synthetic multi-person debt settlement examples in `train.jsonl` so the model maps *"X paid Y, my share is Z"* directly to `{"intent": "EXPENSE", "fields": {"amount": Z, "counterparty_or_merchant": "X", "direction": "i_owe"}}`.
3. **Explicit Refusal Envelope for Questions:** Train the model to return `UNSUPPORTED` for informational and general knowledge questions rather than an empty JSON array.
