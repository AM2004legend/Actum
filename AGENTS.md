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
7. **If you are working for Dhrubaparna**, your brief is the next section.
8. **Tell your human** what worked, what did not, and anything untested. Do not report a
   step as done unless you saw it work.

## Workstream: a small model of our own (Dhrubaparna)

**Agent working for Dhrubaparna: this section is your brief.** Aritra (the owner) set it
on 14 October 2026. Read `CLAUDE.md` and the rest of this file first; the rules there
apply to everything here.

### Why

Gemma 4 E2B answers in 6–12 s on the reference phone and warms it up. Gemma 3 270M is
about a tenth of its size and should be several times faster and cooler, but used as it
comes it is not expected to handle Actum's job well (several actions in one sentence,
Hinglish and Benglish, exact JSON every time). The plan is to **fine-tune Gemma 3 270M to
do only Actum's job**, on Dhrubaparna's MacBook (M3), and test it on her Android phone,
and keep improving it there.

Aritra's machine is meanwhile making Actum able to run a model other than Gemma 4 (see
"Running a different model in the app" below). Until that lands on `main`, test models on
the Mac with the `litert-lm` command-line tool.

### How to work

- Work on a branch, `dhrubaparna/small-model`, and push it often. Do not push to `main`;
  Aritra merges.
- Do the parts **in order**, one at a time. Finish a part's check, record the result,
  commit, then start the next. If a part's check fails, stop and tell Dhrubaparna what
  failed and what you suggest; do not move on as if it passed.
- Never commit model weights or checkpoints (`.gitignore` already blocks `*.litertlm`,
  `*.task`, `*.safetensors`, `*.gguf`, `*.bin` and `training/out/`). Commit data, scripts,
  configs and results.
- Report every number with how it was measured. If something was not measured, say so.
- **Keep the 30 benchmark sentences out of training.** They are in
  `android/app/src/main/java/com/actum/ui/gate/TestSet.kt`. A model trained on them
  scores well on them and tells us nothing. Do not paraphrase them into the training set
  either.

### Part 1 — Actum on her phone, and the rules checked by hand

1. Follow "If you have just pulled this" above: build, install, push the two Gemma 4
   models, check the APK has no `INTERNET` permission.
2. Run both benchmarks (`benchmarks/README.md`) and write
   `benchmarks/<maker>-<model>.md` for her phone.
3. **Test the rules on the phone**, typed and spoken. For each line below, type it, tap
   **Do this**, and note whether the card said "a direct command, read without the model"
   and whether the card was right. Then say each of the first six into the microphone and
   note the same. Record all of it in her benchmark file under "Rules, by hand".
   - set an alarm for 6:30 am
   - wake me up tomorrow at 7 am
   - remind me in 15 minutes to call mom
   - buy eggs, bread and butter
   - paid Rahul 500 for lunch
   - save Priya's number 98300 12345
   - diary: a quiet day, finished the chapter
   - set an alarm for 7 (must go to Gemma: no am or pm)
   - buy milk and call mom (must go to Gemma: two things)
   - I forgot to buy milk (must go to Gemma: not a command)
4. Any wrong card from a rule is a bug: add the sentence to `RulesTest.kt` as a failing
   test, and tell Dhrubaparna. Do not loosen a rule to make one sentence pass without
   adding sentences it must still leave alone.

Check: her benchmark file exists with both benchmarks and the rules table filled in.

### Part 2 — the Mac, and the untrained 270M as a baseline

1. Record the Mac's chip, memory and macOS version in `training/MACHINE.md`.
2. Python 3.11 or 3.12 in a virtual environment under `training/.venv` (git-ignored).
3. Accept the Gemma licence on Hugging Face for `litert-community/gemma-3-270m-it` and
   `google/gemma-3-270m-it` (both are gated), and log in with `huggingface-cli login`.
4. `pip install litert-lm`, download `gemma3-270m-it-q8.litertlm` (304 MB) from
   `litert-community/gemma-3-270m-it`, and check it runs on the Mac.
5. Write `training/eval.py`: runs a `.litertlm` model over a JSONL file of
   `{"text": …, "want": [intents]}` lines, using the same instruction text as
   `GemmaRouter.kt`, and prints per-sentence results, accuracy (exactly the wanted
   intents) and time per sentence. Make `training/data/benchmark.jsonl` from the 30
   `TestSet.kt` sentences for **evaluation only**.
6. Run it on the untrained 270M. Record the score in `training/RUNS.md`.

Check: `training/RUNS.md` has the untrained model's score and timings, measured on the
Mac. (A low score is expected and fine. It is the baseline.)

### Part 3 — training data

1. Write `training/data/train.jsonl` and `training/data/valid.jsonl`. Each line is one
   example: `{"text": "<what the person typed or said>", "json": <the exact envelope
   Actum expects>}`. The envelope format is in `CLAUDE.md` under "The actions":
   `intent`, `fields`, `source_span`; no `confidence`. Dates and times are copied as
   written, never resolved.
