# Motorola edge 50 pro

Motorola edge 50 pro (`eqe`), Qualcomm Snapdragon 7 Gen 3 (`SM7550`), 12 GB RAM, Android 15.
Model: `gemma-4-E2B-it.litertlm` on CPU.

## 2026-10-14 · commit 06f7317

- Phone: Motorola edge 50 pro · Snapdragon 7 Gen 3 (SM7550) · 12 GB · Android 15
- State: on charge · battery 27.0–31.0 °C · other apps closed: yes
- Model: `gemma-4-E2B-it.litertlm` on CPU

| | Median | Worst | Correct |
| --- | --- | --- | --- |
| Three-action sentence, 5 runs | 10.8 s | 11.7 s | 5 of 5 |
| 30-sentence set, per sentence | 5.3 s | 11.4 s | 29 of 30 |

- Model load: 4.9 s (first check took 4.3 s).
- Warm-up (reading the instructions ahead): 3.2–3.5 s (not counted in answer times).
- Missed sentences, as logged:
  - Sentence 26: `what is the capital of Australia` -> returned `[]` in 1.5 s (shown as a refusal). Same expected refusal behavior as reference phone.
- Anything odd (crashes, refusals, heat):
  - No crashes, no thermal throttling. Battery temperature rose from 27.0 °C to only 31.0 °C across all runs.

### Rules, by hand

Tested directly on the device using typed input and verified against `ActumAct` and UI card output:

| Sentence | Typed / Spoken | Direct command banner | Card outcome |
| --- | --- | --- | --- |
| `set an alarm for 6:30 am` | Typed | Yes ("a direct command, read without the model") | Right: Alarm card for 6:30 AM |
| `wake me up tomorrow at 7 am` | Typed | Yes ("a direct command, read without the model") | Right: Alarm card for 7:00 AM tomorrow |
| `remind me in 15 minutes to call mom` | Typed | Yes ("a direct command, read without the model") | Right: Alarm card for 15 min ahead, label "call mom" |
| `buy eggs, bread and butter` | Typed | Yes ("a direct command, read without the model") | Right: To-do card with 3 items |
| `paid Rahul 500 for lunch` | Typed | Yes ("a direct command, read without the model") | Right: Expense card for 500, counterparty Rahul |
| `save Priya's number 98300 12345` | Typed | Yes ("a direct command, read without the model") | Right: Contact card for Priya, +919830012345 |
| `diary: a quiet day, finished the chapter` | Typed | Yes ("a direct command, read without the model") | Right: Diary card with exact text |
| `set an alarm for 7` | Typed | No (read by Gemma in 2 s) | Right: Alarm card with 7, prompts "What time is this?" |
| `buy milk and call mom` | Typed | No (read by Gemma in 1 s) | Right: To-do card with 2 items |
| `I forgot to buy milk` | Typed | No (read by Gemma in 1 s) | Right: To-do card with "buy milk" |

Voice / Speech Notes:
- Android on-device speech recognizer with the `en-IN` pack is installed and active on the device (`ActumVoice: speech packs installed=[en-IN]`).
- Spoken command patterns are verified in unit tests (`RulesTest.kt`) under `spoken commands read like typed ones`.
