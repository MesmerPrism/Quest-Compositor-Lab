# Finite invocation and custody contract

The pure APIs are CaptureAdmission, CapturePorts, CaptureAttempt,
FiniteCaptureController and ControllerStatus. One controller and its injected
OriginalWorker own one invocation forever. begin cannot rearm it. post is FIFO,
nonblocking and never executes inline; all driver/sink/owner operations run on
that original queue. tick is called by a separate coordinator/finisher.

## Admission and adjacent dispatch

CaptureAdmission binds exact firmware/API/ABI/profile/artifact/service/Binder
version/default/signature/loader/privacy expectations, an exact installed sink
APK/service/process UID/birth/NUL cmdline and complete immutable descriptor,
target/boot/lease/native-owner identity, fresh request/session/nonce declarations,
explicit prior-custody/sole-operator/one-own-Stop policy and finite budgets.
Collections are defensively copied. These typed declarations are not live proof.

Actual framework Shell UID/eUID/attribution UID must all be 2000, with matching
Shell package/op-package/attribution, the framework Application class, DUMP and
the selected capture permission. Root/system/app UID are not substitutes.

Immediately before each capture RPC the original worker obtains a fresh owner
observation, inspects exact original Binder compatibility/privacy/permission and
observes the exact original sink, then obtains another owner observation after
potentially blocking calls. Every owner observation joins target, boot, lease,
PID+birth, Binder reference, helper monotonic domain, request/session/nonce and a
strictly newer serial. Age, action/cleanup limits, prior custody, sole operator
and foreign-capture absence must remain admissible. No external UTC clock is
compared directly with helper monotonic time. The marker and last guard share a
serialized controller transition. The final immutable owner observation is
retained across the last lease check and revalidated inside the same monitor
transition as each attempt marker. That transition samples helper monotonic
time once and checks observation age, action/cleanup authority, exact identity,
current observation serial and all locally known invalidation/attempt/latch
facts. It does not perform a new blocking observation RPC or lease getter under
the monitor. Publisher invalidation and Stop latching are one observable state
transition before queue posting, without a flag-set/latch gap.

The recorded startDecisionAtMillis/stopDecisionAtMillis identify this local
authorization boundary (zero means no such attempted RPC). They are not native
dispatch timestamps. External time and global state can advance immediately
after the sampled decision, including before the actual vendor method enters.
The original attempt is committed at its marker and remains an uncancelled
original operation; later events latch cleanup rather than replay or invent a
returned outcome. No OS/native ownership fence is claimed. The residual
decision-to-call race requires independently enforced sole-operator admission;
this controller cannot promise that external state remains frozen after its
local decision. It does refuse facts already known locally invalid at that
decision.

Start also requires native quiet, Start authorization, an ACTIVE/live reader,
valid exact Surface lease and this invocation's fresh helper lifetime token.
Stop requires its own attempted Start, original native owner/Binder and cleanup
authorization, without requiring quiet capture. Same-generation sink invalidation
or observed original publisher death is cleanup-only: it may latch the own Stop,
never Ready. A replaced descriptor/publisher, stale observation, unknown sink
RPC, changed owner or failed privacy/compatibility gate blocks global Stop and
retains custody. No new Binder with matching names/versions can substitute.

## Outcomes and original operations

CallResult is ACCEPTED or REJECTED. CaptureAttempt separately records
NOT_ATTEMPTED, IN_FLIGHT, ACCEPTED, REJECTED or OUTCOME_UNKNOWN for Start and Stop.
The attempted marker precedes the original call. A rejected/throwing Start can
have partial effects and still owes cleanup. Null/throwing returns are not
rejection. Repeated stop requests latch reasons but never issue another Stop.
There is no retry, replacement worker, cancellation or recovery process API.

A deadline latches Stop on the original queue. A blocked Start leaves that Stop
queued behind it. Both RPC tickets and the entire queued/running admission/Stop
tasks retain custody, including gaps between RPC calls. Original death cannot
pretend a blocked call returned. Wrong/replacement Binder death is ignored.

