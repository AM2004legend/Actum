# CLAUDE.md — Actum's rules

You are working on **Actum**. Read this file and `AGENTS.md` before changing anything.
This file is the rules and what the product is. `AGENTS.md` is how the code is laid out,
how to build and run it on a phone, and what is known to be unfinished. Follow both over
your own defaults.

## What Actum is

An **offline Android app** that does two things, both on the phone:

1. **Keeps and finds.** Whatever is shared into it (text, screenshots, pictures, PDFs) is
   saved, read by OCR, and searchable by meaning and by words.
2. **Does.** A request that is typed, spoken, or found in a saved item becomes the right
   action after one confirming tap.

It is not a chatbot, not an open-ended assistant, and it never talks to a server. If you
find yourself building a chat screen, a cloud call or an agent that acts on its own, stop.

English first. Direct commands are also read in everyday slang ("tmrw", "2 mins later") and
in Hinglish and Benglish written in Roman letters. Gemma reads those languages loosely;
there is no test set for them yet. Hindi and Bengali script are wanted later.

## Rules that must not be broken

1. **No network.** The app must not have the `INTERNET` permission. Libraries merge it in,
   so the manifest removes it; check the built APK after any dependency change. No cloud
   APIs, analytics, crash uploaders or ads. Models are sideloaded, never downloaded by
   the app.
2. **Closed action list.** `EVENT, ALARM, EXPENSE, CONTACT, TODO, DIARY, EXPLAIN,
   UNSUPPORTED`. Anything else is `UNSUPPORTED` and the app says so. Do not add an action
   unless the owner asks.
3. **Confirm before execute.** Nothing happens without a tap on a card (or, for a spoken
   diary entry, on **Save to diary**). Neither the model nor a rule triggers a side
   effect.
4. **Deterministic code does the real work.** Dates, times, amounts, phone numbers,
   validation, storage and Android actions are plain Kotlin. The model and the rules only
   propose. The model is never told today's date and never computes one.
5. **Two readers, one path.**
   - **Rules** read only plain, direct commands, and only when the whole sentence fits a
     pattern. A rule is right or silent, never a best guess. Anything with a second
     clause, a negation (also "na", "nahi", "mat"), a question or an unexpected word goes
     to the model. Slang is made plain first (`Lingo`), only where it has one meaning:
     "in a bit" or "eod" is never turned into a time.
   - A saved picture, PDF or note is split into sentences; each sentence a rule reads
     whole becomes that rule's card, and **Gemma** reads the rest. Gemma also reads
     everything typed or spoken that no rule reads.
   - Both produce the same kind of proposal, which goes through the same validator, the
     same card and the same confirmation.
6. **An answer must be about what was said.** A model reply whose actions are not
   grounded in the person's words is dropped, not shown.
7. **Actum never teaches itself.** Requests that went to the model but could have been
   instant, and rule cards the person corrected, are kept on the phone as lessons for a
   person to read and turn into rules and tests. No rule is ever made or changed by the
   app.
8. **No fake success, no invented claims.** Show what actually happened. A missing or
   unsure field is marked and blocks or questions the action. Never put a capability in
   the UI or the README that is not built and checked.
9. **No sensitive advice.** `EXPLAIN` restates a document only. No medical, legal or
   financial advice anywhere.
10. **Voice stays on the phone.** Only Android's on-device speech recognizer. If the phone
   has none, say so and do not listen. Keep the words, never the audio.
11. **Verify before you assert.** For a library API, a dependency or a model file, read
    the real artifact or current official docs. Do not guess.

## The actions

The model returns strict JSON, at most 5 actions:

```json
{ "actions": [
  { "intent": "EVENT|ALARM|EXPENSE|CONTACT|TODO|DIARY|EXPLAIN|UNSUPPORTED",
    "fields": { } }
] }
```

Each action's `source_span` (the person's words it came from) is found by Kotlin
(`Spans`), after the grounding check, not written by the model: writing it out was about a
fifth of every answer. Rules set it to the sentence they read.

| Intent | Fields (required*) | What confirming does |
| --- | --- | --- |
| EVENT | title*, date_text*, time_text, location, notes | Calendar insert intent, prefilled; the person saves it in their calendar app. Marks the day in Actum's calendar tab. |
| ALARM | time_text*, label, date_text | Clock app's set-alarm intent. For any day but its next natural ring, a calendar event instead. Marks the day. |
| EXPENSE | amount*, counterparty_or_merchant, category, date_text, direction (spent / owed_to_me / i_owe) | A row in Actum's ledger. |
| CONTACT | name*, phone* (validated), note | Contacts insert intent, prefilled. |
| TODO | items* (list), list_name | Rows in Actum's to-do list. |
| DIARY | text* (verbatim) | Sealed with the diary's key and stored. Its words never go in the log. |
| EXPLAIN | summary, key_dates, what_to_do | Shown only. Nothing runs. |
| UNSUPPORTED | reason | "Actum can't do this yet." Nothing runs. |

Dates and times are copied as written (`date_text`, `time_text`) and resolved by Kotlin:
relative days (today, tomorrow, tmrw, next day, kal, parshu), weekdays, written dates,
clock times ("7 pm", "subah 7 baje", "sokal 7 tay"), and stretches from now ("in 8
minutes", "2 mins later", "5 min baad", "10 minute pore").

## How a request flows

1. Words arrive: typed, spoken (on-device recognizer, editable transcript), or taken from
   a saved item's text.
2. Typed and spoken words are offered to the rules first, whole. A saved item's are split
   into sentences and each is offered to the rules.
3. Gemma reads what is left, as it is written: each action is shown as a card as soon as
   it is complete. Its reply is repaired if malformed, retried once, checked for being
   about the message, and refused if it still is not an answer. Typed and spoken words
   are read ahead of the tap; a saved item is read in the background soon after it is
   saved, and its proposals are kept with it, so its cards are there when asked for.
4. The validator checks every field against the person's words and resolves dates and
   times: verified, unsure (with one question, and choices where there are some), or
   missing.
5. One card per action. Fields can be edited; an edit is validated again.
6. The person confirms. The executor runs it and writes a small JSON record to the log.
7. A confirmed request that could have been read by a rule, or a rule's card that was
   corrected, is kept as a lesson (never a diary's words).

## UI

Calm, minimal, large tap targets (48 dp), visible progress while the model works, and
never a success that did not happen. Two looks, dark ink and beige paper, switched on the
home screen. Each action has one colour and one icon, used wherever it appears. Amber is
the app's own colour and the colour of a question.

Do not build: a chat screen, a mood dashboard, or onboarding longer than two screens.

## Testing

Unit-test what is pure and what would hurt if wrong: parsers, the validator, the rules
(both what they must read and what they must leave alone), envelope repair, grounding,
and the diary lock. Verify everything else on a real phone. When a slip is found on the
phone, add its sentence to the tests before fixing it. Do not chase coverage.

After any change to the model prompt, the answer format or the repair step, re-run both
benchmarks (see `benchmarks/README.md`) and record them. Speed and accuracy move together
in surprising ways; never report one without the other.

## When you're blocked

If something is stuck for more than about 20 minutes, cut scope, and tell the owner what
you cut and why. Prefer one thing fully working over several half-working. Never cover a
failure with a fake success or an unimplemented claim.
