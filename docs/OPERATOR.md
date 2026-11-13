# Host plans and future attended operation

`list`, `plan` and `verify` are inert host operations. A plan records exact
shader hashes and a deterministic owned stage. Verification recomputes the plan
from current sources; it neither contacts a device nor reads properties.

A later attended device unit must select an exact compatible target and reserve
its resource. Preserve the exact before-state of every changed property,
including absent/empty values, every referenced file and its hashes. Observe
skip/overlay controls and avoid inheriting unrelated size overrides. Stage only
hash-verified files in the content-specific owned directory. Keep prior files
and properties until exact restoration is proven.
Use the compact stage mapping in [PROFILE_API.md](PROFILE_API.md), including
full content digest and per-pass `N.v`/`N.f` names. Verify every staged file
against its source hash; no device staging is performed by this tool.

Native program construction needs independent evidence. Readback records
requested values only. Begin with identity and a distinguishable control,
confirm both eyes, then inspect actual adoption and wearer behavior. Historical
human sleep/wake observations are compatibility evidence; this tool provides
no synthetic wake/reset loops or runtime restart mechanism.

Restore every changed property and referenced byte exactly, then establish
effective restore independently. A bypass is not program/resource retirement.
Unknown cleanup is recorded as unknown. Device time, frame counters, source
timestamps and host clocks must not be conflated.
