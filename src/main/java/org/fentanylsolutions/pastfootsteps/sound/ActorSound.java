package org.fentanylsolutions.pastfootsteps.sound;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.util.ResourceLocation;

/** A PF sample carries its source's category without changing shared sound registry entries. */
public final class ActorSound extends PositionedSoundRecord {

    public final SoundCategory category;

    public ActorSound(ResourceLocation name, float volume, float pitch, AcousticContext context) {
        super(name, volume, pitch, (float) context.x, (float) context.y, (float) context.z);
        category = context.category;
    }

    public static SoundCategory category(boolean player, boolean hostile) {
        return player ? SoundCategory.PLAYERS : hostile ? SoundCategory.MOBS : SoundCategory.ANIMALS;
    }

    public static SoundCategory category(ISound sound, SoundCategory original) {
        return sound instanceof ActorSound ? ((ActorSound) sound).category : original;
    }

    public static float normalizedVolume(ISound sound, double sampleVolume, float categoryVolume) {
        return (float) Math.max(0, Math.min(1, sound.getVolume() * sampleVolume * categoryVolume));
    }
}
