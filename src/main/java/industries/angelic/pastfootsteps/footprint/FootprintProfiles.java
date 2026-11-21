package industries.angelic.pastfootsteps.footprint;

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

/** Independent visual rules selected only after PF has resolved a real contact. */
public final class FootprintProfiles {

    private static final ResourceLocation RESOURCE = new ResourceLocation("pastfootsteps", "config/footprints.json");
    private Snapshot snapshot = new Snapshot();
    private String lastLocal, localPath;

    public enum Style {
        SHOE,
        SQUARE,
        HORSESHOE,
        BIRD,
        PAW,
        SQUARE_SOLID,
        LOWRES_SQUARE
    }

    public static final class ActorRule {

        public final Style style;
        public final double width, length, density;
        public final boolean enabled;

        ActorRule(JsonObject value) {
            JsonData.keys(value, "enabled", "style", "width", "length", "density");
            enabled = JsonData.bool(value, "enabled", true);
            try {
                style = Style.valueOf(JsonData.string(value, "style", "LOWRES_SQUARE"));
            } catch (IllegalArgumentException error) {
                throw new JsonParseException("Unknown footprint style");
            }
            width = JsonData.number(value, "width", 0.4, 0.01, 2);
            length = JsonData.number(value, "length", 0.75, 0.01, 3);
            density = JsonData.number(value, "density", 1, 0, 1);
        }
    }

    public static final class SurfaceRule {

        public final boolean enabled;
        public final double opacity;
        public final int lifetime;

        SurfaceRule(JsonObject value) {
            JsonData.keys(value, "enabled", "opacity", "lifetime");
            enabled = JsonData.bool(value, "enabled", true);
            opacity = JsonData.number(value, "opacity", -1, 0, 1);
            double life = JsonData.number(value, "lifetime", -1, 1, 12000);
            if (life != Math.rint(life)) throw new JsonParseException("lifetime must be a whole number of ticks");
            lifetime = (int) life;
        }
    }

    private static final class Snapshot {

        final Map<String, ActorRule> entities = new LinkedHashMap<>();
        final Map<String, SurfaceRule> soundTypes = new LinkedHashMap<>(), blocks = new LinkedHashMap<>();

        Snapshot() {}

        Snapshot(Snapshot from) {
            entities.putAll(from.entities);
            soundTypes.putAll(from.soundTypes);
            blocks.putAll(from.blocks);
        }
    }

    public ActorRule actor(String id, boolean child) {
        ActorRule value = snapshot.entities.get(id + (child ? "#child" : "#adult"));
        return value == null ? snapshot.entities.get(id) : value;
    }

    public SurfaceRule surface(String block, int meta, String soundType) {
        SurfaceRule value = snapshot.blocks.get(block + "@" + meta);
        if (value == null) value = snapshot.blocks.get(block);
        return value == null ? snapshot.soundTypes.get(soundType) : value;
    }

    public void reload(IResourceManager manager, File directory) {
        Snapshot next = new Snapshot();
        try {
            for (IResource resource : manager.getAllResources(RESOURCE)) {
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    next = apply(next, JsonData.read(reader));
                } catch (Exception error) {
                    PastFootsteps.LOG.warn("Skipping invalid footprint resource layer", error);
                }
            }
        } catch (Exception error) {
            PastFootsteps.LOG.warn("Cannot load footprint resources; retaining last valid rules", error);
            return;
        }
        File local = directory == null ? null : new File(directory, "profiles/footprints.json");
        String path = local == null ? "" : local.getAbsolutePath();
        if (!path.equals(localPath)) {
            localPath = path;
            lastLocal = null;
        }
        if (local != null && local.isFile()) {
            try {
                if (local.length() > 1024 * 1024) throw new JsonParseException("Footprint file exceeds 1 MiB");
                String source = new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8);
                next = apply(next, JsonData.read(new StringReader(source)));
                lastLocal = source;
            } catch (Exception error) {
                PastFootsteps.LOG.warn("Invalid local footprint rules; keeping last valid local layer", error);
                if (lastLocal != null) {
                    try {
                        next = apply(next, JsonData.read(new StringReader(lastLocal)));
                    } catch (Exception cached) {
                        PastFootsteps.LOG.warn("Cached footprint rules invalid", cached);
                    }
                }
            }
        } else lastLocal = null;
        snapshot = next;
    }

    /** Atomic layer replacement: a malformed entry cannot partially override previous rules. */
    private static Snapshot apply(Snapshot previous, JsonObject root) {
        JsonData.keys(root, "schemaVersion", "entities", "soundTypes", "blocks");
        if (JsonData.number(root, "schemaVersion", -1, 1, 1) != 1)
            throw new JsonParseException("schemaVersion must be 1");
        Snapshot next = new Snapshot(previous);
        if (root.has("entities")) for (Map.Entry<String, JsonElement> entry : JsonData.object(root.get("entities"))
            .entrySet()) {
                next.entities.put(entry.getKey(), new ActorRule(JsonData.object(entry.getValue())));
            }
        applySurfaces(root, "soundTypes", next.soundTypes);
        applySurfaces(root, "blocks", next.blocks);
        return next;
    }

    private static void applySurfaces(JsonObject root, String key, Map<String, SurfaceRule> into) {
        if (root.has(key)) for (Map.Entry<String, JsonElement> entry : JsonData.object(root.get(key))
            .entrySet()) {
                into.put(entry.getKey(), new SurfaceRule(JsonData.object(entry.getValue())));
            }
    }
}
