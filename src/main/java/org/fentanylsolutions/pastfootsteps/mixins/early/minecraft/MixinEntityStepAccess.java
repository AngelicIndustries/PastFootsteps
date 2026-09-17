package org.fentanylsolutions.pastfootsteps.mixins.early.minecraft;

import net.minecraft.entity.Entity;

import org.fentanylsolutions.pastfootsteps.client.EntityStepAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface MixinEntityStepAccess extends EntityStepAccess {

    @Override
    @Accessor("nextStepDistance")
    int getPastFootstepsNextStepDistance();

    @Override
    @Accessor("nextStepDistance")
    void setPastFootstepsNextStepDistance(int value);
}
