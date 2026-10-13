# CLAUDE.md — Motus build context and guardrails

You are the coding agent building **Motus**. Read this whole file before writing any
code. Follow it over your own defaults. When something here conflicts with a habit
(for example "write full tests first"), this file wins.

---

## 0. What you are building, in one paragraph

Motus is an **offline Android app** that turns one shared thing (a screenshot, photo,
typed text or voice note — often in Bengali, Hindi, Hinglish or English) into the
**right action after one confirmation tap**. The user shares into Motus; an on-device
**Gemma** model decides which of a fixed list of actions it is and extracts the fields;
**plain Kotlin code** validates and executes the action through normal Android intents or
a local database. Nothing leaves the phone. It is **not** a chatbot, not an assistant,
not a second-brain. Its job is **action, not retrieval or conversation.**

If you ever find yourself building a chat screen, a "search everything" screen, a cloud
call, or an open-ended agent, stop — that is the wrong product.

---

## 1. Hard guardrails (do not break these)

1. **No network.** The app must NOT declare the `INTERNET` permission. No cloud APIs,
   no analytics SDK, no crash-reporting that uploads, no ads. Audit every dependency you
   add; if it needs the network, do not use it.
2. **Closed action list.** Only the intents in section 4 exist. Anything else returns
   `UNSUPPORTED` and the app says so. Never invent a new action type.
3. **Confirm before execute.** Nothing happens without a user tap on a preview card.
   The model never triggers a side effect directly.
4. **Deterministic code does the real work.** Dates, times, amounts, phone numbers,
   validation, storage and Android actions are plain Kotlin. The model only interprets
   messy language and picks the intent. Never let the model compute a date or decide
   whether something is valid.
5. **No fake success, no invented claims.** If a field is missing or unsure, show the
   amber/red state and ask. Never display success for something that did not execute.
   Never put a capability in the UI or README that is not actually implemented.
6. **No sensitive advice.** The Explain action restates a document only. No medical,
   legal or financial advice anywhere.
7. **Verify before you assert.** If you are unsure of a library name, a Gradle
   dependency, an Android API or a model file name, check current official docs or ask —
   do not guess. Facts in this file are marked (VERIFIED) or (UNVERIFIED); treat
   UNVERIFIED as "must test on the real phone."

---

## 2. Reuse Breadcrumb — recycle, don't rebuild

There is an existing repo, **Breadcrumb** (native Kotlin + Jetpack Compose Android app).
**Before writing new code, read it** and reuse what already works. The goal is speed:
do not re-solve problems Breadcrumb already solved.

**Step 2a — inventory the repo first.** Start by reading, in this order:
- the Android `build.gradle(.kts)` files (modules, dependencies, SDK/Java versions),
- the `AndroidManifest.xml` (permissions, share-sheet intent filters, Quick Settings tile),
- the share-sheet / capture entry code,
- the ML Kit OCR integration,
- the Room database setup (entities, DAOs, database class),
- any WorkManager usage,
- the Compose theme / design system files.

Produce a short written inventory: **"reuse as-is / adapt / ignore"** for each of
the above, with file paths. Do this before milestone M1.

**Reuse as-is where possible:**
- Share-sheet intent filters and the capture receiver.
- Quick Settings `TileService`.
- ML Kit text recognition (OCR) wiring — keep the **bundled** model variant so it works
  offline. (Check which scripts it supports; Bengali support is UNVERIFIED.)
- Room scaffolding (database class, migration setup, DAO patterns).
- Compose theme, colors, typography, spacing.

**Do NOT reuse:**
- Breadcrumb's backend (Node/Express), MongoDB Atlas, vector search, or any Gemini
  **cloud** API calls. Motus is offline. Strip those paths out of anything you copy.
- Breadcrumb's retrieval / second-brain UX. Motus's home is actions, not search.

Known Breadcrumb dev setup (from project notes, VERIFY against the repo): Java 21,
Android SDK API 36.1, Gradle wrapper, build with `gradlew installDebug`. Tested on
physical Android phones.

**On tests:** do not spend time writing an exhaustive test suite or chasing coverage.
Reuse Breadcrumb's existing tests if they still pass; don't fix unrelated failing tests.
The one exception is the **field parsers** (section 5) — those get small unit tests
because a wrong date or amount breaks the demo. Everything else is verified by the
**manual check** listed on each task. Speed over ceremony.

