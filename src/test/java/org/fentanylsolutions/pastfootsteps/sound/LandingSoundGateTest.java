package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class LandingSoundGateTest {

    @Test
    public void recognizesOnlyVanillaFallImpactsAndLeavesHurtAndSplashAlone() {
        for (String actor : new String[] { "player", "neutral", "hostile" }) {
            assertTrue(LandingSoundGate.isFallSound("game." + actor + ".hurt.fall.small"));
            assertTrue(LandingSoundGate.isFallSound("minecraft:game." + actor + ".hurt.fall.big"));
        }
        for (String sound : new String[] { null, "game.player.hurt", "game.player.swim.splash",
            "game.neutral.swim.splash", "other:game.player.hurt.fall.small", "step.stone" }) {
            assertFalse(LandingSoundGate.isFallSound(sound));
        }
    }

    @Test
    public void replacementMatchesNativeAudioBeforeOrAfterContact() {
        LandingSoundGate gate = new LandingSoundGate();
        AtomicInteger nativePlays = new AtomicInteger();
        assertTrue(gate.defer(10, 0, 1, 2, 3, nativePlays::incrementAndGet));
        gate.replaced(11, 0, 1.2, 2, 3);
        assertTrue(gate.defer(12, 0, 1.1, 2, 3, nativePlays::incrementAndGet));
        gate.tick(20, 0, true);
        assertEquals(0, nativePlays.get());
    }

    @Test
    public void absentOrDistantReplacementPlaysNativeExactlyOnce() {
        LandingSoundGate gate = new LandingSoundGate();
        AtomicInteger nativePlays = new AtomicInteger();
        assertTrue(gate.defer(10, 0, 0, 0, 0, nativePlays::incrementAndGet));
        gate.replaced(11, 0, 8, 0, 0);
        gate.tick(13, 0, true);
        assertEquals(0, nativePlays.get());
        gate.tick(14, 0, true);
        gate.tick(15, 0, true);
        assertEquals(1, nativePlays.get());
        // A stale landing cannot swallow the next impact at that position.
        assertTrue(gate.defer(20, 0, 8, 0, 0, nativePlays::incrementAndGet));
        gate.tick(24, 0, true);
        assertEquals(2, nativePlays.get());
    }

    @Test
    public void disablingRestoresPendingAudioButTeleportDiscardsOldLocation() {
        LandingSoundGate gate = new LandingSoundGate();
        AtomicInteger nativePlays = new AtomicInteger();
        gate.defer(1, 0, 0, 0, 0, nativePlays::incrementAndGet);
        gate.tick(2, 0, false);
        assertEquals(1, nativePlays.get());
        gate.defer(3, 0, 0, 0, 0, nativePlays::incrementAndGet);
        gate.tick(4, 1, true);
        gate.tick(10, 1, true);
        assertEquals(1, nativePlays.get());
    }

    @Test
    public void queueIsBoundedAndNativeImpactMarkerExpiresNextTick() {
        LandingSoundGate gate = new LandingSoundGate();
        AtomicInteger nativePlays = new AtomicInteger();
        gate.nativeFall(10);
        assertTrue(gate.nativeImpactTick(10));
        assertFalse(gate.nativeImpactTick(11));
        for (int i = 0; i < 4; i++) assertTrue(gate.defer(10, 0, 0, 0, 0, nativePlays::incrementAndGet));
        assertFalse(gate.defer(10, 0, 0, 0, 0, nativePlays::incrementAndGet));
        gate.tick(14, 0, true);
        assertEquals(4, nativePlays.get());
    }
}
