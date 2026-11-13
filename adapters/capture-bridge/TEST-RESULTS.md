# Source-only verification results

Date: 2026-11-13. Toolchain used: Eclipse Temurin OpenJDK javac/java 17.0.19,
Java source/bytecode release 8, `-Xlint:all -Werror`. Android contract compilation
used SDK API 34, platform revision 3, android.jar SHA-256
`6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad`
(26,361,808 bytes). No jar or tool binary is exported.

## Observed test-first cycles

Each row ran against the real production class under test. API scaffolds first
compiled but omitted the behavior named by the test; the recorded RED was an
assertion failure, not a missing dependency/class or compiler error. Scaffolds
are absent from this delivery. Tests were retained and the checks implemented.

| Cycle | Actual RED, exit 1 | Actual GREEN, exit 0 |
| --- | --- | --- |
| Shell/DUMP/synchronous gate | `wrong UID: accepted forbidden operation` | PublisherAccessTest passed all caller/permission/reply cases |
| Immutable descriptor | `zero generation: accepted forbidden operation` | StereoSinkDescriptorTest passed literal ABI, invalid versions/words, format/usage, bounds, eye layout, orientation and immutability cases |
| Lifecycle admission | `new actual reader must be active` | SinkAdmissionTest passed current/stale descriptor, callback/image/fence, destroyed-reader and retire/restart cases |
| Closed app-side status | `wire status must expose held image/fence and no vendor ACK` | Held status, malformed status, false drain and forged vendor ACK cases passed |
| Old publisher generation | `old publisher cannot adopt new generation: accepted forbidden operation` | Old-generation and permanently closed publisher requests rejected |
| Publication race | `earlier image admission cannot publish after invalidation: accepted forbidden operation` | Current-image revalidation rejected after invalidation |
| Impossible status | `wire GPU hold without retained image: accepted forbidden operation` | Orphan GPU hold status rejected |

The access, descriptor and lifecycle suites were then run together successfully.
All three Android classes plus all five pure production classes compiled against
the real pinned SDK jar with no warnings/errors. The inert identity example
also compiled and ran, reporting reader absent and capture not started.

Reproduction from the adapter directory:

```text
python run_tests.py --suite all --require-android --android-jar /path/to/pinned/android.jar
```

Expected successful output:

```text
PASS PublisherAccessTest: shell identity, DUMP, synchronous reply
PASS StereoSinkDescriptorTest: closed layout, exact format/usage, bounds, immutable ABI
PASS SinkAdmissionTest: exact generations, descriptor guards, late callbacks, image/fence retirement
PASS Android API compilation (no Android runtime execution)
PASS selected host contract tests
```

## Evidence boundary

Pure tests run actual validation/reducer classes without Android mocks. API
compilation does not execute Binder caller identity, DUMP/manifest enforcement,
Parcel/Surface transfer, Binder death delivery or Service callbacks on Android.
Those device/runtime cases remain open, as do reader destruction, real held
AImage/import/fence observations, effective output exclusion, live capture,
native presentation, scanout, moving use and sustained sessions. The reducer
only enforces the observations supplied by its actual app resource owner.

No capture RPC/controller, vendor AIDL, rendering queue, native effect code,
peer-protocol changes, app install, headset interaction or remote publication
was performed by this source-only implementation. The staged candidate has no
independent Git repository/history. Its reviewed delivery manifest binds the
portable file hashes; only the destination integrator can adopt them.
