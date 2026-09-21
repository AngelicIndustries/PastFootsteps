# Entity support surfaces

PastFootsteps can resolve a foot contact on an explicitly mapped, collidable entity surface. Built-in mappings cover the Minecraft 1.7 `Boat` entity and Et Futurum Requiem boats (`etfuturum.new_boat` and `etfuturum.chest_boat`), all using wood acoustics. EFR wood variants and bamboo rafts share these entity IDs; its passenger-seat entity is not a support surface. A support is accepted only when the bounded foot probe overlaps the entity's actual collision bounding box and reaches its top. Nearby, unloaded, non-collidable, or unknown entities are ignored and normal block or liquid resolution continues.

Support mappings are exact 1.7 entity IDs. Resource packs layer `assets/pastfootsteps/config/supports.json`; the local override is `config/pastfootsteps/profiles/supports.json`.

```json
{
  "schemaVersion": 1,
  "entities": {
    "example:walkable_cart": {
      "material": "metalbar",
      "vanillaSound": "step.anvil",
      "volume": 0.15,
      "pitch": 1.0
    }
  }
}
```

Each entry may set `enabled`, `material`, `vanillaSound`, `volume`, and `pitch`. The geometry always comes from the live entity collision box, never from JSON. Invalid upper layers do not replace lower rules, and an invalid local file retains its last valid local layer until it is deleted.

Concrete mod integrations can register a `SurfaceSupportProvider` through `SurfaceSupportProviders.register`. The provider is invoked only after the same top-of-collision check and must return `null` when it cannot supply a real grounded surface. Provider exceptions are contained and fall back to normal resolution. Entity support surfaces carry their support identity; the footprint renderer intentionally excludes them because world-space prints cannot remain attached to a moving support.
