package org.fentanylsolutions.pastfootsteps.mixins.late.anextratouch;

import net.minecraft.entity.EntityLivingBase;

import org.fentanylsolutions.pastfootsteps.client.FootstepController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Older AET versions can keep their equip detection while PF owns their walking accents. */
@Pseudo
@Mixin(targets = "org.fentanylsolutions.anextratouch.handlers.client.StepSoundHandler", remap = false)
public class MixinLegacyAccents {

    @Inject(method = "playStepAccents", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void pastfootsteps$ownAccents(EntityLivingBase actor, CallbackInfo callback) {
        if (FootstepController.ownsContacts(actor)) callback.cancel();
    }
}
