package industries.angelic.pastfootsteps.api;

import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/** Slice 1: namespaced sound-event replacements for exact walking contacts. */
public final class WalkingSoundFormat {

    private WalkingSoundFormat() {}

    /** Invalid layers are atomic; missing sound events retain the lower-priority replacement. */
    public static void applyLayer(Map<String, String> merged, JsonElement json,
        java.util.function.Predicate<String> available) {
        Map<String, String> layer = decode(json);
        layer.forEach((material, sound) -> { if (available.test(sound)) merged.put(material, sound); });
    }

    public static Map<String, String> decode(JsonElement json) {
        JsonObject root = json.getAsJsonObject();
        if (!root.has("schemaVersion") || !"1".equals(
            root.get("schemaVersion")
                .toString()))
            throw new JsonParseException("Unsupported walking sound schemaVersion");
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("walking")
            .entrySet()) {
            if (!entry.getKey()
                .matches("[a-z0-9_.-]+")
                || !entry.getValue()
                    .isJsonPrimitive()
                || !entry.getValue()
                    .getAsJsonPrimitive()
                    .isString())
                throw new JsonParseException("Invalid walking entry");
            String sound = entry.getValue()
                .getAsString();
            if (!sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new JsonParseException("Expected namespaced sound event");
            result.put(entry.getKey(), sound);
        }
        return result;
    }
}
