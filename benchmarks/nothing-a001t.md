# Nothing A001T

The reference phone: MediaTek Dimensity 7300 (MT6878), 8 GB RAM, Android 16.
Model: `gemma-4-E2B-it.litertlm` on the CPU unless a row says otherwise.

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
