package org.fentanylsolutions.pastfootsteps.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.Test;

public class SpiderWallResolverTest {

    private static final Block AIR = new Block(Material.air) {};
    private static final Block WALL = register(720, "test:spider_wall", new Block(Material.wood) {

        {
            setStepSound(Block.soundTypeWood);
        }
    });
    private static final Block FLOOR = register(721, "test:spider_floor", new Block(Material.rock) {

        {
            setStepSound(Block.soundTypeStone);
        }
    });

    @Test
    public void spiderAndCaveSpiderUseAdjacentWallMaterialAsUngroundedContact() {
        for (EntitySpider spider : new EntitySpider[] { spider(new EntitySpider(worldWithWall())),
            spider(new EntityCaveSpider(worldWithWall())) }) {
            SurfaceResolver.Surface surface = resolver().resolve(spider, 0.0D, 0.0D);

            assertEquals("woodwall", surface.material);
            assertEquals(1, surface.x);
            assertFalse(surface.grounded);
            assertEquals(1.0D, surface.contactX, 0.000001D);
            assertEquals("step.wood", surface.vanillaSound);
        }
    }

    @Test
    public void groundedSpiderKeepsFloorAndMissingOrUnloadedWallFallsBackToNative() {
        TestWorld groundedWorld = worldWithWall();
        groundedWorld.put(0, 0, 0, FLOOR);
        EntitySpider grounded = spider(new EntitySpider(groundedWorld));
        grounded.onGround = true;
        SurfaceResolver.Surface floor = resolver().resolve(grounded, 0.0D, 0.0D);
        assertEquals("stonefloor", floor.material);
        if (!floor.grounded) throw new AssertionError("real floor support was not retained");

        TestWorld empty = new TestWorld();
        assertNull(resolver().resolve(spider(new EntitySpider(empty)), 0.0D, 0.0D));
        empty.put(1, 1, 0, WALL);
        empty.loaded = false;
        assertNull(resolver().resolve(spider(new EntitySpider(empty)), 0.0D, 0.0D));
        assertEquals(0, empty.wallReads);
    }

    @Test
    public void interpolatedContactUsesItsOwnBodyPositionRatherThanTheLiveWall() {
        EntitySpider actor = spider(new EntitySpider(worldWithWall()));
        actor.posX += 10;
        actor.posY += 5;
        actor.boundingBox.offset(10, 5, 0);
        SurfaceResolver resolver = resolver();
        assertNull(resolver.resolve(actor, 0.0D, 0.0D));
        SurfaceResolver.Surface historical = resolver.resolveAt(actor, 0.5, 1, 0.5, 0, 0);
        assertEquals("woodwall", historical.material);
        assertEquals(1, historical.contactX, 0);
        assertFalse(historical.grounded);
    }

    private static TestWorld worldWithWall() {
        TestWorld world = new TestWorld();
        world.put(1, 1, 0, WALL);
        return world;
    }

    private static <T extends EntitySpider> T spider(T spider) {
        spider.boundingBox.setBounds(0.05D, 1.0D, 0.05D, 0.95D, 2.0D, 0.95D);
        spider.posX = 0.5D;
        spider.posY = 1.0D;
        spider.posZ = 0.5D;
        spider.setBesideClimbableBlock(true);
        return spider;
    }

    private static SurfaceResolver resolver() {
        Resources resources = new Resources();
        resources
            .add("config/blockmap.json", "{\"test:spider_wall\":\"woodwall\",\"test:spider_floor\":\"stonefloor\"}");
        MaterialLibrary materials = new MaterialLibrary();
        materials.reload(resources, null, Collections.<String>emptySet());
        return new SurfaceResolver(materials);
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

    private static final class TestWorld extends World {

        private final Map<String, Block> blocks = new HashMap<String, Block>();
        boolean loaded = true;
        int blockReads;
        int wallReads;

        TestWorld() {
            super(
                null,
                "spider-wall-resolver-test",
                new WorldProviderSurface(),
                new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                new Profiler());
        }

        void put(int x, int y, int z, Block block) {
            blocks.put(key(x, y, z), block);
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
            if (x == 1 && y == 1 && z == 0) wallReads++;
            Block block = blocks.get(key(x, y, z));
            return block == null ? AIR : block;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public List getEntitiesWithinAABBExcludingEntity(Entity entity, net.minecraft.util.AxisAlignedBB box) {
            return Collections.emptyList();
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

        private final Map<String, List<IResource>> values = new HashMap<String, List<IResource>>();

        void add(String path, String json) {
            values.put(path, Collections.<IResource>singletonList(new StringResource(json)));
        }

        @Override
        public java.util.Set<String> getResourceDomains() {
            return Collections.singleton("presencefootsteps");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            return getAllResources(location).get(0);
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            List<IResource> resources = values.get(location.getResourcePath());
            if (resources == null) throw new IOException("missing " + location);
            return resources;
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
