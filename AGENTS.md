# AGENTS.md — working on Actum

For any coding agent or teammate picking this repo up cold, on their own machine and their
own phone. `CLAUDE.md` has the rules and what the product is; read it first. This file is
how to get Actum running, what you must test and record, and how the code is laid out.
It describes the project as of 13 October 2026.

## If you have just pulled this: do these, in order

1. **Check the phone and machine against "What you need"** below.
2. **Build and install** (`cd android && ./gradlew installDebug`).
3. **Download both models, verify their hashes, push them to the phone.** Commands are in
   `README.md` under "Build and install". Open Actum once before pushing.
4. **Check the APK has no `INTERNET` permission** (command under "Checks").
5. **Try it by hand** with the list under "Manual checks on a new phone".
6. **Run both benchmarks and record them** in `benchmarks/<your-phone>.md`, following
   `benchmarks/README.md`. This is required, not optional: the owner compares phones, and
   one phone's numbers say nothing about another's. Commit that file.
7. **Tell your human** what worked, what did not, and anything untested. Do not report a
   step as done unless you saw it work.

## What you need

| | |
| --- | --- |
| Phone | Android 10 or later, 64-bit ARM (`adb shell getprop ro.product.cpu.abi` says `arm64-v8a`), USB debugging on. |
| Free space on the phone | About **3.5 GB**: 2.59 GB (Gemma) + 0.39 GB (embedding model) + about 0.2 GB for the app, plus working room. |
| RAM | Run so far only on 8 GB. On less, try it and record what happens; do not assume. |
| Voice | Android 12 or later, with an English on-device speech pack installed in Android's settings. Without one the voice sheet says so and does not listen; everything else works. |
| Computer | JDK 21, Android SDK with API 36.1, `adb` on the path, about 3 GB free for the models. Gradle 9.4.1, AGP 9.2.1 and Kotlin 2.4.21 come through the wrapper. |

Things that trip people up on a new machine or phone:

- **Other apps on the phone do not matter.** Actum's app id is `com.actum.app`. Android
  keeps every app id apart: its own data, its own storage folder, its own keys. A phone
  that has any other app installed, including an earlier project of this team's such as
  Breadcrumb, is unaffected and does not affect Actum. Both will simply appear in the
  share sheet. Nothing needs uninstalling.
- **A debug build from another computer will not install over yours.** Each machine signs
  debug builds with its own key. If `installDebug` fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an Actum signed elsewhere is already on the phone.
  Uninstalling it (`adb uninstall com.actum.app`) fixes that, but **deletes that
  install's data and its pushed models**, so ask the phone's owner first.
- **Uninstalling or clearing the app deletes the models.** They live in the app's own
  folder on shared storage and go with it. Push them again afterwards.
- **`android/local.properties` is yours.** It holds your SDK path and is not committed.
  Android Studio writes it; otherwise create it with `sdk.dir=<path to your SDK>`.
- **On Windows with Git Bash**, prefix `adb` commands that take phone paths with
  `MSYS_NO_PATHCONV=1`, or the path is rewritten into a Windows one.
- **If `adb push` to `/sdcard/Android/data/com.actum.app/files/` is refused**, open Actum
  once and try again. Actum also looks in its private `files/models/` folder; copying a
  model there through `adb shell run-as com.actum.app` is a fallback that has not been
  tried.
- **VS Code's Java extensions must not import this project.** Red Hat's Java extension
  (and the Gradle extension in the Java pack) import any Gradle project they find, using
  their own bundled Java. That Java never matches the command line's, so every import
  starts a new 2 GB Gradle daemon; when imports restart, they pile up. On a 16 GB machine
  this once ran out of memory within six minutes and left crash reports
  (`hs_err_pid*.log`) in the repo folder. Nothing in VS Code is needed to build Actum. If
  you open the repo in VS Code with those extensions installed, put this in
  `.vscode/settings.json` (the folder is git-ignored, so it is not shared) and reload the
  window:

  ```json
  {
    "java.import.gradle.enabled": false,
    "java.import.maven.enabled": false,
    "java.autobuild.enabled": false,
    "gradle.autoDetect": "off"
  }
  ```

  Or disable those extensions for this workspace. Either way, check with Task Manager that
  no stray `java.exe` processes are left, or run `android/gradlew --stop`.
