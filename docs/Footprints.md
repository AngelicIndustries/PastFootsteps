# Footprints

PF generates prints from its resolved foot contacts. Sounds and prints use the same named feet, gait phase and support surface. Print density filters those contacts; it never introduces another stride clock. Prints continue when surface audio is disabled or muted, including while sneaking. Swimming, climbing, takeoff, stationary and shuffle contacts do not leave prints.

Open **Mods > PastFootsteps > Config > Footprints** for ordinary settings:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Enable prints independently of audio. The General master switch still disables all PF effects. |
| `maximum` | `2000` | Retained print cap, from 0 to 10000. Zero disables prints. Oldest prints are removed first. |
| `lifetime` | `200` | Default lifetime in ticks, from 1 to 12000. Twenty ticks is one second. |
| `distance` | `32` | Maximum rendering distance in blocks, from 8 to 96. Actor discovery has its own range. |
| `opacity` | `0.32` | Default opacity from 0 to 1, before explicit surface overrides. |
| `size` | `1` | Visual size multiplier from 0.1 to 4. Does not change foot positions or gait. |
| `density` | `1` | Fraction of contacts retained from 0 to 1. Selection is stable for each contact. |
| `rainLifetime` | `0.4` | Lifetime multiplier when the print is created in exposed rain. |
| `snowLifetime` | `0.6` | Lifetime multiplier when created in exposed snowfall. Both weather factors range from 0.01 to 1. |

An editor [JSON schema](schemas/footprints.schema.json) is available for visual rules.

## Entity and surface rules

Resource packs override `assets/pastfootsteps/config/footprints.json`. Local rules go in `config/pastfootsteps/profiles/footprints.json`. Run `/pf reload` after editing. Layers apply from bundled defaults through resource packs to the local file. Each entry replaces the same key. Invalid local edits retain the last valid local layer; deleting the file removes it.

```json
{
  "schemaVersion": 1,
  "entities": {
    "Player": { "style": "LOWRES_SQUARE", "width": 0.38, "length": 0.7, "density": 1 },
    "Cow#child": { "style": "LOWRES_SQUARE", "width": 0.28, "length": 0.32 },
    "Creeper": { "enabled": false }
  },
  "soundTypes": {
    "sand": { "opacity": 0.5, "lifetime": 300 },
    "grass": { "enabled": false }
  },
  "blocks": {
    "minecraft:stone": { "opacity": 0.25 },
    "minecraft:wool@14": { "enabled": false }
  }
}
```

Entity keys use the exact registered IDs used by [entity profiles](Resource-packs.md#entity-and-gait-profiles). Optional `#child` and `#adult` keys override that actor's visual rule. Adding a visual rule alone does not enable a new creature's gait. Both a matching contact profile and an enabled footprint rule are needed.

`width` and `length` multiply the actor's actual width and the CFG size. Babies therefore use their current smaller body size automatically. Width ranges from 0.01 to 2 and length from 0.01 to 3. Gait lateral/forward offsets control stance. Available atlas styles are `SHOE`, `SQUARE`, `HORSESHOE`, `BIRD`, `PAW`, `SQUARE_SOLID` and `LOWRES_SQUARE`. All bundled entity rules use `LOWRES_SQUARE`, the same footprint used by AET. Omitting `style` also selects `LOWRES_SQUARE`; other atlas styles remain available through explicit overrides.

Block-and-metadata rules take priority over whole-block rules, then vanilla step-sound types. An explicit disabled rule blocks prints even when a lower-priority rule permits them. Omitted surface opacity/lifetime uses the CFG default. Default types are sand (300 ticks, 0.4 opacity), snow (100, 0.48), grass (200, 0.32) and gravel (250, 0.28). All other surfaces need a rule.

## Rendering

Print polygons are clipped to their collision support while retaining their UV coordinates. They fade during the final 30% of their lifetime and are invalidated when the supporting block or metadata changes. Unloaded chunks are never loaded for a print. Rendering is bounded by the cap and distance setting and samples light per print. Moving entity supports produce contacts and sounds but do not receive attached decals.

The footprint atlas comes from the verified MIT-era Dynamic Surroundings assets. The consumer, geometry and renderer are built around PF's contact engine rather than AET's former footprint timing and renderer.
