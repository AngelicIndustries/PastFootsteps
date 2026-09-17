# Custom gait generators

PF normally creates contacts with its distance-based `ContactTracker`. A client integration for a concrete custom
model can replace that generator for one exact gait ID without enabling a generic gait for unrelated entities.
Register during client initialization:

```java
AutoCloseable registration = ContactGenerators.register("example_model_gait", (actor, profile) ->
    new ExampleContactGenerator(actor, profile));
```

The factory returns `ContactGenerator`. Its `update` method receives the already-resolved movement state and emits
`Contact` objects through `ContactTracker.Listener`. `phase`, `kind`, `droppedContacts`, and `rebaselined` have the
same meanings as the vanilla tracker. A generator must keep its own state per tracked actor and must not mutate the
actor or world.

The registration key is an exact gait ID from an entity profile, such as the `gait` value in
`assets/pastfootsteps/config/entities.json`. One factory may register each gait ID. Closing its handle removes that
factory. No class scanning, renderer inspection, or fallback matching occurs, so unsupported entities retain native
behavior unless their profile deliberately selects the gait.

If a factory throws, PF logs the diagnostic and creates the normal `ContactTracker` for that actor. Returning `null`
also selects that fallback without treating the decision as an error. If a custom generator later throws from `update`,
PF's controller replaces it with the vanilla tracker for the same
profile. Implementations should emit at most 16 contacts per tick and preserve grouped contacts when several feet
land together.

Use this API only when a profile's ordinary contact geometry cannot describe a concrete model. Most bipeds,
quadrupeds, pawed mobs, hoofed mobs, and horses are configured through resource-pack entity and gait profiles.
