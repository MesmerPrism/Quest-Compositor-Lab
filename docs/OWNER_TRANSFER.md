# Generic audio supplier decision

The six generic Java audio files remain owned by Quest's `producer-v2`
development candidate. [AUDIO_SUPPLIER.json](AUDIO_SUPPLIER.json) pins exact
source bytes and accepted-input/current host-build receipt digests. The
candidate is not asserted to be a separately published artifact. This repo
contains no duplicate implementation and no automatic machine-path lookup.

Consumers must resolve those exact bytes through their explicit supplier
composition. Existing Synedelica copies preserve their prior provenance; this
milestone does not silently reassign their mutable ownership. The direct
Synedelica audio/LUT core is a separate consumer contract.

If ownership is deliberately transferred later: agree API/license/test hashes,
land the destination first, migrate Quest and Synedelica to an exact revision,
verify both consumers, and only then retire the old mutable owner or install
an explicit compatibility facade. Record the sequential handoff. Never keep
two independently edited copies under a claimed single owner.

Host capture, permission, microphone, main-thread View/window admission and
retirement remain with each embedding application. No shell microphone
bootstrap or second capture source is selected by these documentation pins.
