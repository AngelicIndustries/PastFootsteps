package industries.angelic.pastfootsteps.world;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** No-load IBlockAccess view; unloaded reads throw so callers can fall back safely. */
final class LoadedBlockAccess implements IBlockAccess {

    private final World world;

    LoadedBlockAccess(World world) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        this.world = world;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        check(x, y, z);
        return world.getBlock(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        check(x, y, z);
        return world.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int lightValue) {
        throw new UnsupportedOperationException("lighting is not available through the no-load view");
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        check(x, y, z);
        return world.getBlockMetadata(x, y, z);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int direction) {
        return getBlock(x, y, z).isProvidingStrongPower(this, x, y, z, direction);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return getBlock(x, y, z).isAir(this, x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        if (!world.blockExists(x, 0, z)) throw unloaded(x, 0, z);
        return world.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return world.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean defaultValue) {
        return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
    }

    private void check(int x, int y, int z) {
        if (!world.blockExists(x, y, z)) throw unloaded(x, y, z);
    }

    private static IllegalStateException unloaded(int x, int y, int z) {
        return new IllegalStateException("unloaded block access at " + x + "," + y + "," + z);
    }
}
