# Acoustic and material parity

The bundled acoustic model supports the MIT PF node shapes used by this port: event selectors, simultaneous layers,
weighted/probability choices, chance, delays, basic sounds, and explicit silence. `none` now uses the explicit `empty`
node; `empty`, `silent`, and JSON `null` can deliberately mute a resolved event without making it an unresolved mapping.
That distinction lets the client suppress a channel it owns while still retaining native fallback for absent or invalid
acoustics.

Swimming selects `swim_water` or `swim_lava` and their `swim` event. Both bundled definitions use Minecraft's swim
recordings, with a lower pitch for lava. The shared contact engine supplies stroke timing and smoothed three-dimensional
speed; simultaneous foot contacts produce one stroke. Deep swimming skips ordinary ground/wet layers but can retain
foliage rustles. Shallow supported wading keeps the existing material and wet-layer behavior.

An acoustic context can select a position along volume and pitch ranges instead of choosing randomly. Swimming uses
`clamp(speed * 0.35, 0, 1)` for volume, with remote strokes additionally scaled by `0.125`, following MIT PF. Range
endpoint order is preserved, including descending ranges. Context scaling and delayed playback retain these controls.
Other playback keeps random range sampling unless its caller requests interpolation. The general range function
also permits finite extrapolation beyond 0..1, matching MIT `Range.on`; negative results are floored at zero and
range results are kept finite for playback. Swimming still clamps its own intensity before supplying it.

Both audited MIT branches supply dynamic volume only from swimming and the winged generator. They expose dynamic
pitch in the acoustic API but contain no built-in callers for it. There is no missing terrestrial speed-to-pitch
mapping to apply. Winged volume callers, including a landing range position of `2`, belong to the remaining winged
locomotion work; the shared range engine can now represent them.

The audited 1.7.10 Dynamic Surroundings footstep engine already supplies player swimming through `_SWIM`, using
`game.player.swim`. PF's existing `Footsteps.process` compatibility hook replaces that path while PF owns swimming;
disabling PF swimming or removing its acoustic definition leaves that path available. Separate DS underwater ambience
and water-entry splash sounds are not suppressed. An explicitly empty acoustic still means intentional silence.

## Takeoff and landing

Standing takeoff selects `wander`; moving takeoff selects `jump` on the next gait foot. Soft landings select `climb`
or `climb_run`, with the usual `walk`/`run` fallback. Hard landings over 0.9 blocks select `land`. The standing-jump
threshold is squared horizontal movement below `0.005`, matching the units of the MIT source. Physical contacts still
cover the whole gait for footprint consumers; the sound consumer selects one foot for moving takeoff/soft landing and
deduplicates identical materials and layers within standing takeoff/hard landing groups. Armor body accents remain
once per group. A hard landing can still sound while sneaking; quieter movements retain sneak suppression.

Native small/big fall impacts and their same-tick block sound wait up to four client ticks for a matching PF landing.
A nearby landing with an available mapped acoustic replaces them. Missing material/event definitions retain native
fallback; explicit silent definitions retain ownership. Queue limits fail open, and disabled effects restore pending
native audio. Actor resets/teleports discard old-position pending impacts. Remote packet attribution stays conservative
when multiple actors could be the source. Hurt cries, damage itself, and water-entry splashes are not intercepted.
This adapts upstream's broader native-sound filter to PF's per-actor ownership and AET's separate splash effects.

## Foliage

MIT `PFSolver` and `TerrestrialStepSoundGenerator` distinguish ordinary `.foliage` contacts from the
`MESSY_FOLIAGE_STRATEGY` entry/swimming query requiring `.messy: MESSY_GROUND`. The Forge adapter uses physical
foot contacts for ordinary lookup and a bounded scan at foot height for marked vegetation. All lookups use the
selected per-entity material map. Any equipped boots suppress the low foot-contact walking layer; the sprint state restores
contact foliage. Low entry rustles require movement, no sneaking and no boots. The swimming vegetation
path uses the messy marker without the ordinary footwear gate, matching the upstream separate swimming branch.

PF keeps its shared contact timing. Per-actor acoustic-name claims suppress duplicate foliage within a tick,
including simultaneous feet and entry/step overlap. Entry detection uses a two-tick cooldown in place of upstream's
100 ms polling, tracks occupancy even while muted, and baselines on actor generation changes so teleports and
support changes do not become entries. Distinct acoustic names can still layer; authored surface sounds are unchanged.

