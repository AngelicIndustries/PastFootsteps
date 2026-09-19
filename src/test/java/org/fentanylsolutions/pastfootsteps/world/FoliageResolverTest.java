package org.fentanylsolutions.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.fentanylsolutions.pastfootsteps.api.BlockAppearanceProviders;
import org.fentanylsolutions.pastfootsteps.api.BlockMaterial;
import org.junit.Test;

public class FoliageResolverTest {

    private static final Block AIR = new Block(Material.air) {};
    private static final Block FOLIAGE = register(253, "test:foliage", new TestBlock(false));
    private static final Block SOLID = register(254, "test:solid_plant", new TestBlock(true));

    @Test
    public void contactUsesFoliageAtPhysicalContactAndMessyRequiresMarker() {
        TestWorld world = new TestWorld();
        world.put(0, 1, 0, FOLIAGE, 0);
        EntityCow cow = actor(world);
        MaterialLibrary materials = materials(
            "{\"test:foliage\":\"stone\",\"test:foliage.foliage\":\"brush\",\"test:foliage.messy\":\"MESSY_GROUND\"}");
        SurfaceResolver resolver = new SurfaceResolver(materials);
        SurfaceResolver.Surface contact = new SurfaceResolver.Surface(
            "stone",
            null,
            null,
            0.0F,
            1.0F,
            0,
            0,
            0,
            true,
            false,
            0.5D,
            1.0D,
            0.5D);

        assertEquals("brush", resolver.contactFoliage(cow, contact, ""));
        assertEquals("brush", resolver.messyFoliage(cow, ""));
        // Crops occupy the cell above farmland's 15/16-height collision surface.
        cow.boundingBox.setBounds(0.1, 0.9375, 0.1, 0.9, 1.9375, 0.9);
        SurfaceResolver.Surface farmland = new SurfaceResolver.Surface(
            "dirt",
            null,
            null,
            0,
            1,
            0,
            0,
            0,
            true,
            false,
            0.5,
            0.9375,
            0.5);
        assertEquals("brush", resolver.contactFoliage(cow, farmland, ""));
        assertEquals("brush", resolver.messyFoliage(cow, ""));
    }

    @Test
    public void overheadPlantDoesNotBecomeContactFoliageAndMutedSurfaceSuppressesIt() {
        TestWorld world = new TestWorld();
        world.put(0, 2, 0, FOLIAGE, 0);
        EntityCow cow = actor(world);
        SurfaceResolver resolver = new SurfaceResolver(materials("{\"test:foliage.foliage\":\"brush\"}"));
        SurfaceResolver.Surface contact = new SurfaceResolver.Surface(
            "stone",
            null,
            null,
            0.0F,
            1.0F,
            0,
            0,
            0,
            true,
            false,
            0.5D,
            1.0D,
            0.5D);
        SurfaceResolver.Surface muted = new SurfaceResolver.Surface(
            "NOT_EMITTER",
            null,
            null,
            0.0F,
            1.0F,
            0,
            0,
            0,
            true,
            false,
            0.5D,
            1.0D,
            0.5D);

        assertNull(resolver.contactFoliage(cow, contact, ""));
        world.put(0, 1, 0, FOLIAGE, 0);
        assertNull(resolver.contactFoliage(cow, muted, ""));
    }

    @Test
    public void actorMapCanMuteFoliageAndMessyLookup() {
        TestWorld world = new TestWorld();
        world.put(0, 1, 0, FOLIAGE, 0);
        EntityCow cow = actor(world);
        MaterialLibrary materials = materials(
            "{\"test:foliage\":\"stone\",\"test:foliage.foliage\":\"brush\",\"test:foliage.messy\":\"MESSY_GROUND\"}",
            "{\"test:foliage.foliage\":\"NOT_EMITTER\",\"test:foliage.messy\":\"NOT_EMITTER\"}");
        SurfaceResolver resolver = new SurfaceResolver(materials);
        SurfaceResolver.Surface contact = new SurfaceResolver.Surface(
            "stone",
            null,
            null,
            0.0F,
            1.0F,
            0,
            0,
            0,
            true,
            false,
            0.5D,
            1.0D,
            0.5D);

        assertNull(resolver.contactFoliage(cow, contact, "hooves"));
        assertNull(resolver.messyFoliage(cow, "hooves"));
    }

