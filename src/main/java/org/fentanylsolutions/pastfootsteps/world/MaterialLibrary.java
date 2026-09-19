package org.fentanylsolutions.pastfootsteps.world;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;
import org.fentanylsolutions.pastfootsteps.profile.LocalJsonOverrides;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/** Loads the material maps supplied by the built-in pack and resource packs. */
public final class MaterialLibrary {

    private static final ResourceLocation BLOCK_MAP = new ResourceLocation("presencefootsteps", "config/blockmap.json");
    private static final ResourceLocation PRIMITIVE_MAP = new ResourceLocation(
        "presencefootsteps",
        "config/primitivemap.json");
    private static final ResourceLocation BIOME_MAP = new ResourceLocation(
        "presencefootsteps",
        "config/biomevariancemap.json");
    private static final Gson GSON = new Gson();

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private final LocalJsonOverrides local = new LocalJsonOverrides();

    /**
     * Builds a complete replacement snapshot. Resources are returned from lowest to highest priority,
     * so later valid entries override earlier ones. A malformed layer is skipped without discarding
     * valid lower-priority data.
     */
    public void reload(IResourceManager resources) {
        reload(resources, null);
    }

    /**
     * Reloads pack layers and optional local layers. Local files are applied last and are therefore
     * suited to small per-installation corrections without repacking the bundled maps.
     */
    public void reload(IResourceManager resources, File directory) {
        reload(resources, directory, Collections.<String>emptySet());
    }

    /** Loads only maps referenced by compiled profiles; no resource access occurs during contact resolution. */
    public void reload(IResourceManager resources, File directory, Set<String> materialMaps) {
        if (resources == null) return;

        Map<String, String> blocks = new LinkedHashMap<String, String>();
        Map<String, String> primitives = new LinkedHashMap<String, String>();
        Map<String, BiomeVariance> biomes = new LinkedHashMap<String, BiomeVariance>();
        loadStringLayers(resources, BLOCK_MAP, blocks);
        loadStringLayers(resources, PRIMITIVE_MAP, primitives);
        loadBiomeLayers(resources, BIOME_MAP, biomes);
        loadLocalStrings(directory, "profiles/blockmap.json", "block material map", blocks);
        loadLocalStrings(directory, "profiles/primitivemap.json", "primitive material map", primitives);
        loadLocalBiomes(directory, "profiles/biomevariancemap.json", biomes);
        Map<String, Map<String, String>> actorBlocks = new LinkedHashMap<>();
        for (String id : new TreeSet<>(materialMaps)) {
            if (id.isEmpty()) continue;
            if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid material map ID: " + id);
            Map<String, String> entries = new LinkedHashMap<>();
            boolean loaded = loadStringLayers(
                resources,
                new ResourceLocation("pastfootsteps", "config/materials/" + id + ".json"),
                entries,
                true);
            loaded |= loadLocalStrings(directory, "profiles/materials/" + id + ".json", "material map " + id, entries);
            if (!loaded) PastFootsteps.LOG.warn("Material map '{}' is missing or invalid; using shared mappings", id);
            actorBlocks.put(id, entries);
        }
        if (directory != null) local.dropMissingUnder(new File(directory, "profiles/materials"));
        snapshot = new Snapshot(blocks, primitives, biomes, actorBlocks);
    }

    /**
     * Resolves an explicit block entry. Primitive fallback is used only for the normal surface and
     * the {@code bigger} lookup used for fences and other narrow supporting blocks.
     */
    public String lookup(Block block, int meta, String substrate) {
        return lookup(block, meta, substrate, "");
    }

    /** Actor map, then shared map, then primitive fallback. Explicit UNASSIGNED skips the block-map stage. */
    public String lookup(Block block, int meta, String substrate, String materialMap) {
        if (block == null) return null;
        Object registeredName = Block.blockRegistry.getNameForObject(block);
        if (!(registeredName instanceof String)) return null;

        Snapshot current = snapshot;
        String name = ((String) registeredName).toLowerCase(Locale.ROOT);
        String suffix = normalizeSubstrate(substrate);
        Map<String, String> actorMap = current.actorBlocks.get(materialMap);
        String result = actorMap == null ? null : explicit(actorMap, name, meta, suffix);
        if (result == null) result = explicit(current.blocks, name, meta, suffix);
        if (result != null && !"UNASSIGNED".equals(result)) return result;

        if (!suffix.isEmpty() && !"bigger".equals(suffix)) return null;
        result = primitive(current.primitives, block);
        return "UNASSIGNED".equals(result) ? null : result;
    }

