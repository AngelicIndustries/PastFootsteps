# Diagnostics

`/pf report` writes the runtime diagnostics report for tracked actors and their recent contacts. Use it while walking on the relevant blocks to investigate behavior that depends on the loaded world.

`/pf mappings` writes the shared mapping report, equivalent to `/pf mappings all`. Use `/pf mappings all MAP` or `/pf mappings unmapped MAP` to select a loaded named entity map. The map name is matched exactly; the omitted map selects the shared mapping. Reports are written as UTF-8 JSON under `config/pastfootsteps/diagnostics/`.

Mapping reports cover registered blocks at metadata values 0 through 15 and group metadata entries with equal results. They do not scan the world. Static base-material inheritance is included in the effective result for each selected map.

The `unmapped` mode lists ordinary mappings that still rely on primitive or native fallback, or that reference a missing acoustic definition. Intentional silence and explicit native mappings are excluded. Lookup errors are included. A primitive fallback is a candidate for refinement, not necessarily a broken or silent block. Missing acoustic checks concern definitions, not the existence of every referenced audio file.

Runtime tile covers and normalized upper double-plant states depend on world state, so inspect those through `/pf report` while the relevant blocks are loaded.

Shared reports are saved as `mappings-all-shared.json` and `mappings-unmapped-shared.json`. Named reports use `mappings-all-map-MAP.json` or `mappings-unmapped-map-MAP.json`. Repeating a command replaces the same report. An empty `materialMap` field means the shared map; `acousticsLoaded` indicates whether the sound definitions were loaded.

All 16 metadata values are examined, including combinations a block may never use. Registry reports do not inspect tile entities, choose collision surfaces or normalize upper plant metadata. The report is diagnostic only and does not change mappings or sounds.

## Acoustic export

`/pf acoustics` saves `config/pastfootsteps/diagnostics/acoustics-export.zip`. Repeating the command replaces
that archive after the new export finishes. It does not install or enable the pack.

The ZIP contains `pack.mcmeta`, a sorted acoustic index and one JSON file per loaded definition under
`assets/presencefootsteps/config/acoustics/`. These are the successful definitions PF currently uses, after
resource-pack layers and local overrides. Malformed overrides export the same lower-priority or cached last-valid
definition used for playback. Run `/pf reload` before exporting edits you have just made. A reload also removes
obsolete definitions from the next export. Explicit silence, including JSON null events, is preserved.

Extract a definition to edit it or use the ZIP as a resource-pack starting point. It includes no audio files,
`sounds.json`, material maps, entity/gait profiles or CFG settings. Keep PF and any source packs supplying its
referenced sound events available. Local `config/pastfootsteps/acoustics/` files still override an installed pack.
This export uses PF's indexed format; it does not add support for the older monolithic `acoustics.json` format.

Bundled PF license/source notices are included. Definitions supplied by other packs or local files retain their
original authorship and terms. Empty exports or acoustic names that cannot be represented safely as pack files
(including the reserved filename `index`) fail without replacing an existing archive.
