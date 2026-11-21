package industries.angelic.pastfootsteps.movement;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import industries.angelic.pastfootsteps.profile.GaitDefinition;

public class ContactTrackerTest {

    private static final GaitDefinition BIPED = new GaitDefinition(
        "biped",
        feet("left", 0.5, "right", 1),
        feet("left", 0.5, "right", 1));
    private static final GaitDefinition COW = new GaitDefinition(
        "quadruped",
        feet("hind_left", 0.25, "front_left", 0.5, "hind_right", 0.75, "front_right", 1),
        feet("front_left", 0.5, "hind_right", 0.5, "front_right", 1, "hind_left", 1));

    @Test
    public void firstSampleAndOrdinaryIdleDoNotManufactureContacts() {
        Fixture f = new Fixture(BIPED);
        for (int i = 0; i < 200; i++) f.at(10, 64, 10);
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void bipedHasTwoAlternatingContactsAtTheirCrossedPositions() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        for (int i = 1; i <= 24; i++) f.at(i * 0.1, 0, 0);
        assertEquals(Arrays.asList("left", "right"), ids(f.events));
        assertEquals(1.2, f.events.get(0).x, 0.00001);
        assertEquals(2.4, f.events.get(1).x, 0.00001);
        assertEquals(ContactKind.WALK, f.events.get(0).kind);
    }

    @Test
    public void cowWalkUsesAllFourDistinctFeetRatherThanTwoRepeatedFootPairs() {
        Fixture f = new Fixture(COW);
        f.at(0, 0, 0);
        for (int i = 1; i <= 24; i++) f.at(i * 0.1, 0, 0);
        assertEquals(Arrays.asList("hind_left", "front_left", "hind_right", "front_right"), ids(f.events));
        for (int i = 0; i < 4; i++) {
            assertEquals((i + 1) * 0.6, f.events.get(i).x, 0.00001);
            assertEquals(1, f.events.get(i).groupSize);
        }
    }

    @Test
    public void fastStrideIsNeutralAtLowSpeed() {
        GaitDefinition tuned = new GaitDefinition(
            "tuned",
            BIPED.contacts(false),
            BIPED.contacts(true),
            new GaitDefinition.FastStride(0.3, 0.8, 1.5));
        Fixture ordinary = new Fixture(BIPED);
        Fixture fast = new Fixture(tuned);
        for (int i = 0; i <= 100; i++) {
            double x = i * 0.1;
            ordinary.at(x, 0, 0);
            fast.at(x, 0, 0);
        }
        assertTrue(ordinary.events.size() >= 8);
        assertEquals(ids(ordinary.events), ids(fast.events));
        for (int i = 0; i < ordinary.events.size(); i++)
            assertEquals(ordinary.events.get(i).x, fast.events.get(i).x, 0);
    }

    @Test
    public void fastGroundStrideReducesBeatsButKeepsEveryRunningDiagonalPair() {
        GaitDefinition tuned = new GaitDefinition(
            "tuned",
            COW.contacts(false),
            COW.contacts(true),
            new GaitDefinition.FastStride(0.2, 0.6, 1.5));
        Fixture ordinary = new Fixture(COW);
        Fixture fast = new Fixture(tuned);
        ordinary.runSpeed = fast.runSpeed = 0.2;
        for (int i = 0; i <= 40; i++) {
            double x = i * 0.6;
            ordinary.at(x, 0, 0);
            fast.at(x, 0, 0);
        }
        assertTrue(fast.events.size() >= ordinary.events.size() * 0.6);
        assertTrue(fast.events.size() <= ordinary.events.size() * 0.75);
        assertEquals(
            4,
            fast.events.stream()
                .map(c -> c.foot.footId)
                .distinct()
                .count());
        for (int i = 0; i < fast.events.size(); i += 2) {
            Contact first = fast.events.get(i), second = fast.events.get(i + 1);
            assertEquals(2, first.groupSize);
            assertEquals(first.group, second.group);
            assertNotEquals(first.foot.footId.startsWith("front"), second.foot.footId.startsWith("front"));
            assertNotEquals(first.foot.footId.endsWith("left"), second.foot.footId.endsWith("left"));
            assertEquals(first.x, second.x, 0);
        }
    }

