package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.junit.Test;

public class TimedSoundQueueTest {

    @Test
    public void earlyAndLateBoundariesAreInclusiveAsSpecified() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        assertTrue(queue.schedule(100, 20, 30, () -> true, () -> played.add("early")));
        clock.now = 79;
        queue.tick();
        assertTrue(played.isEmpty());
        clock.now = 80;
        queue.tick();
        assertEquals(java.util.Arrays.asList("early"), played);

        assertTrue(queue.schedule(100, 0, 30, () -> true, () -> played.add("late-boundary")));
        clock.now = 210;
        queue.tick();
        assertEquals(java.util.Arrays.asList("early", "late-boundary"), played);

        assertTrue(queue.schedule(100, 0, 30, () -> true, () -> played.add("expired")));
        clock.now = 341;
        queue.tick();
        assertFalse(played.contains("expired"));
    }

    @Test
    public void noExpiryUsesLateMinusOne() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        assertTrue(queue.schedule(100, 0, -1, () -> true, () -> played.add("late")));
        clock.now = 60000;
        queue.tick();
        assertEquals(java.util.Arrays.asList("late"), played);
    }

    @Test
    public void dueCallbacksAreFifoAndRemovedBeforeCallbacks() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        assertTrue(queue.schedule(10, 0, 100, () -> true, () -> played.add("first")));
        assertTrue(queue.schedule(10, 0, 100, () -> true, () -> played.add("second")));
        clock.now = 10;
        queue.tick();
        assertEquals(java.util.Arrays.asList("first", "second"), played);
    }

    @Test
    public void invalidEntriesAreRemovedBeforeCallbacksAndRecheckedDuringPlayback() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        AtomicBoolean secondValid = new AtomicBoolean(true);
        assertTrue(queue.schedule(10, 0, 100, () -> true, () -> {
            played.add("first");
            secondValid.set(false);
        }));
        assertTrue(queue.schedule(10, 0, 100, secondValid::get, () -> played.add("second")));
        AtomicBoolean thirdValid = new AtomicBoolean(true);
        assertTrue(queue.schedule(20, 0, 100, thirdValid::get, () -> played.add("third")));
        queue.beginTick();
        thirdValid.set(false);
        clock.now = 10;
        queue.tick();
        assertEquals(java.util.Arrays.asList("first"), played);
    }

    @Test
    public void callbacksQueueChildrenForTheNextTick() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        assertTrue(queue.schedule(10, 0, 100, () -> true, () -> {
            played.add("parent");
            assertTrue(queue.schedule(0, 0, 100, () -> true, () -> played.add("child")));
        }));
        clock.now = 10;
        queue.beginTick();
        queue.tick();
        assertEquals(java.util.Arrays.asList("parent"), played);
        queue.tick();
        assertEquals(java.util.Arrays.asList("parent", "child"), played);
    }

    @Test
    public void admissionIsBoundedAndClearResetsSnapshot() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        Runnable noop = () -> {};
        for (int i = 0; i < TimedSoundQueue.DEFAULT_MAX_ENTRIES; i++)
            assertTrue(queue.schedule(60000, 0, -1, () -> true, noop));
        assertFalse(queue.schedule(60000, 0, -1, () -> true, noop));

        queue.clear();
        assertTrue(queue.schedule(0, 0, 0, () -> true, noop));
        queue.tick();

        queue.clear();
        assertTrue(queue.schedule(Long.MAX_VALUE, 0, -1, () -> true, noop));
        clock.now = 59999;
        queue.tick();
        assertTrue(queue.schedule(0, 0, 0, () -> true, noop));
    }

    @Test
    public void delayIsClampedToSixtySeconds() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<String>();
        assertTrue(queue.schedule(Long.MAX_VALUE, 0, -1, () -> true, () -> played.add("long")));
        clock.now = 59999;
        queue.tick();
        assertTrue(played.isEmpty());
        clock.now = 60000;
        queue.tick();
        assertEquals(java.util.Arrays.asList("long"), played);
    }

    @Test
    public void aStallDuringPlaybackExpiresRemainingDueSounds() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<>();
        queue.schedule(10, 0, 50, () -> true, () -> {
            played.add("first");
            clock.now = 1000;
        });
        queue.schedule(10, 0, 50, () -> true, () -> played.add("stale"));
        clock.now = 10;
        queue.tick();
        assertEquals(java.util.Arrays.asList("first"), played);
    }

    @Test
    public void clearDuringPlaybackCancelsAlreadyExtractedCallbacks() {
        Clock clock = new Clock();
        TimedSoundQueue queue = new TimedSoundQueue(clock);
        List<String> played = new ArrayList<>();
        queue.schedule(10, 0, 50, () -> true, () -> {
            played.add("first");
            queue.clear();
        });
        queue.schedule(10, 0, 50, () -> true, () -> played.add("cancelled"));
        clock.now = 10;
        queue.tick();
        assertEquals(java.util.Arrays.asList("first"), played);
    }

    private static final class Clock implements LongSupplier {

        long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
