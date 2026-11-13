# Quest Compositor Lab

Neutral packed-stereo shader examples and host-only selection tools. Identity,
separable RGBA blur and independently authored RGB offsets form a small source
laboratory. The repository is independent of any umbrella planning workspace.

This is an experimental source/host milestone. It is not a supported Meta API,
an APK, a native-loader adoption result or headset/wearer qualification. The
whole-composite input includes application/UI content. See
[compatibility](docs/COMPATIBILITY.md) before interpreting results.

The experimental [capture sink adapter](adapters/capture-bridge/README.md)
defines an app-owned stereo Surface descriptor, a Shell-only Binder boundary
and app-side lifecycle accounting. It does not start capture or supply a native
reader, renderer or global restart authority. Its
[inert example](examples/wide-stereo-source/README.md) creates no media resource.
Java 17 can run its three pure suites and optional pinned Android SDK compilation:

```text
python adapters/capture-bridge/run_tests.py --suite all
```

Android compilation additionally requires an explicitly selected SDK jar as
documented by the adapter. It does not execute Android runtime behavior.

The [finite controller source](adapters/capture-bridge/controller/README.md)
adds typed orchestration for one original invocation and conservative unknown
custody. Its real firmware vendor and durable external-custody providers are
absent, so the public Android entry fails closed. It is not a working capture
distribution or device execution recipe. Its own tests compile the pinned sink
source as a dependency without copying it or rerunning unchanged sink suites.

Python 3.12 or newer runs the core tooling without third-party packages.
The full test suite includes numerical and GPU cases and needs NumPy,
PyOpenGL and a suitable Windows GL driver. Tested versions are pinned
in `requirements-host.txt`:

```text
python -m unittest discover -s tests -v
python tools/check_public_boundary.py
python tools/compositor_profiles.py list --catalog profiles/catalog.json
python tools/compositor_profiles.py plan --catalog profiles/catalog.json --profile identity --output local/identity-plan.json
python tools/compositor_profiles.py verify --plan local/identity-plan.json
```

Plan outputs are immutable: use a fresh output name for another run. Tools
describe requested property values without executing device commands. Compile
fixtures require an explicitly configured host compiler; see
[examples](examples/README.md). Generated files belong under ignored `local/`.
The [typed API and stage mapping](docs/PROFILE_API.md) define consumer use.

Source admission is defined by [provenance](docs/PROVENANCE.json),
[notices](THIRD_PARTY.md) and the public closure checker. Private effects,
vendor binaries, raw captures and machine-specific receipts are excluded.
The prior starter bundle was not copied because it lacked a standalone license
grant. The new examples and fixtures were independently written.

Generic audio APIs retain one supplier in Quest. This repository records its
[pins and ownership](docs/OWNER_TRANSFER.md) without vendoring another mutable
copy or starting a microphone. Direct in-app audio/LUT consumption is independent
of the [optional optical carrier experiment](docs/AUDIO_CARRIER_EXPERIMENT.md).

Current source milestone, evidence limits and next work live in the single
[continuation](.planning/CONTINUATION.md).
