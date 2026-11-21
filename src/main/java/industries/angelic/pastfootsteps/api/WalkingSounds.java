package industries.angelic.pastfootsteps.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.client.FootstepController;
import industries.angelic.pastfootsteps.profile.LocalJsonOverrides;
import industries.angelic.pastfootsteps.sound.SoundEngine;

/** Experimental walk-only resource-pack adapter. Does not depend on the AET editor. */
public final class WalkingSounds {

    public static final ResourceLocation RESOURCE = new ResourceLocation("pastfootsteps", "sound_editor/walking.json");
    private static Map<String, String> active = Collections.emptyMap();
    private static Map<String, String> origins = Collections.emptyMap();

    private WalkingSounds() {}

    /** Small optional-editor API: null means the original PF acoustic remains in charge. */
    public static String replacement(String material) {
        return active.get(material);
    }

    /** Shared block-material view for editors, rebuilt only when the browser is opened. */
    public static Map<String, List<ItemStack>> materials() {
        return FootstepController.INSTANCE.walkingMaterials();
    }

    public static String origin(String material) {
        return origins.get(material);
    }

    public static void reload(IResourceManager manager, SoundEngine engine) {
        Map<String, String> merged = new TreeMap<>();
        Map<String, String> sources = new TreeMap<>();
        int layer = 0;
        try {
            for (IResource resource : manager.getAllResources(RESOURCE)) {
                String origin = RESOURCE + " (layer " + (++layer) + ")";
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    StringBuilder source = new StringBuilder();
                    char[] buffer = new char[4096];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        source.append(buffer, 0, count);
                        if (source.length() > 1024 * 1024) throw new IOException("Walking override exceeds 1 MiB");
                    }
                    Map<String, String> accepted = new TreeMap<>();
                    WalkingSoundFormat.applyLayer(accepted, LocalJsonOverrides.read(source.toString()), sound -> {
                        boolean exists = Minecraft.getMinecraft()
                            .getSoundHandler()
                            .getSound(new ResourceLocation(sound)) != null;
                        if (!exists) PastFootsteps.LOG.warn("Ignoring unavailable walking sound {}", sound);
                        return exists;
                    });
                    merged.putAll(accepted);
                    accepted.keySet()
                        .forEach(material -> sources.put(material, origin));
                } catch (Exception e) {
                    PastFootsteps.LOG.warn("Skipping invalid walking sound override layer", e);
                }
            }
        } catch (FileNotFoundException absent) {
            // No editor packs installed is the normal default.
        } catch (Exception e) {
            PastFootsteps.LOG.warn("Could not read walking sound overrides", e);
        }
        active = Collections.unmodifiableMap(merged);
        origins = Collections.unmodifiableMap(sources);
        engine.walkingOverrides(active);
    }
}
