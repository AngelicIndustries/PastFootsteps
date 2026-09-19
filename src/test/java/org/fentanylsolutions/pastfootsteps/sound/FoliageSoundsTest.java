package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoliageSoundsTest {

    @Test
    public void contactAndEntryClaimsDeduplicateComponents() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(10, 1, "fern, fern, grass");
        sounds.begin(11, 1, "fern, grass, leaf");

        assertEquals("fern", sounds.claim(" fern, fern "));
        assertNull(sounds.entry(true)); // The contact already supplied this tick's rustle.
        assertEquals("grass,leaf", sounds.claim("fern, grass, leaf"));
        assertNull(sounds.claim("leaf,grass"));
        sounds.begin(11, 1, "new");
        assertNull(sounds.claim("fern"));
        sounds.begin(11, 2, "new");
        assertNull(sounds.entry(true));
        assertEquals("fern", sounds.claim("fern"));
    }

    @Test
    public void firstObservationAndNewMixedNamesHaveNoStaleEntry() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(1, 3, "NOT_EMITTER, fern, UNASSIGNED, , grass");
        assertNull(sounds.entry(true));

        sounds.begin(2, 3, "fern, grass, moss, VANILLA");
        assertEquals("moss", sounds.entry(true));
    }

    @Test
    public void reentryWaitsTwoTicksAfterAnyClaim() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, "fern");
        sounds.begin(1, 1, "");
        assertEquals("fern", sounds.claim("fern"));

        sounds.begin(2, 1, "fern");
        assertNull(sounds.entry(true));
        sounds.begin(3, 1, "");
        sounds.begin(4, 1, "fern");
        assertEquals("fern", sounds.entry(true));
    }

    @Test
    public void resetTeleportAndGenerationChangeBaselineWithoutEntry() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(10, 1, "fern");
        sounds.begin(8, 1, "grass");
        assertNull(sounds.entry(true));
        sounds.begin(9, 2, "moss");
        assertNull(sounds.entry(true));
        sounds.begin(10, 2, "fern,moss");
        assertEquals("fern", sounds.entry(true));
    }

    @Test
    public void ineligibleObservationsStillUpdateOccupancy() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, "fern");
        sounds.begin(1, 1, "fern,grass");
        assertNull(sounds.entry(false));
        sounds.begin(2, 1, "fern,grass");
        assertNull(sounds.entry(true));
    }

    @Test
    public void eligibilityHelpersEncodeStandingSneakingAndBootRules() {
        assertTrue(FoliageSounds.contactAllowed(false, false));
        assertTrue(FoliageSounds.contactAllowed(true, true));
        assertTrue(FoliageSounds.entryAllowed(true, false, false));
        assertTrue(!FoliageSounds.contactAllowed(true, false));
        assertTrue(!FoliageSounds.entryAllowed(false, false, false));
        assertTrue(!FoliageSounds.entryAllowed(true, true, false));
        assertTrue(!FoliageSounds.entryAllowed(true, false, true));
    }
}
