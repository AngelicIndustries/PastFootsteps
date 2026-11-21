# 0.0.1 preparation

Reviewed 2026-10-03. Past Footsteps is the next release focus. Even Mo' Bends release preparation is on hold
pending the WawelAuth player-model rework. The maintainer will push and publish.

## Implemented

* Material footsteps, entity and gait profiles, foliage rustling, equipment/rain accents and footprints.
* Water/lava swimming, takeoff and landing distinctions, positional playback, actor volume/pitch controls,
  distance delay and native-sound fallback.
* Resource-pack and local overrides, material diagnostics, acoustic export and partial upstream translations.
* Stair materials, thin coverings, Carpenter's Blocks covers, EFR boats, spiders and optional Even Mo' Bends
  spider foot layouts. Optional server support supplies remote motion information.
* Walking effects formerly owned by AET are implemented here. AET retains equip sounds and its other effects;
  see [the migration guide](docs/AET-migration.md).

## Work in progress

The user rejected the current editor slice on 2026-10-03. It is being redesigned around explicit block/material
scope, editable sound recipes and original/draft comparison. The existing code below remains a prototype.

* The walking-sound override API and resource loader are committed as an experimental prototype, paired with Sound Manager's experimental material browser
  and sound picker. The format replaces only exact walking events with installed sound events. Running,
  jumping, landing and other recipes retain their existing behavior.
* Layer precedence, malformed-input handling, missing-sound fallback and acoustic playback have tests.
  End-to-end editor acceptance still needs to cover audition, save/apply, revert, reopening, pack priority,
  resource reload and missing/older PF versions. This is not yet a general sound-pack authoring system.
* The editor moved from AET to the standalone `SoundManager/` project on 2026-10-19. Its code and draft
  documentation remain uncommitted during review; PF's API and resource format are unchanged.

## Validation and cleanup

* `VERSION=0.0.1 ./gradlew build` passes with 220 tests and no failures or skips. Existing gameplay acceptance
  records cover the core features, AET/Dynamic Surroundings coexistence, resource packs and dimension changes.
* The full build exposed a Checkstyle failure in `JsonData` that the test task does not run. Replaced its wildcard
  Gson import with explicit imports; the full build now passes, including Checkstyle.
* Local AET integration is now an explicit `-PanExtraTouchJar=...` development option. A standalone checkout
  no longer references a sibling build. Checked both the standalone and explicit-jar runtime classpaths. The
  optional jar is never a published dependency.

## Before publishing

* Complete the shared sound-editor review and acceptance, then commit the feature in both repositories.
* Validate packaged client and dedicated-server startup, including PF without AET and AET/PF together.
* Review the README, migration instructions, bundled notices and final 0.0.1 changelog.
* Push the local repository history: the configured GitHub remote advertised no branch heads at this review.
* Configure the chosen platform project IDs and required UniMixins relation; CurseForge, Modrinth and 67 IDs
  are currently empty. Maven/release workflows already exist. Tagging and publication are maintainer steps.

Automatic leaf classification, old monolithic acoustic-pack compatibility, winged locomotion, Mine Little Pony
and further moving-surface integrations remain deferred or out of scope under the existing decisions. They are
not first-release requirements.