PF additionally restores body-contact rustling above the footwear region, including head-only vines and tall
vegetation while wearing boots. This extends the audited MIT blanket footwear gate. Body and foot entries track
occupancy separately but claim acoustic names together; body playback uses the existing gait/stroke contacts,
requires movement and no sneaking, and resets with the actor generation. Bounds and collision intersection keep
low plants and nearby untouched vines out of the body layer. Double-plant upper halves resolve the loaded lower
variant. Fern, tall-grass and rose-bush mappings now follow the audited MIT assets.

## Materials and biome variance

Movement playback combines master, source-class, profile and biome volume with an optional running adjustment.
Own-player volume is independent of other players. The MIT PF running multiplier `1 + adjustment * progress` is normalized
by `1 + max(0, adjustment)` to reserve headroom below Minecraft's gain ceiling. Positive adjustment softens walking
and retains the configured running volume; negative adjustment softens running. Adjustment defaults to zero.
The progress uses a squared-speed ramp, smoothed by 0.35 per tick. Unlike upstream's local post-friction velocity, PF measures support-relative tick displacement for all actors, so its ramp spans 1 to
1.25 times each profile's `runSpeed`. Vertical movement participates; teleports and actor resets clear the envelope.

Size pitch follows MIT PF's `1 / (1 + (scale - 1) * 0.6)`, with scale clamped to 0.01 through 200. Minecraft 1.7 has no
entity-type default-dimensions API, so profiles provide adult `referenceWidth` and actual entity width supplies the
scale. Bundled vanilla profiles include verified adult widths. A zero/unspecified reference leaves pitch unchanged.
The existing child gait scaling is independent and retains its accepted behavior.

Each PF sample carries its actor's player/neutral/hostile category through context scaling and delayed playback.
A scoped SoundManager mixin uses it for initial playback, live slider changes and channel cleanup. Shared sound
definitions, namespaced pack resources, sample variation, mono routing and non-PF playback keep their existing paths.

MIT Isolator's per-entity block maps are adapted to inherited PF profile `materialMap` IDs, allowing exact,
age/variant and explicit class selectors to share maps. Referenced maps load at resource/local reload; contact
resolution uses immutable snapshots. Missing actor entries use the shared block map. Explicit `UNASSIGNED` skips
to primitive fallback, while non-emitter and vanilla markers remain distinct. Map paths and profile selection
are PF-specific; upstream modern entity registry paths are not loaded automatically.

Material maps continue to use 1.7 block registry names and metadata. The reader also accepts vanilla block keys without
the `minecraft:` prefix and modern PF primitive spellings such as `minecraft:block.stone.step`. Modern block-state/tag
selectors cannot be translated reliably to 1.7 metadata and are not supported.

Biome variance is active: `MaterialLibrary` resolves numeric biome IDs first, then the lowercase 1.7 display name,
underscore form, and `minecraft:` underscore form. The bundled map covers the vanilla 1.7 IDs with small volume/pitch
changes; modded biomes remain neutral until a resource pack maps them. The controller smooths those values per tick.

`variator.json` is intentionally not active. Its cached MIT counterpart belongs to the retired motion generator, while
this port's stride, run threshold, idle behavior, ladder stride, and jump/landing policy live in the current tracker and
Forge settings. It remains packaged only to avoid presenting a removed resource as a missing file.

## Local material and acoustic corrections

On resource reload, optional local JSON layers under the mod configuration directory apply after resource packs:

- `profiles/blockmap.json`, `profiles/primitivemap.json`, and `profiles/biomevariancemap.json` use the same object
  forms as their bundled counterparts.
- Each `acoustics/<id>.json` file is an acoustic definition. These files may override an indexed definition or add a
  new material ID without editing `index.json`.

Local files are limited to 1 MiB; at most 128 acoustic files are considered in lexical filename order. Their JSON is
strict (no duplicate keys or trailing data) and each file is decoded as one atomic layer. During an in-place malformed
edit, the previous valid version of that same file remains active. Removing the file removes its local layer on the next
reload, exposing the resource-pack definition or no definition.

## Sound timing

Acoustic delays now merge into the final sample, matching MIT's inner-option precedence rather than
adding nested delays together. A bounded monotonic queue applies early tolerance and drops stale
layers, with one tick of minimum late grace for 1.7's dispatcher. Delay ranges use their stated
endpoints; the pinned upstream nonzero-minimum random-range bug is not reproduced. Caller-controlled
late retention and the PF JSON `skippable` extension preserve the legacy presence-only semantics.