    public float biomeVolume(BiomeGenBase biome) {
        BiomeVariance variance = biomeVariance(snapshot.biomes, biome);
        return variance == null ? 1.0F : variance.volume;
    }

    public float biomePitch(BiomeGenBase biome) {
        BiomeVariance variance = biomeVariance(snapshot.biomes, biome);
        return variance == null ? 1.0F : variance.pitch;
    }

    private static String explicit(Map<String, String> map, String name, int meta, String suffix) {
        String ending = suffix.isEmpty() ? "" : "." + suffix;
        String value = map.get(name + "^" + (meta & 15) + ending);
        if (value != null) return value;
        value = map.get(name + ending);
        if (value != null || !name.startsWith("minecraft:")) return value;
        // Older PF maps commonly omit minecraft: for vanilla blocks.
        String path = name.substring(name.indexOf(':') + 1);
        value = map.get(path + "^" + (meta & 15) + ending);
        return value != null ? value : map.get(path + ending);
    }

    private static String primitive(Map<String, String> map, Block block) {
        Block.SoundType sound = block.stepSound;
        if (sound == null) return null;
        // The legacy PF primitive key is SoundType.soundName ("stone", "wood", "anvil"),
        // not the actual step event returned by func_150496_b().
        String step = stripSoundPrefix(sound.soundName);
        if (step.isEmpty()) return null;

        String dimensions = String.format(Locale.ROOT, "%.2f_%.2f", sound.getVolume(), sound.getPitch());
        String value = map.get(step + "@" + dimensions);
        if (value != null) return value;
        // Current PF resource packs use registry-style step-event names; this spelling maps cleanly to 1.7 SoundType.
        value = map.get("minecraft:block." + step + ".step@" + dimensions);
        if (value != null) return value;

        String breaking = sound.getBreakSound();
        if (breaking != null && !breaking.isEmpty()) {
            value = map.get(step + "@break_" + breaking);
            if (value != null) return value;
            value = map.get("minecraft:block." + step + ".step@break_" + breaking);
            if (value != null) return value;
        }
        value = map.get(step);
        return value != null ? value : map.get("minecraft:block." + step + ".step");
    }

