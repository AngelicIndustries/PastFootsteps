package org.fentanylsolutions.pastfootsteps.network;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.fentanylsolutions.pastfootsteps.movement.Contact;
import org.fentanylsolutions.pastfootsteps.movement.ContactTracker;
import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition;
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

    @Test
    public void interpolatedAirborneEndpointsCannotAcquireGroundEarly() {
        MotionFrame ground = airborneFrame(1, 100, 0, true);
        MotionFrame air = airborneFrame(2, 102, 0.5, false);
        for (double fraction : new double[] { 0.25, 0.5, 0.75 }) {
            assertFalse(ground.interpolate(air, fraction).grounded);
            assertFalse(air.interpolate(ground, fraction).grounded);
        }
        assertTrue(ground.interpolate(air, 0).grounded);
        assertFalse(ground.interpolate(air, 1).grounded);
        assertFalse(air.interpolate(ground, 0).grounded);
        assertTrue(air.interpolate(ground, 1).grounded);
        assertTrue(ground.interpolate(ground, 0.5).grounded);
    }

    @Test
    public void synchronizedHopResolvesLandingAtTheBlockTop() {
        GaitDefinition.Contact[] feet = { new GaitDefinition.Contact("left", 0.5, 0.25, 0),
            new GaitDefinition.Contact("right", 1, -0.25, 0) };
        ContactTracker tracker = new ContactTracker(new GaitDefinition("biped", feet, feet), 1);
        MotionTimeline timeline = new MotionTimeline();
        List<Contact> contacts = new ArrayList<>();
        double[] heights = { 0, 1, 1.25, 0.5, 0, 0 };
        for (int i = 0; i <= 12; i++) {
            if (i % 2 == 0 && i / 2 < heights.length) {
                double height = heights[i / 2];
                timeline.accept(airborneFrame(i / 2 + 1, 100 + i, height, height == 0), 20 + i);
            }
            MotionFrame sample = timeline.sample(20 + i);
            tracker.update(
                sample.x,
                sample.feetY,
                sample.z,
                sample.grounded,
                false,
                false,
                false,
                false,
                2.4,
                0.65,
                0.2,
                true,
                false,
                false,
                contacts::add);
        }
        assertEquals(4, contacts.size());
        assertTrue(contacts.get(0).kind.isJump());
        assertTrue(contacts.get(1).kind.isJump());
        assertTrue(contacts.get(2).kind.isLanding());
        assertTrue(contacts.get(3).kind.isLanding());
        // At 0.25 above the block, the bounded material resolver cannot find its surface.
        // Arrival must be at the actual block top, rather than halfway to the grounded packet.
        assertEquals(0, contacts.get(2).y, 0.00001);
        assertEquals(0, contacts.get(3).y, 0.00001);
    }

    private static MotionFrame airborneFrame(int sequence, int tick, double height, boolean grounded) {
        return new MotionFrame(ACTOR, 41, 62, 1, sequence, tick, 0, 0, height, 0, grounded, false, false, false);
    }

    private static MotionFrame frame(int sequence, int serverTick, double x) {
        return new MotionFrame(ACTOR, 41, 90, 1, sequence, serverTick, 0, x, 64, 0, true, false, false, false);
    }
}
