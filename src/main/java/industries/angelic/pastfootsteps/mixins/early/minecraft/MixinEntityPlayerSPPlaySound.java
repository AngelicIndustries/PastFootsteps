package industries.angelic.pastfootsteps.mixins.early.minecraft;

import net.minecraft.client.entity.EntityPlayerSP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import industries.angelic.pastfootsteps.client.FootstepController;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSPPlaySound {

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void pastfootsteps$suppressSound(String sound, float volume, float pitch, CallbackInfo callback) {
        if (FootstepController.interceptSound((EntityPlayerSP) (Object) this, sound, volume, pitch)) {
            callback.cancel();
        }
    }
}
