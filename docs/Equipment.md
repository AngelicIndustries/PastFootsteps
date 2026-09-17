# Equipment and rain accents

PastFootsteps consumes the same resolved contacts as surface audio. It has no independent distance counter, so local players, observed players, and supported mobs share the same contact timing.

The Forge configuration screen exposes separate controls for boot-on-ground accents, chest/leg armor accents, and rain accents. Armor eligibility defaults to `Player`, `Zombie`, `Skeleton`, and `PigZombie`, preserving An Extra Touch's movement default. Rain uses an empty blacklist by default, so every actor already selected by PastFootsteps can receive it. Rain sounds require an exposed contact and scale with the world rain strength. A rainy biome, wet block, or sheltered actor alone does not trigger the accent.

Body armor uses chest and leg slots only. In `priority` mode it selects heavy, crystal, slime, medium, then light. In `mixed` mode it plays both distinct chest and leg categories. Boots never enter body selection; they use the separate footwear channel. A simultaneous contact group produces one body accent, including grouped takeoff and landing, while each actual boot contact retains its own footwear accent.

Vanilla armor materials map to light (leather), medium (chain), heavy (iron and gold), and crystal (diamond). Unknown `ItemArmor` materials use **Default Armor Category**, initially heavy. Non-armor items are silent unless an override explicitly maps them.

## CFG controls

| Menu / key | Default | Meaning |
| --- | --- | --- |
| Equipment / `footwearEnabled` | `true` | Enable boot accents. |
| Equipment / `footwearVolume` | `0.3` | Boot volume multiplier, from 0 to 2. |
| Equipment / `bodyArmorEnabled` | `true` | Enable chest/leg accents. |
| Equipment / `armorMode` | `priority` | Select one category, or use `mixed` for distinct chest/leg categories. |
| Equipment / `armorVolume` | `0.3` | Body volume multiplier, from 0 to 2. |
| Equipment / `defaultArmorCategory` | `heavy` | Fallback for unknown armor materials, or `none` to silence them. |
| Equipment / `armorEntities` | Player, Zombie, Skeleton, PigZombie | Exact entity allow list for both armor channels. |
| Rain / `rainEnabled` | `true` | Enable exposed-rain contact accents. |
| Rain / `rainVolume` | `0.15` | Volume multiplier from 0 to 2, also scaled by rain strength. |
| Rain / `rainEntities` | Empty list | Exact entity filter. |
| Rain / `rainEntityListIsBlacklist` | `true` | Treat the filter as exclusions; false makes it an allow list. |

Sneaking, swimming, climbing and stationary contacts are silent for these consumers. When the separate rain layer owns an exposed contact, the generic wet surface layer does not stack on it, including when rain accents are disabled.

## Modded item overrides

Resource packs layer `assets/pastfootsteps/config/equipment.json` in normal resource-pack order. A local override at `config/pastfootsteps/profiles/equipment.json` is applied last. The file is strict JSON with schema version 1. Each item entry is structured so it can be disabled by a higher layer:

```json
{
  "schemaVersion": 1,
  "items": {
    "example:resonant_boots": { "category": "crystal" },
    "example:silent_boots": { "category": "none" },
    "etfuturum:elytra": { "enabled": false }
  }
}
```

Allowed categories are `light`, `medium`, `heavy`, `crystal`, `slime`, `elytra`, and `none`. `elytra` is retained for compatibility with existing AET mappings but has no bundled movement sample, so it is intentionally silent for locomotion. Map the item to `light` for a cloth accent; its equip sound remains in AET. Malformed upper layers are ignored without discarding valid lower layers.

## Recording notices

`equipment.rain` reuses the bundled Presence Footsteps stereo-fixed water event. The armor event recordings and their recording-specific attribution are described in [EQUIPMENT-SOURCE-NOTICE.txt](../src/main/resources/assets/pastfootsteps/EQUIPMENT-SOURCE-NOTICE.txt). In particular, the light/medium recordings retain CC BY attribution and the heavy walk/run recordings retain the LordSane attribution and permission caveat. They are not blanket-labelled MIT.

An editor [JSON schema](schemas/equipment.schema.json) is available for equipment rules.
