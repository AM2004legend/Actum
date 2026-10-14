# Nothing A001T

The reference phone: MediaTek Dimensity 7300 (MT6878), 8 GB RAM, Android 16.
Model: `gemma-4-E2B-it.litertlm` on the CPU unless a row says otherwise.

## 2026-10-14 · uncommitted work on top of 06f7317

Phone on USB power throughout. Battery 28 °C at the start, 32–39 °C later in the day.

### Shorter answers (Gemma 4 E2B)

The model stopped writing `source_span`; Kotlin finds it (`Spans`). Same day, same phone,
back to back.

| Answer format | Three-action sentence, 5 runs: median / worst / correct | 30-sentence set: median / worst / correct | Battery |
| --- | --- | --- | --- |
| With `source_span` (as on 13 October) | 11.8 s / 12.8 s / 5 of 5 | 6.7 s / 13.7 s / 29 of 30 | 32–36 °C |
| Without, before the repair below | 9.3 s / 11.1 s / 5 of 5 | 4.9 s / 10.6 s / 28 of 30 | 38 °C |
| **Without, with stitching** (current) | **8.5 s / 10.4 s / 5 of 5** | **4.1 s / 10.6 s / 30 of 30** | 39 °C |

- Without the span, the model twice wrote an action's last fields as an object of its own
  (`{"fields":{"key_dates":…}}`, `{"intent":"list_name",…}`), which read as a second,
  unsupported action. The repair now stitches those back onto the action before.
- "what is the capital of Australia" now comes back UNSUPPORTED instead of an empty list.
- Still not a held-out score: the set was used to find these faults.
- Cards are now shown as each action is written. In one typed try of the three-action
  sentence, read ahead and tapped after it had begun, the cards came 0 s, 2.5 s and 7.8 s
  after the tap. The benchmarks do not measure this.

### Other models, untrained, with the 13 October prompt

| Model | File | Three-action: median / worst / correct | 30-sentence set: median / worst / correct |
| --- | --- | --- | --- |
| Gemma 3 1B, instructions as a system turn | `gemma3-1b-it-int4.litertlm` (584 MB) | 11.4 s / 12.6 s / 0 of 5 | 12.3 s / 24.1 s / 12 of 30 |
| Gemma 3 1B, instructions as opening turns | same | 6.6 s / 7.2 s / 0 of 5 | 7.0 s / 10.9 s / 14 of 30 |
| Gemma 3 270M, opening turns | `gemma3-270m-it-q8.litertlm` (304 MB) | not measured | 4.3 s / 14.8 s / 5 of 30 |

- Load: 1B 0.9–4.4 s, 270M 2.2 s. Warm-up (instructions read in) for the 1B with opening
  turns: 5.1–7.0 s; with a system turn, 3 ms, because Gemma 3's template cannot read a
  system instruction until the first message arrives.
- The 1B mistook alarms for UNSUPPORTED or EXPLAIN, lending and owing for contacts with
  the amount as the phone number, and invented phone numbers (212-555-1212) for bookings.
- The 270M mostly wrapped its answer in a code fence, invented intents ("BUY"), or handed
  back the prompt's examples; at least one of its five "right" answers was the example.
- Neither is usable untrained. Fine-tuning the 270M is Dhrubaparna's workstream.

## 2026-10-13 · uncommitted work on top of 0d7525f

All runs with the phone on charge. Four states of the app were measured the same day;
the last is the current one.

| App state | Three-action sentence, 5 runs: median / worst / correct | 30-sentence set: median / worst / correct | Battery |
| --- | --- | --- | --- |
| First working version, long prompt, pretty-printed answers | 31.3 s / 34.1 s / 5 of 5 | not measured | not recorded |
| Short prompt, one-line answers | 16.6 s / 20.9 s / 5 of 5 | 11.2 s / 20.4 s / 29 of 30 | not recorded |
| Same, after two examples were added to the prompt | 19.5 s / 22.4 s / 5 of 5 | not measured | 40.0 °C |
| **Current:** instructions read ahead, no confidence field, bracket repair, grounding check | **12.0 s / 13.3 s / 5 of 5** | **6.0 s / 13.4 s / 29 of 30** | 39–42 °C |

- Model load: 1.7–2.6 s warm, 5.5 s the first time after install.
- Warm-up (reading the instructions ahead): 3.9–5.6 s, not counted in the current row.
- GPU: 40.2 s to load, median 49.9 s, worst 50.8 s, 5 of 5. Not used.
- Missed sentence in the current row: "what is the capital of Australia" returns an
  empty list (shown as a refusal) in 1.7 s.
- Two intermediate runs are worth knowing about. Removing the confidence field alone
  dropped the set to 25 of 30, because the model closed a bracket early more often; the
  repair step now mends that. A reworded retry message then made the model hand back the
  example from its own instructions as an answer (28 of 30, with two answers about a
  meeting nobody mentioned); the grounding check now drops such answers.
- Direct-command rules read 9 of the 30 set sentences, all correctly, in well under
  0.1 s. That is from the unit tests, not timed on the phone.
- The set was used to find these faults and then run again, so its score is not a
  held-out number.