    @Test
    public void accelerationAndDecelerationKeepPhaseAndFootGroupsContinuous() {
        GaitDefinition tuned = new GaitDefinition(
            "tuned",
            BIPED.contacts(false),
            BIPED.contacts(true),
            new GaitDefinition.FastStride(0.2, 0.8, 1.5));
        Fixture f = new Fixture(tuned);
        double x = 0;
        f.at(x, 0, 0);
        for (int i = 0; i < 20; i++) {
            x += 0.05;
            f.at(x, 0, 0);
        }
        for (int i = 0; i < 20; i++) {
            x += 0.6;
            f.at(x, 0, 0);
        }
        for (int i = 0; i < 20; i++) {
            x += 0.05;
            f.at(x, 0, 0);
        }
        assertFalse(f.tracker.rebaselined());
        assertTrue(f.events.size() >= 6);
        for (int i = 0; i < f.events.size(); i++) {
            Contact contact = f.events.get(i);
            assertEquals(i % 2 == 0 ? "left" : "right", contact.foot.footId);
            assertEquals(i + 1, contact.sequence);
            if (i > 0) {
                double spacing = contact.x - f.events.get(i - 1).x;
                assertTrue("stride shortened or phase reset: " + spacing, spacing >= 1.2 - 1E-8);
                assertTrue("lost foot contact: " + spacing, spacing <= 1.8 + 1E-8);
            }
        }
    }

    @Test
    public void fastStrideDoesNotChangeSwimmingOrClimbingCadence() {
        GaitDefinition tuned = new GaitDefinition(
            "tuned",
            BIPED.contacts(false),
            BIPED.contacts(true),
            new GaitDefinition.FastStride(0.1, 0.2, 8.0));
        Fixture ordinaryClimb = new Fixture(BIPED), tunedClimb = new Fixture(tuned);
        ordinaryClimb.ground = tunedClimb.ground = false;
        ordinaryClimb.climbing = tunedClimb.climbing = true;
        for (int i = 0; i <= 24; i++) {
            ordinaryClimb.at(i * 0.3, i * 0.1, 0);
            tunedClimb.at(i * 0.3, i * 0.1, 0);
        }
        assertTrue(ordinaryClimb.events.size() > 4);
        assertEquals(ids(ordinaryClimb.events), ids(tunedClimb.events));
        for (int i = 0; i < ordinaryClimb.events.size(); i++)
            assertEquals(ordinaryClimb.events.get(i).x, tunedClimb.events.get(i).x, 0);
        Fixture ordinarySwim = new Fixture(BIPED), tunedSwim = new Fixture(tuned);
        ordinarySwim.ground = tunedSwim.ground = false;
        ordinarySwim.swimming = tunedSwim.swimming = true;
        for (int i = 0; i <= 48; i++) {
            ordinarySwim.at(i * 0.3, i * 0.1, 0);
            tunedSwim.at(i * 0.3, i * 0.1, 0);
        }
        assertTrue(ordinarySwim.events.size() > 4);
        assertEquals(ids(ordinarySwim.events), ids(tunedSwim.events));
        for (int i = 0; i < ordinarySwim.events.size(); i++)
            assertEquals(ordinarySwim.events.get(i).x, tunedSwim.events.get(i).x, 0);
        assertEquals(
            ordinarySwim.events.stream()
                .map(c -> c.speed)
                .collect(Collectors.toList()),
            tunedSwim.events.stream()
                .map(c -> c.speed)
                .collect(Collectors.toList()));
    }

