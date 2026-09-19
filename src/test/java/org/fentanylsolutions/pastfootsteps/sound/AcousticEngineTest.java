package org.fentanylsolutions.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class AcousticEngineTest {

    private final List<String> names = new ArrayList<>();
    private final List<Float> volumes = new ArrayList<>();
    private final List<Float> pitches = new ArrayList<>();
    private long now;
    private final AcousticEngine engine = new AcousticEngine((name, volume, pitch, context) -> {
        names.add(name);
        volumes.add(volume);
        pitches.add(pitch);
        assertTrue(Float.isFinite(volume));
        assertTrue(Float.isFinite(pitch));
        assertTrue(volume >= 0);
        assertTrue(pitch >= 0);
    }, () -> now);
    private final AcousticContext context = new AcousticContext(0, 0, 0, 1, 1);

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private void tick() {
        now += 50;
        engine.tick();
    }

    private void load(String json) {
        engine.load("test", new JsonParser().parse(json));
    }

    @Test
    public void allBundledDefinitionsParseAndPlay() throws Exception {
        String root = "/assets/presencefootsteps/config/acoustics/";
        try (InputStreamReader reader = new InputStreamReader(
            getClass().getResourceAsStream(root + "index.json"),
            StandardCharsets.UTF_8)) {
            for (JsonElement id : new JsonParser().parse(reader)
                .getAsJsonArray()) {
                String name = id.getAsString();
                try (InputStreamReader acoustic = new InputStreamReader(
                    getClass().getResourceAsStream(root + name + ".json"),
                    StandardCharsets.UTF_8)) {
                    engine.load(name, new JsonParser().parse(acoustic));
                }
                assertTrue(name, engine.has(name));
                for (String event : new String[] { "walk", "run", "land", "jump", "wander", "stand", "up", "down_run",
                    "swim" }) {
                    engine.play(name, event, context);
                    for (int i = 0; i < 100; i++) tick();
                }
            }
        }
        assertTrue(names.size() > 200);
    }

    @Test
    public void fallbackFollowsTransitionsAndStandingStaysSilent() {
        load("{\"type\":\"events\",\"walk\":\"walk\",\"run\":\"run\",\"wander\":\"shuffle\"}");
        engine.play("test", "up_run", context);
        engine.play("test", "jump", context);
        engine.play("test", "stand", context);
        assertEquals(java.util.Arrays.asList("run", "shuffle"), names);
        names.clear();
        load("{\"type\":\"events\",\"walk\":\"walk\"}");
        engine.play("test", "land", context);
        assertEquals(java.util.Arrays.asList("walk"), names);
    }

    @Test
    public void explicitEmptyAcousticsRemainOwnedAndSilent() {
        load("{\"type\":\"events\",\"walk\":{\"type\":\"empty\"},\"run\":null}");
        engine.play("test", "walk", context);
        engine.play("test", "run", context);
        assertTrue(engine.has("test"));
        assertTrue(names.isEmpty());
    }

    @Test
    public void impactOwnershipFollowsEventFallbackAndPreservesExplicitSilence() {
        load("{\"type\":\"events\",\"walk\":\"walk\",\"run\":\"run\",\"wander\":\"shuffle\"}");
        for (String event : new String[] { "jump", "wander", "land", "climb", "climb_run" }) {
            assertTrue(engine.has("test", event));
            engine.play("test", event, context);
        }
        assertEquals(Arrays.asList("shuffle", "shuffle", "run", "walk", "run"), names);
        load("{\"type\":\"events\",\"jump\":\"jumpOnly\"}");
        assertFalse(engine.has("test", "land"));
        assertFalse(engine.has("missing", "land"));
        load("{\"type\":\"events\",\"land\":null}");
        assertTrue(engine.has("test", "land"));
        names.clear();
        engine.play("test", "land", context);
        assertTrue(names.isEmpty());
    }

    @Test
    public void nestedImpactDefinitionsCannotClaimAnAbsentEvent() {
        for (String wrapper : new String[] { "{\"type\":\"delayed\",\"delay\":50,\"acoustic\":%s}",
            "{\"type\":\"chance\",\"probability\":50,\"acoustic\":%s}",
            "{\"type\":\"weighted\",\"entries\":[{\"weight\":1,\"acoustic\":%s}]}", "[%s]" }) {
            load(String.format(wrapper, "{\"type\":\"events\",\"jump\":\"jumpOnly\"}"));
            assertFalse(wrapper, engine.has("test", "land"));
            assertTrue(wrapper, engine.has("test", "jump"));
            load(String.format(wrapper, "{\"type\":\"empty\"}"));
            assertTrue(wrapper, engine.has("test", "land"));
        }
    }

    @Test
    public void weightedEntriesAlwaysChooseOneAndHonorZeroWeights() {
        load(
            "{\"type\":\"probability\",\"entries\":[{\"weight\":0,\"acoustic\":\"never\"},{\"acoustic\":\"a\"},{\"acoustic\":\"b\"}]}");
        for (int i = 0; i < 200; i++) engine.play("test", "walk", context);
        assertEquals(200, names.size());
        assertFalse(names.contains("never"));
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));
        names.clear();
        load("{\"type\":\"probability\",\"entries\":[{\"weight\":0,\"acoustic\":\"never\"}]}");
        engine.play("test", "walk", context);
        assertTrue(names.isEmpty());
    }

    @Test
    public void percentagesDefaultsAndPerPlaybackVariation() {
        load("{\"type\":\"basic\",\"name\":\"quiet\",\"volume\":1,\"pitch\":{\"min\":90,\"max\":110}}");
        for (int i = 0; i < 20; i++) engine.play("test", "walk", context);
        for (float volume : volumes) assertEquals(0.01f, volume, 0.00001f);
        assertTrue(new java.util.HashSet<>(pitches).size() > 1);
        load("{\"type\":\"basic\",\"name\":\"default\"}");
        engine.play("test", "walk", context);
        assertEquals(1f, volumes.get(volumes.size() - 1), 0.00001f);
        assertEquals(1f, pitches.get(pitches.size() - 1), 0.00001f);
    }

    @Test
    public void rangePositionsInterpolateExtrapolateAndSurviveScalingAndDelay() {
        load(
            "{\"type\":\"basic\",\"name\":\"range\",\"volume\":{\"min\":20,\"max\":80},\"pitch\":{\"min\":120,\"max\":80}}");
        AcousticContext positioned = new AcousticContext(0, 0, 0, 0.5F, 2.0F).withRangePosition(0.25F, 2.0F)
            .scale(0.5F, 0.5F);
        engine.play("test", "walk", positioned);
        assertEquals(0.0875F, volumes.get(0), 0.00001F);
        assertEquals(0.4F, pitches.get(0), 0.00001F);

        load(
            "{\"type\":\"delayed\",\"delay\":50,\"acoustic\":{\"type\":\"basic\",\"name\":\"delayed\",\"volume\":{\"min\":20,\"max\":80},\"pitch\":{\"min\":120,\"max\":80}}}");
        engine.play("test", "walk", positioned.withRangePosition(2.0F, -1.0F));
        tick();
        assertEquals(2, volumes.size());
        assertEquals(0.35F, volumes.get(1), 0.00001F);
        assertEquals(1.6F, pitches.get(1), 0.00001F);
    }

    @Test
    public void explicitVolumePositionRetainsRandomPitchAndRejectsInfinitePositions() {
        load("{\"name\":\"range\",\"volume\":{\"min\":20,\"max\":80},\"pitch\":{\"min\":90,\"max\":110}}");
        AcousticContext ranged = context.withRangePosition(2, Float.NaN);
        for (int i = 0; i < 20; i++) engine.play("test", "walk", ranged);
        for (float volume : volumes) assertEquals(1.4F * context.volume, volume, 0.00001F);
        assertTrue(new java.util.HashSet<>(pitches).size() > 1);
        for (float[] positions : new float[][] { { Float.POSITIVE_INFINITY, 0 }, { 0, Float.NEGATIVE_INFINITY } }) {
            try {
                context.withRangePosition(positions[0], positions[1]);
                fail("Infinite range positions must be rejected");
            } catch (IllegalArgumentException expected) {}
        }
    }

    @Test
    public void extrapolationCannotProduceNegativeOrInfiniteRangeParameters() {
        load("{\"name\":\"range\",\"volume\":{\"min\":20,\"max\":80},\"pitch\":{\"min\":120,\"max\":80}}");
        engine.play("test", "walk", new AcousticContext(0, 0, 0, 1, 1).withRangePosition(-1, 4));
        assertEquals(0F, volumes.get(0), 0);
        assertEquals(0F, pitches.get(0), 0);
        engine.play(
            "test",
            "walk",
            new AcousticContext(0, 0, 0, 1, 1).withRangePosition(Float.MAX_VALUE, -Float.MAX_VALUE));
        assertTrue(Float.isFinite(volumes.get(1)));
        assertTrue(Float.isFinite(pitches.get(1)));
    }

    @Test
    public void delayedSoundsKeepTheirContextAndClearOnlyPending() {
        load("{\"type\":\"delayed\",\"delay\":{\"min\":100,\"max\":100},\"acoustic\":\"delayed\"}");
        engine.play("test", "walk", new AcousticContext(0, 0, 0, 0.25f, 0.75f));
        tick();
        assertTrue(names.isEmpty());
        tick();
        assertEquals(1, names.size());
        assertEquals(0.25f, volumes.get(0), 0.00001f);
        assertEquals(0.75f, pitches.get(0), 0.00001f);
        engine.play("test", "walk", context);
        engine.clear();
        tick();
        tick();
        assertEquals(1, names.size());
        assertTrue(engine.has("test"));
    }

    @Test
    public void nestedDelayOverridesOuterAndSchedulesSamplesOnce() {
        load(
            "{\"type\":\"delayed\",\"delay\":1000,\"acoustic\":[{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"inner\"},\"outer\"]}");
        engine.play("test", "walk", context);
        tick();
        assertEquals(Arrays.asList("inner"), names);
        now = 1000;
        engine.tick();
        assertEquals(Arrays.asList("inner", "outer"), names);
        now = 1050;
        engine.tick();
        assertEquals(2, names.size());
    }

    @Test
    public void nestedZeroDelayOverridesOuterAndSiblingOrderIsStable() {
        load(
            "{\"type\":\"delayed\",\"delay\":100,\"acoustic\":[{\"type\":\"delayed\",\"delay\":0,\"acoustic\":\"immediate\"},\"first\",\"second\"]}");
        engine.play("test", "walk", context);
        assertEquals(Arrays.asList("immediate"), names);
        now = 100;
        engine.tick();
        assertEquals(Arrays.asList("immediate", "first", "second"), names);
    }

    @Test
    public void stalledDelayedLayersExpireWithoutRecreatingNestedWork() {
        load(
            "{\"type\":\"delayed\",\"delay_min\":50,\"delay_max\":50,\"acoustic\":{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"stale\"}}");
        engine.play("test", "walk", context);
        now = 1000;
        engine.tick();
        tick();
        assertTrue(names.isEmpty());
        engine.play("test", "walk", context);
        tick();
        assertEquals(Arrays.asList("stale"), names);
    }

    @Test
    public void callerCanRetainLateLayersThroughContextChanges() {
        load("{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"kept\"}");
        AcousticContext keep = context.withLateSoundSkipping(false)
            .scale(0.5f, 1)
            .withRangePosition(0.25f, 0.5f)
            .withCategory(net.minecraft.client.audio.SoundCategory.MOBS);
        engine.play("test", "walk", keep);
        engine.play("test", "walk", keep.withLateSoundSkipping(true));
        now = 1000;
        engine.tick();
        assertEquals(Arrays.asList("kept"), names);
        assertEquals(0.5f, volumes.get(0), 0);
    }

    @Test
    public void legacySkippablePresenceKeepsLateLayersRegardlessOfValue() {
        load(
            "{\"type\":\"delayed\",\"delay\":200,\"skippable\":false,\"acoustic\":{\"type\":\"delayed\",\"delay\":50,\"name\":\"kept\"}}");
        engine.play("test", "walk", context);
        now = 1000;
        engine.tick();
        assertEquals(Arrays.asList("kept"), names);
    }

    @Test
    public void invalidOverridePreservesDefinition() {
        load("\"good\"");
        try {
            load("{\"type\":\"unrecognized\"}");
            fail("invalid type accepted");
        } catch (com.google.gson.JsonParseException expected) {}
        engine.play("test", "walk", context);
        assertEquals(java.util.Arrays.asList("good"), names);
    }

    @Test
    public void contactsCreatedDuringTickDoNotAgeTheirDelayImmediately() {
        load("{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"nextTick\"}");
        engine.beginTick();
        engine.play("test", "walk", context);
        tick();
        assertTrue(names.isEmpty());
        engine.beginTick();
        tick();
        assertEquals(java.util.Arrays.asList("nextTick"), names);
    }

    @Test
    public void invalidatedActorCannotPlayOrRescheduleDelayedSounds() {
        java.util.concurrent.atomic.AtomicBoolean alive = new java.util.concurrent.atomic.AtomicBoolean(true);
        AcousticContext owned = new AcousticContext(1, 2, 3, 1, 1, alive::get);
        load("{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"obsolete\"}");
        engine.play("test", "walk", owned);
        engine.beginTick();
        alive.set(false);
        tick();
        engine.play("test", "walk", owned);
        tick();
        assertTrue(names.isEmpty());
        alive.set(true);
        tick();
        assertTrue(names.isEmpty());
    }

    @Test
    public void localAcousticsCanAddOrOverrideWithoutAnIndexAndRecoverMalformedEdits() throws Exception {
        Resources resources = new Resources();
        resources.add("config/acoustics/base.json", "\"pack\"");
        File acoustics = temporary.newFolder("acoustics");
        File base = new File(acoustics, "base.json");
        Files.write(base.toPath(), "\"local\"".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(acoustics, "added.json").toPath(), "\"added\"".getBytes(StandardCharsets.UTF_8));

        engine.reload(resources, Arrays.asList("base"), temporary.getRoot());
        engine.play("base", "walk", context);
        engine.play("added", "walk", context);
        assertEquals(Arrays.asList("local", "added"), names);
        Map<String, String> firstSnapshot = engine.definitions();
        assertEquals("\"local\"", firstSnapshot.get("base"));
        assertEquals(Arrays.asList("added", "base"), new ArrayList<>(firstSnapshot.keySet()));

        names.clear();
        Files.write(base.toPath(), "{\"type\":\"unknown\"}".getBytes(StandardCharsets.UTF_8));
        engine.reload(resources, Arrays.asList("base"), temporary.getRoot());
        engine.play("base", "walk", context);
        assertEquals(Collections.singletonList("local"), names);
        assertEquals(firstSnapshot, engine.definitions());

        names.clear();
        assertTrue(base.delete());
        engine.reload(resources, Arrays.asList("base"), temporary.getRoot());
        engine.play("base", "walk", context);
        assertEquals(Collections.singletonList("pack"), names);
        assertEquals(
            "\"pack\"",
            engine.definitions()
                .get("base"));
        assertEquals("\"local\"", firstSnapshot.get("base"));
        engine.reload(resources, Collections.emptyList());
        assertTrue(
            engine.definitions()
                .isEmpty());
    }

    @Test
    public void snapshotUsesWinningValidLayerAndDoesNotAliasMutableInput() throws Exception {
        Resources resources = new Resources();
        resources.add("config/acoustics/base.json", "\"lower\"");
        resources.add("config/acoustics/base.json", "\"higher\"");
        resources.add("config/acoustics/base.json", "{\"type\":\"invalid\"}");
        engine.reload(resources, Collections.singletonList("base"));
        assertEquals(
            "\"higher\"",
            engine.definitions()
                .get("base"));
        engine.play("base", "walk", context);
        assertEquals(Collections.singletonList("higher"), names);
        com.google.gson.JsonObject original = new JsonParser().parse("{\"name\":\"original\"}")
            .getAsJsonObject();
        engine.load("base", original);
        original.addProperty("name", "mutated");
        assertEquals(
            "original",
            new JsonParser().parse(
                engine.definitions()
                    .get("base"))
                .getAsJsonObject()
                .get("name")
                .getAsString());
        engine.clear();
        assertTrue(
            engine.definitions()
                .containsKey("base"));
    }

    @Test
    public void exportedPackRoundTripsDefinitionsIncludingExplicitNulls() throws Exception {
        engine.load("quiet", new JsonParser().parse("null"));
        engine.load("events", new JsonParser().parse("{\"type\":\"events\",\"walk\":\"custom:étape\",\"run\":null}"));
        engine.load("layers", new JsonParser().parse("[\"first\",{\"type\":\"empty\"}]"));
        File target = new File(temporary.getRoot(), "exports/acoustics.zip");
        assertEquals(3, AcousticExport.write(target, engine.definitions()));
        byte[] first = Files.readAllBytes(target.toPath());
        AcousticExport.write(target, engine.definitions());
        assertArrayEquals(first, Files.readAllBytes(target.toPath()));
        Resources resources = new Resources();
        List<String> ids = new ArrayList<>();
        try (ZipFile zip = new ZipFile(target)) {
            assertEquals(
                1,
                new JsonParser().parse(zipText(zip, "pack.mcmeta"))
                    .getAsJsonObject()
                    .getAsJsonObject("pack")
                    .get("pack_format")
                    .getAsInt());
            assertNotNull(zip.getEntry("LICENSE-MIT.txt"));
            assertNotNull(zip.getEntry("SOURCE-NOTICE.txt"));
            for (JsonElement id : new JsonParser()
                .parse(zipText(zip, "assets/presencefootsteps/config/acoustics/index.json"))
                .getAsJsonArray()) {
                String name = id.getAsString();
                ids.add(name);
                String path = "config/acoustics/" + name + ".json";
                String json = zipText(zip, "assets/presencefootsteps/" + path);
                assertEquals(
                    new JsonParser().parse(
                        engine.definitions()
                            .get(name)),
                    new JsonParser().parse(json));
                resources.add(path, json);
            }
        }
        assertEquals(Arrays.asList("events", "layers", "quiet"), ids);
        engine.reload(resources, ids);
        engine.play("events", "walk", context);
        engine.play("events", "run", context);
        engine.play("quiet", "walk", context);
        assertEquals(Collections.singletonList("custom:étape"), names);
        assertTrue(engine.has("events", "run"));
        assertTrue(engine.has("quiet"));
    }

    @Test
    public void invalidOrEmptyExportPreservesPreviousArchive() throws Exception {
        File target = temporary.newFile("existing.zip");
        byte[] original = new byte[] { 1, 2, 3 };
        Files.write(target.toPath(), original);
        for (Map<String, String> definitions : Arrays.asList(
            Collections.<String, String>emptyMap(),
            Collections.singletonMap("../escape", "null"),
            Collections.singletonMap("index", "null"))) {
            try {
                AcousticExport.write(target, definitions);
                fail("Invalid export accepted");
            } catch (IllegalArgumentException | IllegalStateException | IOException expected) {}
            assertArrayEquals(original, Files.readAllBytes(target.toPath()));
        }
    }

    private static String zipText(ZipFile zip, String path) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            zip.getInputStream(zip.getEntry(path)),
            StandardCharsets.UTF_8)) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[2048];
            int length;
            while ((length = reader.read(buffer)) != -1) result.append(buffer, 0, length);
            return result.toString();
        }
    }

    private static final class Resources implements IResourceManager {

        private final Map<String, List<IResource>> layers = new HashMap<String, List<IResource>>();

        void add(String path, String json) {
            List<IResource> values = layers.get(path);
            if (values == null) {
                values = new ArrayList<IResource>();
                layers.put(path, values);
            }
            values.add(new StringResource(json));
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton("presencefootsteps");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            List<IResource> values = getAllResources(location);
            return values.get(values.size() - 1);
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            List<IResource> values = layers.get(location.getResourcePath());
            if (values == null) throw new IOException("missing " + location);
            return values;
        }
    }

    private static final class StringResource implements IResource {

        private final byte[] bytes;

        StringResource(String value) {
            bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public IMetadataSection getMetadata(String section) {
            return null;
        }
    }
}
