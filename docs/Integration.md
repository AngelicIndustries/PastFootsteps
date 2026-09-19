# Contact integrations

Use PF's contact stream to coordinate additional cosmetic effects with its footsteps. Register from client initialization:

```java
AutoCloseable registration = ContactEvents.listen(contact -> {
    // contact.actor, profile, contact.kind, contact.foot.footId,
    // contact.sequence/group/groupSize, and surface contact coordinates
});
```

`org.fentanylsolutions.pastfootsteps.api.ContactEvents` calls observers on the client simulation thread, once per resolved foot contact. Closing the returned handle unregisters it. A throwing observer is removed and logged. No event is allocated between contacts.

Observers must not mutate the actor/world or retain actor references indefinitely. The event describes cosmetic observation, not server authority or proof that a gameplay action occurred. Print and sound settings do not suppress the contact stream; the master toggle and actor eligibility do. Foot IDs and simultaneous group IDs are stable within a tracked actor lifetime. Sequence/group counters can restart after a reset, so never use an entity ID alone as a persistent key. Actors are removed on death, mount/flight, range/budget changes and world changes.

For a concrete mod model whose gait cannot be expressed in profile JSON, see [Custom gait generators](Gait-integrations.md).

`ContactKind.isJump()` covers moving `JUMP` and standing `JUMP_STILL`; `isLanding()` covers `LAND`, `LAND_SOFT`, and
`LAND_SOFT_RUN`. Jump/landing groups retain all physical feet even when the audio consumer uses a single foot or
deduplicates a shared material. The first contact in an airborne group is its primary foot, selected from gait phase.
Use these predicates rather than assuming every jump or landing has one enum value.

Contacts use actor-relative gait geometry and collision-aware world positions. They do not claim to match arbitrary renderer bones. Unknown creatures retain native sounds until an entity profile explicitly selects them. Resource-pack profiles are the normal integration path for ordinary bipeds and quadrupeds; specialized anatomy and moving contraptions require a concrete adapter.

Use `/pf status`, `/pf reload`, `/pf report` and the contact HUD/markers during development. The local JSON report includes effective profiles, gait phase, material, authority source, budgets and resource diagnostics, without player names or server addresses.

## Block materials

`org.fentanylsolutions.pastfootsteps.api.DerivedBlock` describes a static base material. Implement
`getBaseMaterial(int metadata)` on a block and return `new BlockMaterial(baseBlock, baseMetadata)`, or `null`
when no base is known. Metadata must be in `0..15`. PF supplies this for vanilla `BlockStairs` and its subclasses.
Explicit wrapper mappings are authoritative; missing or `UNASSIGNED` mappings can inherit from the base.
Base lookup is bounded and cycle-safe. Keep this method cheap and free of world access.

For position-dependent covers, register a top-face appearance provider during client initialization:

```java
AutoCloseable handle = BlockAppearanceProviders.register(MyCoveredBlock.class,
    (world, x, y, z, physicalBlock, metadata) -> {
        // Read the existing tile's exposed top cover without loading any chunks.
        return new BlockMaterial(coverBlock, coverMetadata);
    });
```

Return `null` when the provider does not apply. Registration order decides precedence: the first non-null,
non-air result wins. Closing the handle removes that provider. Runtime/linkage failures are isolated and allow
later providers or the original block to resolve. PF checks that the location is loaded before invoking providers;
providers must also avoid loading neighbors. Do not mutate the world or retain tile/entity instances.

Appearance is queried before all block-map lookups, without persistent tile-cover caching. It replaces material
identity for sounds, not physical geometry. The represented material's `SoundType` supplies native fallback.
A provider represents a block's top face; contact-specific triangle or multipart selection needs a richer future
adapter. Do not use renderer textures or pick-block results as material guesses.
