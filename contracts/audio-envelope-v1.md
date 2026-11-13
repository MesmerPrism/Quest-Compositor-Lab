# Pinned generic audio contract

This document describes the existing Quest six-file API, not a new codec.
`AudioEnvelope` has four finite levels in [0,1], two phases in [0,1),
nonnegative increasing source sequence and positive capture-end nanoseconds.
Its domain is Android CLOCK_MONOTONIC (`System.nanoTime`). The direct LUT ABI's
BOOTTIME domain is distinct; converting between them requires an explicit
clock-boundary adapter. No retimestamping old source as fresh is allowed.

`ExternalEnvelopeAdapter` takes a clock supplier, finite 1..900000 ms duration
and byte run nonce. Both source sequence and timestamp strictly increase.
Source and delivery age each expire after 250 ms; future source is rejected.
Stop clears the latest value permanently. Another run needs a new adapter.
`ProducerObservation` captures published state before taking observation time.

`EnvelopePacketView` belongs on the host UI thread and host-owned window.
Its 168x120 Canvas contains a 14x10 grid of 12-pixel cells; the interior 12x8
cells encode 96 bits row-major, MSB first, red one/blue zero. The 12 bytes are:
magic B6 5A; version/flags 11 when valid or 10 when invalid; modulo-256 draw
sequence; four level bytes; two phase bytes; run nonce; CRC-8. CRC polynomial
07, init 00, non-reflected, xorout 00 covers the first 11 bytes. Levels round
clamped values times 255; phase wraps and floors times 256. Invalid packets
zero all six controls while retaining sequence/nonce and valid CRC.

The packet does not carry a source timestamp or source sequence. Its draw
sequence, CRC and nonce cannot establish fresh capture, UI delivery or scanout.
Strict source freshness lives in the supplier's host adapter. A stalled UI
cannot promise a final neutral draw; the host must retire its own window.
No microphone, IPC, permissions or optical decoding authority is conferred.
