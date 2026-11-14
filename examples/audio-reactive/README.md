# Synthetic audio-to-RGB consumer

`AudioNeutralExample` is a JVM-only consumer of the real pinned Synedelica
`AudioSnapshot` and Lab mapper. Its five authored cases are `fresh`, `blue`,
`expired`, `generation-change` and `stopped`. No microphone or device is opened;
stdout contains only the complete existing RGB shader defines.

Build instructions and exact scope are in the
[mapper module](../../adapters/audio-neutral/README.md). After `installDist`,
run its generated distribution with one case argument, or use the explicit
headless conformance runner to run all five and compile their shader stages.

The example first prepares a source at its original BOOTTIME timestamp, then
checks the current key and time again at emission. The expired case is delayed
one nanosecond beyond 250 ms. Changed generation and Stop emit bypass; the fresh
case maps amplitude/RMS to bounded red/blue offsets and `blue` keeps red neutral.
Synthetic timestamps and values are authored fixtures, not audio recordings or
hardware observations. Emitting or compiling a snapshot does not submit it to
a native renderer and does not refresh capture age.