Distance arrival follows MIT ImmediateSoundPlayer: strictly over 10 blocks, delay by
`floor(distance / 2)` ticks. The configurable adapter uses the 1.7 sound listener's eye position and
its own bounded queue instead of Minecraft's unmanaged delayed queue. Sounds retain snapshot source
positions and actor validity. Arrival queues also expire stale work; nested scheduling cannot apply
the distance delay twice. Pausing singleplayer, changing worlds and reloading clears pending work.

## Giant and magma cube profiles

Both audited MIT locomotion maps select BIPED for `minecraft:giant` and `minecraft:magma_cube`.
The Forge profiles now match those through legacy IDs `Giant` and `LavaSlime`. Existing contacts
supply ground steps and physical takeoff/landing timing; no specialized hopping generator was
present in the audited upstream. Magma cube native body sounds remain outside PF surface/fall
suppression. Regular `Slime` now also has a profile, a user-requested PF addition beyond the audited
MIT mappings. Its normal 0.42 upward jump velocity uses the same physical contact tracker, with native
body sounds preserved and no footprints. Nominal widths come from the 1.7
entity classes (3.6 for Giants, 0.6 for size-one cubes); cube dimensions scale with slime size.

Synchronized hop interpolation keeps airborne-to-ground transitions airborne until the grounded
sample's position is reached. This prevents material landing contacts from being emitted above the
block and discarded by the bounded surface resolver. Departure likewise becomes airborne as soon
as the interpolated position leaves a grounded sample.

## Wildcards and material groups

The audited MIT StateLookup supports a global `*`, block-state predicates and modern registry tags. PF now
supports the global wildcard with its 1.7 metadata/substrate spellings, plus registry-name `*` patterns as a
Forge adapter extension. `#ore:<name>` provides group membership from Forge's Ore Dictionary instead of modern
tags. Existing `#` comment keys remain inert. State predicates still require metadata conversion. Derived/appearance lookup is described below.

PF preserves its established merged-key precedence rather than copying modern lookup quirks: exact entries
precede groups, groups precede patterns, and entity maps precede the shared map. Metadata constraints win
within a class; patterns then rank by literal specificity, with later declarations breaking ties. Rules and
Ore Dictionary metadata membership compile at reload and cache results within a bounded snapshot-local cache.

## Derived and appearance materials

MIT `AssociationPool`, `DerivedBlock` and `PFSolver` supply the base/appearance lookup model. The Forge adapter
exposes vanilla stair constructor materials through a client mixin and an opt-in static base API, plus a loaded-only
top-face appearance provider API. Carpenter's Blocks is the first concrete cover adapter, using its public 1.7 API
through reflection. No Carpenter's implementation or assets are copied, and the mod is optional.

PF retains explicit mapping precedence, `NOT_EMITTER` termination and `UNASSIGNED` fallback. It deliberately limits
base-to-default-substrate fallback to `.bigger`; ordinary sounds must not leak into wet/foliage/carpet channels.
It also bounds and cycle-checks base chains, and retains the physical collision geometry for footprints.
Modern copied-properties base metadata has no generic 1.7 equivalent. MIT's grass-sound/name-to-leaves heuristic
remains unported; only known bases and registered appearance adapters are used here.

## Thin floor coverings

MIT `PFSolver` checks a mapped `.carpet` substrate on a collision shape below 0.3 blocks high. PF adapts this to
1.7 state bounds because legacy carpets and pressure plates may have no collision. Only explicit emitting
`.carpet` mappings opt generic coverings in. Floor alignment, horizontal coverage and real support bounds constrain
the overlay; snow/carpet special cases retain their known visual/collision offsets. Shape reads use a guarded
block-access view and restore the block's shared bounds. Footprint clipping and height change without moving the
physical contact. This does not provide arbitrary multipart shape inference.

## Horse cadence

Both audited MIT generators triple the horse step threshold above squared horizontal speed 0.1. Their quadruped
modifier uses randomized walking intervals and a three-event running pattern. PF retains its four physical feet
and diagonal trot for stable contact positions. The optional gait `fastStride` curve adapts the high-speed correction:
the bundled horse gradually lengthens strides above sqrt(0.1) blocks/tick, reaching 1.5x at 0.6. The smaller multiplier
accounts for PF's existing cycle spacing. Smoothed displacement keeps acceleration and deceleration continuous;
contact phase is not reset. Other bundled quadrupeds retain their accepted timing. Swimming, climbing and impact
contacts bypass this extension. See [gait profiles](Resource-packs.md) for tuning.
