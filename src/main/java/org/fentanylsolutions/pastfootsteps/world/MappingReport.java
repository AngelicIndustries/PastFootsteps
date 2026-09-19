package org.fentanylsolutions.pastfootsteps.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.Block;

import org.fentanylsolutions.pastfootsteps.Tags;
import org.fentanylsolutions.pastfootsteps.sound.SoundEngine;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** On-demand registry diagnostics. Never requests world state, tile covers or creative item variants. */
public final class MappingReport {

    private static final String[] SUBSTRATES = { "", "carpet", "foliage", "foliage_low", "messy", "wet", "bigger" };

    private MappingReport() {}

    public static String generate(MaterialLibrary materials, SoundEngine sound, String materialMap,
        boolean unmappedOnly) {
        Map<String, Block> blocks = new TreeMap<>();
        for (Object key : Block.blockRegistry.getKeys()) {
            if (!(key instanceof String)) continue;
            Object block = Block.blockRegistry.getObject((String) key);
            if (block instanceof Block) blocks.put((String) key, (Block) block);
        }
        return new GsonBuilder().setPrettyPrinting()
            .create()
            .toJson(create(materials, sound, materialMap, unmappedOnly, blocks)) + "\n";
    }

    /** Package-private registry injection keeps tests independent of unrelated test block registrations. */
    static JsonObject create(MaterialLibrary materials, SoundEngine sound, String materialMap, boolean unmappedOnly,
        Map<String, Block> blocks) {
        if (materialMap == null || !materialMap.isEmpty() && !materials.materialMapNames()
            .contains(materialMap)) throw new IllegalArgumentException("Unknown material map: " + materialMap);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("version", Tags.VERSION);
        root.addProperty("mode", unmappedOnly ? "unmapped" : "all");
        root.addProperty("materialMap", materialMap);
        root.addProperty("acousticsLoaded", sound.ready());
        root.addProperty(
            "scope",
            "Static registry lookup for metadata 0..15, including metadata not used by a block. "
                + "World-dependent covers, collision/overlay selection and double-plant normalization are not evaluated.");
        root.addProperty(
            "unmappedMeaning",
            "Normal surface uses primitive/native fallback, a mapping references a "
                + "missing acoustic definition, or lookup failed. Explicit silence and VANILLA mappings are intentional.");
        JsonArray maps = new JsonArray();
        for (String id : new TreeSet<>(materials.materialMapNames())) maps.add(new JsonPrimitive(id));
        root.add("availableMaterialMaps", maps);
        JsonArray output = new JsonArray();
        int scanned = 0, included = 0, needsMapping = 0, errors = 0;
        for (Map.Entry<String, Block> entry : new TreeMap<>(blocks).entrySet()) {
            Block block = entry.getValue();
            Map<String, JsonObject> groups = new LinkedHashMap<>();
            for (int meta = 0; meta < 16; meta++) {
                scanned++;
                JsonObject state = new JsonObject();
                boolean attention = false;
                try {
                    for (String substrate : SUBSTRATES) {
                        MaterialLibrary.Association association = materials
                            .resolve(block, meta, substrate, materialMap);
                        if (!substrate.isEmpty() && association.origin == MaterialLibrary.Origin.NONE) continue;
                        JsonObject result = describe(association, sound);
                        state.add(substrate.isEmpty() ? "normal" : substrate, result);
                        attention |= result.has("missingAcoustics") || substrate.isEmpty() && fallback(association);
                    }
                } catch (RuntimeException | LinkageError error) {
                    state.addProperty(
                        "error",
                        error.getClass()
                            .getSimpleName());
                    attention = true;
                    errors++;
                }
                state.addProperty("needsMapping", attention);
                if (attention) needsMapping++;
                if (unmappedOnly && !attention) continue;
                included++;
                String signature = state.toString();
                JsonObject group = groups.get(signature);
                if (group == null) {
                    group = new JsonObject();
                    group.add("metadata", new JsonArray());
                    group.add("resolution", state);
                    groups.put(signature, group);
                }
                group.getAsJsonArray("metadata")
                    .add(new JsonPrimitive(meta));
            }
            if (groups.isEmpty()) continue;
            JsonObject item = new JsonObject();
            item.addProperty("block", entry.getKey());
            item.addProperty(
                "class",
                block.getClass()
                    .getName());
            JsonArray states = new JsonArray();
            for (JsonObject group : groups.values()) states.add(group);
            item.add("states", states);
            output.add(item);
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("registeredBlocks", blocks.size());
        summary.addProperty("scannedStates", scanned);
        summary.addProperty("includedBlocks", output.size());
        summary.addProperty("includedStates", included);
        summary.addProperty("statesNeedingMapping", needsMapping);
        summary.addProperty("statesWithErrors", errors);
        root.add("summary", summary);
        root.add("blocks", output);
        return root;
    }

    private static boolean fallback(MaterialLibrary.Association association) {
        if (association.name != null && special(association.name)) return false;
        return association.origin == MaterialLibrary.Origin.PRIMITIVE
            || association.origin == MaterialLibrary.Origin.BASE_PRIMITIVE
            || association.origin == MaterialLibrary.Origin.NATIVE;
    }

    private static JsonObject describe(MaterialLibrary.Association association, SoundEngine sound) {
        JsonObject result = new JsonObject();
        result.addProperty("source", association.origin.name());
        if (association.name != null) result.addProperty("acoustic", association.name);
        if (association.origin == MaterialLibrary.Origin.BASE_BLOCK
            || association.origin == MaterialLibrary.Origin.BASE_PRIMITIVE
            || association.origin == MaterialLibrary.Origin.NATIVE) {
            Object source = Block.blockRegistry.getNameForObject(association.sourceBlock);
            if (source instanceof String) result.addProperty("sourceBlock", (String) source);
            // Native metadata does not affect SoundType; do not split otherwise-identical groups.
            if (association.origin != MaterialLibrary.Origin.NATIVE)
                result.addProperty("sourceMetadata", association.sourceMetadata);
        }
        if (association.sound != null) {
            result.addProperty("nativeSound", association.sound.getStepResourcePath());
            result.addProperty("nativeVolume", association.sound.getVolume() * 0.15F);
            result.addProperty("nativePitch", association.sound.getPitch());
        }
        JsonArray missing = new JsonArray();
        if (association.name != null) for (String part : association.name.split(",")) {
            String name = part.trim();
            if (!name.isEmpty() && !special(name) && !sound.canPlay(name)) missing.add(new JsonPrimitive(name));
        }
        if (missing.size() > 0) result.add("missingAcoustics", missing);
        return result;
    }

    private static boolean special(String name) {
        return "NOT_EMITTER".equals(name) || "NON_EMITTER".equals(name)
            || "VANILLA".equals(name)
            || "MESSY_GROUND".equals(name)
            || "UNASSIGNED".equals(name);
    }
}
