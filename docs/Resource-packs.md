# Resource packs

PastFootsteps reads its material data from the `presencefootsteps` namespace for the sound assets and JSON mappings described below. Modern block-state/tag selectors and legacy
LiteLoader CFG packs need conversion to 1.7 block names and metadata. Files from all enabled packs are merged in pack order; entries from higher packs
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

`UNASSIGNED` explicitly clears a block association and continues to primitive fallback for ordinary surfaces and
`bigger` lookups. It does not revive a less-specific or lower-pack block entry. For supplementary substrates such
as `foliage` or `wet`, it returns no association; the resolver may still use its usual wet-layer default.
`NOT_EMITTER` is an explicit non-emitter and does not fall back to a primitive acoustic. On the `carpet` substrate,
non-emitting overlays still allow the underlying surface to be considered. `VANILLA` requests native step playback
for a normal surface rather than a PF acoustic. `MESSY_GROUND` on the `.messy` substrate enables the foliage entry strategy described below.

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
then the shared primitive map where applicable. An entity-map generic match therefore overrides a shared metadata
match. Explicit `UNASSIGNED` bypasses the shared block map and proceeds to primitive fallback; omit an entry to use
the shared block mapping. `NOT_EMITTER`, `VANILLA` and explicit empty acoustic definitions retain their distinct meanings.
Maps apply to normal contacts, wet layers, foliage, ladders and snow/carpet overlays. Explicit materials provided
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

Both lookups honor metadata and the actor's `materialMap`. Set `.messy` to `NOT_EMITTER` to disable entry/swimming
vegetation while keeping ordinary contact rustles, or `.foliage` to `NOT_EMITTER` to silence that vegetation layer
entirely. Young crops with non-emitting `.foliage` entries remain silent even when marked messy. Missing `.messy`
does not disable regular foot-contact foliage, such as rails and vines. Ordinary contacts sample the plant at the
resolved foot position, excluding solid blocks and the supporting overlay itself; entry detection samples a bounded horizontal area at foot height. It does not rustle plants
only touching the actor's head. Boots and sprinting follow the rules in [Configuration](Configuration.md).

Overlapping comma-separated foliage names, simultaneous feet, and entry/contact detection share per-actor deduplication.
An entry rustle also waits at least two ticks after the last foliage playback to avoid rapid boundary chatter.
Explicit layers authored inside surface acoustic definitions are preserved.

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
and `VillagerGolem`; `Chicken`; four-legged `Creeper`, `Cow`, `MushroomCow`, `Pig`, `Sheep`, `Wolf`, and `Ozelot`; and
`EntityHorse`. The horse gait uses diagonal running contact groups for a trot. Paw and hoof gaits use different
front/rear geometry, and every supplied profile retains child stride scaling. Other registered entities keep native
sounds unless explicitly matched, including spiders, slimes, magma cubes, bats, squids, ghasts, and flying bosses.
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
