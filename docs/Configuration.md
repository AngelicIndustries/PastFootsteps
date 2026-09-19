# Configuration

Open **Mods > PastFootsteps > Config**. Ordinary toggles, volumes and budgets use Forge CFG at `config/pastfootsteps/pastfootsteps.cfg`; structured mappings use JSON. Save the GUI with Done. Edit the CFG with the game closed to avoid overwriting edits. `/pf reload` reloads JSON/resource definitions; it does not reread CFG from disk.

| Menu | Controls |
| --- | --- |
| General | Master enable; other players and profiled mobs; actor discovery range (24 blocks), tracked actor cap (64), sound sample cap (32 per tick); diagnostic HUD/markers. |
| Volume | Master (1), other players (0.7), passive mobs (0.7), hostile mobs (0.7), foliage (1), shallow-water layer (1) and swimming (1). All are multipliers from 0 to 2. |
| Effects | Material sound replacement, foliage, shallow-water audio, swimming, jump/landing, stopping/reversal shuffles and material-defined stationary creaks. All default on. |
| Movement | Base distance between player footsteps (1.2 blocks) and ladder spacing (0.65 blocks). Entity/gait profiles determine other creatures' cycles. |
| Footwear and armor movement | Independent boots/body toggles and volumes, mixed-armor mode and item/entity categories. See [Equipment](Equipment.md). |
| Rain footsteps | Exposed-rain accents, volume and entity allow/exclusion list. See [Equipment](Equipment.md). |
| Footprints | Visual enable, cap, lifetime, draw distance, size, density, opacity and weather lifetime. See [Footprints](Footprints.md). |
| Optional server support | Server-owned stream enable, range, actor and packet limits. Client-only users can leave these alone. See [Server support](Server-support.md). |

Disabling the General master restores the prior footstep engine and disables all PF effects. Disabling material sounds restores native/DS surface audio while equipment and prints retain their own switches. Master volume zero mutes PF audio without disabling footprints. Disabling footprints has no effect on sound timing. Unsupported mobs always keep native sounds.

Swimming uses dedicated water/lava acoustics, with stroke volume following movement in any direction, including vertical movement. Other swimmers are quieter than your own strokes. Supported shallow-water wading keeps the surface and wet layers; swimming skips those dry surface sounds. `effects.swimming=false` restores vanilla or Dynamic Surroundings swimming audio, while `volume.swimming=0` mutes PF's owned swimming channel. Underwater ambience and AET's water-entry splashes remain separate.

Sound samples obey Minecraft's Players volume, as in the original PF sound library. PF's separate other-player/passive/hostile multipliers provide actor-specific control. These do not move events into Minecraft's separate mob sound categories.

JSON overrides are documented in [Resource packs](Resource-packs.md), [Footprints](Footprints.md), [Equipment](Equipment.md) and [Support surfaces](Support-surfaces.md). Files are not copied from AET automatically. Use `/pf status` for the focused actor, `/pf reload` after JSON edits, and `/pf report` to save `config/pastfootsteps/diagnostics/latest-report.json` for debugging.
