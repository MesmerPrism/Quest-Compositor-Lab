# Typed inert selection API

`tools.compositor_profiles.load_profile(path: str) -> Profile` requires a
manifest directly below `ROOT/profiles`. Source paths are portable and relative
to ROOT. `make_plan(profile: Profile, stage_root: str) -> SelectionPlan` accepts
only the owned `/data/local/tmp/qcl` base. Neither API contacts a device.

The frozen dataclasses are `ShaderPass`, `Profile` and `SelectionPlan` in
`tools/compositor_profiles.py`. JSON schemas live in `contracts/`; runtime
validation additionally enforces source hashes, closure and cross-field
relationships. No private formula, catalog or source is an input dependency.

The stage directory appends the full SHA-256 of canonical profile content.
For pass N, stage `vertex_path` as `N.v` and `fragment_path` as `N.f`, each
verified against the corresponding manifest hash. Compact stage filenames keep
property values below Android's 92-byte buffer limit (at most 91 value bytes).
Renaming at staging does not change source content. Never stage the host-only
fixture prefixes into the native loader.

All five pass slots receive explicit vertex, fragment, width, height and filter
values; unused slots are cleared. Partial dimensions are invalid. The plan
requires fresh native construction and describes requested state only.

CLI artifacts use `quest.compositor.plan-artifact.v1`, containing schema,
relative profile_path, stage_root and the SelectionPlan. Save only to a new
JSON file under ROOT/local. `verify` reloads the current profile/sources and
recomputes the entire plan. It verifies consistency, not identity, signatures,
public-source licensing, bytes on a device, actual loading or display.

The public compatibility record is experimental and source-only. A private
consumer writes its adapter under its own owner and keeps opaque/compositor
input, alpha policy, clock/reset and effective evidence distinct from app-owned
camera processing. No reverse private dependency belongs in this repository.
