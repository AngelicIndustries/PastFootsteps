# Configuration

Open **Mods > PastFootsteps > Config**. Ordinary toggles, volumes and budgets use Forge CFG at `config/pastfootsteps/pastfootsteps.cfg`; structured mappings use JSON. Save the GUI with Done. Edit the CFG with the game closed to avoid overwriting edits. `/pf reload` reloads JSON/resource definitions; it does not reread CFG from disk.

| Menu | Controls |
| --- | --- |
| General | Master enable; other players and profiled mobs; actor discovery range (24 blocks), tracked actor cap (64), sound sample cap (32 per tick); diagnostic HUD/markers. |
| Volume | Master (1), own player (1), other players (0.7), passive mobs (0.7), hostile mobs (0.7), foliage (1), shallow-water layer (1) and swimming (1), each from 0 to 2. Running adjustment defaults to 0, range -1 to 1. |
| Effects | Material sound replacement, foliage, shallow-water audio, swimming, jump/landing, stopping/reversal shuffles, material-defined stationary creaks size-sensitive pitch and distance-based sound delay. All default on. |
| Movement | Base distance between player footsteps (1.2 blocks) and ladder spacing (0.65 blocks). Entity/gait profiles determine other creatures' cycles. |
| Footwear and armor movement | Independent boots/body toggles and volumes, mixed-armor mode and item/entity categories. See [Equipment](Equipment.md). |
| Rain footsteps | Exposed-rain accents, volume and entity allow/exclusion list. See [Equipment](Equipment.md). |
| Footprints | Visual enable, cap, lifetime, draw distance, size, density, opacity and weather lifetime. See [Footprints](Footprints.md). |
| Optional server support | Server-owned stream enable, range, actor and packet limits. Client-only users can leave these alone. See [Server support](Server-support.md). |

Disabling the General master restores the prior footstep engine and disables all PF effects. Disabling material sounds restores native/DS surface audio while equipment and prints retain their own switches. Master volume zero mutes PF audio without disabling footprints. Disabling footprints has no effect on sound timing. Unsupported mobs always keep native sounds.

`effects.foliage` and `volume.foliage` control the additional vegetation layer. Boots suppress the low foot-contact
layer while walking; sprinting restores it. Marked `MESSY_GROUND` plants can also create foot-level entry rustles
when moving without boots or sneaking. Vegetation touching the legs, torso or head above the footwear region
still rustles while moving with boots. This includes tall grass and vines touched only by the head.

Body rustles use the existing gait contacts plus entry detection, without another stride timer. Standing still
and sneaking do not produce body rustles. Body scanning uses bounded samples, each plant's current bounds and
the actor's size. The footwear region is the lower quarter of the actor's height, capped at 0.35 blocks.
Swimming can layer marked low vegetation and body vegetation on its existing strokes. Foot, body, swimming and
entry rustles share per-actor acoustic deduplication. Leaf/vine body contacts use pure leaf rustling without the
dirt-step layer. Normal ground/climbing sounds remain unchanged.

Swimming uses dedicated water/lava acoustics, with stroke volume following movement in any direction, including vertical movement. Other swimmers are quieter than your own strokes. Supported shallow-water wading keeps the surface and wet layers; swimming skips those dry surface sounds. `effects.swimming=false` restores vanilla or Dynamic Surroundings swimming audio, while `volume.swimming=0` mutes PF's owned swimming channel. Underwater ambience and AET's water-entry splashes remain separate.

`effects.jumpAndLand` controls takeoff and landing audio. Standing takeoff uses a shuffle; moving takeoff and soft landing use one foot's sound. Running soft landings use the running acoustic, and drops over 0.9 blocks use a grouped hard landing. Identical materials within an impact play once; landing across different materials can play both. Hard landings remain audible while sneaking. Disabling this setting retains physical landing contacts and footprints, and leaves native fall sounds available.

PF movement samples obey Minecraft's Players, Friendly Creatures or Hostile Creatures slider according to their source. This includes material sounds, swimming, equipment, rain and delayed layers. PF's own source multipliers apply in addition. Other mods' playback and shared sound definitions retain their original categories.

`volume.ownPlayer` affects your own movement sounds without changing other actors. `volume.runningBoost` smoothly adjusts volume with movement speed, including vertical movement: `0` preserves constant volume. Positive values reserve headroom by softening walking while running retains the configured volume: `0.5` gives running up to 50% more gain than walking, and `1` doubles it. This preserves the contrast for samples that already reach Minecraft's volume ceiling. Negative values soften running instead: `-0.5` reduces its gain by up to 50%. It defaults to `0`, matching MIT PF. All PF movement layers share this adjustment.

`effects.sizePitch` defaults on. It compares actual width with the entity profile's adult `referenceWidth`: babies sound higher, enlarged actors lower. Adult vanilla mobs keep their normal pitch. Profiles without a reference width stay neutral; disable the toggle to keep normal pitch at every size. This does not change gait timing or footprint size.

`effects.distanceDelay` defaults on. Movement sounds farther than 10 blocks from the listening entity arrive after `floor(distance / 2)` ticks: about 0.3 seconds at 12 blocks or 0.6 seconds at 24 blocks. Disable it for immediate arrival at every distance; material-defined delayed layers still apply. The 1.7 adapter uses the listening entity's eye position, including a spectated entity, rather than an offset third-person camera. Near sounds, including your own footsteps, remain immediate apart from authored layers.

Delayed layers and distance arrivals use bounded queues and discard stale sounds after stalls. Actor removal, teleport, world changes and resource reloads cancel pending sounds. Singleplayer pause clears pending audio so it does not resume after the menu closes. The normal per-tick sound budget also applies when delayed sounds start.

JSON overrides are documented in [Resource packs](Resource-packs.md), [Footprints](Footprints.md), [Equipment](Equipment.md) and [Support surfaces](Support-surfaces.md). Files are not copied from AET automatically. Use `/pf status` for the focused actor, `/pf reload` after JSON edits, and `/pf report` to save `config/pastfootsteps/diagnostics/latest-report.json` for debugging.

Use `/pf mappings all` or `/pf mappings unmapped` to inspect registered block mappings. See [Diagnostics](Diagnostics.md) for named maps, output files and report limitations.

`/pf acoustics` exports loaded sound definitions as a ZIP for inspection and editing. See [Acoustic export](Diagnostics.md#acoustic-export).

Flowers, small mushrooms and pumpkin/melon stems now have quiet plant rustles. Flowers, mushrooms and young stems use the low foliage channel, so boots suppress walking rustles; the existing sprint exception remains. Taller foliage and raised plants touching the body can still rustle with boots.
