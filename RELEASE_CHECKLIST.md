# 0.0.1 preparation

Status checked 2026-11-26. Publishing is a separate maintainer step.

## State

* Feature-complete for 0.0.1 and running in Adventura Arcana as a local mod.
* GitHub `AngelicIndustries/PastFootsteps` is private. The only tag is `656b492-snapshot`, which is also the only build
  on the Angelic Maven.
* 67minecraft has a draft project, `past-footsteps` (`c09YLjZ1`), with one snapshot version and no categories,
  sides or license yet. There are no Modrinth or CurseForge projects.
* Publishing uses the shared release workflow (`@v1`) and 67minecraft publish script 1.0.6, which sets the Modrinth
  environment (`client_only_server_optional`). README sync and maintenance workflows are in place.

## Open decisions

* The experimental walking-sound overrides (`api/WalkingSounds`, `docs/walking-sound-overrides.md`) are in the code
  but not in the changelog or README. Keep, mark or hide them for 0.0.1.
* The logo is 446x446; the conventions ask for 512x512.

## Release steps

1. Make the GitHub repo public, set the 67minecraft draft's details and list it.
2. Tag `0.0.1`: GitHub, Maven and 67minecraft.
3. With a later version: create the Modrinth and CurseForge projects and set `modrinthProjectId` and
   `curseForgeProjectId`.

Automatic leaf classification, old monolithic acoustic-pack compatibility, winged locomotion, Mine Little Pony and
further moving-surface integrations remain out of scope for the first release.
