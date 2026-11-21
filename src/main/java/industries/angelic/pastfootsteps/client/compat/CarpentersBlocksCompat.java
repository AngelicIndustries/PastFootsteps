package industries.angelic.pastfootsteps.client.compat;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.api.BlockAppearanceProviders;
import industries.angelic.pastfootsteps.api.BlockMaterial;

/** Optional Carpenter's Blocks appearance integration with no compile-time mod dependency. */
public final class CarpentersBlocksCompat {

    private static final String CARPENTERS_BLOCK = "com.carpentersblocks.block.BlockCoverable";
    private static final String BLOCK_PROPERTIES = "com.carpentersblocks.util.BlockProperties";
    private static volatile boolean initialized;
    private static Method getTileEntity;
    private static Method getFeatureSensitiveSideItemStack;

    private CarpentersBlocksCompat() {}

    /** Registers the optional provider once; absence is silent and an incompatible installed API is logged once. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> coverable;
            try {
                coverable = Class.forName(CARPENTERS_BLOCK, false, CarpentersBlocksCompat.class.getClassLoader());
            } catch (ClassNotFoundException absent) {
                return;
            }
            Class<?> tileEntity = Class.forName(
                "com.carpentersblocks.tileentity.TEBase",
                false,
                CarpentersBlocksCompat.class.getClassLoader());
            Class<?> properties = Class.forName(BLOCK_PROPERTIES, false, CarpentersBlocksCompat.class.getClassLoader());
            getTileEntity = properties
                .getMethod("getTileEntity", Block.class, World.class, int.class, int.class, int.class);
            getFeatureSensitiveSideItemStack = properties
                .getMethod("getFeatureSensitiveSideItemStack", tileEntity, ForgeDirection.class);
            @SuppressWarnings("unchecked")
            Class<? extends Block> blockClass = (Class<? extends Block>) coverable;
            BlockAppearanceProviders.register(blockClass, new BlockAppearanceProviders.Provider() {

                @Override
                public BlockMaterial appearance(World world, int x, int y, int z, Block block, int metadata) {
                    return appearanceAt(world, x, y, z, block);
                }
            });
        } catch (ReflectiveOperationException error) {
            logInitializationFailure(error);
        } catch (LinkageError error) {
            logInitializationFailure(error);
        } catch (RuntimeException error) {
            logInitializationFailure(error);
        }
    }

    private static BlockMaterial appearanceAt(World world, int x, int y, int z, Block block) {
        if (world == null || block == null || !world.blockExists(x, y, z) || world.getBlock(x, y, z) != block)
            return null;
        try {
            Object tile = getTileEntity
                .invoke(null, block, world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
            if (tile == null) return null;
            ItemStack stack = (ItemStack) getFeatureSensitiveSideItemStack.invoke(null, tile, ForgeDirection.UP);
            if (stack == null) return null;
            Item item = stack.getItem();
            if (!(item instanceof ItemBlock)) return null;
            Block represented = ((ItemBlock) item).field_150939_a;
            return represented == null ? null : new BlockMaterial(represented, stack.getItemDamage() & 15);
        } catch (ReflectiveOperationException error) {
            return null;
        } catch (RuntimeException error) {
            return null;
        } catch (LinkageError error) {
            return null;
        }
    }

    private static void logInitializationFailure(Throwable error) {
        PastFootsteps.LOG.warn("Carpenter's Blocks appearance integration unavailable; continuing without it", error);
    }
}
