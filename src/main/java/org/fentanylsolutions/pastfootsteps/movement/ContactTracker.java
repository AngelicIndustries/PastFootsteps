package org.fentanylsolutions.pastfootsteps.movement;

import java.util.Random;

import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition;

/** Simulation-tick contact generation, independent of Minecraft and rendering. */
public final class ContactTracker implements ContactGenerator {

    public static final int MAX_CONTACTS_PER_TICK = 16;
    private static final double EPSILON = 1.0E-8;

    public interface Listener {

        void contact(Contact contact);
    }

    private final GaitDefinition.Contact[] walk, run;
    private final Random random;
    private boolean initialized, grounded, moving, cycleRunning, running, jumped, climbing, swimming;
    private double x, y, z, peakY, phase, lastDx, lastDz, speed, liquidSpeed;
    private double suspectDx, suspectDy, suspectDz;
    private boolean suspectMotion;
    private int airTicks, groundMisses, idleTicks, nextStand, shuffleCooldown;
    private int emitted, dropped;
    private boolean rebaselined;
    private long sequence, group;
    private ContactKind kind = ContactKind.STAND;

    public ContactTracker(GaitDefinition gait, long seed) {
        walk = gait.contacts(false);
        run = gait.contacts(true);
        random = new Random(seed);
        nextStand = nextStandInterval();
    }

    public double phase() {
        return phase;
    }

    public ContactKind kind() {
        return kind;
    }

    public int droppedContacts() {
        return dropped;
    }

    public boolean rebaselined() {
        return rebaselined;
    }

    public void update(double newX, double newY, double newZ, boolean onGround, boolean onLadder, boolean inLiquid,
        boolean sneaking, boolean sprinting, double cycleDistance, double ladderStride, double runSpeed,
        boolean jumpLand, boolean shuffle, boolean stationary, Listener listener) {
        emitted = 0;
        dropped = 0;
        rebaselined = false;
        double dx = newX - x, dy = newY - y, dz = newZ - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // Allow sustained fast movement without turning a teleport into a trail of contacts.
        double discontinuity = Math.min(16, Math.max(4, speed * 4 + 1));
        boolean finite = Double.isFinite(newX) && Double.isFinite(newY) && Double.isFinite(newZ);
        if (!initialized || !finite || horizontal * horizontal + dy * dy > discontinuity * discontinuity) {
            double changeX = dx - suspectDx, changeY = dy - suspectDy, changeZ = dz - suspectDz;
            boolean sustained = initialized && finite
                && suspectMotion
                && horizontal <= 16
                && Math.abs(dy) <= 4
                && changeX * changeX + changeY * changeY + changeZ * changeZ
                    < 0.0625 * (suspectDx * suspectDx + suspectDy * suspectDy + suspectDz * suspectDz);
            if (!sustained) {
                boolean canLearn = initialized && finite && horizontal > 4 && horizontal <= 16 && Math.abs(dy) <= 4;
                rebaseline(newX, newY, newZ, onGround, onLadder, inLiquid);
                suspectMotion = canLearn;
                suspectDx = dx;
                suspectDy = dy;
                suspectDz = dz;
                return;
            }
            // Two coherent observations can establish an already-fast actor, without replaying the first jump.
            speed = horizontal;
        }
        suspectMotion = false;

        if (shuffleCooldown > 0) shuffleCooldown--;
        if (onGround) groundMisses = 0;
        else groundMisses++;
        // A missed collision sample or very small bob must not manufacture a landing.
        boolean supported = onGround || grounded && groundMisses == 1 && Math.abs(dy) < 0.08;
        boolean changedMode = climbing != onLadder || swimming != inLiquid;
        if (changedMode) {
            phase = 0;
            jumped = false;
            airTicks = 0;
            peakY = newY;
        }
        double distance = onLadder || inLiquid ? Math.sqrt(horizontal * horizontal + dy * dy) : horizontal;
        boolean nowMoving = distance > 0.003;
        speed += (horizontal - speed) * 0.35;
        liquidSpeed = inLiquid ? liquidSpeed + (distance - liquidSpeed) * 0.35 : 0;
        // Remote mobs rarely expose a useful sprint flag. Their actual displacement determines running.
        if (sprinting || speed > runSpeed) running = true;
        else if (speed < runSpeed * 0.8) running = false;

        boolean landed = false;
        if (!onLadder && !inLiquid && !changedMode) {
            if (grounded && !supported && dy > 0.1) {
                jumped = true;
                if (jumpLand && !sneaking) emitGroup(walk, ContactKind.JUMP, x, y, z, 1, listener);
                phase = 0;
            } else if (!grounded && supported) {
                double fall = peakY - newY;
                if ((airTicks >= 2 || jumped) && fall > 0.15) {
                    if (jumpLand && !sneaking) {
                        emitGroup(
                            walk,
                            fall > 0.9 ? ContactKind.LAND : ContactKind.LAND_SOFT,
                            newX,
                            newY,
                            newZ,
                            1,
                            listener);
                    }
                    landed = true;
                    phase = 0;
                }
                jumped = false;
            }
        }

        if (supported || onLadder || inLiquid) {
            if (nowMoving) {
                if (idleTicks >= 4) {
                    phase = 0;
                    cycleRunning = running;
                }
                idleTicks = 0;
                nextStand = nextStandInterval();
                kind = onLadder ? ContactKind.CLIMB
                    : inLiquid ? ContactKind.SWIM : running ? ContactKind.RUN : ContactKind.WALK;
                double interval = Math.max(0.15, onLadder ? ladderStride * 2 : cycleDistance);
                if (running && !onLadder && !inLiquid) interval *= 1.15;
                if (supported && grounded && Math.abs(dy) > 0.35 && !onLadder && !inLiquid) {
                    interval *= 0.8;
                    kind = dy > 0 ? running ? ContactKind.UP_RUN : ContactKind.UP
                        : running ? ContactKind.DOWN_RUN : ContactKind.DOWN;
                }
                if (!landed) advance(distance / interval, newX, newY, newZ, sneaking, listener);
                double previousDistance = Math.sqrt(lastDx * lastDx + lastDz * lastDz);
                if (shuffle && moving
                    && supported
                    && !inLiquid
                    && !onLadder
                    && !sneaking
                    && horizontal > 0.03
                    && previousDistance > 0.03
                    && shuffleCooldown == 0
                    && dx * lastDx + dz * lastDz < -0.5 * horizontal * previousDistance
                    && emitted == 0) {
                    emitSingle(walk[0], ContactKind.SHUFFLE, newX, newY, newZ, listener);
                    shuffleCooldown = 10;
                }
            } else {
                idleTicks++;
                if (moving && shuffle
                    && supported
                    && !sneaking
                    && !inLiquid
                    && !onLadder
                    && shuffleCooldown == 0
                    && !landed) {
                    emitSingle(walk[0], ContactKind.SHUFFLE, newX, newY, newZ, listener);
                    shuffleCooldown = 10;
                }
                if (stationary && !sneaking && supported && !inLiquid && !onLadder && !landed && --nextStand <= 0) {
                    emitSingle(walk[random.nextInt(walk.length)], ContactKind.STAND, newX, newY, newZ, listener);
                    nextStand = nextStandInterval();
                }
            }
        } else {
            phase = 0;
        }
        airTicks = supported || onLadder || inLiquid ? 0 : airTicks + 1;
        peakY = supported || onLadder || inLiquid ? newY : Math.max(peakY, newY);
        grounded = supported;
        climbing = onLadder;
        swimming = inLiquid;
        moving = nowMoving;
        lastDx = dx;
        lastDz = dz;
        x = newX;
        y = newY;
        z = newZ;
    }

