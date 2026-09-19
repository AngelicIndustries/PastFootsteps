package org.fentanylsolutions.pastfootsteps.network;

import java.util.UUID;

/**
 * A cosmetic, server-observed motion sample. Coordinates describe the actor's feet, not its eye or body centre.
 * This is deliberately separate from an entity's client interpolation state: consumers must use one or the other.
 */
public final class MotionFrame {

    public final UUID actorId;
    public final int entityId, entityType, generation, sequence, serverTick, dimension;
    public final double x, feetY, z;
    public final boolean grounded, climbing, swimming, sprinting;

    public MotionFrame(UUID actorId, int entityId, int entityType, int generation, int sequence, int serverTick,
        int dimension, double x, double feetY, double z, boolean grounded, boolean climbing, boolean swimming,
        boolean sprinting) {
        this.actorId = actorId;
        this.entityId = entityId;
        this.entityType = entityType;
        this.generation = generation;
        this.sequence = sequence;
        this.serverTick = serverTick;
        this.dimension = dimension;
        this.x = x;
        this.feetY = feetY;
        this.z = z;
        this.grounded = grounded;
        this.climbing = climbing;
        this.swimming = swimming;
        this.sprinting = sprinting;
    }

    MotionFrame interpolate(MotionFrame later, double fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        // Do not attach a grounded endpoint's flag to a position still above its surface.
        // A prematurely emitted landing cannot resolve its material and is then lost for this hop.
        boolean onGround = fraction <= 0 ? grounded : fraction >= 1 ? later.grounded : grounded && later.grounded;
        return new MotionFrame(
            actorId,
            entityId,
            entityType,
            generation,
            later.sequence,
            (int) Math.round(serverTick + (later.serverTick - serverTick) * fraction),
            dimension,
            x + (later.x - x) * fraction,
            feetY + (later.feetY - feetY) * fraction,
            z + (later.z - z) * fraction,
            onGround,
            fraction < 0.5 ? climbing : later.climbing,
            fraction < 0.5 ? swimming : later.swimming,
            fraction < 0.5 ? sprinting : later.sprinting);
    }
}
