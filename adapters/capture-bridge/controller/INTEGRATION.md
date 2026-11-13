# Sole-integrator handoff

Adopt only the exact reviewed controller manifest under
`adapters/capture-bridge/controller/`. Merge its per-file notices with the
repository's existing inventory. Preserve the existing sink source/tests and
all unrelated work. No source integration, Git commit, remote action, device
operation or publication is performed by this candidate.

The public dependency is selected main
`b83e3278cc6ef41765b8457bcc29eaf4b515ec62`, with sink source
`fa68038e75e7175511f9a9f68c041cb321e7fa5e`. SINK-DEPENDENCY.json binds exact
external file hashes/blobs. The runner compiles those external source files but
does not copy them or rerun unchanged sink tests. Do not relabel diagnostic or
ignored private sources with those public Git revisions.

The destination boundary checker needs narrow Java source prefixes for this
controller's main/android/tests package beneath its new controller directory.
Every new file still needs its exact manifest notice/hash. No blanket binary,
class/jar/APK, runtime loader, private artifact/profile or vendor source allowance
is required. Generated compiler/test outputs must remain outside public payload.

This is a reusable orchestration implementation with synthetic deterministic
tests and Android API typing. There is no real vendor capture implementation,
qualified firmware compatibility, durable host execution provider or public
device execution recipe. A private source composition must select one reviewed
typed RuntimeSelection at build time and implement original durable custody,
closed private input parsing, live compatibility/privacy/permission/owner joins,
actual process/pipe retention and exact cleanup. Runtime class/command/native
path selectors and automatic fallback are prohibited. Do not replace the absent
provider with a success stub.

API additions over the mapping: OwnerObservation carries requestId, sessionId
and invocationNonce in addition to its fresh operation serial; SinkClient.acquire
receives this invocation's opaque local identity; typed death Monitor ports make
actual registration retirement observable. These local joins are not native
ownership fences or authentication tokens. They prevent accidental cross-invocation
reuse and preserve all global capture risks/unknown outcomes explicitly.

The reviewed successor also retains/revalidates the final OwnerObservation at
the serialized attempt decision, records its sampled decision time, and joins
publisher invalidation with the Stop latch atomically. Scope/observation expiry
is checked again after the final lease read. CapturePorts.bootstrap is the
testable seam actually used by the Android entry to prepare/reuse one Looper
before framework creation and pass that same coordinator to Session. These
source corrections do not qualify framework execution or remove the external
decision-to-vendor-call race described in CONTRACT.md.

Normal accepted Stop/grace and helper-local retirement do not prove native
retirement, app drain, source availability, exclusion, continuous media or
presentation. Keep future peer-profile/RQPS/RMANVID, renderer/image lifetime and
restart/review admission separately owned and independently qualified.