    @Test
    public void unloadedContactCellIsIgnored() {
        TestWorld world = new TestWorld();
        world.put(0, 1, 0, FOLIAGE, 0);
        world.loaded = false;
        EntityCow cow = actor(world);
        SurfaceResolver resolver = new SurfaceResolver(materials("{\"test:foliage.foliage\":\"brush\"}"));
        SurfaceResolver.Surface contact = new SurfaceResolver.Surface(
            "stone",
            null,
            null,
            0.0F,
            1.0F,
            0,
            0,
            0,
            true,
            false,
            0.5D,
            1.0D,
            0.5D);

        assertNull(resolver.contactFoliage(cow, contact, ""));
        assertNull(resolver.messyFoliage(cow, ""));
    }

    @Test
    public void ordinaryFoliageExcludesSolidPlantsAndTheSupportingOverlay() {
        TestWorld world = new TestWorld();
        world.put(0, 1, 0, SOLID, 0);
        EntityCow cow = actor(world);
        SurfaceResolver resolver = new SurfaceResolver(
            materials("{\"test:solid_plant.foliage\":\"brush\",\"test:foliage.foliage\":\"brush\"}"));
        SurfaceResolver.Surface contact = new SurfaceResolver.Surface(
            "stone",
            null,
            null,
            0,
            1,
            0,
            0,
            0,
            true,
            false,
            0.5,
            1,
            0.5);
        assertNull(resolver.contactFoliage(cow, contact, ""));
        world.put(0, 1, 0, FOLIAGE, 0);
        SurfaceResolver.Surface overlay = new SurfaceResolver.Surface(
            "rug",
            null,
            null,
            0,
            1,
            0,
            1,
            0,
            true,
            false,
            0.5,
            1.0625,
            0.5);
        assertNull(resolver.contactFoliage(cow, overlay, ""));
        // Unmarked foliage still contributes at a contact, but never creates messy entry sounds.
        assertEquals("brush", resolver.contactFoliage(cow, contact, ""));
        assertNull(resolver.messyFoliage(cow, ""));
    }

    @Test
    public void appearanceTracksChangedMetadataWithoutMovingPhysicalContacts() throws Exception {
        TestWorld world = new TestWorld();
        Block partial = register(390, "test:covered_partial", new PartialBlock());
        world.put(0, 0, 0, partial, 0);
        EntityCow cow = actor(world);
        cow.boundingBox.setBounds(0.1, 0.5, 0.1, 0.9, 1.5, 0.9);
        SurfaceResolver resolver = new SurfaceResolver(
            materials("{\"test:foliage^1\":\"sand\",\"test:foliage^2\":\"snow\",\"test:covered_partial\":\"wood\"}"));
        int[] coverMeta = { 1 };
        try (AutoCloseable registration = BlockAppearanceProviders
            .register(PartialBlock.class, (w, x, y, z, b, meta) -> new BlockMaterial(FOLIAGE, coverMeta[0]))) {
            SurfaceResolver.Surface first = resolver.resolve(cow, 0.0, 0.0);
            assertEquals("sand", first.material);
            assertEquals(0.5, first.contactY, 0.000001);
            assertEquals(0.5, first.decalY, 0.000001);
            assertEquals(0.25, first.minX, 0.000001);
            assertEquals(0.75, first.maxX, 0.000001);
            assertEquals(0, first.y);
            coverMeta[0] = 2;
            assertEquals("snow", resolver.resolve(cow, 0.0, 0.0).material);
            coverMeta[0] = 3;
            SurfaceResolver.Surface nativeCover = resolver.resolve(cow, 0.0, 0.0);
            assertNull(nativeCover.material);
            assertEquals(FOLIAGE.stepSound.getStepResourcePath(), nativeCover.vanillaSound);
        }
        assertEquals("wood", resolver.resolve(cow, 0.0, 0.0).material);
    }

