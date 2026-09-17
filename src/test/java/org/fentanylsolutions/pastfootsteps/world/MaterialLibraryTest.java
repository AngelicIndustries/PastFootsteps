package org.fentanylsolutions.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;

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
            super(Material.rock);
            setStepSound(Block.soundTypeStone);
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
