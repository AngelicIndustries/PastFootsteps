package industries.angelic.pastfootsteps.equipment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonParseException;

public class EquipmentSoundsTest {

    @Test
    public void structuredItemLayersOverrideAndCanRemoveEarlierMappings() {
        Map<String, String> categories = new HashMap<String, String>();
        EquipmentSounds.applyLayer(
            new StringReader("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"category\":\"heavy\"}}}"),
            "lower",
            categories);
        EquipmentSounds.applyLayer(
            new StringReader(
                "{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"category\":\"crystal\"},\"example:silent\":{\"category\":\"none\"}}}"),
            "higher",
            categories);
        assertEquals("crystal", categories.get("example:boots"));
        assertEquals("none", categories.get("example:silent"));

        EquipmentSounds.applyLayer(
            new StringReader("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"enabled\":false}}}"),
            "removal",
            categories);
        assertFalse(categories.containsKey("example:boots"));
    }

    @Test
    public void scalarAndUnknownOverrideFormsAreRejected() {
        assertRejected("{\"schemaVersion\":1,\"items\":{\"example:boots\":\"heavy\"}}");
        assertRejected("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"category\":\"nope\"}}}");
        assertRejected("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"category\":\"heavy\",\"extra\":true}}}");
        assertRejected("{\"schemaVersion\":1,\"schemaVersion\":1,\"items\":{}}");
        assertRejected("{\"schemaVersion\":1.5,\"items\":{}}");
        assertRejected("{\"schemaVersion\":\"1\",\"items\":{}}");
        assertRejected("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"category\":true}}}");
        assertRejected("{\"schemaVersion\":1,\"items\":{\"example:boots\":{\"enabled\":\"false\"}}}");
    }

    @Test
    public void legacyArmorModesAndCategoriesAreNormalized() {
        assertEquals("mixed", EquipmentSettings.mode("MIXED"));
        assertEquals("priority", EquipmentSettings.mode("anything else"));
        assertEquals("crystal", EquipmentSettings.category("Crystal"));
        assertEquals("none", EquipmentSettings.category("not-a-category"));
    }

    private static void assertRejected(String source) {
        try {
            EquipmentSounds.applyLayer(new StringReader(source), "test", new HashMap<String, String>());
            fail("invalid equipment layer accepted");
        } catch (JsonParseException expected) {}
    }
}
