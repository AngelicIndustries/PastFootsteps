# Resource packs

PastFootsteps reads its material data from the `presencefootsteps` namespace for the sound assets and JSON mappings described below. Modern block-state/tag selectors and legacy
LiteLoader CFG packs need conversion to 1.7 block names, metadata and Ore Dictionary groups. Files from all enabled packs are merged in pack order; entries from higher packs
replace entries with the same key. If one layer is malformed, that layer is skipped and valid lower layers remain.

## Block mappings

`assets/presencefootsteps/config/blockmap.json` is a JSON object mapping blocks to acoustic names:

```json
{
  "minecraft:stone": "stone",
  "minecraft:stone^1": "ore",
  "minecraft:tallgrass^1.foliage": "brush",
  "minecraft:snow_layer.carpet": "snow",
  "example:narrow_post.bigger": "wood",
  "example:marsh.wet": "mudfine,splash"
}
```

`^0` through `^15` select metadata and take precedence over the block's generic entry. The optional suffix is one of
`carpet`, `foliage`, `messy`, `wet`, or `bigger`. Values may name one acoustic, a comma-separated composite, or a
special value such as `NOT_EMITTER`, `MESSY_GROUND`, `VANILLA`, or `UNASSIGNED`. Acoustic names are normalized to
lowercase; special values are recognized regardless of case. `NON_EMITTER` is an alias for `NOT_EMITTER`.

`UNASSIGNED` explicitly clears a block association and continues to known base-material mappings, then primitive fallback for ordinary surfaces and
`bigger` lookups. It does not revive a less-specific or lower-pack block entry. For supplementary substrates such
as `foliage` or `wet`, only matching base substrates can supply an association; the resolver may still use its usual wet-layer default.
`NOT_EMITTER` is an explicit non-emitter and does not fall back to a primitive acoustic. On the `carpet` substrate,
non-emitting overlays still allow the underlying surface to be considered. `VANILLA` requests native step playback
for a normal surface rather than a PF acoustic. `MESSY_GROUND` on the `.messy` substrate enables the foliage entry strategy described below.

## Wildcard and group mappings

The shared block map and per-entity maps also accept `*` patterns and Forge Ore Dictionary groups:

```json
{
  "*": "stone",
  "example:*_planks": "wood",
  "example:*_planks^2": "squeakywood",
  "#ore:plankWood": "wood",
  "#ore:treeLeaves.foliage": "brush",
  "example:special_planks": "hardmetal"
}
```

`*` matches zero or more characters. A bare `*` matches every registered block; other patterns without a
namespace are limited to `minecraft:`. For example, `*_stairs` means `minecraft:*_stairs`. Namespaced patterns
can match the namespace too, such as `*:polished_*`. Only `*` is a wildcard; regex, `?` and modern block-state
predicates are not supported. `^0` through `^15` and the usual substrate suffixes work with either kind of selector.
A rule for the normal surface does not also match `.wet`, `.foliage`, or other substrates.

`#ore:<name>` matches blocks whose item form belongs to that Forge Ore Dictionary category. Selector names are
case-insensitive, so `#ore:plankWood` and `#ore:plankwood` are equivalent. An entry registered for a specific
metadata value matches only that value; an Ore Dictionary wildcard registration matches all 16 values.
An optional `^meta` further restricts the group. Non-block items are ignored. Group membership is captured on
reload, so use `/pf reload` after late registrations. Missing groups match nothing and are not created in the
Ore Dictionary. Other `#` keys remain inert, preserving legacy commented entries such as `#minecraft:slime`.
Modern tag names therefore need conversion to an available Ore Dictionary group; they are not automatic aliases.

Within each map and substrate, rules are checked in this order:

1. Exact block entries, metadata-specific before generic, retaining the existing vanilla namespace aliases.
2. Ore Dictionary groups, metadata-specific before generic.
3. Registry patterns, metadata-specific first, then patterns with more literal characters.

Equally ranked group or pattern rules use the last declaration. Replacing the same key in a higher resource pack
or local file makes it the later declaration. Specific rules remain more important than broad rules, even across
pack layers. An entity-specific map is searched before the shared map, so its wildcard can override a shared
exact entry. `UNASSIGNED`, `NOT_EMITTER`, `VANILLA` and composites retain their existing behavior; an explicit
`UNASSIGNED` does not continue through less-specific group or wildcard rules.

