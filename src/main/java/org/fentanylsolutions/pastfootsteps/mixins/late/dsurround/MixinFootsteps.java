package org.fentanylsolutions.pastfootsteps.mixins.late.dsurround;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.fentanylsolutions.pastfootsteps.client.EntityStepAccess;
import org.fentanylsolutions.pastfootsteps.client.FootstepController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.blockartistry.mod.DynSurround.client.footsteps.Footsteps", remap = false)
public class MixinFootsteps {

    @Inject(method = "process", at = @At("HEAD"), cancellable = true, remap = false)
    private void pastfootsteps$replaceDynamicSurroundings(World world, EntityPlayer player, CallbackInfo callback) {
        if (FootstepController.shouldReplace(player)) {
            EntityStepAccess access = (EntityStepAccess) player;
            if (access.getPastFootstepsNextStepDistance() == Integer.MAX_VALUE) {
                access.setPastFootstepsNextStepDistance((int) player.distanceWalkedOnStepModified + 1);
            }
            callback.cancel();
        }
    }
}
