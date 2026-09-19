package org.fentanylsolutions.pastfootsteps.profile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;
import org.fentanylsolutions.pastfootsteps.profile.EntitySelector.Age;
import org.fentanylsolutions.pastfootsteps.profile.EntitySelector.HorseType;
import org.fentanylsolutions.pastfootsteps.profile.EntitySelector.SkeletonType;
import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition.Contact;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Loads inherited entity-to-gait profiles from resource packs and an optional local override. */
public final class ProfileLibrary implements IResourceManagerReloadListener {

    private static final ResourceLocation GAITS = new ResourceLocation("pastfootsteps", "config/gaits.json");
    private static final ResourceLocation ENTITIES = new ResourceLocation("pastfootsteps", "config/entities.json");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern MATERIAL_MAP_ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern CLASS_NAME = Pattern
        .compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*){0,31}");
    private static final int MAX_CONTACTS = 16;
    private static final int MAX_LOCAL_CHARACTERS = 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_INHERITANCE_DEPTH = 64;

    private final File configuredDirectory;
    private String localGaitsPath, localEntitiesPath;
    private String lastGoodLocalGaits, lastGoodLocalEntities;
    private final Map<String, Class<? extends EntityLivingBase>> resolvedClasses = new HashMap<>();
    private final Set<String> unavailableClasses = new HashSet<>();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile List<String> diagnostics = Collections.emptyList();

    public ProfileLibrary() {
        this(null);
    }

    /** Creates a resource listener whose local overrides live below the supplied mod config directory. */
    public ProfileLibrary(File modConfigDirectory) {
        configuredDirectory = modConfigDirectory;
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        reload(manager, configuredDirectory);
    }

    /**
     * Reloads on the caller's resource-reload thread. Resource layers are lowest to highest priority;
     * local files are applied last. Invalid layers are diagnosed and skipped atomically.
     */
    public synchronized void reload(IResourceManager manager, File modConfigDirectory) {
        if (manager == null) return;

        List<String> messages = new ArrayList<String>();
        Map<String, GaitDefinition> gaits = new LinkedHashMap<String, GaitDefinition>();
        int validGaitLayers = loadGaitResources(manager, gaits, messages);
        File profileDirectory = modConfigDirectory == null ? null : new File(modConfigDirectory, "profiles");
        File localGaits = profileDirectory == null ? null : new File(profileDirectory, "gaits.json");
        validGaitLayers += loadLocalGaits(localGaits, gaits, messages);

        Map<String, EntitySpec> specifications = new LinkedHashMap<String, EntitySpec>();
        int validEntityLayers = loadEntityResources(manager, gaits, specifications, messages);
        File localEntities = profileDirectory == null ? null : new File(profileDirectory, "entities.json");
        validEntityLayers += loadLocalEntities(localEntities, gaits, specifications, messages);

        if (validGaitLayers == 0 || validEntityLayers == 0) {
            diagnose(
                messages,
                "Profile reload did not contain a valid gait and entity layer; retaining last-good data");
            diagnostics = Collections.unmodifiableList(new ArrayList<String>(messages));
            return;
        }
        CompiledProfiles compiled;
        try {
            compiled = compileProfiles(specifications, gaits, "merged entity profiles", messages, true);
        } catch (JsonParseException error) {
            diagnose(messages, "Merged entity profiles became invalid; retaining last-good data: " + message(error));
            diagnostics = Collections.unmodifiableList(new ArrayList<String>(messages));
            return;
        }
        snapshot = new Snapshot(gaits, compiled.profiles, compiled.excludedEntityIds);
        diagnostics = Collections.unmodifiableList(new ArrayList<String>(messages));
        PastFootsteps.LOG.info(
            "Loaded {} gaits and {} entity profiles with {} diagnostic(s)",
            gaits.size(),
            compiled.profiles.size(),
            messages.size());
    }

    /** Returns only exact, case-sensitive entity-ID matches. Unknown entities keep native behavior. */
    public EntityProfile resolve(String exactEntityId) {
        return exactEntityId == null || snapshot.excludedEntityIds.contains(exactEntityId) ? null
            : snapshot.exactProfiles.get(exactEntityId);
    }

    /** Resolves exact ID selectors before explicit class selectors. Unknown actors remain unmatched. */
    public EntityProfile resolve(EntityLivingBase actor) {
        if (actor == null) return null;
        String entityId = actor instanceof EntityPlayer ? "Player" : EntityList.getEntityString(actor);
        return snapshot.resolve(actor, entityId);
    }

    public GaitDefinition gait(String id) {
        return id == null ? null : snapshot.gaits.get(id);
    }

    /** Human-readable errors from the latest reload, ordered by layer processing order. */
    public List<String> diagnostics() {
        return diagnostics;
    }

    /** Returns the unique nonempty material map IDs referenced by compiled profiles. */
    public Set<String> materialMaps() {
        return snapshot.materialMaps;
    }

    private static int loadGaitResources(IResourceManager manager, Map<String, GaitDefinition> destination,
        List<String> diagnostics) {
        int valid = 0;
        List<IResource> resources;
        try {
            resources = manager.getAllResources(GAITS);
        } catch (IOException error) {
            diagnose(diagnostics, "Could not enumerate " + GAITS + ": " + error.getMessage());
            return 0;
        }
        for (int index = 0; index < resources.size(); index++) {
            IResource resource = resources.get(index);
            String label = GAITS + " layer " + index + " (" + resource + ")";
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                applyGaitLayer(reader, label, destination);
                valid++;
            } catch (Exception error) {
                diagnose(diagnostics, "Skipping invalid " + label + ": " + message(error));
            }
        }
        return valid;
    }

    private int loadEntityResources(IResourceManager manager, Map<String, GaitDefinition> gaits,
        Map<String, EntitySpec> destination, List<String> diagnostics) {
        int valid = 0;
        List<IResource> resources;
        try {
            resources = manager.getAllResources(ENTITIES);
        } catch (IOException error) {
            diagnose(diagnostics, "Could not enumerate " + ENTITIES + ": " + error.getMessage());
            return 0;
        }
        for (int index = 0; index < resources.size(); index++) {
            IResource resource = resources.get(index);
            String label = ENTITIES + " layer " + index + " (" + resource + ")";
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                applyEntityLayer(reader, label, gaits, destination);
                valid++;
            } catch (Exception error) {
                diagnose(diagnostics, "Skipping invalid " + label + ": " + message(error));
            }
        }
        return valid;
    }

    private int loadLocalGaits(File file, Map<String, GaitDefinition> destination, List<String> diagnostics) {
        String path = absolutePath(file);
        if (!equal(path, localGaitsPath)) {
            localGaitsPath = path;
            lastGoodLocalGaits = null;
        }
        if (file == null || !file.isFile()) {
            lastGoodLocalGaits = null;
            return 0;
        }
        try {
            String source = readLocalFile(file);
            applyGaitLayer(new StringReader(source), file.toString(), destination);
            lastGoodLocalGaits = source;
            return 1;
        } catch (Exception error) {
            diagnose(diagnostics, "Invalid local gait file " + file + ": " + message(error));
            if (lastGoodLocalGaits == null) return 0;
            try {
                applyGaitLayer(new StringReader(lastGoodLocalGaits), file + " (cached last-valid layer)", destination);
                diagnose(diagnostics, "Retained cached last-valid local gait layer for " + file);
                return 1;
            } catch (Exception cachedError) {
                diagnose(
                    diagnostics,
                    "Cached local gait layer is no longer valid for " + file + ": " + message(cachedError));
                lastGoodLocalGaits = null;
                return 0;
            }
        }
    }

    private int loadLocalEntities(File file, Map<String, GaitDefinition> gaits, Map<String, EntitySpec> destination,
        List<String> diagnostics) {
        String path = absolutePath(file);
        if (!equal(path, localEntitiesPath)) {
            localEntitiesPath = path;
            lastGoodLocalEntities = null;
        }
        if (file == null || !file.isFile()) {
            lastGoodLocalEntities = null;
            return 0;
        }
        try {
            String source = readLocalFile(file);
            applyEntityLayer(new StringReader(source), file.toString(), gaits, destination);
            lastGoodLocalEntities = source;
            return 1;
        } catch (Exception error) {
            diagnose(diagnostics, "Invalid local entity file " + file + ": " + message(error));
            if (lastGoodLocalEntities == null) return 0;
            try {
                applyEntityLayer(
                    new StringReader(lastGoodLocalEntities),
                    file + " (cached last-valid layer)",
                    gaits,
                    destination);
                diagnose(diagnostics, "Retained cached last-valid local entity layer for " + file);
                return 1;
            } catch (Exception cachedError) {
                diagnose(
                    diagnostics,
                    "Cached local entity layer is no longer valid for " + file + ": " + message(cachedError));
                lastGoodLocalEntities = null;
                return 0;
            }
        }
    }

    private static String readLocalFile(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder source = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (source.length() + read > MAX_LOCAL_CHARACTERS)
                    throw new IOException("file exceeds " + MAX_LOCAL_CHARACTERS + " characters");
                source.append(buffer, 0, read);
            }
            return source.toString();
        }
    }

    private static String absolutePath(File file) {
        return file == null ? null
            : file.getAbsoluteFile()
                .toString();
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void applyGaitLayer(Reader reader, String label, Map<String, GaitDefinition> destination) {
        JsonObject root = root(reader, label, "gaits");
        JsonObject entries = object(root, "gaits", label);
        Map<String, GaitDefinition> replacements = new LinkedHashMap<String, GaitDefinition>();
        List<String> removals = new ArrayList<String>();
        for (Entry<String, JsonElement> entry : entries.entrySet()) {
            String id = checkedId(entry.getKey(), label + ".gaits");
            JsonObject value = object(entry.getValue(), label + ".gaits." + id);
            checkKeys(value, label + ".gaits." + id, "enabled", "walk", "run", "fastStride");
            if (!bool(value, "enabled", true, label)) {
                removals.add(id);
                continue;
            }
            Contact[] walk = contacts(value.get("walk"), label + ".gaits." + id + ".walk");
            Contact[] run = value.has("run") ? contacts(value.get("run"), label + ".gaits." + id + ".run") : walk;
            GaitDefinition.FastStride fastStride = null;
            if (value.has("fastStride")) {
                String path = label + ".gaits." + id + ".fastStride";
                JsonObject tuning = object(value.get("fastStride"), path);
                checkKeys(tuning, path, "startSpeed", "fullSpeed", "multiplier");
                fastStride = new GaitDefinition.FastStride(
                    number(tuning, "startSpeed", Double.NaN, 0, 16, path),
                    number(tuning, "fullSpeed", Double.NaN, 0, 16, path),
                    number(tuning, "multiplier", Double.NaN, 1, 8, path));
            }
            replacements.put(id, new GaitDefinition(id, walk, run, fastStride));
        }
        for (String id : removals) destination.remove(id);
        destination.putAll(replacements);
    }

    private void applyEntityLayer(Reader reader, String label, Map<String, GaitDefinition> gaits,
        Map<String, EntitySpec> destination) {
        JsonObject root = root(reader, label, "entities");
        JsonObject entries = object(root, "entities", label);
        Map<String, EntitySpec> candidate = new LinkedHashMap<String, EntitySpec>(destination);
        for (Entry<String, JsonElement> entry : entries.entrySet()) {
            String id = checkedId(entry.getKey(), label + ".entities");
            String path = label + ".entities." + id;
            JsonObject value = object(entry.getValue(), path);
            checkKeys(
                value,
                path,
                "enabled",
                "parent",
                "selector",
                "priority",
                "gait",
                "strideMultiplier",
                "childScale",
                "runSpeed",
                "volume",
                "referenceWidth",
                "materialMap");
            if (!bool(value, "enabled", true, label)) {
                candidate.put(id, EntitySpec.disabled(id));
                continue;
            }
            String parent = optionalId(value, "parent", path);
            SelectorSpec selector = value.has("selector") ? selector(value.get("selector"), path + ".selector")
                : SelectorSpec.exact(id);
            Integer priority = optionalInteger(value, "priority", -10000, 10000, path);
            String gaitId = optionalId(value, "gait", path);
            Double strideMultiplier = optionalNumber(value, "strideMultiplier", 0.1, 8.0, path);
            Double childScale = optionalNumber(value, "childScale", 0.1, 2.0, path);
            Double runSpeed = optionalNumber(value, "runSpeed", Double.MIN_VALUE, 4.0, path);
            Double volume = optionalNumber(value, "volume", 0.0, 4.0, path);
            Double referenceWidth = optionalNumber(value, "referenceWidth", 0.0, 64.0, path);
            String materialMap = optionalString(value, "materialMap", path);
            if (materialMap != null && !materialMap.isEmpty()
                && !MATERIAL_MAP_ID.matcher(materialMap)
                    .matches())
                throw new JsonParseException(path + ".materialMap must be empty or match [a-z0-9_-]{1,64}");
            if (parent == null && gaitId == null)
                throw new JsonParseException(path + ".gait is required without parent");
            candidate.put(
                id,
                new EntitySpec(
                    id,
                    parent,
                    selector,
                    priority == null ? 0 : priority.intValue(),
                    gaitId,
                    strideMultiplier,
                    childScale,
                    runSpeed,
                    volume,
                    referenceWidth,
                    materialMap));
        }
        // Resolve the whole merged graph before committing this layer so a bad parent cannot partially apply.
        compileProfiles(candidate, gaits, label, Collections.<String>emptyList(), false);
        destination.clear();
        destination.putAll(candidate);
    }

    private static SelectorSpec selector(JsonElement element, String path) {
        JsonObject value = object(element, path);
        checkKeys(value, path, "entityId", "class", "age", "horseType", "skeletonType");
        String entityId = optionalId(value, "entityId", path);
        String className = optionalString(value, "class", path);
        if (className != null && (className.length() > 256 || !CLASS_NAME.matcher(className)
            .matches())) throw new JsonParseException(path + ".class must be a binary Java class name");
        if (entityId == null && className == null) throw new JsonParseException(path + " requires entityId or class");
        Age age = enumValue(value, "age", Age.ANY, Age.class, path);
        HorseType horse = enumValue(value, "horseType", HorseType.ANY, HorseType.class, path);
        SkeletonType skeleton = enumValue(value, "skeletonType", SkeletonType.ANY, SkeletonType.class, path);
        if (horse != HorseType.ANY && skeleton != SkeletonType.ANY)
            throw new JsonParseException(path + " cannot combine horseType and skeletonType");
        return new SelectorSpec(entityId, className, age, horse, skeleton);
    }

    private CompiledProfiles compileProfiles(Map<String, EntitySpec> specifications, Map<String, GaitDefinition> gaits,
        String label, List<String> diagnostics, boolean reportDormantClasses) {
        Map<String, EntityProfile> result = new LinkedHashMap<String, EntityProfile>();
        Set<String> excludedEntityIds = new HashSet<String>();
        Set<String> visiting = new HashSet<String>();
        Set<String> reportedClasses = new HashSet<String>();
        for (EntitySpec spec : specifications.values()) {
            if (spec.disabled) {
                excludedEntityIds.add(spec.id);
                continue;
            }
            resolveProfile(
                spec.id,
                specifications,
                gaits,
                result,
                visiting,
                label,
                diagnostics,
                reportedClasses,
                reportDormantClasses,
                0);
        }
        return new CompiledProfiles(result, excludedEntityIds);
    }

    private EntityProfile resolveProfile(String id, Map<String, EntitySpec> specifications,
        Map<String, GaitDefinition> gaits, Map<String, EntityProfile> result, Set<String> visiting, String label,
        List<String> diagnostics, Set<String> reportedClasses, boolean reportDormantClasses, int depth) {
        if (depth >= MAX_INHERITANCE_DEPTH) throw new JsonParseException(
            label + ": inheritance depth exceeds " + MAX_INHERITANCE_DEPTH + " at '" + id + "'");
        EntityProfile resolved = result.get(id);
        if (resolved != null) return resolved;
        EntitySpec spec = specifications.get(id);
        if (spec == null) throw new JsonParseException(label + ": missing parent profile '" + id + "'");
        if (spec.disabled) throw new JsonParseException(label + ": parent profile '" + id + "' is disabled");
        if (!visiting.add(id)) throw new JsonParseException(label + ": inheritance cycle includes '" + id + "'");

        EntityProfile parent = null;
        if (spec.parentId != null) parent = resolveProfile(
            spec.parentId,
            specifications,
            gaits,
            result,
            visiting,
            label,
            diagnostics,
            reportedClasses,
            reportDormantClasses,
            depth + 1);

        String gaitId = spec.gaitId != null ? spec.gaitId : parent == null ? null : parent.gait.id;
        GaitDefinition gait = gaits.get(gaitId);
        if (gait == null) throw new JsonParseException(label + ".entities." + id + ": missing gait '" + gaitId + "'");
        double stride = spec.strideMultiplier != null ? spec.strideMultiplier.doubleValue()
            : parent == null ? 1.0 : parent.strideMultiplier;
        double childScale = spec.childScale != null ? spec.childScale.doubleValue()
            : parent == null ? 0.5 : parent.childScale;
        double runSpeed = spec.runSpeed != null ? spec.runSpeed.doubleValue() : parent == null ? 0.22 : parent.runSpeed;
        float volume = spec.volume != null ? spec.volume.floatValue() : parent == null ? 1.0F : parent.volume;
        double referenceWidth = spec.referenceWidth != null ? spec.referenceWidth.doubleValue()
            : parent == null ? 0.0 : parent.referenceWidth;
        String materialMap = spec.materialMap != null ? spec.materialMap : parent == null ? "" : parent.materialMap;

        Class<? extends EntityLivingBase> entityClass = resolveClass(spec.selector.className);
        if (reportDormantClasses && spec.selector.className != null
            && entityClass == null
            && reportedClasses.add(spec.selector.className))
            diagnose(
                diagnostics,
                "Entity selector class '" + spec.selector.className
                    + "' is unavailable; matching profiles are dormant");
        EntitySelector selector = new EntitySelector(
            spec.selector.entityId,
            spec.selector.className,
            entityClass,
            spec.selector.age,
            spec.selector.horseType,
            spec.selector.skeletonType);
        resolved = new EntityProfile(
            id,
            spec.parentId,
            selector,
            spec.priority,
            gait,
            stride,
            childScale,
            runSpeed,
            volume,
            referenceWidth,
            materialMap);
        visiting.remove(id);
        result.put(id, resolved);
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends EntityLivingBase> resolveClass(String className) {
        if (className == null) return null;
        Class<? extends EntityLivingBase> cached = resolvedClasses.get(className);
        if (cached != null) return cached;
        if (unavailableClasses.contains(className)) return null;
        try {
            Class<?> loaded = Class.forName(className, false, EntityLivingBase.class.getClassLoader());
            if (!EntityLivingBase.class.isAssignableFrom(loaded))
                throw new JsonParseException(className + " is not an EntityLivingBase class");
            cached = (Class<? extends EntityLivingBase>) loaded;
            resolvedClasses.put(className, cached);
            return cached;
        } catch (ClassNotFoundException error) {
            unavailableClasses.add(className);
            return null;
        } catch (LinkageError error) {
            unavailableClasses.add(className);
            return null;
        }
    }

    private static JsonObject root(Reader reader, String label, String entriesKey) {
        JsonElement parsed = parseStrict(reader, label);
        JsonObject root = object(parsed, label);
        checkKeys(root, label, "schemaVersion", entriesKey);
        JsonElement schema = root.get("schemaVersion");
        if (schema == null || !schema.isJsonPrimitive()
            || !schema.getAsJsonPrimitive()
                .isNumber()
            || schema.getAsDouble() != 1.0) throw new JsonParseException(label + ".schemaVersion must be 1");
        return root;
    }

    private static JsonElement parseStrict(Reader source, String path) {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        try {
            JsonElement result = readElement(reader, path, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new JsonParseException(path + " contains trailing JSON data");
            return result;
        } catch (IOException error) {
            throw new JsonParseException(path + " could not be read", error);
        }
    }

    private static JsonElement readElement(JsonReader reader, String path, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH)
            throw new JsonParseException(path + " exceeds maximum JSON depth " + MAX_JSON_DEPTH);
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_OBJECT) {
            JsonObject result = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (result.has(name)) throw new JsonParseException(path + " contains duplicate key '" + name + "'");
                result.add(name, readElement(reader, path + "." + name, depth + 1));
            }
            reader.endObject();
            return result;
        }
        if (token == JsonToken.BEGIN_ARRAY) {
            JsonArray result = new JsonArray();
            reader.beginArray();
            int index = 0;
            while (reader.hasNext()) result.add(readElement(reader, path + "[" + index++ + "]", depth + 1));
            reader.endArray();
            return result;
        }
        if (token == JsonToken.STRING) return new JsonPrimitive(reader.nextString());
        if (token == JsonToken.NUMBER) return new JsonPrimitive(new BigDecimal(reader.nextString()));
        if (token == JsonToken.BOOLEAN) return new JsonPrimitive(reader.nextBoolean());
        if (token == JsonToken.NULL) {
            reader.nextNull();
            return JsonNull.INSTANCE;
        }
        throw new JsonParseException(path + " contains unexpected token " + token);
    }

    private static Contact[] contacts(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) throw new JsonParseException(path + " must be an array");
        int size = element.getAsJsonArray()
            .size();
        if (size < 1 || size > MAX_CONTACTS)
            throw new JsonParseException(path + " must contain 1 to " + MAX_CONTACTS + " contacts");
        Contact[] result = new Contact[size];
        for (int index = 0; index < size; index++) {
            String contactPath = path + "[" + index + "]";
            JsonObject contact = object(
                element.getAsJsonArray()
                    .get(index),
                contactPath);
            checkKeys(contact, contactPath, "footId", "phase", "lateral", "forward");
            String footId = checkedId(string(contact, "footId", contactPath), contactPath + ".footId");
            double phase = number(contact, "phase", Double.NaN, GaitDefinition.MIN_PHASE_GAP, 1.0, contactPath);
            double lateral = number(contact, "lateral", Double.NaN, -4.0, 4.0, contactPath);
            double forward = number(contact, "forward", Double.NaN, -4.0, 4.0, contactPath);
            result[index] = new Contact(footId, phase, lateral, forward);
        }
        return result;
    }

    private static JsonObject object(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) throw new JsonParseException(path + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonObject object(JsonObject parent, String key, String path) {
        return object(parent.get(key), path + "." + key);
    }

    private static String string(JsonObject object, String key, String path) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive()
                .isString())
            throw new JsonParseException(path + "." + key + " must be a string");
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String key, String path) {
        if (!object.has(key)) return null;
        return string(object, key, path);
    }

    private static String optionalId(JsonObject object, String key, String path) {
        String value = optionalString(object, key, path);
        return value == null ? null : checkedId(value, path + "." + key);
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String key, E fallback, Class<E> type,
        String path) {
        String value = optionalString(object, key, path);
        if (value == null) return fallback;
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name()
                .equalsIgnoreCase(value)) return candidate;
        }
        throw new JsonParseException(path + "." + key + " has unsupported value '" + value + "'");
    }

    private static boolean bool(JsonObject object, String key, boolean fallback, String path) {
        JsonElement value = object.get(key);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isBoolean()) throw new JsonParseException(path + "." + key + " must be a boolean");
        return value.getAsBoolean();
    }

    private static double number(JsonObject object, String key, double fallback, double minimum, double maximum,
        String path) {
        JsonElement value = object.get(key);
        if (value == null) {
            if (!Double.isNaN(fallback)) return fallback;
            throw new JsonParseException(path + "." + key + " is required");
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isNumber()) throw new JsonParseException(path + "." + key + " must be a number");
        double result = value.getAsDouble();
        if (Double.isNaN(result) || Double.isInfinite(result) || result < minimum || result > maximum)
            throw new JsonParseException(
                path + "." + key + " must be finite and between " + minimum + " and " + maximum);
        return result;
    }

    private static Double optionalNumber(JsonObject object, String key, double minimum, double maximum, String path) {
        if (!object.has(key)) return null;
        return Double.valueOf(number(object, key, Double.NaN, minimum, maximum, path));
    }

    private static Integer optionalInteger(JsonObject object, String key, int minimum, int maximum, String path) {
        if (!object.has(key)) return null;
        double value = number(object, key, Double.NaN, minimum, maximum, path);
        if (value != Math.rint(value)) throw new JsonParseException(path + "." + key + " must be an integer");
        return Integer.valueOf((int) value);
    }

    private static String checkedId(String id, String path) {
        if (id == null || !ID.matcher(id)
            .matches()) throw new JsonParseException(path + " contains invalid ID '" + id + "'");
        return id;
    }

    private static void checkKeys(JsonObject object, String path, String... allowed) {
        for (Entry<String, JsonElement> entry : object.entrySet()) {
            boolean known = false;
            for (String key : allowed) {
                if (key.equals(entry.getKey())) {
                    known = true;
                    break;
                }
            }
            if (!known) throw new JsonParseException(path + " contains unknown field '" + entry.getKey() + "'");
        }
    }

    private static void diagnose(List<String> diagnostics, String message) {
        diagnostics.add(message);
        PastFootsteps.LOG.warn(message);
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass()
            .getSimpleName() : message;
    }

    private static final class SelectorSpec {

        final String entityId;
        final String className;
        final Age age;
        final HorseType horseType;
        final SkeletonType skeletonType;

        SelectorSpec(String entityId, String className, Age age, HorseType horseType, SkeletonType skeletonType) {
            this.entityId = entityId;
            this.className = className;
            this.age = age;
            this.horseType = horseType;
            this.skeletonType = skeletonType;
        }

        static SelectorSpec exact(String entityId) {
            return new SelectorSpec(entityId, null, Age.ANY, HorseType.ANY, SkeletonType.ANY);
        }
    }

    private static final class EntitySpec {

        final String id;
        final boolean disabled;
        final String parentId;
        final SelectorSpec selector;
        final int priority;
        final String gaitId;
        final Double strideMultiplier;
        final Double childScale;
        final Double runSpeed;
        final Double volume;
        final Double referenceWidth;
        final String materialMap;

        EntitySpec(String id, String parentId, SelectorSpec selector, int priority, String gaitId,
            Double strideMultiplier, Double childScale, Double runSpeed, Double volume, Double referenceWidth,
            String materialMap) {
            this(
                id,
                false,
                parentId,
                selector,
                priority,
                gaitId,
                strideMultiplier,
                childScale,
                runSpeed,
                volume,
                referenceWidth,
                materialMap);
        }

        private EntitySpec(String id, boolean disabled, String parentId, SelectorSpec selector, int priority,
            String gaitId, Double strideMultiplier, Double childScale, Double runSpeed, Double volume,
            Double referenceWidth, String materialMap) {
            this.id = id;
            this.disabled = disabled;
            this.parentId = parentId;
            this.selector = selector;
            this.priority = priority;
            this.gaitId = gaitId;
            this.strideMultiplier = strideMultiplier;
            this.childScale = childScale;
            this.runSpeed = runSpeed;
            this.volume = volume;
            this.referenceWidth = referenceWidth;
            this.materialMap = materialMap;
        }

        static EntitySpec disabled(String id) {
            return new EntitySpec(id, true, null, SelectorSpec.exact(id), 0, null, null, null, null, null, null, null);
        }
    }

    private static final class CompiledProfiles {

        final Map<String, EntityProfile> profiles;
        final Set<String> excludedEntityIds;

        CompiledProfiles(Map<String, EntityProfile> profiles, Set<String> excludedEntityIds) {
            this.profiles = profiles;
            this.excludedEntityIds = excludedEntityIds;
        }
    }

    private static final class Snapshot {

        static final Snapshot EMPTY = new Snapshot(
            Collections.<String, GaitDefinition>emptyMap(),
            Collections.<String, EntityProfile>emptyMap(),
            Collections.<String>emptySet());
        final Map<String, GaitDefinition> gaits;
        final Map<String, EntityProfile> profiles;
        final Map<String, EntityProfile> exactProfiles;
        final List<EntityProfile> selectors;
        final Set<String> excludedEntityIds;
        final Set<String> materialMaps;

        Snapshot(Map<String, GaitDefinition> gaits, Map<String, EntityProfile> profiles,
            Set<String> excludedEntityIds) {
            this.gaits = Collections.unmodifiableMap(new LinkedHashMap<String, GaitDefinition>(gaits));
            this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, EntityProfile>(profiles));
            Map<String, EntityProfile> exact = new LinkedHashMap<String, EntityProfile>();
            List<EntityProfile> ordered = new ArrayList<EntityProfile>(profiles.values());
            Collections.sort(ordered, new Comparator<EntityProfile>() {

                @Override
                public int compare(EntityProfile left, EntityProfile right) {
                    return left.id.compareTo(right.id);
                }
            });
            for (EntityProfile profile : ordered) {
                if (!profile.selector.resolvesByIdOnly()) continue;
                EntityProfile current = exact.get(profile.selector.entityId);
                if (current == null || profile.priority > current.priority)
                    exact.put(profile.selector.entityId, profile);
            }
            exactProfiles = Collections.unmodifiableMap(exact);
            selectors = Collections.unmodifiableList(ordered);
            this.excludedEntityIds = Collections.unmodifiableSet(new HashSet<String>(excludedEntityIds));
            Set<String> materialMaps = new HashSet<String>();
            for (EntityProfile profile : profiles.values()) {
                if (!profile.materialMap.isEmpty()) materialMaps.add(profile.materialMap);
            }
            this.materialMaps = Collections.unmodifiableSet(materialMaps);
        }

        EntityProfile resolve(EntityLivingBase actor, String entityId) {
            if (excludedEntityIds.contains(entityId)) return null;
            EntityProfile best = null;
            int bestExact = -1;
            int bestConditions = -1;
            int bestDistance = Integer.MAX_VALUE;
            int bestPriority = Integer.MIN_VALUE;
            for (EntityProfile profile : selectors) {
                EntitySelector selector = profile.selector;
                if (!selector.matches(actor, entityId)) continue;
                int exact = selector.hasExactEntityId() ? 1 : 0;
                int conditions = selector.conditionCount();
                int distance = selector.classDistance(actor.getClass());
                if (best == null || exact > bestExact
                    || exact == bestExact && conditions > bestConditions
                    || exact == bestExact && conditions == bestConditions && distance < bestDistance
                    || exact == bestExact && conditions == bestConditions
                        && distance == bestDistance
                        && profile.priority > bestPriority) {
                    best = profile;
                    bestExact = exact;
                    bestConditions = conditions;
                    bestDistance = distance;
                    bestPriority = profile.priority;
                }
            }
            return best;
        }
    }
}
