package org.fentanylsolutions.pastfootsteps.equipment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;
import org.fentanylsolutions.pastfootsteps.client.ResolvedContact;
import org.fentanylsolutions.pastfootsteps.movement.ContactKind;
import org.fentanylsolutions.pastfootsteps.profile.JsonData;
import org.fentanylsolutions.pastfootsteps.sound.AcousticContext;
import org.fentanylsolutions.pastfootsteps.sound.AcousticEngine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Contact consumer for boot, body-armor and rain accents. It deliberately has no movement tracker: the controller
 * supplies every contact, including remote and landing contacts.
 */
public final class EquipmentSounds {

    private static final ResourceLocation DEFINITIONS = new ResourceLocation("pastfootsteps", "config/equipment.json");
    private static final int MAX_LOCAL_BYTES = 1024 * 1024;
    private final AcousticEngine engine;
    private final Map<EntityLivingBase, Long> lastBodyGroup = new WeakHashMap<EntityLivingBase, Long>();
    private volatile Map<String, String> itemCategories = Collections.emptyMap();
    private volatile List<String> diagnostics = Collections.emptyList();
    private String localPath, lastGoodLocal;

    public EquipmentSounds(AcousticEngine.Player sink) {
        engine = new AcousticEngine(sink);
        loadBuiltinAcoustics();
    }

    /**
     * Reloads layered bundled/resource-pack item definitions, followed by config/pastfootsteps/profiles/equipment.json.
     */
    public synchronized void reload(IResourceManager resources, File modConfigDirectory) {
        Map<String, String> categories = new HashMap<String, String>();
        List<String> messages = new ArrayList<String>();
        int valid = 0;
        if (resources != null) {
            try {
                List<IResource> layers = resources.getAllResources(DEFINITIONS);
                for (int i = 0; i < layers.size(); i++) {
                    IResource layer = layers.get(i);
                    try (Reader reader = new InputStreamReader(layer.getInputStream(), StandardCharsets.UTF_8)) {
                        applyLayer(reader, DEFINITIONS + " layer " + i, categories);
                        valid++;
                    } catch (Exception error) {
                        messages.add("Skipping invalid " + DEFINITIONS + " layer " + i + ": " + message(error));
                    }
                }
            } catch (Exception error) {
                messages.add("Could not enumerate " + DEFINITIONS + ": " + message(error));
            }
        }
        File local = modConfigDirectory == null ? null
            : new File(new File(modConfigDirectory, "profiles"), "equipment.json");
        String path = local == null ? null : local.getAbsolutePath();
        if (path == null ? localPath != null : !path.equals(localPath)) {
            localPath = path;
            lastGoodLocal = null;
        }
        if (local != null && local.isFile()) {
            try {
                String source = readLocal(local);
                applyLayer(new java.io.StringReader(source), local.toString(), categories);
                lastGoodLocal = source;
                valid++;
            } catch (Exception error) {
                messages.add("Skipping invalid local equipment file " + local + ": " + message(error));
                if (lastGoodLocal != null) try {
                    applyLayer(
                        new java.io.StringReader(lastGoodLocal),
                        local + " (cached last-valid layer)",
                        categories);
                    messages.add("Retained cached last-valid local equipment layer for " + local);
                    valid++;
                } catch (Exception cachedError) {
                    lastGoodLocal = null;
                    messages.add(
                        "Discarded invalid cached local equipment layer for " + local + ": " + message(cachedError));
                }
            }
        } else lastGoodLocal = null;
        if (valid > 0) itemCategories = Collections.unmodifiableMap(categories);
        diagnostics = Collections.unmodifiableList(messages);
        loadBuiltinAcoustics();
        for (String diagnostic : messages) PastFootsteps.LOG.warn(diagnostic);
    }

    /** Plays at most one body accent per simultaneous contact group, and at most one boot accent per foot contact. */
    public void play(ResolvedContact resolved, AcousticContext base) {
        play(resolved, base, true);
    }

