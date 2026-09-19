package org.fentanylsolutions.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.oredict.OreDictionary;

import org.fentanylsolutions.pastfootsteps.api.BlockMaterial;
import org.fentanylsolutions.pastfootsteps.api.DerivedBlock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MaterialLibraryTest {

    private static final Block STONE = register(250, "test:stone", new TestBlock());
    private static final Block COBBLESTONE = register(251, "test:cobblestone", new TestBlock());

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void higherLayerAndMetadataOverrideGenericEntries() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"low\",\"test:stone^1\":\"meta\"}");
        resources.add("config/blockmap.json", "{\"test:stone\":\"high\",\"test:stone.foliage\":\"brush\"}");
        MaterialLibrary library = load(resources);
        assertEquals("meta", library.lookup(STONE, 1, ""));
        assertEquals("high", library.lookup(STONE, 0, ""));
        assertEquals("brush", library.lookup(STONE, 0, "foliage"));
        assertNull(library.lookup(STONE, 0, "wet"));
    }

    @Test
    public void invalidHigherLayerLeavesLowerLayerAndPrimitiveFallback() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"stone-explicit\"}");
        resources.add("config/blockmap.json", "{broken");
        resources.add("config/primitivemap.json", "{\"stone\":\"primitive # legacy mapping comment\"}");
        MaterialLibrary library = load(resources);
        assertEquals("stone-explicit", library.lookup(STONE, 0, ""));
        assertEquals("primitive", library.lookup(STONE, 0, "bigger"));
        assertNull(library.lookup(STONE, 0, "foliage"));
        assertEquals("primitive", library.lookup(COBBLESTONE, 0, ""));
    }

    @Test
    public void biomeIdPrecedesNameAndMissingValuesDefault() {
        Resources resources = new Resources();
        resources.add(
            "config/biomevariancemap.json",
            "{\"test_biome\":{\"volume\":0.5},\"222\":{\"volume\":0.75,\"pitch\":1.25}}");
        MaterialLibrary library = load(resources);
        TestBiome biome = new TestBiome(222, "Test Biome");
        assertEquals(0.75F, library.biomeVolume(biome), 0.0001F);
        assertEquals(1.25F, library.biomePitch(biome), 0.0001F);
        assertEquals(1.0F, library.biomePitch(null), 0.0001F);
    }

    @Test
    public void legacyBlockKeysAndModernPrimitiveSpellingRemainUsable() {
        Resources resources = new Resources();
        Block vanilla = register(252, "minecraft:test_legacy_stone", new TestBlock());
        resources.add("config/blockmap.json", "{\"test_legacy_stone\":\"legacy-block\"}");
        resources.add("config/primitivemap.json", "{\"minecraft:block.stone.step\":\"modern-primitive\"}");
        MaterialLibrary library = load(resources);
        assertEquals("legacy-block", library.lookup(vanilla, 0, ""));
        assertEquals("modern-primitive", library.lookup(COBBLESTONE, 0, ""));
    }

    @Test
    public void biomeNamespaceAliasFollowsTheVanillaDisplayName() {
        Resources resources = new Resources();
        resources.add("config/biomevariancemap.json", "{\"minecraft:test_biome\":{\"volume\":0.8,\"pitch\":1.1}}");
        MaterialLibrary library = load(resources);
        TestBiome biome = new TestBiome(223, "Test Biome");
        assertEquals(0.8F, library.biomeVolume(biome), 0.0001F);
        assertEquals(1.1F, library.biomePitch(biome), 0.0001F);
    }

    @Test
    public void localMapsOverrideThenRetainLastGoodVersionAndDropOnDeletion() throws Exception {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"pack\"}");
        File profiles = temporary.newFolder("profiles");
        File local = new File(profiles, "blockmap.json");
        Files.write(local.toPath(), "{\"test:stone\":\"local\"}".getBytes(StandardCharsets.UTF_8));
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, temporary.getRoot());
        assertEquals("local", library.lookup(STONE, 0, ""));

        // Numeric values are semantically invalid for a material map, so this edit retains local.
        Files.write(local.toPath(), "{\"test:stone\":12}".getBytes(StandardCharsets.UTF_8));
        library.reload(resources, temporary.getRoot());
        assertEquals("local", library.lookup(STONE, 0, ""));

        assertTrue(local.delete());
        library.reload(resources, temporary.getRoot());
        assertEquals("pack", library.lookup(STONE, 0, ""));
    }

    @Test
    public void explicitUnassignedSkipsTheBlockMapWithoutRevivingGenericOrLowerEntries() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"generic\",\"test:stone^1\":\"lower\"}");
        resources.add(
            "config/blockmap.json",
            "{\"test:stone^1\":\" unassigned # reset \",\"test:stone.wet\":\"UNASSIGNED\"}");
        resources.add("config/primitivemap.json", "{\"stone\":\"primitive\"}");
        MaterialLibrary library = load(resources);
        assertEquals("primitive", library.lookup(STONE, 1, ""));
        assertEquals("generic", library.lookup(STONE, 0, ""));
        assertNull(library.lookup(STONE, 1, "wet"));
    }

    @Test
    public void primitiveUnassignedReturnsNativeFallbackWhileSilenceRemainsAssigned() {
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"test:stone\":\"non_emitter\",\"test:stone^1\":\"VaNiLlA\",\"test:stone^2\":\"WOOD\"}");
        resources.add("config/primitivemap.json", "{\"stone@1.00_1.00\":\"UNASSIGNED\",\"stone\":\"generic\"}");
        MaterialLibrary library = load(resources);
        assertNull(library.lookup(COBBLESTONE, 0, ""));
        assertEquals("NOT_EMITTER", library.lookup(STONE, 0, ""));
        assertEquals("VANILLA", library.lookup(STONE, 1, ""));
        assertEquals("wood", library.lookup(STONE, 2, ""));
    }

    @Test
    public void actorMapsOverrideSharedSpecificityAndKeepMissingSubstratesShared() {
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"test:stone^1\":\"global-meta\",\"test:stone\":\"global\",\"test:stone.wet\":\"splash\",\"test:stone.foliage\":\"brush\"}");
        resources.add(
            "config/materials/hooves.json",
            "{\"test:stone\":\"hoof\",\"test:stone^2\":\"hoof-meta\",\"test:stone.foliage\":\"NOT_EMITTER\"}");
        resources.add("config/materials/paws.json", "{\"test:stone\":\"paw\"}");
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, null, new HashSet<>(Arrays.asList("hooves", "paws", "missing")));
        assertEquals("hoof", library.lookup(STONE, 1, "", "hooves"));
        assertEquals("hoof-meta", library.lookup(STONE, 2, "", "hooves"));
        assertEquals("paw", library.lookup(STONE, 1, "", "paws"));
        assertEquals("global-meta", library.lookup(STONE, 1, ""));
        assertEquals("global-meta", library.lookup(STONE, 1, "", "missing"));
        assertEquals("splash", library.lookup(STONE, 0, "wet", "hooves"));
        assertEquals("NOT_EMITTER", library.lookup(STONE, 0, "foliage", "hooves"));
        assertEquals("brush", library.lookup(STONE, 0, "foliage", "paws"));
    }

    @Test
    public void actorUnassignedUsesPrimitiveWhileExplicitSilenceAndNativeStopLookup() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"global\",\"test:stone.wet\":\"splash\"}");
        resources.add("config/primitivemap.json", "{\"stone\":\"primitive\"}");
        resources.add(
            "config/materials/hooves.json",
            "{\"test:stone\":\"UNASSIGNED\",\"test:stone^1\":\"NOT_EMITTER\",\"test:stone^2\":\"VANILLA\",\"test:stone.wet\":\"UNASSIGNED\"}");
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, null, Collections.singleton("hooves"));
        assertEquals("primitive", library.lookup(STONE, 0, "", "hooves"));
        assertEquals("NOT_EMITTER", library.lookup(STONE, 1, "", "hooves"));
        assertEquals("VANILLA", library.lookup(STONE, 2, "", "hooves"));
        assertNull(library.lookup(STONE, 0, "wet", "hooves"));
        assertEquals("global", library.lookup(STONE, 0, ""));
    }

    @Test
    public void actorLocalLayersReloadAtomicallyAndDeletionRestoresPacks() throws Exception {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"shared\"}");
        resources.add("config/materials/hooves.json", "{\"test:stone\":\"pack\"}");
        resources.add("config/materials/hooves.json", "{\"test:stone\":\"should-not-leak\",\"test:cobblestone\":12}");
        File folder = temporary.newFolder("profiles", "materials");
        File file = new File(folder, "hooves.json");
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, temporary.getRoot(), Collections.singleton("hooves"));
        assertEquals("pack", library.lookup(STONE, 0, "", "hooves"));
        Files.write(file.toPath(), "{\"test:stone\":\"local\"}".getBytes(StandardCharsets.UTF_8));
        library.reload(resources, temporary.getRoot(), Collections.singleton("hooves"));
        assertEquals("local", library.lookup(STONE, 0, "", "hooves"));
        Files.write(file.toPath(), "{\"test:stone\":12}".getBytes(StandardCharsets.UTF_8));
        library.reload(resources, temporary.getRoot(), Collections.singleton("hooves"));
        assertEquals("local", library.lookup(STONE, 0, "", "hooves"));
        assertTrue(file.delete());
        library.reload(resources, temporary.getRoot(), Collections.singleton("hooves"));
        assertEquals("pack", library.lookup(STONE, 0, "", "hooves"));
        library.reload(resources, temporary.getRoot(), Collections.<String>emptySet());
        assertEquals("shared", library.lookup(STONE, 0, "", "hooves"));
    }

    @Test
    public void wildcardFamiliesKeepExactMetadataAndSubstratePrecedence() {
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"*\":\"global\",\"test:*\":\"namespace\"," + "\"test:*stone\":\"family\",\"test:cobble*^1\":\"variant\","
                + "\"test:stone\":\"exact\",\"*.wet\":\"splash\",\"test:stone.wet\":\"mudfine\"}");
        MaterialLibrary library = load(resources);
        assertEquals("exact", library.lookup(STONE, 0, ""));
        assertEquals("exact", library.lookup(STONE, 1, ""));
        assertEquals("family", library.lookup(COBBLESTONE, 0, ""));
        assertEquals("variant", library.lookup(COBBLESTONE, 1, ""));
        assertEquals("splash", library.lookup(COBBLESTONE, 0, "wet"));
        assertEquals("mudfine", library.lookup(STONE, 0, "wet"));
        assertNull(library.lookup(COBBLESTONE, 0, "foliage"));
    }

    @Test
    public void vanillaWildcardAliasesAndReloadedMissesStaySeparate() {
        Block vanilla = register(253, "minecraft:test_selector_block", new TestBlock());
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test_selector_*\":\"vanilla-only\"}");
        MaterialLibrary library = load(resources);
        assertEquals("vanilla-only", library.lookup(vanilla, 0, ""));
        assertNull(library.lookup(STONE, 0, ""));
        resources.add("config/blockmap.json", "{\"test:*\":\"now-present\"}");
        library.reload(resources);
        assertEquals("now-present", library.lookup(STONE, 0, ""));
    }

    @Test
    public void equalWildcardRulesUseLatestDeclarationAcrossLayers() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:*stone\":\"first\",\"test:stone*\":\"second\"}");
        assertEquals("second", load(resources).lookup(STONE, 0, ""));
        resources.add("config/blockmap.json", "{\"test:*stone\":\"rewritten\"}");
        assertEquals("rewritten", load(resources).lookup(STONE, 0, ""));
    }

    @Test
    public void actorWildcardCanOverrideSharedExactAndUnassignedStillFallsThrough() {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:stone\":\"shared\",\"*.wet\":\"splash\"}");
        resources.add("config/materials/hooves.json", "{\"test:*\":\"NOT_EMITTER\",\"test:*^1\":\"UNASSIGNED\"}");
        resources.add("config/primitivemap.json", "{\"stone\":\"primitive\"}");
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, null, Collections.singleton("hooves"));
        assertEquals("NOT_EMITTER", library.lookup(STONE, 0, "", "hooves"));
        assertEquals("primitive", library.lookup(STONE, 1, "", "hooves"));
        assertEquals("splash", library.lookup(STONE, 1, "wet", "hooves"));
        assertEquals("shared", library.lookup(STONE, 0, ""));
    }

    @Test
    public void invalidWildcardLocalEditRetainsLastValidSelectorLayer() throws Exception {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"*\":\"pack\"}");
        File profiles = temporary.newFolder("profiles");
        File file = new File(profiles, "blockmap.json");
        Files.write(file.toPath(), "{\"test:*\":\"local\"}".getBytes(StandardCharsets.UTF_8));
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, temporary.getRoot());
        for (String key : new String[] { "test:*^16", "test:*^oops", "test:*[state=true]", "#ore:" }) {
            Files.write(file.toPath(), ("{\"" + key + "\":\"broken\"}").getBytes(StandardCharsets.UTF_8));
            library.reload(resources, temporary.getRoot());
            assertEquals(key, "local", library.lookup(STONE, 0, ""));
        }
        assertTrue(file.delete());
        library.reload(resources, temporary.getRoot());
        assertEquals("pack", library.lookup(STONE, 0, ""));
    }

    @Test
    public void oreGroupsRespectMembershipMetadataAndExactExceptions() {
        String group = "pfTestStoneFamily";
        TestGroups groups = new TestGroups();
        groups.add(group, new ItemStack(new ItemBlock(STONE), 1, OreDictionary.WILDCARD_VALUE));
        groups.add(group, new ItemStack(new ItemBlock(COBBLESTONE), 1, 2));
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"*\":\"wild\",\"#ore:pfTestStoneFamily\":\"group\","
                + "\"#ore:pfTestStoneFamily^1\":\"group-meta\",\"#ore:pfTestStoneFamily.wet\":\"splash\","
                + "\"test:stone^3\":\"exception\",\"#minecraft:stone\":\"legacy-comment\"}");
        MaterialLibrary library = new MaterialLibrary(groups);
        library.reload(resources);
        assertEquals("group", library.lookup(STONE, 0, ""));
        assertEquals("group-meta", library.lookup(STONE, 1, ""));
        assertEquals("exception", library.lookup(STONE, 3, ""));
        assertEquals("group", library.lookup(COBBLESTONE, 2, ""));
        assertEquals("wild", library.lookup(COBBLESTONE, 1, ""));
        assertEquals("splash", library.lookup(COBBLESTONE, 2, "wet"));
        assertNull(library.lookup(COBBLESTONE, 0, "wet"));
    }

    @Test
    public void oreMembershipIsASnapshotAndMissingGroupsDoNotMutateDictionary() {
        String group = "pfTestLateStoneFamily";
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"#ore:" + group + "\":\"group\"}");
        TestGroups groups = new TestGroups();
        Set<String> before = new HashSet<>(Arrays.asList(groups.names()));
        MaterialLibrary library = new MaterialLibrary(groups);
        library.reload(resources);
        assertEquals(before, new HashSet<>(Arrays.asList(groups.names())));
        assertNull(library.lookup(STONE, 0, ""));
        groups.add(group, new ItemStack(new ItemBlock(STONE), 1, OreDictionary.WILDCARD_VALUE));
        assertNull(library.lookup(STONE, 0, ""));
        library.reload(resources);
        assertEquals("group", library.lookup(STONE, 0, ""));
    }

    @Test
    public void namespaceAndMultipleStarsMatchWithoutSwallowingOtherSubstrates() {
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"*:cob*sto*\":\"pattern\",\"test:stone.custom\":\"legacy\","
                + "\"test:stone^1\":\"meta\",\"*\":\"normal-only\"}");
        MaterialLibrary library = load(resources);
        assertEquals("pattern", library.lookup(COBBLESTONE, 0, ""));
        assertEquals("normal-only", library.lookup(STONE, 0, ""));
        assertEquals("legacy", library.lookup(STONE, 0, "custom"));
        assertNull(library.lookup(COBBLESTONE, 0, "custom"));
        assertEquals("meta", library.lookup(STONE, 17, ""));
    }

    @Test
    public void groupConflictsPreferMetadataThenLaterDeclarationAndIgnoreNonBlocks() {
        TestGroups groups = new TestGroups();
        groups.add("pfAlpha", new ItemStack(new ItemBlock(STONE), 1, OreDictionary.WILDCARD_VALUE));
        groups.add("pfBeta", new ItemStack(new ItemBlock(STONE), 1, OreDictionary.WILDCARD_VALUE));
        groups.add("pfBeta", new ItemStack(new Item(), 1, 0));
        groups.add("pfBeta", null);
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"#ore:pfAlpha^1\":\"meta\"," + "\"#ore:pfAlpha\":\"alpha\",\"#ore:pfBeta\":\"beta\"}");
        MaterialLibrary library = new MaterialLibrary(groups);
        library.reload(resources);
        assertEquals("beta", library.lookup(STONE, 0, ""));
        assertEquals("meta", library.lookup(STONE, 1, ""));
        assertNull(library.lookup(COBBLESTONE, 0, ""));
        resources.add("config/blockmap.json", "{\"#ore:pfAlpha\":\"rewritten\"}");
        library.reload(resources);
        assertEquals("rewritten", library.lookup(STONE, 0, ""));
        resources.add("config/blockmap.json", "{\"test:stone\":\"exact\"}");
        library.reload(resources);
        assertEquals("exact", library.lookup(STONE, 1, ""));
    }

    @Test
    public void derivedBlockInheritsBaseMetadataAfterWrapperMiss() {
        Block base = register(500, "test:derived_metadata_base", new TestBlock());
        Block wrapper = register(501, "test:derived_metadata_wrapper", new DerivedTestBlock(base, true));
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:derived_metadata_base^7\":\"base-meta\"}");

        assertEquals("base-meta", load(resources).lookup(wrapper, 7, ""));
    }

    @Test
    public void wrapperExplicitAndNotEmitterEntriesBeatBaseEntries() {
        Block explicitBase = register(502, "test:derived_explicit_base", new TestBlock());
        Block explicit = register(503, "test:derived_explicit_wrapper", new DerivedTestBlock(explicitBase, false));
        Block silentBase = register(504, "test:derived_silent_base", new TestBlock());
        Block silent = register(505, "test:derived_silent_wrapper", new DerivedTestBlock(silentBase, false));
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"test:derived_explicit_base\":\"base\",\"test:derived_explicit_wrapper\":\"wrapper\","
                + "\"test:derived_silent_base\":\"base\",\"test:derived_silent_wrapper\":\"NOT_EMITTER\"}");
        MaterialLibrary library = load(resources);

        assertEquals("wrapper", library.lookup(explicit, 0, ""));
        assertEquals("NOT_EMITTER", library.lookup(silent, 0, ""));
    }

    @Test
    public void derivedUnassignedFallsThroughToActorBaseMap() {
        Block base = register(506, "test:derived_actor_base", new TestBlock());
        Block wrapper = register(507, "test:derived_actor_wrapper", new DerivedTestBlock(base, false));
        Resources resources = new Resources();
        resources.add(
            "config/blockmap.json",
            "{\"test:derived_actor_base\":\"shared\",\"test:derived_actor_wrapper\":\"UNASSIGNED\"}");
        resources.add("config/materials/hooves.json", "{\"test:derived_actor_base\":\"actor\"}");
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources, null, Collections.singleton("hooves"));

        assertEquals("actor", library.lookup(wrapper, 0, "", "hooves"));
        assertEquals("shared", library.lookup(wrapper, 0, ""));
    }

    @Test
    public void derivedBaseOrdinaryMappingDoesNotLeakIntoSupplementaryChannels() {
        Block base = register(508, "test:derived_supplementary_base", new TestBlock());
        Block wrapper = register(509, "test:derived_supplementary_wrapper", new DerivedTestBlock(base, false));
        Resources resources = new Resources();
        resources.add("config/blockmap.json", "{\"test:derived_supplementary_base\":\"grass\"}");

        MaterialLibrary library = load(resources);
        assertEquals("grass", library.lookup(wrapper, 0, ""));
        assertNull(library.lookup(wrapper, 0, "wet"));
        assertNull(library.lookup(wrapper, 0, "foliage"));
    }

    @Test
    public void derivedCyclesTerminateWithoutRecursion() {
        DerivedTestBlock left = new DerivedTestBlock(null, false);
        DerivedTestBlock right = new DerivedTestBlock(left, false);
        left.base = new BlockMaterial(right, 0);
        Block registered = register(510, "test:derived_cycle_left", left);
        register(511, "test:derived_cycle_right", right);

        assertNull(load(new Resources()).lookup(registered, 0, ""));
    }

    @Test
    public void derivedPrimitiveFallbackRetainsBaseNativeSoundType() {
        Block base = register(512, "test:derived_native_base", new TestBlock(Block.soundTypeWood));
        Block wrapper = register(513, "test:derived_native_wrapper", new DerivedTestBlock(base, false));
        Resources resources = new Resources();
        resources.add("config/primitivemap.json", "{\"wood\":\"base-native\"}");

        MaterialLibrary.Association association = load(resources).resolve(wrapper, 0, "", "");
        assertEquals("base-native", association.name);
        assertSame(base.stepSound, association.sound);
    }

    private static final class TestGroups implements BlockMaterialMap.Groups {

        private final Map<String, List<ItemStack>> entries = new HashMap<>();

        void add(String name, ItemStack stack) {
            entries.computeIfAbsent(name, ignored -> new ArrayList<>())
                .add(stack);
        }

        @Override
        public String[] names() {
            return entries.keySet()
                .toArray(new String[0]);
        }

        @Override
        public List<ItemStack> entries(String name) {
            return entries.getOrDefault(name, Collections.emptyList());
        }
    }

    private static Block register(int id, String name, Block block) {
        try {
            Method addRaw = Block.blockRegistry.getClass()
                .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
            addRaw.setAccessible(true);
            addRaw.invoke(Block.blockRegistry, id, name, block);
            return block;
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static final class TestBlock extends Block {

        TestBlock() {
            this(Block.soundTypeStone);
        }

        TestBlock(Block.SoundType sound) {
            super(Material.rock);
            setStepSound(sound);
        }
    }

    private static final class DerivedTestBlock extends Block implements DerivedBlock {

        private BlockMaterial base;
        private final boolean preserveMetadata;

        DerivedTestBlock(Block base, boolean preserveMetadata) {
            super(Material.rock);
            setStepSound(Block.soundTypeStone);
            this.base = base == null ? null : new BlockMaterial(base, 0);
            this.preserveMetadata = preserveMetadata;
        }

        @Override
        public BlockMaterial getBaseMaterial(int metadata) {
            if (base == null) return null;
            return preserveMetadata ? new BlockMaterial(base.block, metadata & 15) : base;
        }
    }

    private static MaterialLibrary load(Resources resources) {
        MaterialLibrary library = new MaterialLibrary();
        library.reload(resources);
        return library;
    }

    private static final class TestBiome extends BiomeGenBase {

        TestBiome(int id, String name) {
            super(id);
            this.biomeName = name;
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
