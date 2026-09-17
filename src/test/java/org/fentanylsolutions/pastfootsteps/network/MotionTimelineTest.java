package org.fentanylsolutions.pastfootsteps.network;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Test;

public class MotionTimelineTest {

    private static final UUID ACTOR = new UUID(2, 3);

    @Test
    public void twoTickPacketsBecomeHalfStepsOnSuccessiveClientTicks() {
        MotionTimeline timeline = new MotionTimeline();
        assertTrue(timeline.accept(frame(1, 100, 0), 20));
        assertTrue(timeline.accept(frame(2, 102, 2), 22));

        assertEquals(0, timeline.sample(22).x, 0.0001);
        assertEquals(1, timeline.sample(23).x, 0.0001);
        assertEquals(2, timeline.sample(24).x, 0.0001);
    }

    @Test
    public void latePacketsHoldInsteadOfExtrapolatingANewStride() {
        MotionTimeline timeline = new MotionTimeline();
        timeline.accept(frame(1, 100, 0), 20);
        timeline.accept(frame(2, 102, 2), 22);
        assertEquals(2, timeline.sample(30).x, 0.0001);
        assertNull(timeline.sample(63));
    }

    @Test
    public void oldPacketsCannotReplaceANewerGeneration() {
        MotionTimeline timeline = new MotionTimeline();
        assertTrue(timeline.accept(frame(4, 100, 0), 20));
        assertTrue(
            timeline.accept(new MotionFrame(ACTOR, 4, 90, 2, 1, 102, 0, 4, 0, 0, true, false, false, false), 22));
        assertFalse(timeline.accept(frame(5, 104, 8), 24));
        assertEquals(4, timeline.sample(24).x, 0.0001);
    }

    @Test
    public void reorderedSequenceIsDiscarded() {
        MotionTimeline timeline = new MotionTimeline();
        assertTrue(timeline.accept(frame(4, 100, 0), 20));
        assertFalse(timeline.accept(frame(3, 102, 2), 22));
    }

    private static MotionFrame frame(int sequence, int serverTick, double x) {
        return new MotionFrame(ACTOR, 41, 90, 1, sequence, serverTick, 0, x, 64, 0, true, false, false, false);
    }
}
