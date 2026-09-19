# Configuration

Open **Mods > PastFootsteps > Config**. Ordinary toggles, volumes and budgets use Forge CFG at `config/pastfootsteps/pastfootsteps.cfg`; structured mappings use JSON. Save the GUI with Done. Edit the CFG with the game closed to avoid overwriting edits. `/pf reload` reloads JSON/resource definitions; it does not reread CFG from disk.

| Menu | Controls |
| --- | --- |
| General | Master enable; other players and profiled mobs; actor discovery range (24 blocks), tracked actor cap (64), sound sample cap (32 per tick); diagnostic HUD/markers. |
| Volume | Master (1), own player (1), other players (0.7), passive mobs (0.7), hostile mobs (0.7), foliage (1), shallow-water layer (1) and swimming (1), each from 0 to 2. Running adjustment defaults to 0, range -1 to 1. |
| Effects | Material sound replacement, foliage, shallow-water audio, swimming, jump/landing, stopping/reversal shuffles, material-defined stationary creaks and size-sensitive pitch. All default on. |
| Movement | Base distance between player footsteps (1.2 blocks) and ladder spacing (0.65 blocks). Entity/gait profiles determine other creatures' cycles. |
| Footwear and armor movement | Independent boots/body toggles and volumes, mixed-armor mode and item/entity categories. See [Equipment](Equipment.md). |
| Rain footsteps | Exposed-rain accents, volume and entity allow/exclusion list. See [Equipment](Equipment.md). |
| Footprints | Visual enable, cap, lifetime, draw distance, size, density, opacity and weather lifetime. See [Footprints](Footprints.md). |
| Optional server support | Server-owned stream enable, range, actor and packet limits. Client-only users can leave these alone. See [Server support](Server-support.md). |

Disabling the General master restores the prior footstep engine and disables all PF effects. Disabling material sounds restores native/DS surface audio while equipment and prints retain their own switches. Master volume zero mutes PF audio without disabling footprints. Disabling footprints has no effect on sound timing. Unsupported mobs always keep native sounds.

`effects.foliage` and `volume.foliage` control the additional vegetation layer. Barefoot movement rustles plants at
foot contacts; wearing any boots suppresses walking rustles, while sprinting restores the contact layer. Plants
marked `MESSY_GROUND` can also rustle when entered between footsteps, provided the actor is moving, not sneaking,
and not wearing boots. Standing still adds no entry rustle. Swimming uses marked vegetation at the swimmer's feet.
The same foliage acoustic is emitted at most once per actor tick, including simultaneous feet and entry detection.
These rules affect the added foliage layer; surface acoustics can still contain their own authored vegetation sounds.

Swimming uses dedicated water/lava acoustics, with stroke volume following movement in any direction, including vertical movement. Other swimmers are quieter than your own strokes. Supported shallow-water wading keeps the surface and wet layers; swimming skips those dry surface sounds. `effects.swimming=false` restores vanilla or Dynamic Surroundings swimming audio, while `volume.swimming=0` mutes PF's owned swimming channel. Underwater ambience and AET's water-entry splashes remain separate.

`effects.jumpAndLand` controls takeoff and landing audio. Standing takeoff uses a shuffle; moving takeoff and soft landing use one foot's sound. Running soft landings use the running acoustic, and drops over 0.9 blocks use a grouped hard landing. Identical materials within an impact play once; landing across different materials can play both. Hard landings remain audible while sneaking. Disabling this setting retains physical landing contacts and footprints, and leaves native fall sounds available.

PF movement samples obey Minecraft's Players, Friendly Creatures or Hostile Creatures slider according to their source. This includes material sounds, swimming, equipment, rain and delayed layers. PF's own source multipliers apply in addition. Other mods' playback and shared sound definitions retain their original categories.

`volume.ownPlayer` affects your own movement sounds without changing other actors. `volume.runningBoost` smoothly adjusts volume with movement speed, including vertical movement: `0` preserves constant volume. Positive values reserve headroom by softening walking while running retains the configured volume: `0.5` gives running up to 50% more gain than walking, and `1` doubles it. This preserves the contrast for samples that already reach Minecraft's volume ceiling. Negative values soften running instead: `-0.5` reduces its gain by up to 50%. It defaults to `0`, matching MIT PF. All PF movement layers share this adjustment.

`effects.sizePitch` defaults on. It compares actual width with the entity profile's adult `referenceWidth`: babies sound higher, enlarged actors lower. Adult vanilla mobs keep their normal pitch. Profiles without a reference width stay neutral; disable the toggle to keep normal pitch at every size. This does not change gait timing or footprint size.

JSON overrides are documented in [Resource packs](Resource-packs.md), [Footprints](Footprints.md), [Equipment](Equipment.md) and [Support surfaces](Support-surfaces.md). Files are not copied from AET automatically. Use `/pf status` for the focused actor, `/pf reload` after JSON edits, and `/pf report` to save `config/pastfootsteps/diagnostics/latest-report.json` for debugging.
