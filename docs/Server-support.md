# Optional server motion synchronization

Past Footsteps works on ordinary servers without this component. The client continues to infer remote actor movement from
vanilla updates, including its conservative support check. A Past Footsteps server also accepts players without the mod:
it sends no motion data to them and never changes movement, collision, sounds, or other gameplay state.

When both sides have compatible support, the client registers the `pf_motion` channel and the server replies with protocol
version 2. The client then confirms the same version. Only confirmed clients receive frames. A client that joins a server
without Past Footsteps never receives that reply and keeps its normal local authority; no server component is required.

The server sends nearby `EntityLivingBase` samples every two server ticks. Each compact sample contains the entity ID, spawn type, server UUID, lifecycle generation, per-actor sequence, server tick, dimension, fixed-point feet
position, and server-observed ground, climb, swim, and sprint flags. It never selects sounds, gait limbs, profiles, or
client volume settings. The default server
limits are 32 blocks, 24 actors per compatible client, and 48 packets per two-tick pass; these values are under the
`serversupport` configuration category.

On the client, `RemoteMotion.sample(actor)` returns a buffered `MotionFrame` only while the compatible server is the single
authority for that remote actor. Frames are displayed two server ticks behind and linearly interpolated on client ticks.
They are held, rather than extrapolated, if the next packet is late. This avoids turning a 10 Hz packet cadence into an
alternating full-step/no-step gait. The local player always remains local. Callers must rebaseline a `ContactTracker` when
switching between a non-null frame and normal entity observation, and must call `RemoteMotion.reset()` with their own
controller reset path if they retain authority state. Reset clears frames but preserves a live connection's negotiated
capability.

Vanilla 1.7's `S0FPacketSpawnMob` contains an entity ID and byte-sized entity type but no UUID. For mobs, the client
therefore keys a stream by dimension and entity ID, keeps the transmitted server UUID/generation as its lifetime, and
binds it once to the exact client entity object after type and an initial 16-block proximity check. If that entity ID is
reused by another client object, old data is rejected until a new server lifetime arrives. Player spawn packets do carry
a profile UUID, so player binding also requires the transmitted UUID to match the client player UUID.

Frames expire after 40 client ticks. Disconnect clears the connection capability; client-world unload clears only frame
history, so a dimension change retains its negotiated capability. Server UUID/lifetime, entity ID, type, dimension,
generation, and sequence checks discard entity-ID reuse, stale generations, duplicated, and out-of-order packets without replaying a path or
landing. The current 1.7 vanilla movement packets do not include a ground bit; the synchronized value is sampled on the
server and is not inferred from those packets.

Common initialization must call `ContactSync.initialize()` once, and the existing configuration sync must call
`SyncSettings.sync(configuration)`. These APIs have no client renderer or sound references and are safe on a dedicated
server.
