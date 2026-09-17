package org.fentanylsolutions.pastfootsteps.mixins.early.minecraft;

import net.minecraft.entity.Entity;

import org.fentanylsolutions.pastfootsteps.client.FootstepController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntityPlaySound {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void pastfootsteps$suppressSound(String sound, float volume, float pitch, CallbackInfo callback) {
        if (FootstepController.suppressSound((Entity) (Object) this, sound)) {
            callback.cancel();
        }
    }
}
