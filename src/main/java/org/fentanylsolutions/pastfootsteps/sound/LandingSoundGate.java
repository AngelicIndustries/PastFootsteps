package org.fentanylsolutions.pastfootsteps.sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Briefly holds native impact audio until client contacts can confirm its replacement. */
public final class LandingSoundGate {

    private static final int WINDOW_TICKS = 4, MAX_PENDING = 4;
    private final List<Pending> pending = new ArrayList<>();
    private long generation = Long.MIN_VALUE, ownedTick = Long.MIN_VALUE, nativeTick = Long.MIN_VALUE;
    private double x, y, z;

    public static boolean isFallSound(String name) {
        if (name == null) return false;
        String path = name.startsWith("minecraft:") ? name.substring(10) : name;
        return path.equals("game.player.hurt.fall.small") || path.equals("game.player.hurt.fall.big")
            || path.equals("game.neutral.hurt.fall.small")
            || path.equals("game.neutral.hurt.fall.big")
            || path.equals("game.hostile.hurt.fall.small")
            || path.equals("game.hostile.hurt.fall.big");
    }

    public void nativeFall(long tick) {
        nativeTick = tick;
    }

    public boolean nativeImpactTick(long tick) {
        return nativeTick == tick;
    }

    public boolean defer(long tick, long currentGeneration, double atX, double atY, double atZ, Runnable fallback) {
        synchronize(currentGeneration);
        if (matches(tick, atX, atY, atZ)) return true;
        if (pending.size() >= MAX_PENDING) return false;
        pending.add(new Pending(tick, atX, atY, atZ, fallback));
        return true;
    }

    public void replaced(long tick, long currentGeneration, double atX, double atY, double atZ) {
        synchronize(currentGeneration);
        ownedTick = tick;
        x = atX;
        y = atY;
        z = atZ;
        pending.removeIf(entry -> matches(entry.tick, entry.x, entry.y, entry.z));
    }

    public void tick(long tick, long currentGeneration, boolean enabled) {
        synchronize(currentGeneration);
        if (!enabled) ownedTick = Long.MIN_VALUE;
        if (pending.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        Iterator<Pending> entries = pending.iterator();
        while (entries.hasNext()) {
            Pending entry = entries.next();
            if (!enabled || tick - entry.tick >= WINDOW_TICKS) {
                entries.remove();
                due.add(entry.fallback);
            }
        }
        // Remove first so fallback playback cannot be replayed by a reentrant callback.
        for (Runnable fallback : due) fallback.run();
    }

    private boolean matches(long tick, double atX, double atY, double atZ) {
        double dx = x - atX, dy = y - atY, dz = z - atZ;
        return ownedTick != Long.MIN_VALUE && Math.abs(tick - ownedTick) <= WINDOW_TICKS
            && dx * dx + dy * dy + dz * dz <= 2.25;
    }

    private void synchronize(long currentGeneration) {
        if (generation == currentGeneration) return;
        generation = currentGeneration;
        ownedTick = Long.MIN_VALUE;
        pending.clear(); // Teleports and replaced actor states cannot replay old impacts.
    }

    private static final class Pending {

        final long tick;
        final double x, y, z;
        final Runnable fallback;

        Pending(long tick, double x, double y, double z, Runnable fallback) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fallback = fallback;
        }
    }
}
