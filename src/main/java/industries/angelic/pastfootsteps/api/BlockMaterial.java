package industries.angelic.pastfootsteps.api;

import net.minecraft.block.Block;

/** A represented block and its own metadata, independent of the wrapper's orientation metadata. */
public final class BlockMaterial {

    public final Block block;
    public final int metadata;

    public BlockMaterial(Block block, int metadata) {
        if (block == null || metadata < 0 || metadata > 15)
            throw new IllegalArgumentException("A block and metadata between 0 and 15 are required");
        this.block = block;
        this.metadata = metadata;
    }
}
