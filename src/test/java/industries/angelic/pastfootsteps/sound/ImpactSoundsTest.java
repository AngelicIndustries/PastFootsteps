package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.*;

import org.junit.Test;

import industries.angelic.pastfootsteps.movement.Contact;
import industries.angelic.pastfootsteps.movement.ContactKind;
import industries.angelic.pastfootsteps.profile.GaitDefinition;

public class ImpactSoundsTest {

    @Test
    public void movingTakeoffAndSoftLandingsSelectOnePhysicalFoot() {
        for (ContactKind kind : new ContactKind[] { ContactKind.JUMP, ContactKind.LAND_SOFT,
            ContactKind.LAND_SOFT_RUN }) {
            ImpactSounds sounds = new ImpactSounds();
            assertTrue(sounds.select(contact(kind, 1, "right"), 0));
            assertFalse(sounds.select(contact(kind, 1, "left"), 0));
            assertTrue(sounds.select(contact(kind, 2, "left"), 0));
        }
        assertEquals("climb", ContactKind.LAND_SOFT.acousticKey);
        assertEquals("climb_run", ContactKind.LAND_SOFT_RUN.acousticKey);
        assertEquals("wander", ContactKind.JUMP_STILL.acousticKey);
    }

    @Test
    public void groupedImpactsDeduplicateIdenticalLayersButRetainSplitSurfaces() {
        for (ContactKind kind : new ContactKind[] { ContactKind.JUMP_STILL, ContactKind.LAND }) {
            ImpactSounds sounds = new ImpactSounds();
            assertTrue(sounds.select(contact(kind, 1, "left"), 0));
            assertTrue(sounds.claim("surface", "stone"));
            assertTrue(sounds.claim("wet", "splash"));
            assertTrue(sounds.select(contact(kind, 1, "right"), 0));
            assertFalse(sounds.claim("surface", "stone"));
            assertFalse(sounds.claim("wet", "splash"));
            assertTrue(sounds.claim("surface", "wood"));
            assertTrue(sounds.claim("foliage", "brush"));
        }
    }

    @Test
    public void newGroupsAndGenerationsCanReplayAndWalkingStaysPerFoot() {
        ImpactSounds sounds = new ImpactSounds();
        sounds.select(contact(ContactKind.LAND, 1, "left"), 0);
        assertTrue(sounds.claim("surface", "stone"));
        sounds.select(contact(ContactKind.LAND, 1, "left"), 1);
        assertTrue(sounds.claim("surface", "stone"));
        sounds.select(contact(ContactKind.LAND, 2, "left"), 1);
        assertTrue(sounds.claim("surface", "stone"));
        assertTrue(sounds.select(contact(ContactKind.RUN, 3, "left"), 1));
        assertTrue(sounds.select(contact(ContactKind.RUN, 3, "right"), 1));
        assertFalse(ImpactSounds.impact(ContactKind.RUN));
    }

    private static Contact contact(ContactKind kind, long group, String foot) {
        return new Contact(kind, new GaitDefinition.Contact(foot, 0.5, 0, 0), group * 2, group, 2, 0, 0, 0, 1, 0.1);
    }
}