Rules and group membership compile on reload; lookups use a bounded cache that is replaced with the definitions.
Malformed active selectors reject their entire layer. Invalid local edits retain the last valid local layer,
and deleting the local file restores the pack definitions. These selectors add no CFG options and change no
bundled material mappings by default. MIT PF's global wildcard and tag grouping motivate this adapter;
registry-name patterns and Ore Dictionary syntax are PF's Forge-specific extensions.

## Base materials and covers

Unmapped stairs inherit their constructor's base block and metadata, including modded stairs extending vanilla
`BlockStairs`. A matching rule on the stair itself still wins, including group/wildcard rules and `NOT_EMITTER`.
Set a stair mapping to `UNASSIGNED` to deliberately try its base. Base states use the same entity map before the
shared map. Base primitive sounds take priority over the stair's primitive; `VANILLA` inherited from a base plays
that base's native step sound. Ordinary vanilla stairs retain their bundled explicit mappings.

Only `.bigger` may also use the base's ordinary mapping. Supplementary channels such as `.wet`, `.foliage`,
`.messy` and `.carpet` require a matching substrate on the base, so they cannot accidentally duplicate its normal
walking sound. Base chains are bounded to eight states and stop at cycles or invalid optional providers.

When Carpenter's Blocks is installed, PF asks its top-face API for the cover or full-face overlay before material
lookup. Cover metadata is retained and changes are observed at the next contact without a resource reload. With
no cover, Carpenter's frame remains the material source. Carpenter's API may represent unsafe tile-entity covers
as planks; PF respects that fallback. This integration does not infer which individual triangle
of a sloped or mixed-face block a foot touches. Other camouflage mods need an adapter using the
[block material APIs](Integration.md#block-materials).

Appearance affects sound selection only. Collision/contact bounds and footprint height remain those of the real
block; PF never substitutes a full cube's geometry for a covered slab, stair or slope. These integrations add no
CFG settings or required mods.

## Thin floor coverings

An explicit `.carpet` mapping also lets a thin covering select footsteps over the supporting block, even if the
covering has no collision of its own. For example:

```json
{
  "example:floor_mat.carpet": "rug",
  "example:floor_mat^1.carpet": "straw"
}
```

PF reads the block's current state bounds. A generic covering must start at the bottom of its cell, stay within
that cell horizontally, and have a positive height less than 0.3 blocks. The foot must be over both the covering
and the physical support. Missing/non-emitting `.carpet` mappings leave the support's sound in place. Ordinary
material mappings alone do not opt a non-colliding block into this behavior. Entity maps and cover appearance
providers apply as usual; vanilla snow and carpet retain their existing special height handling.

Pressure plates work with the bundled mappings, including their changing pressed height. Footprint bounds are
clipped to the covering and its support, and their visual height follows the covering; footprint eligibility is
still controlled by the normal footprint rules. Physical grounding and contact timing are unchanged. Unmapped
plants, rails and decorations do not automatically become floor coverings.

Bounds are refreshed per query and restored afterward. Bounds code that requires unloaded neighbors or cannot
use a read-only block-access view falls back to the physical surface. Arbitrary multipart/render-only shapes are
not inferred from textures or a broad selection box; they need a specific integration if their state bounds do
not describe their actual floor surface.

## Per-entity material maps

Entity profiles can select a named block map using `materialMap`. For example, add this profile to a local
`config/pastfootsteps/profiles/entities.json` file (merge it with any existing entries):

```json
{
  "schemaVersion": 1,
  "entities": {
    "cow_hooves": {
      "parent": "Cow",
      "selector": { "entityId": "Cow" },
      "priority": 1,
      "materialMap": "hooves"
    }
  }
}
```

Create `config/pastfootsteps/profiles/materials/hooves.json` with the same flat block-map format:

```json
{
  "minecraft:stone": "wood",
  "minecraft:sand": "sand",
  "minecraft:tallgrass.foliage": "NOT_EMITTER"
}
```

The resource-pack equivalent is `assets/pastfootsteps/config/materials/hooves.json`. Maps load only when referenced
by a compiled entity profile. IDs contain 1 to 64 lowercase letters, digits, underscores or hyphens, without `.json`.
Several profiles can share a map. Children inherit `materialMap`; explicit `"materialMap": ""` restores the shared
map for that profile. No bundled profiles select a custom map by default.

For each substrate, the selected map is checked first, then the shared block map if there is no matching entry,
then known base-material mappings and the shared primitive map where applicable. An entity-map generic match therefore overrides a shared metadata
match. Explicit `UNASSIGNED` bypasses the shared block map and proceeds to base-material/primitive fallback; omit an entry to use
the shared block mapping. `NOT_EMITTER`, `VANILLA` and explicit empty acoustic definitions retain their distinct meanings.
Maps apply to normal contacts, wet layers, foliage, ladders and mapped thin overlays. Explicit materials provided
by entity-support integrations remain authoritative. Footprint geometry and contact timing still follow PF's engine.

Pack layers merge by key, with local files applied last. Invalid local edits retain the last valid version;
deleting a local map restores its pack mappings. Missing maps fall back to shared mappings and log a warning.
Use `/pf reload` after edits; `/pf report` includes each tracked actor's selected `materialMap`.

## Foliage mappings

`.foliage` selects the additional acoustic at a foot contact. `.messy: MESSY_GROUND` also enables an entry rustle
between contacts and the vegetation layer while swimming. For example:

```json
{
  "example:reeds.foliage": "straw",
  "example:reeds.messy": "MESSY_GROUND"
}
```

Both lookups honor metadata and the actor's `materialMap`. Set `.messy` to `NOT_EMITTER` to disable the extra
foot-level entry/swimming query while keeping normal contact rustles. `.foliage: NOT_EMITTER` silences that
vegetation's additional foot and body layers entirely. Ordinary foot contacts exclude solid blocks and the
supporting overlay itself.

Body contacts also use `.foliage`, without requiring `.messy`. The bounded scan checks current plant bounds
against the actor above the footwear region. A short plant confined to the boots remains under the foot rules;
tall grass, reeds and head-height vines can rustle with boots equipped. Solid collision intersecting the body,
unloaded cells and invalid bounds are excluded. State bounds are restored after querying. Custom plants opt in
through the same mappings; no registry-name list determines their body eligibility.

For vanilla `BlockDoublePlant` and subclasses, foliage/messy lookup uses the lower half's variant metadata for
both halves. The lower half must be loaded, the same block, and a valid lower variant. Orphan or malformed upper
halves remain silent. Map variants `^0` through `^5`; upper-half `^8` through `^15` orientation values no longer
choose foliage sounds. Built-in fern uses `straw`, tall grass/large fern use `brush_straw_transition`, and rose
bush uses `brush`, matching the audited MIT mappings.

Body rustles require movement and no sneaking and share existing gait timing. Footwear rules for the low layer
are described in [Configuration](Configuration.md). Overlapping comma-separated foliage names, simultaneous feet,
body contacts and entry detection share per-actor deduplication. An entry rustle waits at least two ticks after
the last foliage playback to avoid boundary chatter. Explicit layers authored inside surface acoustics remain
unchanged. For body contact, an acoustic may define a `rustle` event to replace its usual gait event. Without
`rustle`, its normal gait event is retained. Built-in `leaves` and `softleaves` use this event for leaf rustling
without their dirt-footstep layer, including entry and swimming contacts. Deduplication still uses the original
acoustic name. Normal ground/climbing playback continues using its ordinary events.

## Primitive fallback

`assets/presencefootsteps/config/primitivemap.json` maps a block's vanilla step-sound type to an acoustic. It is used
only for ordinary surface and `bigger` lookups after the block map has no match.

```json
{
  "stone": "brickstone",
  "stone@1.00_1.50": "hardmetal",
  "stone@break_random.glass": "glass",
  "wood": "wood"
}
```

The volume-and-pitch form is tried first, followed by the break-sound form and the plain sound type.

## Biome variance

`assets/presencefootsteps/config/biomevariancemap.json` supplies optional volume and pitch multipliers. Minecraft 1.7
does not register biome names as resource locations, so keys are either the numeric biome ID or the biome's displayed
name in lowercase. Spaces may be written as underscores.

```json
{
  "2": { "volume": 0.85, "pitch": 1.05 },
  "extreme_hills": { "volume": 0.9, "pitch": 0.95 }
}
```

The bundled map covers vanilla 1.7 biome IDs `0` through `39` with restrained multipliers. Numeric IDs are the stable
choice for vanilla 1.7. Missing values and unmapped modded biomes use `1.0`; a pack can use a lowercase display name or
its underscore form, including `minecraft:<display_name>`, for a modded biome where that spelling is suitable.

## Acoustic definitions

Use `/pf acoustics` to export the currently loaded definitions as a ZIP resource pack. See [Diagnostics](Diagnostics.md#acoustic-export) for scope and override precedence.

Definitions live in `assets/presencefootsteps/config/acoustics/<name>.json`. Override a bundled file directly;
list additional names in `assets/presencefootsteps/config/acoustics/index.json` (a JSON array of names without `.json`).
Indexes from all packs are combined. Volume and pitch in acoustic definitions are percentages, unlike the multipliers
in the biome map and mod config.

```json
{
  "type": "events",
  "walk": { "type": "basic", "name": "presencefootsteps:pf_presence.wood.wood_walk", "volume": 80, "pitch": 100 }
}
```

Supported nodes are `basic`, `events`, `simultaneous` (or an array), `probability` (weighted `entries`), `chance`,
`delayed`, and `empty` (also `silent`). `null` is also an explicit empty child node. These keep a resolved channel
owned and intentionally silent; omitting an acoustic definition is different and allows normal fallback behavior.
Delay values are milliseconds; see the timing rules below. Ranges use `{"min": 90, "max": 110}`.
They normally sample randomly. A Java caller can use `AcousticContext.withRangePosition(volume, pitch)` to select
fractions independently: `0` chooses `min`, `1` chooses `max`, and `Float.NaN` retains random variation for that axis.
Finite values outside 0..1 extrapolate, including descending ranges; for example, position `2` on volume 20..80
produces 140 percent before source multipliers. Negative results become zero, range results stay finite, and infinite
input positions are rejected. Context scaling, category changes and delayed nodes preserve the selected fractions.
These positions are caller controls, not new JSON keys or CFG settings.
Missing running/landing/climbing events fall back through the Presence Footsteps movement states; missing `stand`
stays silent. Newer vanilla sounds referenced by the bundled definitions use similar sounds available in 1.7.

Swimming reads the `swim` event in `swim_water.json` or `swim_lava.json`. Its volume range is interpolated by movement
intensity, while pitch retains random variation. Override these files in a pack or use local
`config/pastfootsteps/acoustics/swim_water.json` and `swim_lava.json`. An `empty` definition intentionally mutes PF's
owned channel; use the Swimming sounds toggle to hand that channel back to vanilla or Dynamic Surroundings.

Relative sound names beginning with `pf_presence.` or `equipment.` select the ordinary `presencefootsteps` sound
event for your own player and its `presencefootstepsmono` counterpart for other players and mobs. Mono recordings
allow those nearby sounds to follow the actor's position. If no mono event is registered, playback falls back to
the ordinary event. This also applies to delayed contacts and equipment/rain accents.

Packs can override `assets/presencefootstepsmono/sounds.json` and its audio files for remote actors. Supply mono
recordings there. Explicitly namespaced acoustic sound names, such as `presencefootsteps:custom.step` or
`example:custom.step`, keep that exact namespace for all actors; use a relative PF name when automatic selection
is desired. Existing native 1.7 sound names retain their normal routing.

`variator.json` remains an empty compatibility placeholder. In the cached MIT PF source it configured its old motion
generator; this port uses the Forge movement controls and its contact tracker instead. It is not read, so values placed
there do not silently create a second stride, run, or idle policy.

Delayed acoustics accept `delay` in milliseconds, either a number or `{ "min": 30, "max": 60 }`,
and the legacy `delay_min`/`delay_max` pair. They can wrap an `acoustic` child or use the short `name`
form. Inner delayed nodes override outer delay bounds. Simultaneous siblings keep their own options;
samples and variation are selected once before queueing. Delays are capped at 60 seconds.

Timing uses a monotonic clock, dispatched on client ticks, with MIT's `max^0.75` millisecond early
window and `max / 1.5` late tolerance. PF allows at least 50 ms of late grace for its tick dispatcher.
Stale layers are discarded after a stall. Java integrations can use
`AcousticContext.withLateSoundSkipping(false)` to retain late acoustic layers. As a PF JSON extension,
a delayed node's `skippable` key exposes that same legacy presence-only option: either `true` or
`false` keeps late layers. Omit it for normal expiry. It does not bypass actor validity, queue limits,
pause/reload cancellation, or the separate distance-arrival expiry.

## Entity and gait profiles

Bundled entity profiles cover `Player`; bipedal `Zombie`, `Skeleton`, `PigZombie`, `Enderman`, `Witch`, `Villager`,
`VillagerGolem`, `Giant`, `LavaSlime` (magma cube), `Slime` and `Chicken`; four-legged `Creeper`, `Cow`, `MushroomCow`,
`Pig`, `Sheep`, `Wolf`, and `Ozelot`; and
`EntityHorse`. The horse gait uses diagonal running contact groups for a trot, with longer strides at high speed. Paw and hoof gaits use different
front/rear geometry, and every supplied profile retains child stride scaling. Other registered entities keep native
sounds unless explicitly matched, including spiders, bats, squids, ghasts, and flying bosses.

`Giant` and `LavaSlime` use the biped mapping found in both audited MIT branches. Magma cubes use
PF's existing physical takeoff/landing contacts; there is no separate hopping clock. Regular `Slime`
uses the same contact path as a PF addition beyond the audited MIT mappings. Native slime and magma
cube jump, squish, hurt and death sounds remain active. No footprint style is assigned to either.
Giants retain the existing square footprint rule. The profiles use nominal 1.7 widths of 3.6 and
0.6 blocks respectively, so larger magma cubes receive the existing size-sensitive pitch adjustment.
Profile volume/stride values are Forge-adapter tuning, not additional upstream sound assets.

IDs are the exact, case-sensitive Minecraft 1.7 entity names, such as `MushroomCow`, `Ozelot`, and `EntityHorse`,
rather than modern names such as `minecraft:cow`. `Player` is a special alias for players.

Resource-pack definitions live at `assets/pastfootsteps/config/entities.json` and `gaits.json`. Local overrides live at
`config/pastfootsteps/profiles/entities.json` and `gaits.json`, and take priority over resource packs. Ordinary
toggles, sliders and volumes remain in `config/pastfootsteps/pastfootsteps.cfg` and the Forge configuration screen.

For example, this local entity file adjusts cows and opts a known four-legged modded creature into the same gait:

```json
{
  "schemaVersion": 1,
  "entities": {
    "Cow": {
      "gait": "quadruped",
      "strideMultiplier": 0.9,
      "childScale": 0.55,
      "runSpeed": 0.18,
      "volume": 0.75
    },
    "example.Creature": { "gait": "quadruped" },
    "example_creature_class": {
      "parent": "example.Creature",
      "selector": { "class": "example.entity.EntityCreature" }
    },
    "baby_cow": {
      "parent": "Cow",
      "selector": { "entityId": "Cow", "age": "child" },
      "strideMultiplier": 0.75
    }
  }
}
```

Entries without `selector` keep the original behavior: the entry key is an exact, case-sensitive entity ID. An
explicit selector requires `entityId`, `class`, or both. It may also restrict `age` to `adult` or `child`,
`horseType` to `horse`, `donkey`, `mule`, `zombie`, or `skeleton`, and `skeletonType` to `normal` or `wither`.
Class names are loaded without class initialization during reload and cached; an unavailable optional class leaves
that selector dormant and produces a diagnostic. A class selector is the only way to opt an otherwise unknown mob
into tracking. Past Footsteps never assigns a generic gait merely because an entity is living or ground-based.

`parent` inherits gait and locomotion tuning from another merged profile. Fields present on the child replace the
inherited value. Selectors and priority are local to each entry and are not inherited. Entries still replace earlier
entries with the same profile ID as a whole. Missing parents and inheritance cycles reject the complete resource
layer; an invalid local layer retains its cached last-valid version. An entry containing only `"enabled": false`
removes that profile and creates an exact-ID opt-out for its key, so a broad class selector cannot claim that entity.
`volume: 0` deliberately mutes a matched profile while retaining its contact tracking.
Mapped modded creatures may set `referenceWidth` to opt into size-based audio pitch; leaving it unspecified keeps
pitch neutral. The field describes adult nominal width and is separate from `childScale`, which only changes gait
stride.

When several selectors match a live entity, resolution is deterministic in this order: an exact `entityId` match,
more selector constraints, the nearest matching class in the inheritance chain, higher `priority`, then the
lexically smaller profile ID. This makes a legacy exact entity entry stronger than a broad class selector. Use an
exact-ID selector for a child or horse/skeleton variant that must override a bundled exact profile.

| Entity field | Default | Meaning |
| --- | --- | --- |
| `parent` | None | Profile ID whose gait and tuning this entry inherits. |
| `selector` | Exact entry key | Explicit entity ID, class, age, horse type, or skeleton type match. |
| `priority` | `0` | Tie-breaker from `-10000` to `10000` after selector specificity. |
| `gait` | Required without `parent` | Gait ID, including any supplied custom definition. |
| `strideMultiplier` | `1` | Multiplies the full cycle distance, which starts at twice the CFG player stride. |
| `childScale` | `0.5` | Further stride multiplier for child entities. Foot offsets already use actual entity width. |
| `runSpeed` | `0.22` | Running threshold in blocks per client tick, with smoothing and hysteresis. Sprinting also selects running. |
| `volume` | `1` | Multiplier applied with master and player/passive/hostile source volume. |
| `referenceWidth` | `0` | Adult nominal bounding-box width in blocks for audio pitch correction; `0` leaves pitch neutral. Inherited by child profiles and independent of `childScale`. |
| `materialMap` | `""` | Named per-entity block map; inherited, with an explicit empty string restoring shared mappings. |
| `enabled` | `true` | Whether the entry participates in resolution. |

A gait defines named feet and their contact phase within one distance-based cycle. Positive lateral offset is
actor-left, negative is actor-right; positive forward is toward the front. Offsets are multiplied by the actor's
current width. This example supplies a biped gait with a wider stance:

```json
{
  "schemaVersion": 1,
  "gaits": {
    "wide_biped": {
      "walk": [
        { "footId": "left", "phase": 0.5, "lateral": 0.35, "forward": 0 },
        { "footId": "right", "phase": 1, "lateral": -0.35, "forward": 0 }
      ]
    }
  }
}
```

`run` may supply a separate contact array; omitting it uses `walk`. Both arrays must contain the same unique foot
IDs, with 1 to 16 feet. Identical phases make a simultaneous contact group, such as diagonal feet when a cow trots.
Distinct phases must be at least `0.000001` apart, including across the cycle boundary; phases range from
`0.000001` to `1`. Gait changes between walking and running occur at cycle boundaries. One landing groups all feet
and scales its sound contribution, rather than multiplying the volume by the number of feet.

An optional gait-level `fastStride` object lengthens ground contact cycles as movement speeds up:

```json
"fastStride": { "startSpeed": 0.316227766, "fullSpeed": 0.6, "multiplier": 1.5 }
```

Speeds are horizontal blocks per client tick, using the tracker's smoothed displacement. Below `startSpeed`,
stride length is unchanged; at or above `fullSpeed`, it is multiplied by `multiplier`. Between them, a smooth
curve preserves the current contact phase and foot order. This is additional to the entity's `strideMultiplier`
and ordinary running adjustment. Swimming and climbing do not use this correction. Both speeds must be between
0 and 16, with `fullSpeed` strictly greater than `startSpeed`; `multiplier` must be between 1 and 8. Omit the
object (or use multiplier 1) to retain constant stride length. Gait layers replace the entire gait definition,
so include its `walk` and `run` arrays in an override.

Only the bundled `horse` gait enables this by default. Its high-speed correction follows the intent of MIT PF's
horse adjustment, adapted to our four-foot contact cycle rather than copying the upstream distance multiplier.
All horse variants sharing this gait use it, whether ridden or moving independently. Custom gait users can
opt in without Java code. The correction changes cadence, not movement speed or animation.

The machine-readable schemas are [entities.schema.json](schemas/entities.schema.json) and
[gaits.schema.json](schemas/gaits.schema.json).

## Entity support surfaces

`assets/pastfootsteps/config/supports.json` maps exact entity IDs with a verified collidable top surface. The
bundled `Boat` mapping is deliberately the only default. Local overrides use
`config/pastfootsteps/profiles/supports.json`. See [Entity support surfaces](Support-surfaces.md) for the schema,
collision requirement, and the small API for concrete mod entity adapters.

Use F3+T or `/pf reload` to reload resources and local profile files. Files are strict JSON: duplicate keys, unknown fields,
unsupported schema versions and invalid values produce diagnostics in the game log. Invalid upper resource layers
leave valid lower data available. If an existing local file becomes malformed, its last valid layer remains active;
deleting the file deliberately removes that override. A missing gait reference never silently assigns a generic gait.
Use `/pf report` to export the effective configuration and recent diagnostics.

For tuning, enable **Debug HUD** and **Show contact markers** under General. Cyan markers identify left feet and
orange markers identify right feet. The HUD follows a targeted tracked entity, then the nearest tracked entity,
then the local player. Markers expire after one second and are capped independently of sound playback.

Client-only multiplayer infers contacts from observed movement. Nearby native sound packets lack entity IDs in 1.7;
when a supported and an unsupported creature overlap, conservative attribution can retain a duplicate native step.
Optional server synchronization can provide remote contact timing when Past Footsteps is installed on the server;
see [Server support](Server-support.md).

For a registry-wide view of effective mappings and missing acoustic definitions, use `/pf mappings all` or `/pf mappings unmapped`. Optional named maps and report limitations are covered in [Diagnostics](Diagnostics.md).