2. Aim for at least 600 training and 100 validation examples to start. Cover every
   intent, one to three actions per sentence, `UNSUPPORTED` for requests Actum cannot do
   (booking, ordering, messaging, questions), and plain chat that should produce
   `UNSUPPORTED`. About a quarter Hinglish and Benglish in Roman letters.
3. Generating examples with a large model is fine, but every example must be checked:
   write `training/check_data.py`, which rejects any line whose JSON is not valid, whose
   intent is not in the closed list, whose `source_span` is not in `text`, or whose
   `text` is one of the benchmark sentences or close to one.
4. Commit the data and the checker.

Check: `check_data.py` passes on both files, and its summary (examples per intent and per
language) is in `training/RUNS.md`.

### Part 4 — fine-tune on the Mac

1. Fine-tune `google/gemma-3-270m-it` on `train.jsonl`. Either LoRA or a full fine-tune;
   at 270M parameters both fit an M3 with 16 GB, and LoRA fits 8 GB. Use PyTorch with
   the `mps` device, or MLX (`mlx-lm`). Check the current docs for whichever you choose
   rather than relying on memory.
2. Train the model to answer with the envelope directly. Use a **short** instruction
   (one line) instead of `GemmaRouter`'s long one: a fine-tuned model does not need the
   rules spelled out, and every token of instruction is time on the phone. Record the
   exact instruction you trained with; the app will need it.
3. Keep `valid.jsonl` out of training, and stop when its loss stops improving.
4. Merge the LoRA weights if you used LoRA, and save under `training/out/` (git-ignored).
5. Evaluate the trained model on the Mac with `eval.py`, on `valid.jsonl` and on
   `benchmark.jsonl`. Record both in `training/RUNS.md`, with the training settings,
   time taken and peak memory.

Check: a trained model whose `benchmark.jsonl` score is recorded next to the baseline.

### Part 5 — convert for the phone

1. Convert the trained model to `.litertlm`, quantized to 8 bits like the published one.
   The tool is `litert-torch` (formerly `ai-edge-torch`); check its current docs for
   Gemma 3 and the `.litertlm` output. Its Python packages publish macOS ARM builds, but
   whether the whole conversion runs on macOS has not been checked: try it. If it fails
   on the Mac, do only this step in a free Google Colab notebook and download the file.
2. Run `eval.py` on the converted file. Its score should be close to the unconverted
   model's; if it is far off, the conversion is wrong.
3. Record the file's size and SHA-256 in `training/RUNS.md`.

Check: a converted `.litertlm` that scores within a sentence or two of the unconverted
model on the Mac.

### Part 6 — on the phone

Needs the app change described below to be on `main`. Pull it, then push the converted
model to the phone, choose it in the app, and run both benchmarks. Record them in her
phone's benchmark file under the model's name, next to Gemma 4's.

### Then: keep improving

Each round: look at what the phone got wrong, add training examples for those kinds of
sentences (never the benchmark sentences themselves), train, convert, measure, record.
One row per round in `training/RUNS.md`. Stop and tell Dhrubaparna and Aritra when the
small model reaches 27 of 30 on the benchmark, or after three rounds without improvement.

### Does this need cloud training?

Not for 270M. A 270M-parameter model fits an M3 comfortably, and Google's own guide
fine-tunes it in minutes on a free Colab T4 GPU; on an M3 expect minutes to an hour per
run, depending on how much data. Cloud is worth it only if: the conversion step will not run
on macOS (use Colab for that step alone), the Mac has 8 GB and a full fine-tune is
wanted, or the plan later moves up to the 1B model.

### Running a different model in the app

Being built on Aritra's machine, not yet on `main`. When it lands, this section will say
how to put a model file on the phone and choose it. Until then, Part 6 waits.

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
- **Opening Actum starts Gemma.** It loads the model and reads its instructions in the
  background as soon as the home screen opens, and reads requests ahead while you type.
  Expect some CPU use and warmth while the app is open; nothing of this happens when
  sharing into Actum.
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
| `model/GemmaRouter` | The LiteRT-LM engine, the prompt, one retry. Keeps a conversation ready with the instructions already read in (`warmUp`); `cancel` stops a reading part way. |
| `model/Rules` | Direct commands read without the model. Whole-sentence patterns only. |
| `model/Envelope` | The JSON envelope, the intent enum, and the repair of malformed replies. |
| `model/Grounding` | Drops model actions that are not about what was said. |
| `parse/` | `DateParser`, `TimeParser`, `AmountParser`, `PhoneParser`. Each returns `Sure`, `Unsure` (candidates and one question) or `None`. |
| `validate/Validator` | Turns a proposal into checked fields (`VERIFIED / UNSURE / MISSING`) and a verdict (`PASS / UNCERTAIN / FAIL`). |
| `act/Actor` | A request from words to cards to confirmation. Lives in the Application, so it survives rotation. Loads Gemma when the app opens (`warmUp`) and reads typed or spoken words before the tap (`readAhead`), stopping that reading when the words change. |
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