---

## 3. Tech stack (confirm names against current docs before coding)

- **Language / UI:** Kotlin, Jetpack Compose, Material 3. Match Breadcrumb's versions.
- **Model runtime:** Google AI Edge **LiteRT-LM** for on-device Gemma (`.litertlm`).
  Look up the exact current Gradle dependency and API — do not assume. (UNVERIFIED API
  surface.)
- **Model (demo baseline):** **Gemma (E2B, text-only)** loaded from the app's files dir.
  Baseline first because it needs no training. A smaller / faster Gemma-family router is
  a later optimization, not the starting point.
- **OCR:** ML Kit text recognition, bundled, offline.
- **Storage:** Room. Encrypt the DB (SQLCipher or a Keystore-backed key). FTS table for
  the local log search.
- **Voice (optional / stretch):** Android `SpeechRecognizer` offline if a language pack
  exists (UNVERIFIED for Bengali); otherwise accept shared audio or typed text.
- **App lock:** `BiometricPrompt` / device credential for diary and ledger.
- **Model distribution for the hackathon:** sideload the model with `adb` into the app's
  files dir and verify its published SHA-256 before loading. (Honest README note: a
  shipping app would download it on first run.)

**Demo phone:** user's phone, assumed Nothing Phone (3a) Lite — Dimensity 7300 Pro, 8GB
RAM (VERIFIED for that model; UNVERIFIED that it is the exact phone). Expect CPU-bound
inference. **Measure latency early** (see M1 gate).

---

## 4. The closed action list

Model returns **strict JSON only**, this envelope, up to 5 actions per input:

```json
{ "actions": [
  { "intent": "EVENT|ALARM|EXPENSE|CONTACT|TODO|DIARY|EXPLAIN|UNSUPPORTED",
    "confidence": 0.0,
    "fields": { },
    "source_span": "the words from the input that justify this action" }
] }
```

| Intent | Fields (required*) | Executor |
|---|---|---|
| EVENT | title*, date_text*, time_text, location, notes | Calendar `ACTION_INSERT` intent, prefilled; user confirms in their calendar app (no calendar permission needed) |
| ALARM | label, time_text*, date_text, repeat_days | `AlarmClock.ACTION_SET_ALARM` |
| EXPENSE | amount*, currency(INR), counterparty_or_merchant, category(enum), date_text, direction(spent/owed_to_me/i_owe) | insert into local Room ledger |
| CONTACT | name*, phone*(validated), note | Contacts insert intent, prefilled |
| TODO | items*(list), list_name | insert into local Room list |
| DIARY | text*(verbatim), entry_date(today) | local **encrypted** table, app lock, `FLAG_SECURE` on screen; no mood analysis |
| EXPLAIN | summary, key_dates, what_to_do | show text; optional TTS read-aloud; restates only |
| UNSUPPORTED | reason | show "Motus can't do this yet" + reason; do nothing |

The model copies dates / times **as written**; Kotlin resolves them. The model never
executes anything.

---

## 5. Pipeline (build in this order inside the app)

1. Input arrives via share sheet (text / image / audio) or Quick Settings tile (typed / dictated).
2. Normalize: trim, note the script, keep the original.
3. Images → ML Kit OCR → text. (Do not send raw images to the model by default — slow.)
4. **Deterministic pre-parsers** pull candidates: dates + relative dates (kal, parshu,
   aaj, "next Friday", Bengali equivalents), clock times, amounts (₹ / Rs / rupees),
   phone numbers, URLs. Resolve relative dates against the **device date + timezone
   passed into the code**, never the model.
5. Model returns the JSON envelope.
6. **Validator:** schema + enum check; cross-check model fields against the pre-parser
   candidates; reject unknown intents; cap at 5 actions; JSON-repair then one retry, then
   fall back to UNSUPPORTED / amber card.
7. **Preview cards:** one per action; each field shows green (verified) / amber (unsure) /
   red (missing).
8. User taps Confirm per card.
9. Handler executes (intent or DB).
10. Save a small JSON record to the local log (fields only, not raw images).

**Result states:** pass (all required valid, cross-checks agree) → confirm; uncertain
(ambiguity) → highlight field, ask one question; fail / unsupported → say so, do nothing.

---

## 6. UI — make it clean and calm (name everything "Motus")