    @Test
    public void appearanceSkipsUnloadedBlocksAndIsolatesBrokenProviders() throws Exception {
        TestWorld world = new TestWorld();
        world.put(0, 0, 0, SOLID, 0);
        int[] calls = { 0 };
        try (AutoCloseable broken = BlockAppearanceProviders.register(TestBlock.class, (w, x, y, z, b, meta) -> {
            calls[0]++;
            throw new IllegalStateException("broken optional adapter");
        });
            AutoCloseable working = BlockAppearanceProviders
                .register(TestBlock.class, (w, x, y, z, b, meta) -> new BlockMaterial(FOLIAGE, 2))) {
            world.loaded = false;
            assertSame(SOLID, BlockAppearanceProviders.resolve(world, 0, 0, 0, SOLID, 0).block);
            assertEquals(0, calls[0]);
            world.loaded = true;
            BlockMaterial result = BlockAppearanceProviders.resolve(world, 0, 0, 0, SOLID, 0);
            assertSame(FOLIAGE, result.block);
            assertEquals(2, result.metadata);
            assertEquals(1, calls[0]);
        }
        assertSame(SOLID, BlockAppearanceProviders.resolve(world, 0, 0, 0, SOLID, 0).block);
    }

    @Test
    public void headOnlyFoliageRustlesWithoutFootContactAndRespectsActorMap() {
        TestWorld world = new TestWorld();
        world.put(0, 2, 0, FOLIAGE, 0);
        EntityCow cow = actor(world);
        cow.boundingBox.setBounds(0.1, 1, 0.1, 0.9, 2.8, 0.9);
        SurfaceResolver resolver = new SurfaceResolver(
            materials("{\"test:foliage.foliage\":\"brush\"}", "{\"test:foliage.foliage\":\"NOT_EMITTER\"}"));
        SurfaceResolver.Surface floor = new SurfaceResolver.Surface("stone", null, null, 0, 1, 0, 0, 0, true, false);
        assertNull(resolver.contactFoliage(cow, floor, ""));
        assertEquals("brush", resolver.bodyFoliage(cow, ""));
        assertNull(resolver.bodyFoliage(cow, "hooves"));
        world.put(0, 2, 0, SOLID, 0);
        resolver = new SurfaceResolver(materials("{\"test:solid_plant.foliage\":\"brush\"}"));
        assertNull(resolver.bodyFoliage(cow, ""));
    }

    @Test
    public void bodyScanDistinguishesLowPlantsFromTallPlantsAndChecksVineBounds() {
        Block plant = register(391, "test:body_plant", new BodyPlant());
        TestWorld world = new TestWorld();
        EntityCow cow = actor(world);
        cow.boundingBox.setBounds(0.1, 1, 0.1, 0.9, 2.8, 0.9);
        SurfaceResolver resolver = new SurfaceResolver(materials("{\"test:body_plant.foliage\":\"brush\"}"));
        world.put(0, 1, 0, plant, 0); // Low plant ends inside boot region.
        assertNull(resolver.bodyFoliage(cow, ""));
        world.put(0, 1, 0, plant, 1); // Tall plant reaches above boots.
        assertEquals("brush", resolver.bodyFoliage(cow, ""));
        assertEquals(1.0, plant.getBlockBoundsMaxY(), 0.000001); // Shared bounds restored.
        world.put(0, 1, 0, plant, 2); // Thin wall vine does not touch this actor.
        assertNull(resolver.bodyFoliage(cow, ""));
        cow.boundingBox.setBounds(0.02, 1, 0.1, 0.62, 2.8, 0.9);
        assertEquals("brush", resolver.bodyFoliage(cow, ""));
        world.loaded = false;
        assertNull(resolver.bodyFoliage(cow, ""));
    }

