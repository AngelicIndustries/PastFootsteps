package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonParser;

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

    @Test
    public void bodyEntriesCanPassWithBootsAndShareContactClaims() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, "fern", "fern");
        sounds.begin(1, 1, "", "fern,moss");
        assertEquals("moss", sounds.entry(true, false, true));
        assertEquals("fern", sounds.claim("moss,fern"));
        assertNull(sounds.claim("fern"));
    }

    @Test
    public void lowEntriesKeepBootGateWhileBodyRemainsEligible() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, "fern", null);
        sounds.begin(1, 1, "", null);
        sounds.begin(2, 1, "fern", null);
        assertNull(sounds.entry(true, false, true));
        assertEquals("fern", sounds.entry(true, false, false));
    }

    @Test
    public void disallowedLowObservationStillUpdatesOccupancy() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, "fern", null);
        sounds.begin(1, 1, "fern", null);
        assertNull(sounds.entry(true, false, true));
        sounds.begin(2, 1, "fern", null);
        assertNull(sounds.entry(true, false, false));
    }

    @Test
    public void bodyEntryRequiresMovementAndNotSneaking() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(0, 1, null, "fern");
        sounds.begin(1, 1, null, "fern,moss");
        assertNull(sounds.entry(false, false, true));
        assertNull(sounds.entry(true, true, true));
        assertEquals("moss", sounds.entry(true, false, true));
    }

    @Test
    public void bodyResetDoesNotCreateSyntheticEntry() {
        FoliageSounds sounds = new FoliageSounds();
        sounds.begin(10, 1, null, "fern");
        sounds.begin(8, 1, null, "moss");
        assertNull(sounds.entry(true, false, false));
        sounds.begin(9, 2, null, "fern,moss");
        assertNull(sounds.entry(true, false, false));
    }

    @Test
    public void bundledLeafBodySoundsHaveNoDirtButGroundAndClimbingKeepIt() throws Exception {
        List<String> played = new ArrayList<>();
        SoundEngine sound = new SoundEngine((name, volume, pitch, context) -> played.add(name));
        AcousticContext context = new AcousticContext(0, 0, 0, 1, 1);
        for (String material : Arrays.asList("leaves", "softleaves")) {
            try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/assets/presencefootsteps/config/acoustics/" + material + ".json"),
                StandardCharsets.UTF_8)) {
                sound.load(material, new JsonParser().parse(reader));
            }
            for (String event : Arrays.asList("walk", "run", "climb", "jump", "land")) {
                played.clear();
                FoliageSounds.playClaimed(sound, material, material, event, context);
                assertEquals(Collections.singletonList("pf_presence.leaves.leaves_through"), played);
            }
            for (String event : Arrays.asList("walk", "climb")) {
                played.clear();
                FoliageSounds.playClaimed(sound, material, null, event, context);
                assertEquals(Arrays.asList("pf_presence.dirt.dirt_walk", "pf_presence.leaves.leaves_through"), played);
            }
            played.clear();
            FoliageSounds claims = new FoliageSounds();
            claims.begin(0, 1, null, null);
            claims.begin(1, 1, material, material);
            FoliageSounds.playClaimed(sound, claims.entry(true, false, true), material, "walk", context);
            FoliageSounds.playClaimed(sound, claims.claim(material), material, "walk", context);
            assertEquals(Collections.singletonList("pf_presence.leaves.leaves_through"), played);
        }
    }

    @Test
    public void smallPlantRustlesUseOnlyQuietBrushAndStaySilentAtRest() throws Exception {
        List<String> played = new ArrayList<>();
        SoundEngine sound = new SoundEngine((name, volume, pitch, context) -> {
            played.add(name);
            assertTrue(volume > 0 && volume < 0.4F);
        });
        try (InputStreamReader reader = new InputStreamReader(
            getClass().getResourceAsStream("/assets/presencefootsteps/config/acoustics/plant_brush.json"),
            StandardCharsets.UTF_8)) {
            sound.load("plant_brush", new JsonParser().parse(reader));
        }
        AcousticContext context = new AcousticContext(0, 0, 0, 1, 1);
        for (String event : Arrays.asList("walk", "run", "jump", "land", "rustle")) {
            played.clear();
            sound.play("plant_brush", event, context);
            assertEquals(Collections.singletonList("pf_presence.brush.brush_through"), played);
        }
        played.clear();
        sound.play("plant_brush", "stand", context);
        assertTrue(played.isEmpty());
    }

    @Test
    public void bodyEventAllowsPackOverridesAndFallsBackForOtherAcoustics() {
        List<String> played = new ArrayList<>();
        SoundEngine sound = new SoundEngine((name, volume, pitch, context) -> played.add(name));
        sound.load(
            "brush",
            new JsonParser().parse("{\"type\":\"events\",\"walk\":\"brush_walk\",\"run\":\"brush_run\"}"));
        sound.load(
            "leaves",
            new JsonParser().parse("{\"type\":\"events\",\"walk\":\"ground\",\"rustle\":{\"type\":\"empty\"}}"));
        FoliageSounds.playClaimed(sound, "leaves,brush", "leaves,brush", "run", new AcousticContext(0, 0, 0, 1, 1));
        assertEquals(Collections.singletonList("brush_run"), played);
    }

}
