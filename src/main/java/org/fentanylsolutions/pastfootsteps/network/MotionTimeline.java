package org.fentanylsolutions.pastfootsteps.network;

/**
 * A tiny ordered sample buffer. It intentionally holds presentation two server ticks behind so a 10 Hz packet
 * stream becomes one motion sample per client tick instead of alternating a full step and no step.
 */
final class MotionTimeline {

    static final int INTERPOLATION_TICKS = 2;
    static final int TIMEOUT_TICKS = 40;
    private static final int CAPACITY = 4;
    private final MotionFrame[] frames = new MotionFrame[CAPACITY];
    private int size;
    private int generation = Integer.MIN_VALUE, newestSequence = Integer.MIN_VALUE;
    private long clockOffset;
    private boolean clockKnown;
    private long lastArrivalTick;

    boolean accept(MotionFrame frame, long clientTick) {
        if (size != 0 && frame.generation != generation) {
            if (frame.generation < generation) return false;
            clear();
        }
        if (size != 0 && frame.sequence <= newestSequence) return false;
        if (size != 0 && frame.serverTick <= frames[size - 1].serverTick) return false;
        if (!clockKnown) {
            clockOffset = clientTick - frame.serverTick;
            clockKnown = true;
        }
        if (size == CAPACITY) {
            System.arraycopy(frames, 1, frames, 0, CAPACITY - 1);
            size--;
        }
        frames[size++] = frame;
        generation = frame.generation;
        newestSequence = frame.sequence;
        lastArrivalTick = clientTick;
        return true;
    }

    MotionFrame sample(long clientTick) {
        if (size == 0 || clientTick - lastArrivalTick > TIMEOUT_TICKS) return null;
        long target = clientTick - clockOffset - INTERPOLATION_TICKS;
        if (target <= frames[0].serverTick || size == 1) return frames[0];
        for (int i = 1; i < size; i++) {
            MotionFrame before = frames[i - 1], after = frames[i];
            if (target <= after.serverTick) {
                return before
                    .interpolate(after, (target - before.serverTick) / (double) (after.serverTick - before.serverTick));
            }
        }
        // Never extrapolate network position. Holding the latest sample avoids a late packet becoming a second stride.
        return frames[size - 1];
    }

    void clear() {
        for (int i = 0; i < size; i++) frames[i] = null;
        size = 0;
        generation = newestSequence = Integer.MIN_VALUE;
        clockKnown = false;
        lastArrivalTick = 0;
    }

    long lastArrivalTick() {
        return lastArrivalTick;
    }
}