    @Test
    public void doublePlantUpperHalfUsesLowerVariantAndOrphansStaySilent() {
        Block plant = register(392, "test:double_plant", new BlockDoublePlant() {});
        TestWorld world = new TestWorld();
        EntityCow cow = actor(world);
        cow.boundingBox.setBounds(0.1, 2, 0.1, 0.9, 3, 0.9);
        cow.posY = 2;
        SurfaceResolver resolver = new SurfaceResolver(
            materials(
                "{\"test:double_plant^2.foliage\":\"brush_straw_transition\",\"test:double_plant^4.foliage\":\"brush\","
                    + "\"test:double_plant.messy\":\"MESSY_GROUND\"}"));
        world.put(0, 1, 0, plant, 2);
        world.put(0, 2, 0, plant, 11);
        assertEquals("brush_straw_transition", resolver.bodyFoliage(cow, ""));
        assertEquals("brush_straw_transition", resolver.messyFoliage(cow, ""));
        world.put(0, 1, 0, plant, 4);
        assertEquals("brush", resolver.bodyFoliage(cow, ""));
        world.put(0, 1, 0, AIR, 0);
        assertNull(resolver.bodyFoliage(cow, ""));
        world.put(0, 1, 0, plant, 8);
        assertNull(resolver.bodyFoliage(cow, ""));
        world.put(0, 1, 0, plant, 6);
        assertNull(resolver.bodyFoliage(cow, ""));
    }

    private static final class BodyPlant extends Block {

        BodyPlant() {
            super(Material.plants);
        }

        @Override
        public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
            int meta = world.getBlockMetadata(x, y, z);
            setBlockBounds(0, 0, 0, meta == 2 ? 0.0625F : 1, meta == 0 ? 0.2F : 0.8F, 1);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            return null;
        }
    }

    private static final class PartialBlock extends Block {

        PartialBlock() {
            super(Material.wood);
            setStepSound(Block.soundTypeWood);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            return AxisAlignedBB.getBoundingBox(x + 0.25, y, z + 0.25, x + 0.75, y + 0.5, z + 0.75);
        }
    }

    private static EntityCow actor(TestWorld world) {
        EntityCow cow = new EntityCow(world) {

            @Override
            public boolean isOnLadder() {
                return false;
            }
        };
        cow.boundingBox.setBounds(0.1D, 1.0D, 0.1D, 0.9D, 2.0D, 0.9D);
        cow.posX = 0.5D;
        cow.posY = 1.0D;
        cow.posZ = 0.5D;
        return cow;
    }

    private static MaterialLibrary materials(String shared) {
        return materials(shared, null);
    }

    private static MaterialLibrary materials(String shared, String actor) {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", shared);
        if (actor != null) resources.add("config/materials/hooves.json", actor);
        MaterialLibrary result = new MaterialLibrary();
        result
            .reload(resources, null, actor == null ? Collections.<String>emptySet() : Collections.singleton("hooves"));
        return result;
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

        private final boolean solid;

        TestBlock(boolean solid) {
            super(Material.plants);
            this.solid = solid;
            setStepSound(Block.soundTypeGrass);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            return solid ? AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1) : null;
        }
    }

    private static final class TestWorld extends World {

        private final Map<String, Block> blocks = new HashMap<String, Block>();
        private final Map<String, Integer> metadata = new HashMap<String, Integer>();
        boolean loaded = true;

        TestWorld() {
            super(
                null,
                "foliage-resolver-test",
                new WorldProviderSurface(),
                new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                new Profiler());
        }

        void put(int x, int y, int z, Block block, int meta) {
            String key = key(x, y, z);
            blocks.put(key, block);
            metadata.put(key, Integer.valueOf(meta));
        }

        @Override
        public boolean checkChunksExist(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return loaded;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return loaded && blocks.containsKey(key(x, y, z));
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            Block block = blocks.get(key(x, y, z));
            return block == null ? AIR : block;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            Integer value = metadata.get(key(x, y, z));
            return value == null ? 0 : value.intValue();
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
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
        public java.util.Set<String> getResourceDomains() {
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
