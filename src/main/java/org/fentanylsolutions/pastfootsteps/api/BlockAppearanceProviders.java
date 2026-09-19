package org.fentanylsolutions.pastfootsteps.api;

import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/** Opt-in top-face sound appearance for covered blocks. Physical collision and footprint geometry are untouched. */
public final class BlockAppearanceProviders {

    public interface Provider {

        /** Return the top face's represented material, or null to keep the physical block. Never load chunks. */
        BlockMaterial appearance(World world, int x, int y, int z, Block block, int metadata);
    }

    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private BlockAppearanceProviders() {}

    /** First non-null applicable provider wins. Closing the handle removes just this registration. */
    public static AutoCloseable register(Class<? extends Block> blockClass, Provider provider) {
        if (blockClass == null || provider == null)
            throw new IllegalArgumentException("blockClass and provider required");
        Entry entry = new Entry(blockClass, provider);
        ENTRIES.add(entry);
        return () -> ENTRIES.remove(entry);
    }

    /** Resolve once per physical block query; never cache tile-entity covers between contacts. */
    public static BlockMaterial resolve(World world, int x, int y, int z, Block block, int metadata) {
        BlockMaterial original = new BlockMaterial(block, metadata & 15);
        if (world == null || !world.blockExists(x, y, z)) return original;
        for (Entry entry : ENTRIES) if (entry.blockClass.isInstance(block)) {
            try {
                BlockMaterial result = entry.provider.appearance(world, x, y, z, block, metadata & 15);
                if (result != null && result.block.getMaterial() != net.minecraft.block.material.Material.air)
                    return result;
            } catch (RuntimeException | LinkageError ignored) {
                // An optional provider must not take away the normal physical-block sound path.
            }
        }
        return original;
    }

    private static final class Entry {

        final Class<? extends Block> blockClass;
        final Provider provider;

        Entry(Class<? extends Block> blockClass, Provider provider) {
            this.blockClass = blockClass;
            this.provider = provider;
        }
    }
}
