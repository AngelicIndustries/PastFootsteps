package org.fentanylsolutions.pastfootsteps.mixins.early.minecraft;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.client.settings.GameSettings;

import org.fentanylsolutions.pastfootsteps.sound.ActorSound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

@Mixin(SoundManager.class)
public class MixinSoundManager {

    @Shadow
    @Final
    private GameSettings options;

    @ModifyExpressionValue(
        method = "playSound",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/audio/SoundEventAccessorComposite;getSoundCategory()Lnet/minecraft/client/audio/SoundCategory;"),
        require = 1)
    private SoundCategory pastfootsteps$sourceCategory(SoundCategory original, @Local(argsOnly = true) ISound sound) {
        return ActorSound.category(sound, original);
    }

    // The second lookup removes a completed channel from categorySounds. Match the category used at insertion.
    @ModifyExpressionValue(
        method = "updateAllSounds",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/audio/SoundEventAccessorComposite;getSoundCategory()Lnet/minecraft/client/audio/SoundCategory;",
            ordinal = 1),
        require = 1)
    private SoundCategory pastfootsteps$finishedSourceCategory(SoundCategory original, @Local ISound sound) {
        return ActorSound.category(sound, original);
    }

    // Also covers live slider changes and volume updates, without multiplying the event's original category.
    @Inject(method = "getNormalizedVolume", at = @At("HEAD"), cancellable = true, require = 1)
    private void pastfootsteps$sourceVolume(ISound sound, SoundPoolEntry sample, SoundCategory original,
        CallbackInfoReturnable<Float> callback) {
        if (sound instanceof ActorSound) callback.setReturnValue(
            ActorSound
                .normalizedVolume(sound, sample.getVolume(), options.getSoundLevel(((ActorSound) sound).category)));
    }
}
