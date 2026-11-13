# Finite neutral capture controller candidate

This source-only module implements one original invocation, one attempted Start
and at most one authorized global asynchronous Stop through typed ports. It
preserves original RPC/worker/monitor/Surface custody when outcomes are unknown.
The real vendor provider and durable external custody recorder are deliberately
absent. The Android entry fails closed before capture without a build-selected,
separately reviewed provider. It offers no runtime class, command or code-path
selector and is not a working vendor capture distribution.

The public sink is an exact external source dependency, not copied code. Its
selected source is recorded in SINK-DEPENDENCY.json. No sink tests are rerun by
this module. The local 19-word descriptor describes reader storage/admission;
it makes no projection, capture-pose, timestamp-clock or colour assertion.

```text
python adapters/capture-bridge/controller/run_tests.py --sink-root /selected/public/checkout --android-jar /selected/android-34/android.jar --output-root local/controller-checks
```

This runs only the new deterministic Java host suites, four offline Python tests
and short Java/API compilation. The SDK jar is never executed or redistributed.
Outputs stay outside the portable closure. No device, ADB, network, APK build,
capture service, native queue or renderer operation is performed.

Read [CONTRACT.md](CONTRACT.md), [INTEGRATION.md](INTEGRATION.md) and
[TEST-RESULTS.md](TEST-RESULTS.md). The controller grants no Ready, global capture
idle, vendor retirement ACK, app reader drain, restart, held-output review or
presentation authority. Accepted Stop and its explicit compatibility-reviewed
grace can satisfy a helper-local wrapper policy only. The host still needs its
original EOF/reap/PID-birth/pipe/handle custody and actual app/global cleanup.

New code is authored informed by retained private implementation patterns. It
is not claimed to be clean-room or copied under an invented private-source
license. No retained private source text, profile values/hashes, effect modes,
vendor binaries/AIDL, private product identifiers, device evidence or machine
paths are imported. New authored files are AGPL-3.0-or-later under the existing
[adapter LICENSE](../LICENSE); keep dependency/provenance notices on adoption.