    /** Body accents stay grouped even when only one foot supplies the impact's foot/rain audio. */
    public void play(ResolvedContact resolved, AcousticContext base, boolean footAudio) {
        if (resolved == null || base == null || !base.isValid() || resolved.actor == null) return;
        EntityLivingBase actor = resolved.actor;
        ContactKind kind = resolved.contact.kind;
        if (kind == ContactKind.STAND || kind == ContactKind.SWIM || kind == ContactKind.CLIMB) return;

        if (footAudio && EquipmentSettings.rain
            && resolved.rainExposed
            && allowed(actor, EquipmentSettings.rainEntities, EquipmentSettings.rainEntityListIsBlacklist)) {
            float strength = actor.worldObj == null ? 0 : actor.worldObj.getRainStrength(0);
            play("rain", kind, base, EquipmentSettings.rainVolume * strength, 1.75F);
        }

        if (!allowed(actor, EquipmentSettings.armorEntities, false)) return;
        ItemStack boots = actor.getEquipmentInSlot(1);
        String bootCategory = category(boots);
        if (footAudio && EquipmentSettings.footwear && bootCategory != null && !kind.isJump()) {
            play("foot." + bootCategory, kind, base, EquipmentSettings.footwearVolume, 1);
        }

        if (!EquipmentSettings.bodyArmor || !firstBodyContact(actor, resolved)) return;
        String chest = category(actor.getEquipmentInSlot(3));
        String legs = category(actor.getEquipmentInSlot(2));
        if ("mixed".equals(EquipmentSettings.armorMode)) {
            if (chest != null) play("body." + chest, kind, base, EquipmentSettings.armorVolume, 1);
            if (legs != null && !legs.equals(chest)) play("body." + legs, kind, base, EquipmentSettings.armorVolume, 1);
        } else {
            String body = priority(chest) >= priority(legs) ? chest : legs;
            if (body != null) play("body." + body, kind, base, EquipmentSettings.armorVolume, 1);
        }
    }

    public void forget(EntityLivingBase actor) {
        lastBodyGroup.remove(actor);
    }

