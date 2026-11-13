# Local sink ABI v1

This contract is an app-owned input boundary, not authority to start a global
native capture session. App-side retirement never asserts vendor retirement.

## Descriptor

`StereoSinkDescriptor` is immutable. Its fixed 19-word representation is:

| Index | Value |
| --- | --- |
| 0 | ABI version, exactly 1 |
| 1 | exact positive reader generation, supplied by the actual reader owner |
| 2–3 | packed width/height |
| 4 | image format, initially RGBA_8888 = 1 |
| 5 | exact usage, GPU_SAMPLED_IMAGE OR GPU_COLOR_OUTPUT = 768 |
| 6 | maxImages, 2 through 64 |
| 7–10 | left eye x/y/width/height, half-open pixels |
| 11–14 | right eye x/y/width/height, half-open pixels |
| 15 | raster origin: 0 top-left, 1 bottom-left |
| 16 | clockwise quarter-turns, 0 through 3 |
| 17–18 | reflect X/Y, each exactly 0 or 1 |

The initial supported layout is equal full-height left/right SBS halves.
Width is even, 2 through 16384; height is 1 through 16384. Every field is part
of equality. No wildcard generation, cropped/overlapping/swapped eyes,
unsupported format/usage, integer truncation, implicit orientation, or extra
word is accepted. The descriptor describes reader raster storage; transforms
describe how a later consumer interprets that raster. They do not rearrange
pixels here. The future capture controller must explicitly support the selected
layout/orientation before start. maxImages is a declared capacity, not proof
that an AImage was released.

Eye rectangles address the storage raster before orientation. Consumer order is
origin conversion to top-left, clockwise quarter-turns about the eye centre,
then reflection in the oriented eye's X/Y axes. Quarter-turns 1 and 3 swap logical
eye dimensions; they never change the declared packed storage extent.

## Publisher and process boundary

Binder interface token:
`io.github.mesmerprism.questcompositor.capturebridge.StereoSinkPublisher.v1`.
Only two transactions exist, FIRST_CALL_TRANSACTION for GET_SINK and +1 for
GET_STATUS. Every transaction, including interface discovery, checks the actual
`Binder.getCallingUid() == 2000` and `Context.checkCallingPermission(DUMP)`.
Calling root, system or the app itself does not substitute for Shell. All calls
are synchronous and require a reply; one-way flags reject. The caller never
supplies an identity/permission claim. The pure `PublisherAccess` predicate is
used directly by the Android boundary.

The request is the interface token followed by exactly 19 descriptor words,
each written as Parcel long. GET_SINK additionally carries one nonnull strong
Binder belonging to the requesting helper process for death observation.
GET_STATUS has no extra field. Trailing fields reject; no URI, intent, arbitrary
command, mode or generic Bundle is an RPC. Unknown transaction codes return
false after the caller check. This is an authored app-to-helper Parcel ABI;
it is not a vendor-private Binder/AIDL ABI.

GET_SINK replies with writeNoException, the same 19 descriptor words and one
Surface parcel. The requester owns its returned wrapper and must close the
Snapshot. Publisher closes its separate wrapper after synchronous reply write.
`SurfaceFactory.acquireOwnedWrapper` must return a fresh, owned wrapper for the
exact reader, never the app's sole original wrapper. It must be bounded and
nonblocking, and must not reenter unrelated app or GPU work under the owner
monitor. The local owner rechecks descriptor admission after acquisition.
Unavailable/invalid Surface or uncertain reply construction invalidates this
generation's app frame admission; it does not invent a capture Stop.

The publisher pins one descriptor at construction. It can never adopt a
replacement reader generation. Service teardown closes that publisher. A new
reader uses a new publisher, and a delayed old publisher destruction cannot
invalidate its replacement. Only one helper lifetime Binder may be attached to
one active generation. Helper process death invalidates admission; its delayed
death callback is scoped by both lifetime Binder and exact descriptor. A
different helper cannot take over an active generation. None of these local
identifiers establishes exclusive ownership of Meta's global capture API.

GET_STATUS replies with writeNoException followed by eight Parcel longs:
version=1, generation, phase, readerAlive, enteredCallbackCount, imageHoldCount,
gpuFenceHoldCount, vendorRetirementAck. Phase codes are ACTIVE=1,
INVALIDATED=2, DRAINED=3, RETIRED=4. The last word is always 0 because an
authoritative vendor retirement acknowledgment is unavailable. No timestamps,
sleep duration or accepted Stop status can change it. Terminal status requires
all app counts zero and readerAlive=0.

The abstract Service accepts only its exact explicit component, no
action/data/categories/extras, and exposes no start-service control. Register
the concrete subclass with `android:permission="android.permission.DUMP"` and
explicit opt-in enablement. The per-call check remains mandatory. No manifest
or app permission grants a normal app Shell UID.

## App-side lifecycle and resource integration

`SinkAdmission` is a pure immutable reducer. `SinkPublisherOwner` serializes its
updates with publication. The actual host must report every callback, acquired
image/import lease and GPU fence use through that owner; no raw AHB address is
a resource hold. The reducer does not allocate/free/submit/wait for resources.

1. Attach only after the actual app reader exists. An active/invalidated/drained
   reader cannot be replaced. After RETIRED only a strictly newer generation
   may attach; keep the same owner/high-watermark across reader replacements.
   At process boundaries the host supplies a genuinely distinct generation
   namespace and never revives an old publisher.
2. Register `beginCallback` before the callback can acquire an AImage. Pass the
   observed exact descriptor. Changed descriptor or stale generation rejects.
   The host must invalidate the affected current reader on an actual descriptor
   consistency failure, without invalidating an unrelated newer generation.
3. Record each real image/import hold with `imageAcquired`. If invalidation raced
   an already-entered callback, the hold is still tracked but publishable=false:
   it is cleanup-only and cannot open a GPU use or refresh media freshness.
   The returned publishable Boolean is an observation, not a lasting grant.
   Recheck `requireImageCurrent` at publication; the Android owner provides
   `publishCurrent` to serialize the check and a bounded publication operation
   with invalidation. No GPU work/wait or unrelated reentrant work belongs in
   that operation. Retained downstream frames still carry their own generation.
4. Register every GPU use with `beginFence` before queue submission. It requires
   a live admitted image. Never release an image while any of its fence tickets
   remain. Only an actual fence completion observation can call fenceCompleted.
   Unknown submission/fence outcomes retain the ticket and prevent drain.
5. Finish the callback after its actual acquisition/cleanup work leaves. Invalidating
   the publisher or observing helper death stops new callback admission; it does
   not drop entered callback/image/fence tickets. Late completion can only remove
   its exact extant ticket; tokens do not reset across reader generations.
6. `readerDestroyed` is an actual reader/admission destruction observation,
   independent of publisher invalidation. DRAINED requires destroyed reader,
   zero callbacks, zero image/import holds and zero GPU holds. RETIRED then permits
   a fresh local sink generation. A remote Surface held by the vendor is not
   proven retired by these app-side observations.

Before any capture restart or debug snapshot-review surface, the product must
also satisfy its independently owned global capture-idle/current-owner and
controller/peer quiescence gates. This module supplies none of that authority.
No snapshot-review surface or capture controller is implemented here. The
pending SBS authenticated source/peer contract will carry its own profile,
projection/coverage, colour/alpha, timestamp domain and explicitly unknown
capture-pose capability; do not infer those from this local sink descriptor.