Accepted asynchronous Stop plus its explicitly selected grace can satisfy only
the named ACCEPTED_STOP_GRACE_OR_ORIGINAL_DEATH local wrapper policy. Rejected or
unknown Stop has no elapsed-time success path. Actual original Binder death may
satisfy that identity-scoped client policy after original calls return; it cannot
erase an unrelated failed monitor/barrier/join/close or owner/custody hold.

All existing death registrations must retire with known results. Only then is
the original FIFO barrier posted; only actual barrier completion permits the
coordinator to quit/join the original worker. Snapshot.close occurs once after
actual join. False/throwing unlink, incomplete/unknown barrier, false/throwing or
interrupted join, and unknown Surface close remain HELD. No operation is replayed
to obtain a more convenient receipt. ControllerStatus holds/history collections
are immutable. It always reports vendorRetirementAcknowledged=false,
externalHostCustodyResolved=false and appReaderDrainConfirmed=false.

## Android boundary and explicit gaps

GenuineShellApplication checks real process UID/eUID, constructs the default
framework Application using ordinary framework reflection, uses a code-free
Shell package Context, checks AppOps and real permissions/attribution. It never
sets identity fields, calls setAccessible, exempts hidden APIs, loads installed
Shell custom Application/vendor code or grants privilege. A blocked framework
API fails closed; this source has not executed that path on Android.

The actual Android entry uses the pure CapturePorts.bootstrap seam. It obtains
this thread's existing Looper or prepares one once, confirms it is current,
then invokes framework Application/ActivityThread bootstrap. Session receives
that same Looper; no second prepare follows bootstrap, and Looper.loop runs on
the prepared coordinator thread. The host order test exercises this production
seam with typed ports; Android/framework bootstrap remains unexecuted and
unqualified by source/API compilation.

NeutralPublisherClient uses the fixed public sink interface and one original
Binder/lifetime Binder per invocation. It rechecks app APK/manifest and actual
PID/birth/four-UID-row/NUL-cmdline identity around operations. Its AndroidLease
exposes only a borrowed Surface. A private typed driver must require the exact
owned lease/descriptor and never release or persist that borrowed reference.
The helper Snapshot remains the wrapper owner. If post-acquire checks fail, the
same client retains the known wrapper; there is no early close or replacement.
Unknown acquisition may mean no wrapper was transferred to the core, not proof
that no resource exists. The hard acquisition hold and original client remain.

The source-only entry's build-selected provider is null and throws immediately.
A future private composition must supply the actual firmware driver, closed
private runtime input parser, fresh original-owner probe and a real durable
OriginalCustodyRecorder before any attempted invocation. These are explicit
unimplemented runtime seams, not no-op successes. The public state machine is
in-memory; helper death cannot recover its objects or manufacture durable custody.
An unknown recorder observation retains the original session and does not create
a substitute writer. Actual provider/hidden API/Shell/Surface/native use is open.

## Offline host declarations

controller_plan.py imports no execution adapter. plan/validate parse bounded
closed integer-only JSON, reject duplicate keys/unknown fields, check exact local
artifact/profile/APK bytes and declared UID/cmdline/scope joins, and return
runtimeAdmitted=false plus live prerequisites. Files are read-only data under
the explicitly selected input directory. These operations cannot launch code,
ADB, a subprocess or network connection and never claim observed live state.

validate_terminal checks a declared original identity and requires local terminal,
stdout/stderr EOF, child reap, exact helper birth absence, original host-pipe
custody and resolved original handles. It rejects PID reuse, missing facts and
vendor ACK claims, but still returns observationTruthVerified=false and
runtimeAdmitted=false. A JSON declaration is not actual host process custody.
Restart/review additionally requires independent current global capture-idle,
controller/peer quiescence and an actual app reader/image/fence drain seal.