    private static String stripSoundPrefix(String sound) {
        if (sound == null) return "";
        int dot = sound.indexOf('.');
        return (dot >= 0 ? sound.substring(dot + 1) : sound).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSubstrate(String substrate) {
        if (substrate == null || "default".equalsIgnoreCase(substrate)) return "";
        String value = substrate.trim()
            .toLowerCase(Locale.ROOT);
        if ("carpet".equals(value) || "foliage".equals(value)
            || "messy".equals(value)
            || "wet".equals(value)
            || "bigger".equals(value)) return value;
        return value;
    }

    private static BiomeVariance biomeVariance(Map<String, BiomeVariance> map, BiomeGenBase biome) {
        if (biome == null) return null;
        BiomeVariance variance = map.get(Integer.toString(biome.biomeID));
        if (variance != null || biome.biomeName == null) return variance;
        String name = biome.biomeName.trim()
            .toLowerCase(Locale.ROOT);
        variance = map.get(name);
        if (variance != null) return variance;
        name = name.replace(' ', '_');
        variance = map.get(name);
        return variance != null ? variance : map.get("minecraft:" + name);
    }

    private static void loadStringLayers(IResourceManager manager, ResourceLocation location,
        Map<String, String> destination) {
        loadStringLayers(manager, location, destination, false);
    }

    private static boolean loadStringLayers(IResourceManager manager, ResourceLocation location,
        Map<String, String> destination, boolean optional) {
        boolean loaded = false;
        for (IResource resource : all(manager, location, optional)) {
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) throw new JsonParseException("expected a JSON object");
                destination.putAll(stringLayer(root));
                loaded = true;
            } catch (Exception e) {
                PastFootsteps.LOG.warn("Skipping invalid material map layer {} ({})", location, resource, e);
            }
        }
        return loaded;
    }

    private static void loadBiomeLayers(IResourceManager manager, ResourceLocation location,
        Map<String, BiomeVariance> destination) {
        for (IResource resource : all(manager, location)) {
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) throw new JsonParseException("expected a JSON object");
                destination.putAll(biomeLayer(root));
            } catch (Exception e) {
                PastFootsteps.LOG.warn("Skipping invalid biome map layer {} ({})", location, resource, e);
            }
        }
    }

    private boolean loadLocalStrings(File directory, String path, String description, Map<String, String> destination) {
        if (directory == null) return false;
        Map<String, String> layer = local
            .load(new File(directory, path), description, value -> stringLayer(object(value)));
        if (layer != null) destination.putAll(layer);
        return layer != null;
    }

    private void loadLocalBiomes(File directory, String path, Map<String, BiomeVariance> destination) {
        if (directory == null) return;
        Map<String, BiomeVariance> layer = local
            .load(new File(directory, path), "biome variance map", value -> biomeLayer(object(value)));
        if (layer != null) destination.putAll(layer);
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new JsonParseException("expected a JSON object");
        return value.getAsJsonObject();
    }

    private static Map<String, String> stringLayer(JsonObject root) {
        Map<String, String> layer = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue()
                .isJsonPrimitive()
                || !entry.getValue()
                    .getAsJsonPrimitive()
                    .isString()) {
                throw new JsonParseException("entry '" + entry.getKey() + "' must be a string");
            }
            layer.put(
                entry.getKey()
                    .replace('>', ':')
                    .toLowerCase(Locale.ROOT),
                materialValue(
                    entry.getValue()
                        .getAsString()));
        }
        return layer;
    }

    private static String materialValue(String raw) {
        String value = raw.split("#", 2)[0].trim();
        if ("UNASSIGNED".equalsIgnoreCase(value)) return "UNASSIGNED";
        if ("NOT_EMITTER".equalsIgnoreCase(value) || "NON_EMITTER".equalsIgnoreCase(value)) return "NOT_EMITTER";
        if ("MESSY_GROUND".equalsIgnoreCase(value)) return "MESSY_GROUND";
        if ("VANILLA".equalsIgnoreCase(value)) return "VANILLA";
        return value.toLowerCase(Locale.ROOT);
    }

    private static Map<String, BiomeVariance> biomeLayer(JsonObject root) {
        Map<String, BiomeVariance> layer = new LinkedHashMap<String, BiomeVariance>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue()
                .isJsonObject()) {
                throw new JsonParseException("biome '" + entry.getKey() + "' must be an object");
            }
            JsonObject object = entry.getValue()
                .getAsJsonObject();
            layer.put(
                entry.getKey()
                    .trim()
                    .toLowerCase(Locale.ROOT),
                new BiomeVariance(number(object, "volume", 1.0F), number(object, "pitch", 1.0F)));
        }
        return layer;
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isNumber()) {
            throw new JsonParseException(key + " must be a number");
        }
        float result = value.getAsFloat();
        if (Float.isNaN(result) || Float.isInfinite(result) || result < 0.0F) {
            throw new JsonParseException(key + " must be a finite non-negative number");
        }
        return result;
    }

    private static List<IResource> all(IResourceManager manager, ResourceLocation location) {
        return all(manager, location, false);
    }

    private static List<IResource> all(IResourceManager manager, ResourceLocation location, boolean optional) {
        try {
            return manager.getAllResources(location);
        } catch (IOException e) {
            if (!optional) PastFootsteps.LOG.warn("Could not load material resource {}", location, e);
            return Collections.emptyList();
        }
    }

    private static final class BiomeVariance {

        final float volume;
        final float pitch;

        BiomeVariance(float volume, float pitch) {
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static final class Snapshot {

        static final Snapshot EMPTY = new Snapshot(
            Collections.<String, String>emptyMap(),
            Collections.<String, String>emptyMap(),
            Collections.<String, BiomeVariance>emptyMap(),
            Collections.<String, Map<String, String>>emptyMap());
        final Map<String, String> blocks;
        final Map<String, String> primitives;
        final Map<String, BiomeVariance> biomes;
        final Map<String, Map<String, String>> actorBlocks;

        Snapshot(Map<String, String> blocks, Map<String, String> primitives, Map<String, BiomeVariance> biomes,
            Map<String, Map<String, String>> actorBlocks) {
            this.blocks = Collections.unmodifiableMap(new LinkedHashMap<String, String>(blocks));
            this.primitives = Collections.unmodifiableMap(new LinkedHashMap<String, String>(primitives));
            this.biomes = Collections.unmodifiableMap(new LinkedHashMap<String, BiomeVariance>(biomes));
            Map<String, Map<String, String>> maps = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : actorBlocks.entrySet())
                maps.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
            this.actorBlocks = Collections.unmodifiableMap(maps);
        }
    }
}
