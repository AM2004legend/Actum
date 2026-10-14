# Actum

**Share it, find it, do something with it — all on the phone.**

Actum is an Android app with no internet permission. It keeps what you share into it
(screenshots, pictures, PDFs, text), lets you find it again by describing it, and turns
what you type or say into an action you confirm with one tap: a calendar event, an alarm,
an expense, a to-do, a contact or a diary entry.

## What it does

**Keep and find.** Share anything to Actum from another app, or copy it and tap the Actum
Quick Settings tile. Pictures and PDFs are read by on-device OCR. The search field finds
things by meaning ("when does my plane leave" finds a saved flight note) and by words.

**Ask.** Type a request in the same field and tap **Do this**, or tap the microphone and
say it. Your spoken words appear as they are heard and can be corrected before they are
used.

**Two ways a request is read.**

- A plain, direct command ("set an alarm for 7 am", "remind me in 8 minutes", "buy eggs,
  bread and butter", "paid Rahul 500", "save Ankit's number 98300 12345", "diary: …") is
  read by fixed rules, instantly. The card says so and offers **not right? ask Gemma**.
- Everything else, and every saved picture, PDF or note (**Find things to do in this**),
  is read by an on-device Gemma model.

Either way, plain Kotlin resolves the dates, times, amounts and phone numbers and checks
every field against your own words. Each action is shown as a card, and nothing happens
until you tap its button.

| Action | What confirming does |
| --- | --- |
| Calendar event | Opens your calendar app with the event filled in. You save it there. |
| Alarm | Hands the time to your clock app. A reminder for another day opens in the calendar instead, because the clock app cannot take a date. |
| Expense | Adds a line to Actum's own ledger. |
| To-do | Adds items to Actum's own list. |
| Contact | Opens your contacts app with the name and number filled in. |
| Diary | Stores the entry encrypted on the phone. Reading it needs the diary password you set, which you can change later. |
| Explain | Shows a restatement of a notice. Nothing is run, and it is not advice. |
| Anything else | "Actum can't do this yet", and nothing is done. |

On a card, a tick means the field was checked against what you wrote, a question mark
means it needs a look or a choice ("morning or evening?"), and an exclamation mark means
it is missing. Tap a field to change it; your change goes through the same checks.

**Speaking to the diary.** After speaking, **Save to diary** stores your words exactly as
said, with no model in between.

**Lists.** The **to-dos · calendar · more** link opens five tabs: to-dos, a month calendar
that marks the days of events and reminders you confirmed, expenses, the diary, and a log
of every confirmed action.

**Light and dark.** The sun or moon on the home screen switches between the two looks.

## Privacy

The app does not request `android.permission.INTERNET`, so Android gives it no way to
open a connection. The **privacy** link on the home screen reads the installed app's
permissions at that moment and lists them.

What Actum does not control:

- An event or contact you confirm is handed to your calendar or contacts app, which may
  sync it to its own account.
- Speech is turned into words by Android's on-device speech recognizer, a system service.
  Actum uses only the on-device one and refuses to listen if the phone has none.
- The microphone permission is used while the voice sheet is listening and at no other
  time. Only the words are kept, never the audio.
- Backups are disabled for the app.

## Measured on the reference phone

Nothing A001T (MediaTek Dimensity 7300, 8 GB RAM), Android 16, Gemma 4 E2B on the CPU
through LiteRT-LM 0.18.0, 13 October 2026. Phone on charge, battery at 39–42 °C.

| | Median | Worst | Correct |
| --- | --- | --- | --- |
| One sentence with three actions, 5 runs | 12.0 s | 13.3 s | 5 of 5 |
| 30-sentence English test set, per sentence | 6.0 s | 13.4 s | 29 of 30 |

- Direct-command rules read 9 of those 30 sentences, all correctly, without the model.
- Gemma is loaded, and its instructions read in (about 4 s), when Actum opens, so neither
  is part of the figures above. Sharing into Actum does not load it.
- **Requests are read ahead.** When typing pauses for about a second, or as soon as speech
  has become words, Actum starts reading the request before **Do this** is tapped. Tapped
  a few seconds later, the cards are already there: in two tries on the reference phone
  the wait after the tap was under 0.1 s, against about 8 s of reading. Tapped at once,
  the wait was 7 s. Direct commands and one- or two-word searches are not read ahead.
- The one miss ("what is the capital of Australia") returns nothing, which Actum shows as
  a refusal.
- The test set was used to find and fix faults and then run again, so 29 of 30 is not a
  held-out number.
- The GPU was slower on this phone: 40 s to load and about 50 s per answer.

The full history, and results from other phones, are in [benchmarks/](benchmarks/).

## Limits

- **English only.** Typed Hinglish and Benglish in Roman letters worked on the one
  sentence tried, and the date and time parsers read some Hindi and Bengali words, but
  no test set exists for either, so there is no accuracy figure.
- **Voice needs an English speech pack installed in Android.** Android installs packs
  itself; Actum cannot download one. One take ends at the first long pause; "say more"
  adds another.
- **OCR reads Latin script only.** Bengali and Devanagari screenshots are saved but their
  text is not read.
- **The models are sideloaded.** Neither is in the APK or downloaded by the app.
- **Only the first 1,200 characters** of a long screenshot or PDF are read for actions.
- **Only the diary is encrypted and locked.** The ledger, to-dos, saved items, calendar
  marks and the log are in the app's private storage, unencrypted.
- **A forgotten diary password cannot be reset.** Entries are sealed to a key that only
  the password unwraps. Actum keeps no copy of it.
- **The calendar tab shows only what was confirmed in Actum.** It does not read the
  phone's calendar, and removing a mark there removes neither the event nor the alarm.
- **Rules read only what they are sure of.** A sentence with a second clause, a negation
  or anything unexpected goes to Gemma and takes seconds, not milliseconds.
- **The log search is a substring match.** No read-aloud. No settings screen.

## What you need

- An Android phone on Android 10 or later, 64-bit ARM. Voice needs Android 12 or later.
- About **3.5 GB free** on the phone: 2.59 GB and 0.39 GB for the two models, about
  0.2 GB for the app.
- 8 GB of RAM is what it has been run on. Less is untested.
- A computer with JDK 21, the Android SDK (API 36.1) and `adb`, and about 3 GB free for
  the model files.

## Build and install

```sh
cd android
./gradlew installDebug
```

Download the two models to a `models/` folder (it is git-ignored) and check them:

```sh
mkdir -p models && cd models
curl -L -O https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm
curl -L -O https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm
sha256sum *.litertlm
```

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `gemma-4-E2B-it.litertlm` | 2,588,147,712 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| `embeddinggemma-2-text-vision-440m.litertlm` | 387,710,976 | `92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0` |

Open Actum once so its folder exists, then push them:

```sh
adb push models/gemma-4-E2B-it.litertlm /sdcard/Android/data/com.actum.app/files/
adb push models/embeddinggemma-2-text-vision-440m.litertlm /sdcard/Android/data/com.actum.app/files/
```

Actum checks the Gemma file's SHA-256 before loading it. Without the embedding model,
search works by words only. Without the Gemma model, direct commands still work and
everything else says the model is not on the phone.

Both models are Apache 2.0. Fonts are OFL (licences in `android/app/src/main/assets/licenses/`).

## Where things are

Everything is under `android/app/src/main/java/com/actum/`:

- `model/` — Gemma, the rules for direct commands, the JSON envelope and its repair, and
  the check that an answer is about what was said.
- `parse/`, `validate/` — dates, times, amounts, phone numbers, and the field checks.
- `act/` — the request flow and what confirming does.
- `voice/` — on-device speech to text.
- `data/` — lists, calendar marks, diary and its lock, the log.
- `capture/`, `ocr/`, `embed/`, `ui/` — sharing in, reading pictures, search, screens.

`AGENTS.md` has the detail for anyone changing the code. `tools/generate_icons.py`
rebuilds every icon from `tools/actum-icon.png`.
