package industries.angelic.pastfootsteps.world;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.profile.JsonData;

/** Exact entity-ID mappings for legacy entities whose own collision box can be walked on. */
final class SupportProfiles {

    private static final ResourceLocation RESOURCE = new ResourceLocation("pastfootsteps", "config/supports.json");
    private static final int MAX_LOCAL_BYTES = 1024 * 1024;
    private volatile Map<String, Rule> rules = java.util.Collections.emptyMap();
    private String localPath, lastGoodLocal;

    static final class Rule {

        final boolean enabled;
        final String material, vanillaSound;
        final float volume, pitch;

        Rule(JsonObject value) {
            JsonData.keys(value, "enabled", "material", "vanillaSound", "volume", "pitch");
            enabled = JsonData.bool(value, "enabled", true);
            material = JsonData.string(value, "material", "wood");
            vanillaSound = JsonData.string(value, "vanillaSound", "step.wood");
            volume = (float) JsonData.number(value, "volume", 0.15, 0, 2);
            pitch = (float) JsonData.number(value, "pitch", 1, 0.01, 4);
            if (material.isEmpty()) throw new JsonParseException("support material must not be empty");
        }
    }

    Rule rule(String entityId) {
        Rule rule = entityId == null ? null : rules.get(entityId);
        return rule == null || !rule.enabled ? null : rule;
    }

    synchronized void reload(IResourceManager manager, File directory) {
        if (manager == null) return;
        Map<String, Rule> next = new LinkedHashMap<String, Rule>();
        try {
            for (IResource resource : manager.getAllResources(RESOURCE)) {
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    apply(next, JsonData.read(reader));
                } catch (Exception error) {
                    PastFootsteps.LOG.warn("Skipping invalid support profile resource layer", error);
                }
            }
        } catch (Exception error) {
            PastFootsteps.LOG.warn("Cannot load support profile resources; retaining last valid rules", error);
            return;
        }
        File local = directory == null ? null : new File(directory, "profiles/supports.json");
        String path = local == null ? "" : local.getAbsolutePath();
        if (!path.equals(localPath)) {
            localPath = path;
            lastGoodLocal = null;
        }
        if (local != null && local.isFile()) {
            try {
                if (local.length() > MAX_LOCAL_BYTES)
                    throw new JsonParseException("Support profile file exceeds 1 MiB");
                String source = new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8);
                apply(next, JsonData.read(new StringReader(source)));
                lastGoodLocal = source;
            } catch (Exception error) {
                PastFootsteps.LOG.warn("Invalid local support profiles; keeping last valid local layer", error);
                if (lastGoodLocal != null) try {
                    apply(next, JsonData.read(new StringReader(lastGoodLocal)));
                } catch (Exception cached) {
                    lastGoodLocal = null;
                    PastFootsteps.LOG.warn("Cached support profiles are invalid", cached);
                }
            }
        } else lastGoodLocal = null;
        rules = java.util.Collections.unmodifiableMap(next);
    }

    /** Layer application is atomic, so a malformed entry cannot partially replace a lower rule. */
    static void apply(Map<String, Rule> destination, JsonObject root) {
        JsonData.keys(root, "schemaVersion", "entities");
        if (JsonData.number(root, "schemaVersion", -1, 1, 1) != 1)
            throw new JsonParseException("schemaVersion must be 1");
        JsonObject entities = JsonData.object(root.get("entities"));
        Map<String, Rule> replacements = new LinkedHashMap<String, Rule>();
        for (Map.Entry<String, JsonElement> entry : entities.entrySet()) {
            if (!entry.getKey()
                .matches("[A-Za-z0-9_.:-]{1,128}"))
                throw new JsonParseException("Invalid support entity ID " + entry.getKey());
            replacements.put(entry.getKey(), new Rule(JsonData.object(entry.getValue())));
        }
        destination.putAll(replacements);
    }
}
