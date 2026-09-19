package org.fentanylsolutions.pastfootsteps.sound;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.fentanylsolutions.pastfootsteps.profile.LocalJsonOverrides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Java 8 adaptation of Presence Footsteps' MIT acoustic model. See packaged SOURCE-NOTICE.txt. */
public final class AcousticEngine {

    private static final int MAX_LOCAL_ACOUSTICS = 128;
    private static final Acoustic SILENT = (context, event) -> {};

    public interface Player {

        void play(String sound, float volume, float pitch, AcousticContext context);
    }

    private interface Acoustic {

        void play(AcousticContext context, String event);

        default boolean supports(String event) {
            return true;
        }
    }

    private final Map<String, Acoustic> acoustics = new HashMap<>();
    private final TimedSoundQueue pending;
    private final Random random = new Random();
    private final Player player;
    private final LocalJsonOverrides local = new LocalJsonOverrides();

    public AcousticEngine(Player player) {
        this(player, () -> System.nanoTime() / 1_000_000L);
    }

    public AcousticEngine(Player player, LongSupplier clock) {
        this.player = player;
        this.pending = new TimedSoundQueue(clock);
    }

    public void clear() {
        pending.clear();
    }

    public boolean has(String name) {
        return acoustics.containsKey(name);
    }

    public boolean has(String name, String event) {
        Acoustic acoustic = acoustics.get(name);
        return acoustic != null && acoustic.supports(event);
    }

    public boolean ready() {
        return !acoustics.isEmpty();
    }

    /** Lower-priority definitions survive malformed overrides; resource streams are always closed. */
    public void reload(IResourceManager manager, Iterable<String> ids) {
        reload(manager, ids, null);
    }

