package industries.angelic.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import industries.angelic.pastfootsteps.profile.JsonData;
import industries.angelic.pastfootsteps.world.SurfaceResolver.Surface;

public class SupportProfilesTest {

    @Test
    public void exactEntityRulesLayerAndCanBeDisabled() throws Exception {
        Map<String, SupportProfiles.Rule> rules = new LinkedHashMap<String, SupportProfiles.Rule>();
        SupportProfiles.apply(rules, json("{\"schemaVersion\":1,\"entities\":{\"Boat\":{\"material\":\"wood\"}}}"));
        SupportProfiles.apply(
            rules,
            json(
                "{\"schemaVersion\":1,\"entities\":{\"Boat\":{\"enabled\":false},"
                    + "\"mod:platform\":{\"material\":\"metalbar\",\"volume\":0.4}}}"));

        assertFalse(rules.get("Boat").enabled);
        assertEquals("metalbar", rules.get("mod:platform").material);
        assertEquals(0.4F, rules.get("mod:platform").volume, 0.0F);
    }

    @Test
    public void entityNamesWithAPipeInTheModIdAreAccepted() throws Exception {
        Map<String, SupportProfiles.Rule> rules = new LinkedHashMap<String, SupportProfiles.Rule>();
        SupportProfiles.apply(
            rules,
            json("{\"schemaVersion\":1,\"entities\":{\"ProjectZulu|Core.Armadillo\":{\"material\":\"wood\"}}}"));
        assertEquals("wood", rules.get("ProjectZulu|Core.Armadillo").material);
    }

    @Test
    public void invalidLayerDoesNotPartiallyReplaceExistingRules() throws Exception {
        Map<String, SupportProfiles.Rule> rules = new LinkedHashMap<String, SupportProfiles.Rule>();
        SupportProfiles.apply(rules, json("{\"schemaVersion\":1,\"entities\":{\"Boat\":{\"material\":\"wood\"}}}"));
        boolean rejected = false;
        try {
            SupportProfiles.apply(
                rules,
                json(
                    "{\"schemaVersion\":1,\"entities\":{\"Boat\":{\"material\":\"metalbar\"},"
                        + "\"bad id!\":{\"material\":\"stone\"}}}"));
        } catch (RuntimeException expected) {
            // The all-or-nothing layer leaves Boat's lower entry intact.
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("wood", rules.get("Boat").material);
        assertTrue(rules.size() == 1);
    }

    @Test
    public void ordinarySurfacesUseTheirPhysicalTopForDecals() {
        Surface surface = new Surface(
            "wood",
            null,
            "step.wood",
            0.15F,
            1.0F,
            3,
            4,
            5,
            true,
            false,
            3.2,
            4.75,
            5.8,
            3,
            4,
            5,
            6);
        assertEquals(4.75, surface.decalY, 0.0);
        assertNull(surface.supportEntity);
    }

    private static com.google.gson.JsonObject json(String source) throws Exception {
        return JsonData.read(new StringReader(source));
    }
}
