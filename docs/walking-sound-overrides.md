# Experimental walking sound overrides

The optional Sound Manager mod can create these overrides; PF loads them independently. This is a narrow first authoring slice, not the final shared sound-pack format.

Add `assets/pastfootsteps/sound_editor/walking.json` to a normal Minecraft 1.7.10 resource pack:

```json
{
  "schemaVersion": 1,
  "walking": {
    "stone": "minecraft:step.gravel"
  }
}
```

Keys are existing PF acoustic IDs; values are installed, explicitly namespaced sound events. Higher-priority packs replace individual entries. Invalid layers and missing sounds retain lower valid entries or the native PF acoustic. Remove an entry to inherit from below. Resource reload applies changes.

Only the exact `walk` event is replaced. Running, jumping, landing, climbing and shuffling keep their existing recipe and fallback behavior. The replacement uses unit recipe volume/pitch while retaining PF contact timing, spatial position, actor gain/pitch, distance handling and cancellation. Explicit namespaced events keep their selected stereo/mono recording rather than automatically switching namespaces for remote actors.

Sound Manager browses shared block materials using PF's live mapping and block-item variants, and saves walking edits to `resourcepacks/Sound Manager Edits`. Its Revert button removes only that pack's selected material entry. Existing `AET Sound Edits` packs still load normally. Actor-specific maps, non-item block states and other interactions are outside this browser's current scope. The existing PF acoustic exporter still exports native acoustic recipes; this override is a separate resource-pack file.
