package org.fentanylsolutions.pastfootsteps.sound;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** MIT PF distance delay, retained in our own queue so obsolete actors cannot play later. */
public final class SoundArrival {

    private final TimedSoundQueue pending;

    public SoundArrival() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    public SoundArrival(LongSupplier clock) {
        pending = new TimedSoundQueue(clock);
    }

    public void play(double distanceSquared, BooleanSupplier valid, Runnable playback) {
        if (!valid.getAsBoolean()) return;
        long delay = delayMillis(distanceSquared);
        if (delay == 0) playback.run();
        else pending.schedule(delay, 0, Math.max(50, delay * 2 / 3), valid, playback);
    }

    static long delayMillis(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared <= 100) return 0;
        return (long) Math.min(60_000, Math.floor(Math.sqrt(distanceSquared) / 2) * 50);
    }

    public void beginTick() {
        pending.beginTick();
    }

    public void tick() {
        pending.tick();
    }

    public void clear() {
        pending.clear();
    }
}
