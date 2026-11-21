package industries.angelic.pastfootsteps.sound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded monotonic-time queue for delayed sound callbacks. */
public final class TimedSoundQueue {

    public static final int DEFAULT_MAX_ENTRIES = 512;
    private static final long MAX_DELAY_MS = 60000L;
    private static final BooleanSupplier ALWAYS_VALID = () -> true;

    private final LongSupplier clock;
    private final ArrayDeque<Entry> pending = new ArrayDeque<Entry>();
    private final int maxEntries;
    private long lastNow = Long.MIN_VALUE;
    private int snapshot = -1;
    private long generation;

    public TimedSoundQueue() {
        this(() -> System.nanoTime() / 1000000L);
    }

    public TimedSoundQueue(LongSupplier clock) {
        this(clock, DEFAULT_MAX_ENTRIES);
    }

    TimedSoundQueue(LongSupplier clock, int maxEntries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
    }

    /** Reserves the current entries for this tick; callbacks may queue work for the next tick. */
    public void beginTick() {
        snapshot = pending.size();
    }

    /** Processes the tick snapshot, then invokes due callbacks in FIFO order. */
    public void tick() {
        long now = now();
        long dispatchGeneration = generation;
        int count = snapshot < 0 ? pending.size() : snapshot;
        snapshot = -1;
        List<Entry> due = new ArrayList<Entry>();
        Iterator<Entry> entries = pending.iterator();
        int examined = 0;
        while (entries.hasNext() && examined++ < count) {
            Entry entry = entries.next();
            if (!entry.valid.getAsBoolean() || stale(entry, now)) {
                entries.remove();
            } else if (now >= subtract(entry.targetMs, entry.earlyMs)) {
                entries.remove();
                due.add(entry);
            }
        }
        for (Entry entry : due) {
            if (generation != dispatchGeneration) break;
            // Starting an earlier sample can itself stall or trigger a resource reload.
            if (entry.valid.getAsBoolean() && !stale(entry, now())) entry.playback.run();
        }
    }

    /** Drops all queued work and any pending tick snapshot. */
    public void clear() {
        pending.clear();
        snapshot = -1;
        generation++;
    }

    /** Adds a callback, returning false when the bounded queue is full. */
    public boolean schedule(long delayMs, long earlyMs, long lateMs, BooleanSupplier valid, Runnable playback) {
        Objects.requireNonNull(playback, "playback");
        if (pending.size() >= maxEntries) return false;
        long delay = clampDelay(delayMs);
        long early = Math.min(clampNonNegative(earlyMs), delay);
        long late = lateMs == -1L ? -1L : clampNonNegative(lateMs);
        long target = add(now(), delay);
        pending.addLast(new Entry(target, early, late, valid == null ? ALWAYS_VALID : valid, playback));
        return true;
    }

    private boolean stale(Entry entry, long now) {
        return entry.lateMs >= 0 && now > entry.targetMs && now - entry.targetMs > entry.lateMs;
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < lastNow) return lastNow;
        lastNow = value;
        return value;
    }

    private static long clampDelay(long value) {
        return value <= 0 ? 0 : Math.min(MAX_DELAY_MS, value);
    }

    private static long clampNonNegative(long value) {
        return Math.max(0, value);
    }

    private static long add(long first, long second) {
        return second > 0 && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static long subtract(long first, long second) {
        return second > 0 && first < Long.MIN_VALUE + second ? Long.MIN_VALUE : first - second;
    }

    private static final class Entry {

        final long targetMs;
        final long earlyMs;
        final long lateMs;
        final BooleanSupplier valid;
        final Runnable playback;

        Entry(long targetMs, long earlyMs, long lateMs, BooleanSupplier valid, Runnable playback) {
            this.targetMs = targetMs;
            this.earlyMs = earlyMs;
            this.lateMs = lateMs;
            this.valid = valid;
            this.playback = playback;
        }
    }
}
