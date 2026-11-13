# New controller source-only results

Date: 2026-11-13. Java checks use Temurin javac/java 17.0.19,
`--release 8 -Xlint:all -Werror`. Android API compile uses the existing API 34
platform revision 3 jar SHA-256
`6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad`.
No SDK or dependency source/binary is copied into the controller payload.

Observed test-first history against real new production APIs:

| RED | GREEN after implementation |
| --- | --- |
| Stop before own attempted Start was accepted | CaptureAttempt rejects premature/replayed calls and enforces ordered resource facts |
| Rejected/partial Start produced no own Stop | Real controller emits one guarded own Stop and preserves rejected/unknown Start outcome |
| Original admission task could retire monitors between RPCs | Whole queued/running original tasks prevent retirement until actually complete |
| Another invocation's owner observation could authorize Start | Request/session/nonce join and fresh serial are required |
| Throwing original join escaped instead of retaining HELD | False, throwing and interrupted joins retain original worker/Surface custody |
| Four offline tests failed duplicate/hash/inertness/custody checks | Closed file-only planner/validator passes all four real tests |
| Boolean helper PID could alias integer 1 in a terminal declaration | Both original and supplied identity numeric types are validated exactly |

An initial API compile reported the required AppOps check's SDK deprecation.
The check remains with a narrow documented suppression; the subsequent strict
API compile passed. No Android behavior is inferred from compilation.

Successor export02 addresses independent review C1/C2 with additional actual
RED-to-GREEN regressions against production APIs:

| RED failure | Corrected GREEN behavior |
| --- | --- |
| Action authority expired in the final lease hook, below the longer run deadline, but Start dispatched | Final owner facts/time and attempt marker share one serialized decision; zero Start/Stop |
| Cleanup authority expired while observation age remained fresh, but global Stop dispatched | Final cleanup recheck refuses Stop and retains original Surface/custody |
| Final observation aged past its budget while cleanup authority remained valid, but Stop dispatched | Final observation-age recheck refuses Stop and retains custody |
| Publisher invalidation became known after the last lease check, but Start dispatched | Atomic known invalidation/Stop latch and final Start check refuse dispatch; no replacement or invented attempt |
| Framework creation ran before a coordinator Looper was prepared | Actual entry's CapturePorts.bootstrap seam prepares/reuses the Looper first; fresh and existing Looper cases forbid duplicate prepare |

Each C1 failure was executed separately before correction. The cleanup test was
also isolated so expiry occurred only 51 ms after the final observation, within
its 200 ms freshness budget; the old compiled production decision dispatched,
the corrected one refused. The publisher test observes the event's flag/latch
state together through ControllerStatus. The bootstrap test uses the production
seam called by the actual Android entry, not source-text matching or executing
an Android SDK stub. Existing partial/rejected/unknown/blocked and one-Stop tests
remain in the maintained runner. Decision timestamps identify sampled local
authorization, not proof that external time/global state cannot advance later.

Two Java test programs exercise actual production reducer/orchestration with
typed deterministic fake ports. Blocking cases use manually released latches
and the same original queued worker, not sleeps, cancellation or substitute
RPCs. They cover partial/rejected/unknown/blocked Start; accepted/rejected/unknown/
blocked Stop; current-owner rechecks after potentially blocking sink calls;
wrong owner/boot/Binder/serial/invocation; privacy and Shell permission admission;
stale/non-ACTIVE/replaced sink/lifetime; original versus replacement death;
accepted grace versus rejected elapsed time; and unlink/barrier/join/close holds
that cannot be erased by later death. Close is checked after actual barrier/join.

Four Python tests import and execute plan/validate with subprocess/socket and
execution-import traps. They verify unchanged inputs, explicit runtime refusal,
local pin hashing, duplicate/unknown/numeric/identity rejection, path confinement,
and original terminal EOF/reap/birth/pipe/handle declarations. Declaration validation
does not observe actual processes or confer runtime custody.

Only these new host suites and a short API compile were run. The exact public
sink source dependency was hash-checked and compiled as required for typing;
its unchanged tests were not rerun. No Android framework reflection, real UID/
attribution/permissions, Parcel/Surface/death monitor, vendor provider, durable
recorder, native capture, app reader/image/fence drain, device, exclusion,
presentation, restart or sustained-use behavior was executed or qualified.