Principles: one capture screen, one preview screen, large tap targets, readable in
Bengali and English, visible progress while the model runs, never fake success.

Screens to build:
- **Capture receiver** (opens from share sheet): shows what was received, auto-starts
  processing with a clear progress state.
- **Preview cards:** one card per action, editable fields, state chips (green/amber/red),
  Confirm / Skip.
- **Refusal screen:** "Motus can't do this yet" + one reason.
- **Home:** tabs — To-do, Expenses, Diary, **Log** (the JSON gallery, keyword search).
- **Privacy screen:** reads the app's own requested permissions at runtime and shows that
  `INTERNET` is not among them. This is the "proof" screen for the demo.
- **Settings:** language, app lock, (optional) smart-mode download status.

Do NOT build: a chat screen, a search-everything screen, a mood dashboard, or an
onboarding longer than two screens.

Use Breadcrumb's existing theme as the base; refine spacing, states and the empty / error
states rather than restyling from scratch. If a dedicated design pass is warranted, prefer
a calm, high-contrast, single-accent palette; avoid templated defaults.

---

## 7. Work broken into small, testable tasks

Each task has a **manual check** = the one thing that proves it works. Mark a task done
only when its check passes **on the real phone** (or on the parser's unit test where
noted). Do them in order. Stop and tell the user the result at each gate.

**M0 — Repo inventory (no new code).**
- Read Breadcrumb per section 2a; write the reuse / adapt / ignore inventory with file paths.
- Check: a short inventory doc exists and names the exact files for share sheet, OCR,
  Room and theme.

**M1 — Model returns JSON on the phone (the make-or-break gate).**
- Load Gemma E2B via LiteRT-LM; strip the DB down to nothing for now; send one hard-coded
  sentence; get the envelope back; add JSON-repair + one retry.
- **Measure and report median + worst latency to a complete JSON answer.**
- Check: the sentence `"kal 10 tay exam, rice kinte hobe, Rahul ke 500 dilam"` returns 3
  valid actions. Report the time. If it's worse than ~15–20s, stop and we cut scope.

**M2 — Parsers + validator (the only unit-tested part).**
- Kotlin parsers: relative-date (en / hi / bn), time, amount, phone. Validator with
  pass / uncertain / fail and the model-vs-parser cross-check.
- Check: a tiny unit test over ~20 strings passes, including 3 ambiguous dates that must
  come back `uncertain`.

**M3 — Share-sheet → preview cards (reuse Breadcrumb capture).**
- Wire the reused share receiver + OCR to the pipeline; render preview cards with state
  chips and Confirm.
- Check: sharing a text message and a notice **screenshot** each produce correct cards
  with correct field states.

**M4 — Real actions for the 3 demo intents.**
- EVENT (calendar insert intent), EXPENSE (Room ledger), TODO (Room list). UNSUPPORTED
  screen for anything else.
- Check: confirming each card actually creates the calendar event / ledger row / to-do;
  "book me a cab" shows the refusal and does nothing.

**M5 — Polish + privacy proof + log (demo-ready).**
- Privacy screen (runtime permission read), progress / error states, local JSON log with
  keyword search, Motus branding throughout.
- Check: the full demo runs **twice in a row in airplane mode** without a break. Record a
  backup screen capture.

**M6 — Stretch, only if M5 is fully done.**
- CONTACT, ALARM, then the multi-action voice dump, then DIARY / EXPLAIN, then a
  smaller / faster Gemma router. Each with its own manual check. Cut from the bottom if
  time runs out.

**Scope-cut order if behind:** voice → smart mode → diary / explain → alarm → contact →
multi-action split → log search. Protect M1–M5.

---

## 8. Definition of done for the hackathon

- M1–M5 complete; demo runs twice in airplane mode.
- `INTERNET` permission absent; privacy screen proves it.
- 3 actions (EVENT, EXPENSE, TODO) + honest UNSUPPORTED all work from a real shared input.
- A measured accuracy number exists from a ~30-sentence test set (don't fabricate it).
- README states real limits honestly (calendar app may sync; model sideloaded; Bengali
  OCR / TTS status; latency measured on the demo phone).

---

## 9. When you're blocked

If any task is stuck for more than ~20 minutes, **cut scope** rather than adding
complexity, and tell the user what you cut and why. Prefer one fully working vertical
slice over several half-working features. Never paper over a failure with a fake success
state or an unimplemented claim.
