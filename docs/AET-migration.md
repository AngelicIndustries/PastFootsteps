# Migrating walking features from An Extra Touch

Install Past Footsteps on the client for material footsteps, footwear and armor movement, rain footstep accents and footprints. AET and PF work independently. A server installation of PF is optional and improves remote motion information; see [server support](Server-support.md).

AET retains armor equip/unequip, held-item selection, swing/block/bow sounds, thunder, all water effects, ambience, camera behavior, steam/lava jets and grass trampling. It no longer emits armor/rain walking packets. Old AET packet IDs 0 and 1 remain harmless decoders, and explosion shake keeps ID 2.

Settings are not imported automatically. Keep ordinary PF options in `config/pastfootsteps/pastfootsteps.cfg`; structured overrides belong in `config/pastfootsteps/profiles/`.

| Former AET setting | PF equivalent |
| --- | --- |
| `footprintsEnabled`, particle cap, lifetime/opacity/weather | Footprints menu. Zero cap now disables prints; there is no unlimited setting. |
| Footprint stride and stance | Entity `strideMultiplier` and gait foot lateral/forward positions. One gait controls sound and print timing. |
| Foot size and baby overrides | `footprints.json` entity width/length and optional `#child` rules; actual body width supplies baby scaling. |
| Footprint entity filters | Enabled/disabled visual entity rules, in addition to a supported contact profile. |
| Footprint sound-type/block filters and overrides | `footprints.json` soundTypes and blocks. Exact metadata overrides whole blocks, which override sound types. |
| `armorSoundsEnabled`, `armorSoundVolume` | Equipment menu footwear/body toggles and volumes. AET's retained settings now control equip only. |
| Armor mode/default/category/item/entity overrides | Equipment menu and `profiles/equipment.json`. |
| Rain footstep enable, volume, entity filter | Rain menu. |

See [Footprints](Footprints.md), [Equipment](Equipment.md), and [Resource packs](Resource-packs.md) for field formats.

With older AET clients, PF suppresses their walking accents and print creation for actors whose contacts it owns, while keeping equip detection. Unsupported actors retain that older mod's behavior. Using the updated AET removes its obsolete walking engine entirely. Turning off one PF consumer does not reactivate the same legacy AET consumer for a tracked actor.
