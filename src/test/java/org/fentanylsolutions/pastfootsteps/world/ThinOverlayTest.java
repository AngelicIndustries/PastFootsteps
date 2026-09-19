package org.fentanylsolutions.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.Test;

/** Gameplay-level coverage for non-vanilla thin covers discovered above a physical support. */
public class ThinOverlayTest {

    private static final Block AIR = new Block(Material.air) {};
    private static final Block BASE = register(700, "test:thin_overlay_base", new FullSupportBlock());
    private static final ThinOverlayBlock COVER = register(
        701,
        "test:thin_overlay_cover",
        new ThinOverlayBlock(new double[] { 0.12D }, new double[] { 0.25D }, 0.0D, false, false));
    private static final ThinOverlayBlock METADATA_COVER = register(
        702,
        "test:thin_overlay_metadata",
        new ThinOverlayBlock(new double[] { 0.10D, 0.20D }, new double[] { 0.25D, 0.10D }, 0.0D, false, false));
    private static final ThinOverlayBlock TALL = register(
        703,
        "test:thin_overlay_tall",
        new ThinOverlayBlock(new double[] { 0.40D }, new double[] { 0.10D }, 0.0D, false, false));
    private static final ThinOverlayBlock FLOATING = register(
        704,
        "test:thin_overlay_floating",
        new ThinOverlayBlock(new double[] { 0.12D }, new double[] { 0.10D }, 0.05D, false, false));
    private static final ThrowingOverlayBlock THROWING = register(
        705,
        "test:thin_overlay_throwing",
        new ThrowingOverlayBlock());
    private static final ThinOverlayBlock THIN_SUPPORT = register(
        706,
        "test:thin_overlay_support",
        new ThinOverlayBlock(new double[] { 0.12D }, new double[] { 0.0D }, 0.0D, true, false));
    private static final PartialSupportBlock PARTIAL_BASE = register(
        707,
        "test:thin_overlay_partial_base",
        new PartialSupportBlock());
    private static final ThinOverlayBlock WIDE_COVER = register(
        708,
        "test:thin_overlay_wide_cover",
        new ThinOverlayBlock(new double[] { 0.12D }, new double[] { 0.0D }, 0.0D, false, false));
    private static final Block SNOW = register(709, "test:thin_overlay_snow", new TestSnow());
    private static final Block CARPET = register(710, "test:thin_overlay_carpet", new TestCarpet());
    private static final Block NEIGHBOR_READING = register(
        711,
        "test:thin_overlay_neighbor",
        new NeighborOverlayBlock());