    @Test
    public void remoteCowCanTrotWithoutTheSprintFlagAndPairsDiagonalFeet() {
        Fixture f = new Fixture(COW);
        f.runSpeed = 0.2;
        f.at(0, 0, 0);
        for (int i = 1; i <= 40; i++) f.at(i * 0.4, 0, 0);
        List<Contact> paired = f.events.stream()
            .filter(c -> c.groupSize == 2)
            .collect(Collectors.toList());
        assertTrue(paired.size() >= 4);
        for (int i = 0; i < paired.size(); i += 2) {
            Contact first = paired.get(i), second = paired.get(i + 1);
            assertEquals(ContactKind.RUN, first.kind);
            assertEquals(first.group, second.group);
            assertEquals(first.x, second.x, 0);
            assertTrue(first.foot.footId.startsWith("front"));
            assertTrue(second.foot.footId.startsWith("hind"));
            assertNotEquals(first.foot.footId.endsWith("left"), second.foot.footId.endsWith("left"));
        }
    }

    @Test
    public void highSpeedCrossingsAreNotDiscardedByModulo() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.at(3, 0, 0);
        assertEquals(Arrays.asList("left", "right"), ids(f.events));
        f.at(8, 0, 0);
        assertFalse(f.tracker.rebaselined());
        assertEquals(6, f.events.size());
    }

    @Test
    public void alreadyFastActorCanEstablishMotionAfterOneConservativeReset() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.at(5, 0, 0);
        assertTrue(f.tracker.rebaselined());
        assertTrue(f.events.isEmpty());
        f.at(10, 0, 0);
        assertFalse(f.tracker.rebaselined());
        assertEquals(4, f.events.size());
        assertTrue(f.events.get(0).x > 5);
    }

    @Test
    public void landingCannotAlsoConsumeAnExpiredStandingTimer() {
        Fixture clock = new Fixture(BIPED);
        clock.stationary = true;
        clock.at(0, 0, 0);
        int firstStand = 0;
        while (clock.events.isEmpty() && firstStand < 121) {
            firstStand++;
            clock.at(0, 0, 0);
        }
        assertEquals(ContactKind.STAND, clock.events.get(0).kind);
        Fixture f = new Fixture(BIPED);
        f.stationary = true;
        f.at(0, 0, 0);
        for (int i = 1; i < firstStand; i++) f.at(0, 0, 0);
        f.ground = false;
        f.at(0, 0.3, 0);
        f.at(0, 1.1, 0);
        f.ground = true;
        f.at(0, 0, 0);
        assertEquals(4, f.events.size());
        assertFalse(
            f.events.stream()
                .anyMatch(c -> c.kind == ContactKind.STAND));
    }

    @Test
    public void teleportResetsPhaseWithoutBuildingADelayedTrail() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.at(0.9, 0, 0);
        f.at(100, 50, 100);
        assertTrue(f.tracker.rebaselined());
        assertTrue(f.events.isEmpty());
        f.at(100.4, 50, 100);
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void jumpAndLandingProduceOneGroupEachWithNoAirborneWalking() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0.2, 0.3, 0);
        f.at(1, 1.1, 0);
        f.at(1.8, 0.7, 0);
        f.ground = true;
        f.at(2, 0, 0);
        assertEquals(4, f.events.size());
        assertEquals(ContactKind.JUMP, f.events.get(0).kind);
        assertEquals(f.events.get(0).group, f.events.get(1).group);
        assertEquals(ContactKind.LAND, f.events.get(2).kind);
        assertEquals(f.events.get(2).group, f.events.get(3).group);
        assertNotEquals(f.events.get(1).group, f.events.get(2).group);
        for (Contact event : f.events) assertEquals(2, event.groupSize);
    }

    @Test
    public void standingTakeoffUsesStillJumpAndKeepsBothFeet() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0, 0.3, 0);
        assertEquals(ContactKind.JUMP_STILL, f.events.get(0).kind);
        assertEquals(2, f.events.size());
        assertEquals(f.events.get(0).group, f.events.get(1).group);
    }

    @Test
    public void departureAfterMidCycleStartsWithNextFootAndSoftLandingContinuesWithFollowingFoot() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.at(1.3, 0, 0);
        f.ground = false;
        f.at(1.31, 0.3, 0);
        assertEquals("right", f.events.get(f.events.size() - 2).foot.footId);
        f.at(1.5, 0.6, 0);
        f.ground = true;
        f.at(1.6, 0.3, 0);
        Contact soft = f.events.get(f.events.size() - 2);
        assertEquals(ContactKind.LAND_SOFT, soft.kind);
        f.at(3.0, 0.3, 0);
        assertEquals("left", f.events.get(f.events.size() - 1).foot.footId);
    }

    @Test
    public void ordinarySoftLandingUsesWalkVariant() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0.2, 0.3, 0);
        f.at(0.4, 0.6, 0);
        f.ground = true;
        f.at(0.5, 0.3, 0);
        assertTrue(
            f.events.stream()
                .anyMatch(c -> c.kind == ContactKind.LAND_SOFT));
        assertFalse(
            f.events.stream()
                .anyMatch(c -> c.kind == ContactKind.LAND_SOFT_RUN));
    }

    @Test
    public void softLandingUsesRunningVariantWhenRunning() {
        Fixture f = new Fixture(BIPED);
        f.runSpeed = 0.01;
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0.2, 0.3, 0);
        f.at(0.4, 0.6, 0);
        f.ground = true;
        f.at(0.5, 0.3, 0);
        assertTrue(
            f.events.stream()
                .anyMatch(c -> c.kind == ContactKind.LAND_SOFT_RUN));
        assertEquals(
            2,
            f.events.stream()
                .filter(c -> c.kind == ContactKind.LAND_SOFT_RUN)
                .count());
    }

    @Test
    public void disabledJumpLandDoesNotLeaveCadenceDebt() {
        Fixture f = new Fixture(BIPED);
        f.jumpLand = false;
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0, 0.3, 0);
        f.at(0, 1.1, 0);
        f.ground = true;
        f.at(0, 0, 0);
        assertTrue(f.events.isEmpty());
        f.at(0.5, 0, 0);
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void mutedLandingCannotTurnIntoAReversalShuffle() {
        Fixture f = new Fixture(BIPED);
        f.jumpLand = false;
        f.shuffle = true;
        f.at(0, 0, 0);
        f.ground = false;
        f.at(0.2, 0.3, 0);
        f.at(0.4, 1.1, 0);
        f.ground = true;
        f.at(0.2, 0, 0);
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void briefGroundDetectionGapsAndTinyBobbingDoNotLand() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        for (int i = 0; i < 12; i++) {
            f.ground = false;
            f.at(i * 0.02, 0.025, 0);
            f.ground = true;
            f.at(i * 0.02, 0, 0);
        }
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void climbingUsesVerticalDistanceWithoutLandingAtTheExit() {
        Fixture f = new Fixture(BIPED);
        f.ground = false;
        f.climbing = true;
        f.at(0, 0, 0);
        for (int i = 1; i <= 12; i++) f.at(0, i * 0.1, 0);
        assertEquals(Arrays.asList("left", "right"), ids(f.events));
        assertEquals(ContactKind.CLIMB, f.events.get(0).kind);
        f.climbing = false;
        f.ground = true;
        f.at(0, 1.2, 0);
        assertEquals(2, f.events.size());
    }

    @Test
    public void verticalSwimmingHasStrokeIntensityInBothDirectionsAndNoIdleContacts() {
        Fixture f = new Fixture(BIPED);
        f.ground = false;
        f.swimming = true;
        f.stationary = true;
        f.at(0, 0, 0);
        for (int i = 1; i <= 24; i++) f.at(0, i * 0.1, 0);
        for (int i = 1; i <= 24; i++) f.at(0, 2.4 - i * 0.1, 0);
        // One stroke per 1 / 0.6 blocks, like vanilla: 4.8 blocks make two.
        assertEquals(2, f.events.size());
        for (Contact contact : f.events) {
            assertEquals(ContactKind.SWIM, contact.kind);
            assertEquals(0.1, contact.speed, 0.001);
        }
        f.events.clear();
        for (int i = 0; i < 200; i++) f.at(0, 0, 0);
        assertTrue(f.events.isEmpty());
    }

    @Test
    public void enteringAndLeavingWaterDoNotManufactureJumpOrLandingSounds() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.ground = false;
        f.swimming = true;
        for (int i = 1; i <= 24; i++) f.at(i * 0.1, -0.1, 0);
        // About 2.4 blocks swum: one stroke at vanilla's spacing.
        assertEquals(1, f.events.size());
        for (Contact contact : f.events) assertEquals(ContactKind.SWIM, contact.kind);
        f.events.clear();
        f.swimming = false;
        f.ground = true;
        f.at(2.4, 0, 0);
        assertTrue(f.events.isEmpty());
        for (int i = 1; i <= 12; i++) f.at(2.4 + i * 0.1, 0, 0);
        assertEquals(1, f.events.size());
        assertEquals(ContactKind.WALK, f.events.get(0).kind);
    }

    @Test
    public void underwaterTeleportResetsStrokePhaseAndIntensity() {
        Fixture f = new Fixture(BIPED);
        f.ground = false;
        f.swimming = true;
        f.at(0, 0, 0);
        f.at(0, 0.9, 0);
        f.at(0, 100, 0);
        assertTrue(f.tracker.rebaselined());
        assertTrue(f.events.isEmpty());
        for (int i = 1; i <= 16; i++) f.at(0, 100 + i * 0.1, 0);
        assertTrue(f.events.isEmpty());
        f.at(0, 101.7, 0);
        assertEquals(1, f.events.size());
        assertEquals(ContactKind.SWIM, f.events.get(0).kind);
        assertEquals(0.1, f.events.get(0).speed, 0.001);
    }

    @Test
    public void mutedSneakingAdvancesPhaseWithoutContactDebt() {
        Fixture f = new Fixture(BIPED);
        f.at(0, 0, 0);
        f.sneaking = true;
        f.at(2.4, 0, 0);
        f.sneaking = false;
        f.at(2.41, 0, 0);
        assertTrue(f.events.isEmpty());
        f.at(3.6, 0, 0);
        assertEquals(Arrays.asList("left"), ids(f.events));
    }

    @Test
    public void overloadIsBoundedAndDoesNotReplaySkippedContactsAtRest() {
        Object[] pairs = new Object[32];
        for (int i = 0; i < 16; i++) {
            pairs[i * 2] = "foot" + i;
            pairs[i * 2 + 1] = (i + 1) / 16.0;
        }
        Fixture f = new Fixture(new GaitDefinition("many", feet(pairs), feet(pairs)));
        f.cycle = 0.15;
        f.at(0, 0, 0);
        f.at(4, 0, 0);
        assertEquals(ContactTracker.MAX_CONTACTS_PER_TICK, f.events.size());
        assertTrue(f.tracker.droppedContacts() > 0);
        f.events.clear();
        f.at(4, 0, 0);
        assertTrue(f.events.isEmpty());
    }

    private static List<String> ids(List<Contact> contacts) {
        return contacts.stream()
            .map(c -> c.foot.footId)
            .collect(Collectors.toList());
    }

    private static GaitDefinition.Contact[] feet(Object... values) {
        GaitDefinition.Contact[] result = new GaitDefinition.Contact[values.length / 2];
        for (int i = 0; i < result.length; i++) {
            String id = (String) values[i * 2];
            result[i] = new GaitDefinition.Contact(
                id,
                ((Number) values[i * 2 + 1]).doubleValue(),
                id.contains("right") ? -0.3 : 0.3,
                id.startsWith("front") ? 0.3 : 0);
        }
        return result;
    }

    private static final class Fixture {

        final ContactTracker tracker;
        final List<Contact> events = new ArrayList<>();
        boolean ground = true, climbing, swimming, sneaking, stationary;
        boolean jumpLand = true, shuffle;
        double cycle = 2.4, runSpeed = 100;

        Fixture(GaitDefinition gait) {
            tracker = new ContactTracker(gait, 42);
        }

        void at(double x, double y, double z) {
            tracker.update(
                x,
                y,
                z,
                ground,
                climbing,
                swimming,
                sneaking,
                false,
                cycle,
                0.5,
                runSpeed,
                jumpLand,
                shuffle,
                stationary,
                events::add);
        }
    }
}
