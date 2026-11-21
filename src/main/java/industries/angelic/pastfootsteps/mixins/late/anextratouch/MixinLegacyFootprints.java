package industries.angelic.pastfootsteps.mixins.late.anextratouch;

import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import industries.angelic.pastfootsteps.client.FootstepController;

@Pseudo
@Mixin(targets = "org.fentanylsolutions.anextratouch.footsteps.FootprintUtil", remap = false)
public class MixinLegacyFootprints {

    @Inject(method = "spawnFootprint", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void pastfootsteps$ownPrints(Entity actor, boolean baby, boolean right, CallbackInfo callback) {
        if (FootstepController.ownsContacts(actor)) callback.cancel();
    }
}
