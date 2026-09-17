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

Contacts use actor-relative gait geometry and collision-aware world positions. They do not claim to match arbitrary renderer bones. Unknown creatures retain native sounds until an entity profile explicitly selects them. Resource-pack profiles are the normal integration path for ordinary bipeds and quadrupeds; specialized anatomy and moving contraptions require a concrete adapter.

Use `/pf status`, `/pf reload`, `/pf report` and the contact HUD/markers during development. The local JSON report includes effective profiles, gait phase, material, authority source, budgets and resource diagnostics, without player names or server addresses.
