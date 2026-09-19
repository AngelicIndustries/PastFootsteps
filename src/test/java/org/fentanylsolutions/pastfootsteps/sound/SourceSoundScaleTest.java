package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.*;

import org.junit.Test;

public class SourceSoundScaleTest {

    @Test
    public void boostDefaultsToNeutralAndRampsSmoothlyWithThreeDimensionalSpeed() {
        SourceSoundScale scale = new SourceSoundScale();
        scale.update(0, 0, 0, 0, 0.22);
        scale.update(0.1, 0, 0, 0, 0.22);
        assertEquals(1 / 1.5, scale.volume(0.5F), 0.0001);
        scale.update(0.1, 0.3, 0, 0, 0.22);
        assertEquals(1.175 / 1.5, scale.volume(0.5F), 0.0001);
        for (int i = 2; i <= 20; i++) scale.update(0.1, i * 0.3, 0, 0, 0.22);
        assertEquals(1, scale.volume(0.5F), 0.001);
        assertEquals(0.5, scale.volume(-0.5F), 0.001);
        assertEquals(1, scale.volume(0), 0);
        float previous = scale.volume(0.5F);
        scale.update(0.1, 6, 0, 0, 0.22);
        assertTrue(scale.volume(0.5F) < previous);
        assertTrue(scale.volume(0.5F) > 1 / 1.5);
    }

    @Test
    public void teleportAndActorResetCannotCarryRunningBoostToANewPosition() {
        SourceSoundScale scale = new SourceSoundScale();
        scale.update(0, 0, 0, 0, 0.22);
        for (int i = 1; i <= 10; i++) scale.update(i * 0.3, 0, 0, 0, 0.22);
        assertTrue(scale.volume(1) > 0.99);
        scale.update(100, 50, 100, 0, 0.22);
        assertEquals(0.5, scale.volume(1), 0);
        scale.update(100.3, 50, 100, 0, 0.22);
        assertTrue(scale.volume(1) > 0.5);
        scale.update(100.4, 50, 100, 1, 0.22);
        assertEquals(0.5, scale.volume(1), 0);
    }

    @Test
    public void sizePitchUsesRelativeAdultWidthAndUnknownSizesStayNeutral() {
        assertEquals(1, SourceSoundScale.pitch(0.9, 0.9), 0.0001);
        assertEquals(1 / 0.7, SourceSoundScale.pitch(0.45, 0.9), 0.0001);
        assertEquals(1 / 1.6, SourceSoundScale.pitch(1.8, 0.9), 0.0001);
        assertEquals(1, SourceSoundScale.pitch(0.9, 0), 0);
        assertEquals(1, SourceSoundScale.pitch(Double.NaN, 0.9), 0);
        assertEquals(1, SourceSoundScale.pitch(0.9, Double.POSITIVE_INFINITY), 0);
        assertEquals(1, SourceSoundScale.pitch(-1, 0.9), 0);
    }

    @Test
    public void normalPlayerWalkingAndSprintingHaveTheFullRequestedContrast() {
        SourceSoundScale walk = new SourceSoundScale(), run = new SourceSoundScale();
        for (int i = 0; i <= 20; i++) {
            walk.update(i * 0.216, 0, 0, 0, 0.22);
            run.update(i * 0.281, 0, 0, 0, 0.22);
        }
        assertEquals(1 / 1.5, walk.volume(0.5F), 0.0001);
        assertEquals(1, run.volume(0.5F), 0.001);
        assertEquals(1.5, run.volume(0.5F) / walk.volume(0.5F), 0.001);
        assertEquals(2, run.volume(1) / walk.volume(1), 0.001);
        assertEquals(1, walk.volume(0), 0);
        assertEquals(1, run.volume(0), 0);
        assertEquals(1, walk.volume(-0.5F), 0);
        assertEquals(0.5, run.volume(-0.5F), 0.001);
    }
}
