package org.fentanylsolutions.pastfootsteps.mixins.early.minecraft;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S29PacketSoundEffect;

import org.fentanylsolutions.pastfootsteps.client.FootstepController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Inject(method = "handleSoundEffect", at = @At("HEAD"), cancellable = true)
    private void pastfootsteps$filterRemoteStep(S29PacketSoundEffect packet, CallbackInfo ci) {
        if (FootstepController.suppressRemoteSound(
            packet.func_149212_c(),
            packet.func_149207_d(),
            packet.func_149211_e(),
            packet.func_149210_f())) ci.cancel();
    }
}