    @Test
    public void noCollisionPressurePlateOverlayOverridesPhysicalSupport() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, COVER, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_cover.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("rug", result.material);
        assertEquals(1.0D, result.contactY, 0.000001D);
        assertEquals(1.12D, result.decalY, 0.000001D);
    }

    @Test
    public void unmappedAndNotEmitterOverlaysFallBackToPhysicalSupport() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, COVER, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);

        SurfaceResolver resolver = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_cover.carpet\":\"NOT_EMITTER\"}");
        assertEquals("stone", resolver.resolve(cow, 0.0D, 0.0D).material);

        resolver = resolver("{\"test:thin_overlay_base\":\"stone\"}");
        assertEquals("stone", resolver.resolve(cow, 0.0D, 0.0D).material);
    }

    @Test
    public void metadataRefreshSelectsCoverMaterialAndRestoresBounds() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, METADATA_COVER, 1);
        EntityCow cow = actor(world, 0.5D, 1.0D);
        float[] original = bounds(METADATA_COVER);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_metadata^1.carpet\":\"high_rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("high_rug", result.material);
        assertEquals(1.20D, result.decalY, 0.000001D);
        assertBounds(original, METADATA_COVER);
    }

    @Test
    public void footOutsideInsetCoverKeepsPhysicalMaterial() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, COVER, 0);
        EntityCow cow = actor(world, 0.10D, 1.0D);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_cover.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("stone", result.material);
        assertEquals(0.10D, result.contactX, 0.000001D);
    }

    @Test
    public void coverDecalBoundsAreClippedToPhysicalSupport() {
        TestWorld world = world();
        world.put(0, 0, 0, PARTIAL_BASE, 0);
        world.put(0, 1, 0, WIDE_COVER, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_partial_base\":\"stone\",\"test:thin_overlay_wide_cover.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("rug", result.material);
        assertEquals(0.10D, result.minX, 0.000001D);
        assertEquals(0.90D, result.maxX, 0.000001D);
        assertEquals(0.10D, result.minZ, 0.000001D);
        assertEquals(0.90D, result.maxZ, 0.000001D);
    }

    @Test
    public void thinCollisionSupportStillProducesGroundedContact() {
        TestWorld world = world();
        world.put(0, 0, 0, THIN_SUPPORT, 0);
        EntityCow cow = actor(world, 0.5D, 0.12D);

        SurfaceResolver.Surface result = resolver("{\"test:thin_overlay_support\":\"plate\"}").resolve(cow, 0.0D, 0.0D);

        assertEquals("plate", result.material);
        assertEquals(0.12D, result.contactY, 0.000001D);
        if (!result.grounded) throw new AssertionError("thin collision support was not grounded");
    }

    @Test
    public void tallAndFloatingShapesAreRejectedAsOverlays() {
        assertOverlayRejected(TALL, "test:thin_overlay_tall.carpet", 0.0D);
        assertOverlayRejected(FLOATING, "test:thin_overlay_floating.carpet", 0.0D);
    }

    @Test
    public void throwingBoundsQueryFallsBackAndRestoresState() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, THROWING, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);
        float[] original = bounds(THROWING);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_throwing.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("stone", result.material);
        assertBounds(original, THROWING);
    }

    @Test
    public void unloadedOverlayCellIsNeverRead() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, COVER, 0);
        world.loaded = false;
        EntityCow cow = actor(world, 0.5D, 1.0D);

        assertNull(
            resolver("{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_cover.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D));
        if (world.blockReads != 0) throw new AssertionError("unloaded overlay was read");
    }

    @Test
    public void vanillaSnowLayersPreservePhysicalContactAndRaiseDecalByOneLayer() {
        for (int meta : new int[] { 0, 3, 7 }) {
            TestWorld world = world();
            world.put(0, 0, 0, BASE, 0);
            world.put(0, 1, 0, SNOW, meta);
            double feetY = 1.0D + meta * 0.125D;
            EntityCow cow = actor(world, 0.5D, feetY);

            SurfaceResolver.Surface result = resolver(
                "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_snow\":\"snow\"}").resolve(cow, 0.0D, 0.0D);

            assertEquals("snow", result.material);
            assertEquals(feetY, result.contactY, 0.000001D);
            assertEquals(feetY + 0.125D, result.decalY, 0.000001D);
        }
    }

    @Test
    public void vanillaCarpetPreservesPhysicalContactAndUsesThinDecal() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, CARPET, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_carpet.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("rug", result.material);
        assertEquals(1.0D, result.contactY, 0.000001D);
        assertEquals(1.0625D, result.decalY, 0.000001D);
    }

    @Test
    public void unloadedNeighborBoundsFallbackBeforeWorldReadAndRestoreState() {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, NEIGHBOR_READING, 0);
        EntityCow cow = actor(world, 0.5D, 1.0D);
        float[] original = bounds(NEIGHBOR_READING);

        SurfaceResolver.Surface result = resolver(
            "{\"test:thin_overlay_base\":\"stone\",\"test:thin_overlay_neighbor.carpet\":\"rug\"}")
                .resolve(cow, 0.0D, 0.0D);

        assertEquals("stone", result.material);
        assertBounds(original, NEIGHBOR_READING);
        // Base and overlay are the only loaded cells. LoadedBlockAccess rejects the missing
        // neighbor before delegating to TestWorld.getBlock.
        assertEquals(0, world.neighborReads);
    }

    private static void assertOverlayRejected(Block overlay, String key, double centerX) {
        TestWorld world = world();
        world.put(0, 0, 0, BASE, 0);
        world.put(0, 1, 0, overlay, 0);
        EntityCow cow = actor(world, centerX + 0.5D, 1.0D);

        SurfaceResolver.Surface result = resolver("{\"test:thin_overlay_base\":\"stone\",\"" + key + "\":\"rug\"}")
            .resolve(cow, 0.0D, 0.0D);
        assertEquals("stone", result.material);
    }

    private static SurfaceResolver resolver(String blockMap) {
        Resources resources = new Resources();
        resources.add("config/blockmap.json", blockMap);
        MaterialLibrary materials = new MaterialLibrary();
        materials.reload(resources);
        return new SurfaceResolver(materials);
    }

    private static TestWorld world() {
        return new TestWorld();
    }

    private static EntityCow actor(TestWorld world, double centerX, double feetY) {
        EntityCow cow = new EntityCow(world) {

            @Override
            public boolean isOnLadder() {
                return false;
            }

            @Override
            public boolean isInsideOfMaterial(Material material) {
                return false;
            }
        };
        cow.boundingBox.setBounds(centerX - 0.4D, feetY, 0.1D, centerX + 0.4D, feetY + 1.0D, 0.9D);
        cow.posX = centerX;
        cow.posY = feetY;
        cow.posZ = 0.5D;
        cow.renderYawOffset = 0.0F;
        return cow;
    }

    private static float[] bounds(Block block) {
        return new float[] { (float) block.getBlockBoundsMinX(), (float) block.getBlockBoundsMinY(),
            (float) block.getBlockBoundsMinZ(), (float) block.getBlockBoundsMaxX(), (float) block.getBlockBoundsMaxY(),
            (float) block.getBlockBoundsMaxZ() };
    }

    private static void assertBounds(float[] expected, Block actual) {
        float[] result = bounds(actual);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], result[i], 0.000001F);
    }

    private static <T extends Block> T register(int id, String name, T block) {
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

    private static class FullSupportBlock extends Block {

        FullSupportBlock() {
            super(Material.rock);
            setStepSound(Block.soundTypeStone);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            return AxisAlignedBB.getBoundingBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        }
    }

    private static class PartialSupportBlock extends FullSupportBlock {

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            return AxisAlignedBB.getBoundingBox(x + 0.10D, y, z + 0.10D, x + 0.90D, y + 1.0D, z + 0.90D);
        }
    }

    private static class ThinOverlayBlock extends Block {

        private final double[] heights;
        private final double[] insets;
        private final double bottom;
        private final boolean collision;

        ThinOverlayBlock(double[] heights, double[] insets, double bottom, boolean collision, boolean ignored) {
            super(Material.rock);
            this.heights = heights;
            this.insets = insets;
            this.bottom = bottom;
            this.collision = collision;
            setStepSound(Block.soundTypeStone);
            setBlockBounds(0.13F, 0.17F, 0.19F, 0.83F, 0.37F, 0.81F);
        }

        @Override
        public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
            int meta = Math.max(0, Math.min(heights.length - 1, world.getBlockMetadata(x, y, z)));
            float inset = (float) insets[meta];
            setBlockBounds(inset, (float) bottom, inset, 1.0F - inset, (float) (bottom + heights[meta]), 1.0F - inset);
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
            if (!collision) return null;
            int meta = Math.max(0, Math.min(heights.length - 1, world.getBlockMetadata(x, y, z)));
            return AxisAlignedBB.getBoundingBox(
                x + insets[meta],
                y + bottom,
                z + insets[meta],
                x + 1.0D - insets[meta],
                y + bottom + heights[meta],
                z + 1.0D - insets[meta]);
        }
    }

    private static final class ThrowingOverlayBlock extends ThinOverlayBlock {

        ThrowingOverlayBlock() {
            super(new double[] { 0.12D }, new double[] { 0.10D }, 0.0D, false, false);
        }

        @Override
        public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
            super.setBlockBoundsBasedOnState(world, x, y, z);
            throw new IllegalStateException("broken bounds query");
        }
    }

    private static final class NeighborOverlayBlock extends ThinOverlayBlock {

        NeighborOverlayBlock() {
            super(new double[] { 0.12D }, new double[] { 0.10D }, 0.0D, false, false);
        }

        @Override
        public void setBlockBoundsBasedOnState(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
            super.setBlockBoundsBasedOnState(world, x, y, z);
            world.getBlock(x + 1, y, z);
        }
    }

    private static final class TestSnow extends BlockSnow {

        TestSnow() {
            super();
        }
    }

    private static final class TestCarpet extends BlockCarpet {

        TestCarpet() {
            super();
        }
    }

    private static final class TestWorld extends World {

        private final Map<String, Block> blocks = new HashMap<String, Block>();
        private final Map<String, Integer> metadata = new HashMap<String, Integer>();
        boolean loaded = true;
        int blockReads;
        int unloadedNeighborReads;
        int neighborReads;

        TestWorld() {
            super(
                null,
                "thin-overlay-test",
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
            blockReads++;
            if (x == 1 && y == 1 && z == 0) neighborReads++;
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
            layers.computeIfAbsent(path, ignored -> new ArrayList<IResource>())
                .add(new StringResource(json));
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
