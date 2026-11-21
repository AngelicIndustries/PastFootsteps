package industries.angelic.pastfootsteps.api;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.google.gson.JsonParser;

public class WalkingSoundFormatTest {

    @Test
    public void higherLayerWinsOnlyItsValidAvailableEntries() {
        Map<String, String> merged = new TreeMap<>();
        WalkingSoundFormat.applyLayer(
            merged,
            new JsonParser().parse(
                "{\"schemaVersion\":1,\"walking\":{\"stone\":\"minecraft:step.stone\",\"wood\":\"minecraft:step.wood\"}}"),
            sound -> true);
        WalkingSoundFormat.applyLayer(
            merged,
            new JsonParser().parse(
                "{\"schemaVersion\":1,\"walking\":{\"stone\":\"minecraft:step.gravel\",\"wood\":\"missing:event\"}}"),
            sound -> !sound.startsWith("missing:"));
        assertEquals("minecraft:step.gravel", merged.get("stone"));
        assertEquals("minecraft:step.wood", merged.get("wood"));
    }

    @Test
    public void malformedLayerDoesNotPartiallyApply() {
        Map<String, String> merged = new TreeMap<>();
        merged.put("stone", "minecraft:step.stone");
        try {
            WalkingSoundFormat.applyLayer(
                merged,
                new JsonParser()
                    .parse("{\"schemaVersion\":1,\"walking\":{\"stone\":\"minecraft:step.gravel\",\"wood\":17}}"),
                sound -> true);
            fail("Expected invalid layer");
        } catch (RuntimeException expected) {
            assertEquals("minecraft:step.stone", merged.get("stone"));
        }
    }
}
