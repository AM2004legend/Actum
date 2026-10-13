# Benchmarks

One file per phone, named for it (`nothing-a001t.md`), so results from different phones
can be set side by side. Add a new dated section to a phone's file each time it is
measured again; do not overwrite old numbers.

## How to measure

The phone must have Actum installed and both models pushed (see the README). Close other
apps. Note whether the phone is on charge and its battery temperature: a hot phone slows
its CPU and the numbers with it.

```sh
adb shell dumpsys battery | grep -E "temperature|level"     # temperature is in tenths of a degree

# 1. Five runs of one three-action sentence (about 2 minutes)
adb logcat -G 8M && adb logcat -c
adb shell am force-stop com.actum.app
adb shell "am start -n com.actum.app/com.actum.MainActivity --es measure cpu"
adb logcat -d -s ActumM1 | grep -E "loaded|warm-up|run [0-9]|median"

# 2. The 30-sentence accuracy set (about 5 minutes)
adb logcat -c
adb shell am force-stop com.actum.app
adb shell "am start -n com.actum.app/com.actum.MainActivity --es measure eval"
adb logcat -d -s ActumM1 | grep -E "eval [0-9]+ MISS|eval total"
```

Wait for the line that starts `CPU:` (first run) or `eval total:` (second) before reading
the log. Always `force-stop` first: a measurement only starts from a fresh launch.

Optional: `--es measure gpu` runs the first benchmark on the GPU. On the reference phone
it was several times slower; record it if you try it.

## What to record

Copy this block into the phone's file and fill it in. Leave a line as "not measured"
rather than guessing.

```markdown
## YYYY-MM-DD · commit <git rev-parse --short HEAD>

- Phone: <maker and model> · <chip> · <RAM> GB · Android <version>
- State: on charge / on battery · battery <NN.N> °C · other apps closed: yes / no
- Model: gemma-4-E2B-it.litertlm on CPU

| | Median | Worst | Correct |
| --- | --- | --- | --- |
| Three-action sentence, 5 runs | | | of 5 |
| 30-sentence set, per sentence | | | of 30 |

- Model load: 
- Warm-up (reading the instructions ahead): 
- Missed sentences, as logged: 
- Anything odd (crashes, refusals, heat): 
```

The phone's details come from:

```sh
adb shell getprop ro.product.manufacturer; adb shell getprop ro.product.model
adb shell getprop ro.soc.model; adb shell getprop ro.build.version.release
adb shell cat /proc/meminfo | head -1
```