    private void advance(double amount, double newX, double newY, double newZ, boolean muted, Listener listener) {
        double remaining = amount, elapsed = 0;
        // Keep overload bounded, advancing phase even when old contacts are discarded.
        if (remaining > 4) {
            double skipped = Math.floor(remaining - 3);
            remaining -= skipped;
            elapsed += skipped;
            dropped += (int) skipped * walk.length;
            cycleRunning = running;
        }
        while (remaining > EPSILON) {
            if (phase < EPSILON) cycleRunning = running;
            double end = Math.min(1, phase + remaining);
            GaitDefinition.Contact[] feet = cycleRunning ? run : walk;
            for (int i = 0; i < feet.length;) {
                int first = i++;
                while (i < feet.length && Math.abs(feet[i].phase - feet[first].phase) < EPSILON) i++;
                double at = feet[first].phase;
                if (at <= phase + EPSILON || at > end + EPSILON) continue;
                long contactGroup = ++group;
                int count = i - first;
                if (muted) continue;
                if (emitted + count > MAX_CONTACTS_PER_TICK) {
                    dropped += count;
                    continue;
                }
                double fraction = Math.min(1, (elapsed + at - phase) / amount);
                for (int j = first; j < i; j++) {
                    listener.contact(
                        new Contact(
                            kind,
                            feet[j],
                            ++sequence,
                            contactGroup,
                            count,
                            x + (newX - x) * fraction,
                            y + (newY - y) * fraction,
                            z + (newZ - z) * fraction,
                            at,
                            kind == ContactKind.SWIM ? liquidSpeed : speed));
                    emitted++;
                }
            }
            double consumed = end - phase;
            remaining -= consumed;
            elapsed += consumed;
            phase = end >= 1 - EPSILON ? 0 : end;
        }
    }

    private void emitGroup(GaitDefinition.Contact[] feet, ContactKind event, double atX, double atY, double atZ,
        double atPhase, Listener listener) {
        long contactGroup = ++group;
        kind = event;
        for (GaitDefinition.Contact foot : feet) {
            listener.contact(
                new Contact(event, foot, ++sequence, contactGroup, feet.length, atX, atY, atZ, atPhase, speed));
            emitted++;
        }
    }

    private void emitSingle(GaitDefinition.Contact foot, ContactKind event, double atX, double atY, double atZ,
        Listener listener) {
        if (emitted == MAX_CONTACTS_PER_TICK) return;
        kind = event;
        listener.contact(new Contact(event, foot, ++sequence, ++group, 1, atX, atY, atZ, phase, speed));
        emitted++;
    }

    private void rebaseline(double atX, double atY, double atZ, boolean onGround, boolean onLadder, boolean inLiquid) {
        rebaselined = true;
        initialized = Double.isFinite(atX) && Double.isFinite(atY) && Double.isFinite(atZ);
        x = atX;
        y = peakY = atY;
        z = atZ;
        grounded = onGround;
        climbing = onLadder;
        swimming = inLiquid;
        jumped = moving = running = cycleRunning = false;
        phase = lastDx = lastDz = speed = liquidSpeed = 0;
        idleTicks = airTicks = groundMisses = shuffleCooldown = 0;
        nextStand = nextStandInterval();
        kind = ContactKind.STAND;
    }

    private int nextStandInterval() {
        return 40 + random.nextInt(81);
    }
}
