package industries.angelic.pastfootsteps.mixins.early.minecraft;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import industries.angelic.pastfootsteps.client.EntityStepAccess;

@Mixin(Entity.class)
public interface MixinEntityStepAccess extends EntityStepAccess {

    @Override
    @Accessor("nextStepDistance")
    int getPastFootstepsNextStepDistance();

    @Override
    @Accessor("nextStepDistance")
    void setPastFootstepsNextStepDistance(int value);
}
