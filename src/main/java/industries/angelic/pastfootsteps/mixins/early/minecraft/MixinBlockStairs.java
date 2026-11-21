package industries.angelic.pastfootsteps.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import industries.angelic.pastfootsteps.api.BlockMaterial;
import industries.angelic.pastfootsteps.api.DerivedBlock;

/** Constructor-provided model state also covers ordinary modded BlockStairs subclasses. */
@Mixin(BlockStairs.class)
public abstract class MixinBlockStairs implements DerivedBlock {

    @Shadow
    @Final
    private Block field_150149_b;
    @Shadow
    @Final
    private int field_150151_M;

    @Override
    public BlockMaterial getBaseMaterial(int metadata) {
        return new BlockMaterial(field_150149_b, field_150151_M & 15);
    }
}