- **The first request after install is slow.** Actum hashes the 2.59 GB Gemma file once
  (tens of seconds) before trusting it, then remembers.
- **The microphone prompt** appears the first time the mic is tapped.

## Manual checks on a new phone

Type each in the home screen's field and tap **Do this**, unless it says otherwise.

| Try | Expect |
| --- | --- |
| `set an alarm for 8 more minutes` | Instantly, an Alarm card with a clock time 8–9 minutes ahead, marked "a direct command, read without the model". Confirming opens the clock app. |
| `buy eggs, bread and butter` | Instantly, a To-do card with three items. Confirm, then find them under to-dos. |
| `dentist appointment on 20 October at 11am` | After some seconds (Gemma), a Calendar event card. Confirming opens the calendar app, and the 20th is marked in Actum's calendar tab. |
| `paid Rahul 500 for lunch, and book me a cab` | An Expense card and a "can't do this yet" card. |
| `what is the capital of Australia` | A refusal. No card proposing anything. |
| Share a screenshot with English text into Actum, then search for something in it | It is found. Open it and tap **Find things to do in this**. |
| Tap the mic, say "remind me to call the bank tomorrow at 10" | The words appear; **Do this** gives a card. |
| Tap the mic, say a few sentences, tap **Save to diary** | "In your diary". Set a diary password in the Diary tab, lock it, unlock it. |
| Tap **privacy** | "Actum cannot reach the internet", and a permission list with no `INTERNET`. |
| Tap the sun / moon | The whole app switches between dark and beige. |

Before driving a phone over `adb`, check which app is in front before every
`input tap`. The phone's owner may be using it; a blind tap once landed in another app.
`uiautomator dump` gives on-screen text and bounds, and still works on the Diary tab,
where screenshots come out black on purpose.

## Checks

```sh
# INTERNET must not appear
"$ANDROID_HOME"/build-tools/*/aapt2 dump permissions android/app/build/outputs/apk/debug/app-debug.apk

# unit tests
cd android && ./gradlew :app:testDebugUnitTest

# what the app is doing
adb logcat -s ActumAct      # every request: "rule: …" or the time taken and the model's raw answer
adb logcat -s ActumVoice    # speech packs found, recognizer errors
adb logcat -s ActumM1       # benchmark reports
```

Benchmarks: see `benchmarks/README.md`. Two things to remember: always `force-stop` the
app first (a measurement only starts from a fresh launch), and record the battery
temperature.

## Layout

One Gradle module, `android/app`. All code is under
`android/app/src/main/java/com/actum/`.

| Path | What it is |
| --- | --- |
| `model/GemmaModel` | Finds the sideloaded Gemma file and checks its SHA-256. The file name, size and hash are constants here; a different model means changing all three. |
| `model/GemmaRouter` | The LiteRT-LM engine, the prompt, one retry. Keeps a conversation ready with the instructions already read in (`warmUp`). |
| `model/Rules` | Direct commands read without the model. Whole-sentence patterns only. |
| `model/Envelope` | The JSON envelope, the intent enum, and the repair of malformed replies. |
| `model/Grounding` | Drops model actions that are not about what was said. |
| `parse/` | `DateParser`, `TimeParser`, `AmountParser`, `PhoneParser`. Each returns `Sure`, `Unsure` (candidates and one question) or `None`. |
| `validate/Validator` | Turns a proposal into checked fields (`VERIFIED / UNSURE / MISSING`) and a verdict (`PASS / UNCERTAIN / FAIL`). |
| `act/Actor` | A request from words to cards to confirmation. Lives in the Application, so it survives rotation. |
| `act/Executor` | What confirming does: intents and database writes, the log record, the calendar mark. |
| `voice/Listener` | Android's on-device speech recognizer only. Picks an installed English pack, keeps one recognizer per sheet, ignores reports from one it has let go. |
| `data/` | Room entities and `ListsDao` (to-dos, expenses, diary, log, calendar marks); `DiaryVault` (password lock) and `Diary` (keystore layer); the saved-item library (`Memory`, FTS). |
| `capture/`, `ocr/`, `embed/` | Share sheet and tile; ML Kit text recognition; EmbeddingGemma vectors and on-phone search. |
| `ui/act/` | `ActSheet` (cards), `VoiceSheet`, `ListsSheet` and `CalendarTab`, `ProofSheet` (privacy), `IntentLook` (action colours and icons). |
| `ui/home/`, `ui/theme/`, `ui/common/` | Home screen, search, detail sheet; the two looks (`Look`, `Color`); the mark and glyphs. |
| `ui/gate/` | `ModelGate`, `TestSet`: the benchmarks. Opened by an `adb` extra, or a long press on the wordmark in a debug build. |

