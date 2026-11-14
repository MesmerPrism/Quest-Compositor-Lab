# Audio to neutral RGB controls

A pure Kotlin mapper consumes the exact public Synedelica `AudioSnapshot` value
without opening a microphone, owning audio resources or implementing a sink.
The [supplier pin](SYN-SUPPLIER.json) selects public revision
`ad6a5341f0fc185a89d81b1dd1b880b3eaa7a0c7`, the audio retirement-v2 cohort.
Only its immutable core value is compiled externally; no supplier source copy,
old private application, recovered palette or Original2023 data is included.

`AudioNeutralMapper.prepare(source, selected, nowNanos, target)` maps valid
amplitude to red source-sampling offset and RMS to blue, each multiplied by
the authored sample bound 0.01 eye-local UV. Green stays unchanged. `BlueOnly`
sets red to zero. This is direct scalar mapping with no invented spectrum,
image-driven displacement, private tuning, smoothing or temporal shader state.
The directions sample red to the right and blue to the left; apparent image
features move oppositely. Fresh silence yields zero offsets.

The embedding host supplies `AudioSourceKey` from its actually selected source,
or null when stopped/unselected. The mapper rejects an invalid source, malformed
levels, future capture, key mismatch or capture age over 250 ms (inclusive at
exactly 250 ms). Generation, sequence, original timestamp and BOOTTIME clock
survive unchanged. Same-key redraws are allowed while fresh. The host separately
admits monotonic new source selections and rejects sequence/timestamp regression;
the key is data, not resource authority or an authentication token.
`nowNanos` must use that same BOOTTIME domain (Android elapsedRealtimeNanos).
The JVM example uses authored synthetic times, not a hardware clock correlation.

`shaderDefines(frame, currentKey, nowNanos)` rechecks the original source and
current selection immediately before emitting the complete existing RGB macro
set. Delayed/stopped/changed sources emit bypass and zero strengths. A rejected
prepared frame cannot become active again. Source time is never refreshed by a
draw. This BOOTTIME path does not relabel the legacy optical envelope's
CLOCK_MONOTONIC timestamps.

The existing shader controls are compile-time defines, not native uniforms.
The [JVM example](../../examples/audio-reactive/README.md) and conformance runner
compile synthetic snapshots into unchanged shader bodies. They are an offline
consumer example, not a per-frame recompilation strategy or an audio-reactive
runtime upload path. The fixed blur kernel remains unchanged.

For real queued work, recheck the same original key/time at actual native use,
and run expiry/removal even if no new audio arrives.
Bare defines carry no source identity: retain the original frame metadata beside
any queued/compiled result so that final-use validation remains possible.
Host Stop/focus/pause and
physical renderer/resource retirement remain with the application's complete
audio-v2 cohort. Neutral parameter data or a successful callback does not ACK
physical GPU cleanup, microphone release or a Whole transaction. This module
does not construct any of those owners or tickets.

## Ordinary build and host conformance

Use JDK17, Gradle9.4.1 and the pinned Kotlin2.1.0/JUnit4.13.2 dependencies:

```text
gradle -p adapters/audio-neutral --project-cache-dir local/audio-gradle-cache -PsynedelicaSource=/path/to/Synedelica-Spatial test installDist
```

The build compiles only the pinned external `AudioSnapshot.kt`, mapper, tests
and synthetic consumer. Its checksum task fails for different supplier bytes.
Build/distribution data stays under ignored repository `local/`; Kotlin's small
module cache is also ignored and cannot pass public admission.

The explicit headless runner invokes that ordinary build offline, executes five
synthetic JVM cases, and compiles their emitted vertex/fragment stages with glslc:

```text
python adapters/audio-neutral/run_conformance.py --supplier /path/to/Synedelica-Spatial --gradle-home /path/to/gradle-9.4.1 --compiler /path/to/glslc
```

It uses hidden Windows helpers, explicit paths and the actual source files.
Receipts, logs, compiled classes/JARs and shader binaries stay ignored. The
result establishes JVM/conformance and compiler-stage compatibility only—no
graphics context, native shader adoption/upload, scanout, audio smoothness or
headset acceptance. Optical carrier jumps/tearing/visibility remain unresolved
and independent of this direct parameter example.