    public void reset() {
        engine.clear();
        lastBodyGroup.clear();
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    String category(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        String override = id == null ? null : itemCategories.get(id);
        if (override != null) return "none".equals(override) ? null : override;
        if (!(stack.getItem() instanceof ItemArmor)) return null;
        ItemArmor.ArmorMaterial material = ((ItemArmor) stack.getItem()).getArmorMaterial();
        if (material == ItemArmor.ArmorMaterial.CLOTH) return "light";
        if (material == ItemArmor.ArmorMaterial.CHAIN) return "medium";
        if (material == ItemArmor.ArmorMaterial.IRON || material == ItemArmor.ArmorMaterial.GOLD) return "heavy";
        if (material == ItemArmor.ArmorMaterial.DIAMOND) return "crystal";
        return "none".equals(EquipmentSettings.defaultCategory) ? null : EquipmentSettings.defaultCategory;
    }

    private boolean firstBodyContact(EntityLivingBase actor, ResolvedContact resolved) {
        long group = resolved.contact.group;
        Long previous = lastBodyGroup.get(actor);
        if (previous != null && previous.longValue() == group) return false;
        lastBodyGroup.put(actor, group);
        return true;
    }

    private void play(String acoustic, ContactKind kind, AcousticContext base, float volume, float pitch) {
        if (volume <= 0) return;
        engine.play(acoustic, kind == ContactKind.JUMP_STILL ? "jump" : kind.acousticKey, base.scale(volume, pitch));
    }

    private void loadBuiltinAcoustics() {
        engine.load("rain", basic("equipment.rain"));
        for (String category : new String[] { "light", "medium", "heavy", "crystal", "slime" }) {
            engine.load("foot." + category, basic("equipment.armor." + category + ".foot"));
            JsonObject body = new JsonObject();
            body.addProperty("type", "events");
            body.add("walk", basic("equipment.armor." + category + ".body"));
            body.add("run", basic("equipment.armor." + category + ".run"));
            body.add("land", basic("equipment.armor." + category + ".body"));
            body.add("jump", basic("equipment.armor." + category + ".body"));
            engine.load("body." + category, body);
        }
        // AET's optional Et Futurum mapping is retained as an explicitly silent category until a pack supplies it.
        engine.load("foot.elytra", basic(""));
        engine.load("body.elytra", basic(""));
    }

    private static JsonObject basic(String sound) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "basic");
        result.addProperty("name", sound);
        return result;
    }

    static void applyLayer(Reader reader, String label, Map<String, String> destination) {
        JsonObject root;
        try {
            root = JsonData.read(reader);
        } catch (IOException error) {
            throw new JsonParseException(label + " could not be read", error);
        }
        rejectUnknown(root, label, "schemaVersion", "items");
        double schema = JsonData.number(root, "schemaVersion", Double.NaN, 1, 1);
        if (Double.isNaN(schema) || schema != 1 || schema != Math.rint(schema))
            throw new JsonParseException(label + ".schemaVersion must be 1");
        if (!root.has("items") || !root.get("items")
            .isJsonObject()) throw new JsonParseException(label + ".items must be an object");
        Map<String, String> replacements = new HashMap<String, String>();
        Set<String> removals = new HashSet<String>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("items")
            .entrySet()) {
            String id = entry.getKey();
            if (!id.matches("[A-Za-z0-9_.:-]{1,128}")) throw new JsonParseException("Invalid item ID " + id);
            if (!entry.getValue()
                .isJsonObject()) throw new JsonParseException(label + ".items." + id + " must be an object");
            JsonObject value = entry.getValue()
                .getAsJsonObject();
            rejectUnknown(value, label + ".items." + id, "enabled", "category");
            if (!JsonData.bool(value, "enabled", true)) {
                removals.add(id);
                continue;
            }
            String rawCategory = JsonData.string(value, "category", null);
            if (rawCategory == null)
                throw new JsonParseException(label + ".items." + id + ".category must be a string");
            String category = EquipmentSettings.category(rawCategory);
            if ("none".equals(category) && !"none".equalsIgnoreCase(rawCategory))
                throw new JsonParseException(label + ".items." + id + " has an unknown category");
            replacements.put(id, category);
        }
        for (String id : removals) destination.remove(id);
        destination.putAll(replacements);
    }

    private static void rejectUnknown(JsonObject object, String label, String... allowed) {
        Set<String> keys = new HashSet<String>();
        Collections.addAll(keys, allowed);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) if (!keys.contains(entry.getKey()))
            throw new JsonParseException(label + " has unknown field " + entry.getKey());
    }

    private static String readLocal(File file) throws IOException {
        if (file.length() > MAX_LOCAL_BYTES) throw new IOException("file exceeds " + MAX_LOCAL_BYTES + " bytes");
        StringBuilder source = new StringBuilder();
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (source.length() + read > MAX_LOCAL_BYTES)
                    throw new IOException("file exceeds " + MAX_LOCAL_BYTES + " bytes");
                source.append(buffer, 0, read);
            }
        }
        return source.toString();
    }

    private static boolean allowed(EntityLivingBase actor, String[] ids, boolean blacklist) {
        String id = actor instanceof EntityPlayer ? "Player" : EntityList.getEntityString(actor);
        boolean listed = false;
        if (ids != null) for (String candidate : ids) if (candidate != null && candidate.equals(id)) {
            listed = true;
            break;
        }
        return blacklist ? !listed : listed;
    }

    private static int priority(String category) {
        if ("heavy".equals(category)) return 4;
        if ("crystal".equals(category)) return 3;
        if ("slime".equals(category)) return 2;
        if ("medium".equals(category)) return 1;
        return "light".equals(category) || "elytra".equals(category) ? 0 : -1;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass()
            .getSimpleName() : error.getMessage();
    }
}
