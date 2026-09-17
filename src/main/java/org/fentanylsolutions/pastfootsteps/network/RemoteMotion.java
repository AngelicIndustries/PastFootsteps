package org.fentanylsolutions.pastfootsteps.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** Client-side server authority cache. This class is safe to load on a dedicated server. */
public final class RemoteMotion {

    private static final Map<ActorKey, Entry> ACTORS = new HashMap<>();
    private static final ArrayDeque<MotionFrame> PENDING = new ArrayDeque<>();
    private static final int MAX_PENDING = 256;
    private static final double MAX_INITIAL_BIND_DISTANCE_SQ = 256;
    private static long clientTick;
    private static boolean serverCompatible;
    private static Integer pendingProtocol;

    private RemoteMotion() {}

    /** Called by the Netty handler. The change is applied from the client tick, never from the network thread. */
    static synchronized void queueServerHello(int protocol) {
        pendingProtocol = protocol;
    }

    static synchronized void receive(MotionFrame frame) {
        if (frame == null || frame.actorId == null) return;
        if (PENDING.size() == MAX_PENDING) PENDING.removeFirst();
        PENDING.addLast(frame);
    }

    private static void apply(MotionFrame frame) {
        if (!serverCompatible) return;
        ActorKey key = new ActorKey(frame.dimension, frame.entityId);
        Entry entry = ACTORS.get(key);
        if (entry == null || !entry.serverActorId.equals(frame.actorId) || entry.generation != frame.generation) {
            if (entry != null && frame.generation < entry.generation) return;
            entry = new Entry(frame.actorId, frame.generation);
            ACTORS.put(key, entry);
        }
        entry.timeline.accept(frame, clientTick);
    }

    /** Advance the shared client clock exactly once per client simulation tick. */
    static synchronized void tick() {
        clientTick++;
        if (pendingProtocol != null) {
            // A hello scopes the cache to one connection. A dimension unload retains capability but clears frames.
            ACTORS.clear();
            serverCompatible = pendingProtocol == ContactSync.PROTOCOL;
            pendingProtocol = null;
        }
        while (!PENDING.isEmpty()) apply(PENDING.removeFirst());
        Iterator<Map.Entry<ActorKey, Entry>> entries = ACTORS.entrySet()
            .iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next()
                .getValue();
            if (clientTick - entry.timeline.lastArrivalTick() > MotionTimeline.TIMEOUT_TICKS) entries.remove();
        }
    }

    /**
     * Returns the sole server-authoritative frame for this remote actor, or null when normal client observation owns
     * it.
     * A caller changing between null and a frame must rebaseline its ContactTracker before processing movement.
     */
    public static synchronized MotionFrame sample(EntityLivingBase actor) {
        if (!serverCompatible || actor == null) return null;
        ActorKey key = new ActorKey(actor.worldObj.provider.dimensionId, actor.getEntityId());
        Entry entry = ACTORS.get(key);
        if (entry == null) return null;
        MotionFrame frame = entry.timeline.sample(clientTick);
        if (frame == null || frame.entityId != actor.getEntityId()
            || frame.dimension != actor.worldObj.provider.dimensionId) return null;
        if (entry.boundActor == null) {
            if (!canBind(actor, frame)) return null;
            entry.boundActor = actor;
        } else if (entry.boundActor != actor) {
            // An ID-reused client object cannot consume an old stream. A new server UUID/generation replaces the entry.
            return null;
        }
        return frame;
    }

    /** Clear frame history on a client world transition while retaining the negotiated connection capability. */
    static synchronized void clearFrames() {
        ACTORS.clear();
        PENDING.clear();
    }

    /** Clear sampled authority frames; the connection capability remains valid across controller and world resets. */
    public static synchronized void reset() {
        ACTORS.clear();
        PENDING.clear();
    }

    /** Connection teardown only. Called by the network lifecycle hook. */
    static synchronized void disconnect() {
        reset();
        clientTick = 0;
        serverCompatible = false;
        pendingProtocol = null;
    }

    public static synchronized boolean available() {
        return serverCompatible;
    }

    private static boolean canBind(EntityLivingBase actor, MotionFrame frame) {
        if (actor instanceof EntityPlayer)
            return frame.entityType == -1 && frame.actorId.equals(actor.getPersistentID());
        if (frame.entityType == -1 || EntityList.getEntityID(actor) != frame.entityType) return false;
        double dx = actor.posX - frame.x, dy = actor.boundingBox.minY - frame.feetY, dz = actor.posZ - frame.z;
        return dx * dx + dy * dy + dz * dz <= MAX_INITIAL_BIND_DISTANCE_SQ;
    }

    /** Server UUID identifies the stream lifetime; vanilla S0F spawn packets do not transmit a mob UUID. */
    private static final class Entry {

        final UUID serverActorId;
        final int generation;
        final MotionTimeline timeline = new MotionTimeline();
        EntityLivingBase boundActor;

        Entry(UUID serverActorId, int generation) {
            this.serverActorId = serverActorId;
            this.generation = generation;
        }
    }

    private static final class ActorKey {

        final int dimension, entityId;

        ActorKey(int dimension, int entityId) {
            this.dimension = dimension;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ActorKey)) return false;
            ActorKey key = (ActorKey) other;
            return dimension == key.dimension && entityId == key.entityId;
        }

        @Override
        public int hashCode() {
            return 31 * dimension + entityId;
        }
    }
}
