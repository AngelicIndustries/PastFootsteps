package org.fentanylsolutions.pastfootsteps.profile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition.Contact;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProfileLibraryTest {

    private static final String BIPED_GAIT = "{\"schemaVersion\":1,\"gaits\":{\"biped\":{"
        + "\"walk\":[{\"footId\":\"right\",\"phase\":1,\"lateral\":-0.25,\"forward\":0},"
        + "{\"footId\":\"left\",\"phase\":0.5,\"lateral\":0.25,\"forward\":0}]}}}";
    private static final String PLAYER = "{\"schemaVersion\":1,\"entities\":{"
        + "\"Player\":{\"gait\":\"biped\",\"runSpeed\":0.2}}}";

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void malformedHigherLayersLeaveLowerDefinitions() {
        Resources resources = new Resources();
        resources.add("config/gaits.json", BIPED_GAIT);
        resources.add("config/gaits.json", "{broken");
        resources.add("config/entities.json", PLAYER);
        resources.add("config/entities.json", "{\"schemaVersion\":2,\"entities\":{}}");

        ProfileLibrary library = load(resources);
        EntityProfile player = library.resolve("Player");
        assertNotNull(player);
        assertEquals(0.2, player.runSpeed, 0.0001);
        assertFalse(
            library.diagnostics()
                .isEmpty());
        assertArrayEquals(new String[] { "left", "right" }, footIds(player.gait.contacts(false)));
    }

    @Test
    public void missingGaitReferenceDoesNotReplaceLowerProfile() {
        Resources resources = new Resources();
        resources.add("config/gaits.json", BIPED_GAIT);
        resources.add("config/entities.json", PLAYER);
        resources.add("config/entities.json", "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"missing\"}}}");

        ProfileLibrary library = load(resources);
        assertEquals("biped", library.resolve("Player").gait.id);
        assertFalse(
            library.diagnostics()
                .isEmpty());
    }

    @Test
    public void unknownAndCaseMismatchedEntitiesKeepNativeBehavior() {
        ProfileLibrary library = load(validDefaults());
        assertNull(library.resolve("Spider"));
        assertNull(library.resolve("Slime"));
        assertNull(library.resolve("player"));
        assertNull(library.resolve(new EntityCow(null)));
    }

    @Test
    public void quadrupedRunContactsFormTwoDiagonalGroups() {
        Resources resources = validDefaults();
        resources.add(
            "config/gaits.json",
            "{\"schemaVersion\":1,\"gaits\":{\"quadruped\":{\"walk\":[" + contact("hind_left", 0.25)
                + ","
                + contact("front_left", 0.5)
                + ","
                + contact("hind_right", 0.75)
                + ","
                + contact("front_right", 1.0)
                + "],\"run\":["
                + contact("hind_left", 0.5)
                + ","
                + contact("front_right", 0.5)
                + ","
                + contact("front_left", 1.0)
                + ","
                + contact("hind_right", 1.0)
                + "]}}}");
        resources.add("config/entities.json", "{\"schemaVersion\":1,\"entities\":{\"Cow\":{\"gait\":\"quadruped\"}}}");

        Contact[] contacts = load(resources).resolve("Cow").gait.contacts(true);
        assertEquals(4, contacts.length);
        assertEquals(0.5, contacts[0].phase, 0.0);
        assertEquals(0.5, contacts[1].phase, 0.0);
        assertEquals(1.0, contacts[2].phase, 0.0);
        assertEquals(1.0, contacts[3].phase, 0.0);
        assertArrayEquals(new String[] { "hind_left", "front_right", "front_left", "hind_right" }, footIds(contacts));
    }

    @Test
    public void whollyInvalidReloadRetainsLastGoodSnapshot() {
        ProfileLibrary library = load(validDefaults());
        Resources invalid = new Resources();
        invalid.add("config/gaits.json", "{broken");
        invalid.add("config/entities.json", "[]");

        library.reload(invalid, null);
        assertNotNull(library.resolve("Player"));
        assertFalse(
            library.diagnostics()
                .isEmpty());
    }

    @Test
    public void localOverrideIsLastAndCanDisableAnEntity() throws IOException {
        File profileDirectory = temporary.newFolder("profiles");
        Files.write(
            new File(profileDirectory, "entities.json").toPath(),
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"enabled\":false}}}".getBytes(StandardCharsets.UTF_8));

        ProfileLibrary library = new ProfileLibrary();
        library.reload(validDefaults(), temporary.getRoot());
        assertNull(library.resolve("Player"));
    }

    @Test
    public void modelConstructorsRejectInvalidAdapterData() {
        Contact left = new Contact("left", 0.5, 0.25, 0.0);
        Contact right = new Contact("right", 1.0, -0.25, 0.0);
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new Contact("left", 0.0, 0.0, 0.0);
            }
        });
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new GaitDefinition("duplicate", new Contact[] { left, left }, new Contact[] { left, right });
            }
        });
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new GaitDefinition("different", new Contact[] { left }, new Contact[] { right });
            }
        });
        final GaitDefinition valid = new GaitDefinition(
            "biped",
            new Contact[] { left, right },
            new Contact[] { left, right });
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new EntityProfile("Player", valid, Double.POSITIVE_INFINITY, 0.5, 0.2, 1.0F);
            }
        });
    }

    @Test
    public void zeroVolumeIsAValidDeliberatelyMutedProfile() {
        Resources resources = new Resources();
        resources.add("config/gaits.json", BIPED_GAIT);
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"biped\",\"volume\":0}}}");

        EntityProfile player = load(resources).resolve("Player");
        assertNotNull(player);
        assertEquals(0.0F, player.volume, 0.0F);
    }

    @Test
    public void exactPhaseGroupsRemainValidButNearDistinctPhasesAreRejected() {
        final Contact left = new Contact("left", 0.5, 0.25, 0.0);
        final Contact rightGrouped = new Contact("right", 0.5, -0.25, 0.0);
        GaitDefinition grouped = new GaitDefinition(
            "grouped",
            new Contact[] { left, rightGrouped },
            new Contact[] { left, rightGrouped });
        assertEquals(0.5, grouped.contacts(false)[0].phase, 0.0);
        assertEquals(0.5, grouped.contacts(false)[1].phase, 0.0);

        final Contact rightTooClose = new Contact("right", 0.5000005, -0.25, 0.0);
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new GaitDefinition(
                    "too_close",
                    new Contact[] { left, rightTooClose },
                    new Contact[] { left, rightTooClose });
            }
        });
    }

    @Test
    public void phaseTooCloseToCycleBoundaryIsRejected() {
        assertRejected(new CheckedOperation() {

            @Override
            public void run() {
                new Contact("left", 0.0000005, 0.25, 0.0);
            }
        });
    }

    @Test
    public void invalidNearPhaseOverrideLeavesLowerGait() {
        Resources resources = validDefaults();
        resources.add(
            "config/gaits.json",
            "{\"schemaVersion\":1,\"gaits\":{\"biped\":{\"walk\":[" + contact("left", 0.5)
                + ","
                + contact("right", 0.5000005)
                + "]}}}");

        ProfileLibrary library = load(resources);
        Contact[] contacts = library.resolve("Player").gait.contacts(false);
        assertEquals(0.5, contacts[0].phase, 0.0);
        assertEquals(1.0, contacts[1].phase, 0.0);
        assertFalse(
            library.diagnostics()
                .isEmpty());
    }

    @Test
    public void bundledDefaultsUseResolverAxisConvention() throws IOException {
        Resources resources = new Resources();
        resources.add("config/gaits.json", resource("/assets/pastfootsteps/config/gaits.json"));
        resources.add("config/entities.json", resource("/assets/pastfootsteps/config/entities.json"));
        ProfileLibrary library = load(resources);

        Contact playerLeft = contact(library.resolve("Player").gait.contacts(false), "left");
        Contact playerRight = contact(library.resolve("Player").gait.contacts(false), "right");
        assertTrue(playerLeft.lateral > 0.0);
        assertTrue(playerRight.lateral < 0.0);
        Contact cowFrontLeft = contact(library.resolve("Cow").gait.contacts(false), "front_left");
        Contact cowHindRight = contact(library.resolve("Cow").gait.contacts(false), "hind_right");
        assertTrue(cowFrontLeft.lateral > 0.0);
        assertTrue(cowFrontLeft.forward > 0.0);
        assertTrue(cowHindRight.lateral < 0.0);
        assertTrue(cowHindRight.forward < 0.0);
    }

    @Test
    public void bundledVanillaProfilesUseOnlySupportedGroundGaits() throws IOException {
        Resources resources = new Resources();
        resources.add("config/gaits.json", resource("/assets/pastfootsteps/config/gaits.json"));
        resources.add("config/entities.json", resource("/assets/pastfootsteps/config/entities.json"));
        ProfileLibrary library = load(resources);

        for (String id : new String[] { "Zombie", "Skeleton", "PigZombie", "Enderman", "Witch", "Villager",
            "VillagerGolem", "Chicken", "Creeper", "Cow", "MushroomCow", "Pig", "Sheep", "Wolf", "Ozelot",
            "EntityHorse" }) assertNotNull(id, library.resolve(id));
        assertNull(library.resolve("Spider"));
        assertNull(library.resolve("LavaSlime"));
        assertNull(library.resolve("Bat"));

        Contact[] horseTrot = library.resolve("EntityHorse").gait.contacts(true);
        assertEquals("horse", library.resolve("EntityHorse").gait.id);
        assertEquals(0.5, horseTrot[0].phase, 0.0);
        assertEquals(0.5, horseTrot[1].phase, 0.0);
        assertEquals(1.0, horseTrot[2].phase, 0.0);
        assertEquals(1.0, horseTrot[3].phase, 0.0);
    }

    @Test
    public void malformedLocalFilesRetainTheirLastValidLayers() throws IOException {
        File profileDirectory = temporary.newFolder("profiles");
        File gaits = new File(profileDirectory, "gaits.json");
        File entities = new File(profileDirectory, "entities.json");
        write(gaits, gaitLayer("biped", 0.25, 0.75));
        write(entities, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"biped\",\"volume\":0.25}}}");
        ProfileLibrary library = new ProfileLibrary();
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25, library.resolve("Player").gait.contacts(false)[0].phase, 0.0);
        assertEquals(0.25F, library.resolve("Player").volume, 0.0F);

        write(gaits, "{broken");
        write(entities, "{broken");
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25, library.resolve("Player").gait.contacts(false)[0].phase, 0.0);
        assertEquals(0.25F, library.resolve("Player").volume, 0.0F);
        assertTrue(hasDiagnostic(library, "Retained cached last-valid local gait layer"));
        assertTrue(hasDiagnostic(library, "Retained cached last-valid local entity layer"));
    }

    @Test
    public void deletingLocalFileClearsItsCachedLayer() throws IOException {
        File profileDirectory = temporary.newFolder("profiles");
        File entities = new File(profileDirectory, "entities.json");
        write(entities, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"biped\",\"volume\":0.25}}}");
        ProfileLibrary library = new ProfileLibrary();
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25F, library.resolve("Player").volume, 0.0F);

        Files.delete(entities.toPath());
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(1.0F, library.resolve("Player").volume, 0.0F);
    }

    @Test
    public void cachedEntitySpecsResolveAgainstFreshGaits() throws IOException {
        File profileDirectory = temporary.newFolder("profiles");
        File gaits = new File(profileDirectory, "gaits.json");
        File entities = new File(profileDirectory, "entities.json");
        write(gaits, gaitLayer("custom", 0.25, 0.75));
        write(entities, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"custom\"}}}");
        ProfileLibrary library = new ProfileLibrary();
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25, library.resolve("Player").gait.contacts(false)[0].phase, 0.0);

        write(gaits, gaitLayer("custom", 0.4, 0.9));
        write(entities, "{broken");
        library.reload(validDefaults(), temporary.getRoot());
        EntityProfile player = library.resolve("Player");
        assertSame(library.gait("custom"), player.gait);
        assertEquals(0.4, player.gait.contacts(false)[0].phase, 0.0);

        Files.delete(gaits.toPath());
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals("biped", library.resolve("Player").gait.id);
        assertTrue(hasDiagnostic(library, "missing gait 'custom'"));
    }

    @Test
    public void inheritedProfilesOverrideOnlyDeclaredTuning() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{"
                + "\"cow_base\":{\"gait\":\"biped\",\"strideMultiplier\":0.8,\"volume\":0.4},"
                + "\"Cow\":{\"parent\":\"cow_base\",\"volume\":0.7,\"referenceWidth\":0.9}}}");

        EntityProfile cow = load(resources).resolve("Cow");
        assertNotNull(cow);
        assertEquals("cow_base", cow.parentId);
        assertEquals("biped", cow.gait.id);
        assertEquals(0.8, cow.strideMultiplier, 0.0);
        assertEquals(0.7F, cow.volume, 0.0F);
        assertEquals(0.5, cow.childScale, 0.0);
        assertEquals(0.9, cow.referenceWidth, 0.0);
    }

    @Test
    public void referenceWidthIsInheritedAndCanBeOverridden() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{" + "\"base\":{\"gait\":\"biped\",\"referenceWidth\":1.25},"
                + "\"child\":{\"parent\":\"base\"},"
                + "\"override\":{\"parent\":\"base\",\"referenceWidth\":0.4}}}");
        ProfileLibrary library = load(resources);
        assertEquals(1.25, library.resolve("child").referenceWidth, 0.0);
        assertEquals(0.4, library.resolve("override").referenceWidth, 0.0);
    }

    @Test
    public void invalidReferenceWidthLeavesLowerProfileAndOldConstructorIsNeutral() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"biped\",\"referenceWidth\":65}}}");
        ProfileLibrary library = load(resources);
        assertEquals(0.0, library.resolve("Player").referenceWidth, 0.0);
        assertFalse(
            library.diagnostics()
                .isEmpty());
        Contact left = new Contact("left", 0.5, 0.25, 0.0);
        Contact right = new Contact("right", 1.0, -0.25, 0.0);
        EntityProfile profile = new EntityProfile(
            "legacy",
            new GaitDefinition("legacy", new Contact[] { left, right }, new Contact[] { left, right }),
            1.0,
            0.5,
            0.2,
            1.0F);
        assertEquals(0.0, profile.referenceWidth, 0.0);
    }

    @Test
    public void cyclesAndMissingParentsRejectTheWholeHigherLayer() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"parent\":\"missing\",\"volume\":0.1}}}");
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"a\":{\"parent\":\"b\"},\"b\":{\"parent\":\"a\"}}}");

        ProfileLibrary library = load(resources);
        assertEquals(1.0F, library.resolve("Player").volume, 0.0F);
        assertTrue(hasDiagnostic(library, "missing parent profile 'missing'"));
        assertTrue(hasDiagnostic(library, "inheritance cycle"));
    }

    @Test
    public void exactAgeSelectorBeatsClassSelectorsAndUnknownsNeedExplicitSelectors() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{" + "\"animal\":{\"gait\":\"biped\",\"selector\":{\"class\":\""
                + EntityAnimal.class.getName()
                + "\"},\"volume\":0.1},"
                + "\"cow_class\":{\"parent\":\"animal\",\"selector\":{\"class\":\""
                + EntityCow.class.getName()
                + "\"},\"volume\":0.2},"
                + "\"cow_exact\":{\"parent\":\"animal\",\"selector\":{\"entityId\":\"Cow\",\"age\":\"adult\"},\"volume\":0.3},"
                + "\"cow_child\":{\"parent\":\"animal\",\"selector\":{\"entityId\":\"Cow\",\"age\":\"child\"},\"volume\":0.4}}}");

        ProfileLibrary library = load(resources);
        EntityCow cow = new EntityCow(null);
        assertEquals(0.3F, library.resolve(cow).volume, 0.0F);
        cow.setGrowingAge(-24000);
        assertEquals(0.4F, library.resolve(cow).volume, 0.0F);
        assertNull(library.resolve("Cow"));
        assertNull(library.resolve("Spider"));
    }

    @Test
    public void horseAndSkeletonVariantSelectorsAreExplicit() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{"
                + "\"horse\":{\"gait\":\"biped\",\"selector\":{\"entityId\":\"EntityHorse\"}},"
                + "\"donkey\":{\"parent\":\"horse\",\"selector\":{\"entityId\":\"EntityHorse\",\"horseType\":\"donkey\"},\"volume\":0.4},"
                + "\"skeleton\":{\"gait\":\"biped\",\"selector\":{\"entityId\":\"Skeleton\"}},"
                + "\"wither\":{\"parent\":\"skeleton\",\"selector\":{\"entityId\":\"Skeleton\",\"skeletonType\":\"wither\"},\"volume\":0.6}}}");

        ProfileLibrary library = load(resources);
        World world = new TestWorld();
        EntityHorse horse = new EntityHorse(world);
        horse.setHorseType(1);
        assertEquals("donkey", library.resolve(horse).id);
        EntitySkeleton skeleton = new EntitySkeleton(world);
        skeleton.setSkeletonType(1);
        assertEquals("wither", library.resolve(skeleton).id);
    }

    @Test
    public void invalidLocalInheritanceRetainsLastValidLayer() throws IOException {
        File profileDirectory = temporary.newFolder("profiles");
        File entities = new File(profileDirectory, "entities.json");
        write(
            entities,
            "{\"schemaVersion\":1,\"entities\":{\"base\":{\"gait\":\"biped\",\"volume\":0.25},\"Player\":{\"parent\":\"base\"}}}");
        ProfileLibrary library = new ProfileLibrary();
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25F, library.resolve("Player").volume, 0.0F);

        write(entities, "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"parent\":\"gone\"}}}");
        library.reload(validDefaults(), temporary.getRoot());
        assertEquals(0.25F, library.resolve("Player").volume, 0.0F);
        assertTrue(hasDiagnostic(library, "Retained cached last-valid local entity layer"));
    }

    @Test
    public void classSelectorsAreStrictButMissingOptionalClassesStayDormant() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"Player\":{\"gait\":\"biped\",\"selector\":{\"class\":\"java.lang.String\"},\"volume\":0.1}}}");
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"optional\":{\"gait\":\"biped\",\"selector\":{\"class\":\"example.missing.OptionalMob\"}}}}");

        ProfileLibrary library = load(resources);
        assertEquals(1.0F, library.resolve("Player").volume, 0.0F);
        assertTrue(hasDiagnostic(library, "is not an EntityLivingBase class"));
        assertTrue(hasDiagnostic(library, "is unavailable"));
    }

    @Test
    public void exactDisableTombstoneBeatsBroadClassSelector() {
        Resources resources = validDefaults();
        resources.add(
            "config/entities.json",
            "{\"schemaVersion\":1,\"entities\":{\"animal\":{\"gait\":\"biped\",\"selector\":{\"class\":\""
                + EntityAnimal.class.getName()
                + "\"}}}}");
        resources.add("config/entities.json", "{\"schemaVersion\":1,\"entities\":{\"Cow\":{\"enabled\":false}}}");

        ProfileLibrary library = load(resources);
        assertNull(library.resolve(new EntityCow(null)));
        assertNull(library.resolve("Cow"));
        assertEquals("animal", library.resolve(new EntityPig(null)).id);
    }

    @Test
    public void inheritanceAndJsonNestingAreBounded() {
        Resources resources = validDefaults();
        StringBuilder chain = new StringBuilder("{\"schemaVersion\":1,\"entities\":{");
        for (int index = 64; index >= 0; index--) {
            if (index < 64) chain.append(',');
            chain.append("\"p")
                .append(index)
                .append("\":{");
            if (index == 0) chain.append("\"gait\":\"biped\"");
            else chain.append("\"parent\":\"p")
                .append(index - 1)
                .append("\"");
            chain.append('}');
        }
        chain.append("}}");
        resources.add("config/entities.json", chain.toString());

        StringBuilder nested = new StringBuilder("{\"schemaVersion\":1,\"entities\":{},\"extra\":");
        for (int index = 0; index < 70; index++) nested.append('[');
        nested.append('0');
        for (int index = 0; index < 70; index++) nested.append(']');
        nested.append('}');
        resources.add("config/entities.json", nested.toString());

        ProfileLibrary library = load(resources);
        assertNotNull(library.resolve("Player"));
        assertTrue(hasDiagnostic(library, "inheritance depth exceeds 64"));
        assertTrue(hasDiagnostic(library, "maximum JSON depth 64"));
    }

    private static Resources validDefaults() {
        Resources resources = new Resources();
        resources.add("config/gaits.json", BIPED_GAIT);
        resources.add("config/entities.json", PLAYER);
        return resources;
    }

    /** Minimal non-ticking world needed by vanilla entity constructors used in selector tests. */
    private static final class TestWorld extends World {

        TestWorld() {
            super(
                null,
                "profile-selector-test",
                new WorldProviderSurface(),
                new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                new Profiler());
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    private static String contact(String id, double phase) {
        return "{\"footId\":\"" + id + "\",\"phase\":" + phase + ",\"lateral\":0,\"forward\":0}";
    }

    private static String gaitLayer(String id, double leftPhase, double rightPhase) {
        return "{\"schemaVersion\":1,\"gaits\":{\"" + id
            + "\":{\"walk\":["
            + contact("left", leftPhase)
            + ","
            + contact("right", rightPhase)
            + "]}}}";
    }

    private static String[] footIds(Contact[] contacts) {
        String[] ids = new String[contacts.length];
        for (int index = 0; index < contacts.length; index++) ids[index] = contacts[index].footId;
        return ids;
    }

    private static Contact contact(Contact[] contacts, String footId) {
        for (Contact contact : contacts) {
            if (footId.equals(contact.footId)) return contact;
        }
        throw new AssertionError("missing contact " + footId);
    }

    private static String resource(String path) throws IOException {
        InputStream input = ProfileLibraryTest.class.getResourceAsStream(path);
        if (input == null) throw new IOException("missing test resource " + path);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) >= 0) result.append(buffer, 0, read);
            return result.toString();
        }
    }

    private static void write(File file, String value) throws IOException {
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasDiagnostic(ProfileLibrary library, String text) {
        for (String diagnostic : library.diagnostics()) {
            if (diagnostic.contains(text)) return true;
        }
        return false;
    }

    private static ProfileLibrary load(Resources resources) {
        ProfileLibrary library = new ProfileLibrary();
        library.reload(resources, null);
        return library;
    }

    private static void assertRejected(CheckedOperation operation) {
        try {
            operation.run();
            fail("expected invalid adapter data to be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    private interface CheckedOperation {

        void run();
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
            return Collections.singleton("pastfootsteps");
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
