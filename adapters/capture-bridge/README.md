# Neutral app-owned stereo sink contract

Source-only WP07 Task 1 candidate. This adapter publishes an already-owned app
Surface through a narrow Shell-only Binder contract and tracks app-side frame
admission. It does not start capture, create a reader, choose a vendor service,
load a native library, render, own a queue, or grant Shell privilege.

`src/main/java` contains dependency-free Java 8 descriptor, access, publisher
binding, status and immutable lifecycle reducers. `src/android/java` adds a
Binder publisher, a serialized app owner and an abstract Service. The host app
supplies a concrete Service subclass and the actual reader/Surface factory.

The local sink ABI is independent of the pending consuming application peer-source profile.
It makes no projection, capture-pose, timestamp-clock, colour-dataspace or
authenticated-peer media assertion. A matching size is not a calibrated FOV.

Run the real pure tests, and optionally compile the Android classes against an
explicit installed SDK jar:

```text
python run_tests.py --suite all
python run_tests.py --suite all --require-android --android-jar /path/to/pinned/android.jar
```

The runner invokes only javac/java, creates unique disposable directories below
`local/host-checks/`, and never contacts a device, listener, capture service or network.
Android compilation is API typing evidence; the SDK stub jar cannot execute
Binder, Surface or Service behavior on the host. No Android runtime execution,
native reader integration, exclusion, visual presentation, capture compatibility,
restart admission or sustained/moving use is qualified here.

See [CONTRACT.md](CONTRACT.md) for ownership and wire rules,
[TEST-RESULTS.md](TEST-RESULTS.md) for actual red/green evidence and limitations,
and [PROVENANCE.json](PROVENANCE.json) for references and per-file attribution.
The inert identity example lives at `examples/wide-stereo-source` in the
destination repository. Every source file is AGPL-3.0-or-later; retain LICENSE
and the references when integrating. No private modes, formulas, shaders,
witness data, vendor binaries or raw headset evidence are supplied.