    /**
     * Reloads indexed pack definitions followed by direct local files in {@code acoustics/}. Local
     * files may introduce a material name without adding it to the resource-pack index.
     */
    public void reload(IResourceManager manager, Iterable<String> ids, File directory) {
        acoustics.clear();
        clear();
        for (String id : sortedIds(ids)) {
            List<IResource> resources;
            try {
                resources = manager
                    .getAllResources(new ResourceLocation("presencefootsteps", "config/acoustics/" + id + ".json"));
            } catch (Exception e) {
                LogManager.getLogger("PastFootsteps")
                    .warn("Missing acoustic {}", id, e);
                continue;
            }
            for (IResource resource : resources) {
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    load(id, new JsonParser().parse(reader));
                } catch (Exception e) {
                    LogManager.getLogger("PastFootsteps")
                        .warn("Skipping invalid acoustic layer {}", id, e);
                }
            }
        }
        loadLocal(directory);
    }

    public void load(String name, JsonElement json) {
        // Parse before mutation: an invalid node must retain a lower-priority definition.
        Acoustic acoustic = parse(json, 0);
        acoustics.put(name, acoustic);
    }

    /** Let callers invalidate old owners before playback without dispatching newly emitted delays. */
    public void beginTick() {
        pending.beginTick();
    }

    public void tick() {
        pending.tick();
    }

    public void play(String name, String event, AcousticContext context) {
        Acoustic acoustic = acoustics.get(name);
        if (acoustic != null && context.volume > 0 && context.isValid())
            acoustic.play(context, event == null ? "walk" : event);
    }

    private Acoustic parse(JsonElement value, int depth) {
        if (depth > 32) throw new JsonParseException("Acoustic nesting exceeds 32 levels");
        // JSON null and explicit empty nodes own a channel while deliberately producing no sample.
        // A missing definition remains an error so callers can retain native fallback for it.
        if (value == null) throw new JsonParseException("Missing acoustic");
        if (value.isJsonNull()) return SILENT;
        if (value.isJsonPrimitive()) {
            if (!value.getAsJsonPrimitive()
                .isString()) throw new JsonParseException("Acoustic shorthand must be a string");
            return basic(value.getAsString(), new Range(1, 1), new Range(1, 1));
        }
        if (value.isJsonArray()) {
            List<Acoustic> children = new ArrayList<>();
            for (JsonElement child : value.getAsJsonArray()) children.add(parse(child, depth + 1));
            return supported(
                (context, event) -> { for (Acoustic child : children) child.play(context, event); },
                event -> children.isEmpty() || children.stream()
                    .anyMatch(child -> child.supports(event)));
        }
        JsonObject object = value.getAsJsonObject();
        String type = object.has("type") ? object.get("type")
            .getAsString() : "basic";
        switch (type) {
            case "events": {
                Map<String, Acoustic> events = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (!entry.getKey()
                        .equals("type")) events.put(entry.getKey(), parse(entry.getValue(), depth + 1));
                }
                return supported((context, event) -> {
                    Acoustic child = eventAcoustic(events, event);
                    if (child != null) child.play(context, event);
                }, event -> {
                    Acoustic child = eventAcoustic(events, event);
                    return child != null && child.supports(event);
                });
            }
            case "simultaneous":
                return parse(object.has("acoustics") ? object.get("acoustics") : object.get("array"), depth + 1);
            case "probability":
            case "weighted": {
                List<Weighted> entries = new ArrayList<>();
                JsonArray array = object.has("entries") ? object.getAsJsonArray("entries")
                    : object.getAsJsonArray("array");
                if (array == null) throw new JsonParseException("Missing probability entries");
                if (array.size() > 0 && array.get(0)
                    .isJsonObject()) {
                    for (JsonElement entry : array) {
                        JsonObject pair = entry.getAsJsonObject();
                        entries.add(new Weighted(number(pair, "weight", 1), parse(pair.get("acoustic"), depth + 1)));
                    }
                } else {
                    if (array.size() % 2 != 0) throw new JsonParseException("Odd probability array length");
                    for (int i = 0; i < array.size(); i += 2) {
                        entries.add(
                            new Weighted(
                                finite(
                                    array.get(i)
                                        .getAsFloat()),
                                parse(array.get(i + 1), depth + 1)));
                    }
                }
                float sum = 0;
                for (Weighted entry : entries) sum += entry.weight;
                final float total = finite(sum);
                return supported((context, event) -> {
                    if (total <= 0) return;
                    float selection = random.nextFloat() * total;
                    for (Weighted entry : entries) {
                        if (selection < entry.weight) {
                            entry.acoustic.play(context, event);
                            return;
                        }
                        selection -= entry.weight;
                    }
                },
                    event -> total <= 0 || entries.stream()
                        .anyMatch(entry -> entry.weight > 0 && entry.acoustic.supports(event)));
            }
            case "chance": {
                Acoustic child = parse(object.get("acoustic"), depth + 1);
                float probability = number(object, "probability", 100);
                return supported(
                    (context, event) -> { if (random.nextFloat() * 100 < probability) child.play(context, event); },
                    event -> probability <= 0 || child.supports(event));
            }
            case "delayed": {
                Acoustic child = object.has("acoustic") ? parse(object.get("acoustic"), depth + 1)
                    : basic(
                        object.get("name")
                            .getAsString(),
                        range(object.get("volume"), 100, 0.01f),
                        range(object.get("pitch"), 100, 0.01f));
                Range delay = object.has("delay") ? range(object.get("delay"), 0, 1)
                    : new Range(
                        number(object, "delay_min", 0),
                        number(object, "delay_max", number(object, "delay_min", 0)));
                boolean keepLate = object.has("skippable");
                return supported(
                    (context, event) -> child.play(context.withDelay(delay.min, delay.max, keepLate), event),
                    child::supports);
            }
            case "empty":
            case "silent":
                return SILENT;
            case "basic":
                if (!object.has("name")) throw new JsonParseException("Basic acoustic requires a name");
                return basic(
                    object.get("name")
                        .getAsString(),
                    range(object.get("volume"), 100, 0.01f),
                    range(object.get("pitch"), 100, 0.01f));
            default:
                throw new JsonParseException("Unknown acoustic type: " + type);
        }
    }

    private void loadLocal(File directory) {
        if (directory == null) return;
        File acousticDirectory = new File(directory, "acoustics");
        local.dropMissingUnder(acousticDirectory);
        File[] files = acousticDirectory.listFiles();
        if (files == null) return;
        Arrays.sort(
            files,
            (first, second) -> first.getName()
                .compareTo(second.getName()));
        int loaded = 0;
        for (File file : files) {
            String name = file.getName();
            if (!file.isFile() || !name.endsWith(".json")) continue;
            String id = name.substring(0, name.length() - 5);
            if (!validId(id)) {
                LogManager.getLogger("PastFootsteps")
                    .warn("Ignoring local acoustic with invalid id {}", name);
                continue;
            }
            if (loaded++ >= MAX_LOCAL_ACOUSTICS) {
                LogManager.getLogger("PastFootsteps")
                    .warn("Ignoring local acoustics after {} files", MAX_LOCAL_ACOUSTICS);
                break;
            }
            Acoustic acoustic = local.load(file, "acoustic " + id, value -> parse(value, 0));
            if (acoustic != null) acoustics.put(id, acoustic);
        }
    }

    private static List<String> sortedIds(Iterable<String> ids) {
        if (ids == null) return Collections.emptyList();
        Set<String> sorted = new java.util.TreeSet<String>();
        for (String id : ids) if (validId(id)) sorted.add(id);
        return new ArrayList<String>(sorted);
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9_.-]{0,63}");
    }

    private Acoustic basic(String name, Range volume, Range pitch) {
        return (context, event) -> {
            if (name.isEmpty()) return; // Intentional silence, not an absent event.
            float sampledVolume = volume.sample(random, context.volumePosition) * context.volume;
            float sampledPitch = pitch.sample(random, context.pitchPosition) * context.pitch;
            Runnable playback = () -> player.play(name, sampledVolume, sampledPitch, context);
            if (Float.isNaN(context.delayMin)) {
                playback.run();
                return;
            }
            Range delay = new Range(context.delayMin, context.delayMax);
            long milliseconds = (long) Math.min(60_000, Math.ceil(delay.sample(random)));
            if (milliseconds <= 0) {
                playback.run();
                return;
            }
            long maximum = (long) Math.min(60_000, Math.max(delay.min, delay.max));
            // MIT tolerance, with at least one client tick of late grace for our 20 Hz dispatcher.
            long early = context.keepLate ? 0 : (long) Math.pow(maximum, 0.75);
            long late = context.keepLate ? -1 : Math.max(50, (long) (maximum / 1.5));
            pending.schedule(milliseconds, early, late, context::isValid, playback);
        };
    }

    private static Acoustic eventAcoustic(Map<String, Acoustic> events, String event) {
        String state = event;
        while (state != null && !events.containsKey(state)) state = fallback(state);
        return state == null ? null : events.get(state);
    }

    private static Acoustic supported(Acoustic acoustic, Predicate<String> support) {
        return new Acoustic() {

            @Override
            public void play(AcousticContext context, String event) {
                acoustic.play(context, event);
            }

            @Override
            public boolean supports(String event) {
                return support.test(event);
            }
        };
    }

    private static String fallback(String event) {
        switch (event) {
            case "land":
            case "climb_run":
            case "down_run":
            case "up_run":
                return "run";
            case "run":
            case "climb":
            case "down":
            case "up":
                return "walk";
            case "jump":
                return "wander";
            default:
                return null;
        }
    }

    private static Range range(JsonElement value, float fallback, float scale) {
        if (value == null) return new Range(fallback * scale, fallback * scale);
        if (value.isJsonPrimitive()) {
            float point = finite(value.getAsFloat());
            return new Range(point * scale, point * scale);
        }
        JsonObject object = value.getAsJsonObject();
        float min = number(object, "min", fallback);
        return new Range(min * scale, number(object, "max", min) * scale);
    }

    private static float number(JsonObject object, String key, float fallback) {
        return finite(
            object.has(key) ? object.get(key)
                .getAsFloat() : fallback);
    }

    private static float finite(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0)
            throw new JsonParseException("Expected finite non-negative value");
        return value;
    }

    private static final class Range {

        final float min, max;

        Range(float min, float max) {
            // Upstream packs contain descending ranges; sampling either direction is valid.
            finite(min);
            finite(max);
            this.min = min;
            this.max = max;
        }

        float sample(Random random) {
            return sample(random, Float.NaN);
        }

        float sample(Random random, float position) {
            double fraction = Float.isNaN(position) ? random.nextFloat() : position;
            // MIT Range.on extrapolates (winged landings deliberately use 2). The caller owns any
            // movement-specific clamp. Keep the resulting audio parameter finite and non-negative.
            double result = min + ((double) max - min) * fraction;
            return (float) Math.max(0, Math.min(Float.MAX_VALUE, result));
        }
    }

    private static final class Weighted {

        final float weight;
        final Acoustic acoustic;

        Weighted(float weight, Acoustic acoustic) {
            this.weight = finite(weight);
            this.acoustic = acoustic;
        }
    }

}