Good to know:

- **One Room database**, `ActumDatabase`, version 5, with auto-migrations and exported
  schemas in `android/app/schemas/`. A schema change means a version bump, an
  `AutoMigration`, and committing the new schema JSON. Never a destructive migration.
- **Colours are getters.** Every name in `ui/theme/Color.kt` and `IntentLook.kt` answers
  for the current look. Draw with those names, never a literal colour, and do not cache
  one in a top-level `val`.
- **Icons come from one picture.** `python tools/generate_icons.py` rebuilds every
  launcher, splash and tile icon and the in-app mark from `tools/actum-icon.png`.
- **The diary lock** uses a key pair so that writing needs no password and reading does.
  Entries are sealed to the public key; the private key is stored encrypted under a key
  stretched from the password (PBKDF2-HMAC-SHA256, 310,000 rounds), then wrapped again by
  an Android Keystore key. There is no reset. Details are in `DiaryVault`'s own comments.
- **Some code left from an earlier, server-based design is unused**: bookmark import,
  sync columns on saved items, a few types in `net/`. It makes no network call.

## Changing how requests are read

This is where most of the risk is.

- **Rules** (`model/Rules.kt`, `RulesTest.kt`). A rule must be right or silent. For every
  new pattern, add sentences to both lists in the test: what it must read, and what it
  must leave to the model. The test also runs every benchmark sentence through the rules
  and fails if one is misread.
- **The prompt and the answer format** (`GemmaRouter`). Latency is mostly token count: a
  longer prompt or answer costs seconds. Small changes also change how often the model
  malforms its JSON. Removing one field once dropped accuracy from 29 to 25 of 30 until
  the repair step was taught the new slip, and a reworded retry once made the model
  return its own example as an answer. So: after any change here, run both benchmarks
  and read the missed sentences, not just the score.
- **Repair and grounding** (`Envelope.kt`, `Grounding.kt`). Each has a test file; a slip
  seen on a phone goes in as a test first.

## Known gaps

- English only. No Hinglish or Benglish test set exists, so no accuracy figure for them.
- Real speech has been tested by the owner on one phone, not by an agent.
- OCR is Latin script only.
- Only the diary is encrypted. The rest is plain in private storage.
- The log filter is SQL `LIKE`. A card's optional fields can be edited but not added.
- No read-aloud, no settings screen, no language choice.
- The benchmark set is not held out: it has been used to find faults.
- A measurement does not start if the app is already open; it needs `force-stop` first.
- WorkManager adds `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` and `FOREGROUND_SERVICE`; it is
  used only for the embedding pass.

## Conventions

- Match the surrounding code: comments that say why, no abbreviations, Compose text
  styles through `serif()`, `sans()`, `monoStyle()`.
- Tap targets at least 48 dp.
- Commit only when your human asks.
- When scope or behaviour changes, update `README.md`, `CLAUDE.md` and this file
  together, and never describe something that is not built and checked.
