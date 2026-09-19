package org.fentanylsolutions.pastfootsteps.sound;

import java.util.function.BooleanSupplier;

import net.minecraft.client.audio.SoundCategory;

/** Immutable call data supplied by the movement/block solver. */
public final class AcousticContext {

    public final double x, y, z;
    public final float volume, pitch;
    public final boolean localPlayer;
    public final SoundCategory category;
    /** NaN keeps random variation; otherwise this selects a position along the acoustic's range. */
    public final float volumePosition, pitchPosition;
    private final BooleanSupplier validity;

    public AcousticContext(double x, double y, double z, float volume, float pitch) {
        this(x, y, z, volume, pitch, () -> true);
    }

    public AcousticContext(double x, double y, double z, float volume, float pitch, BooleanSupplier validity) {
        this(x, y, z, volume, pitch, true, validity);
    }

    public AcousticContext(double x, double y, double z, float volume, float pitch, boolean localPlayer,
        BooleanSupplier validity) {
        this(x, y, z, volume, pitch, localPlayer, validity, Float.NaN, Float.NaN, SoundCategory.PLAYERS);
    }

    private AcousticContext(double x, double y, double z, float volume, float pitch, boolean localPlayer,
        BooleanSupplier validity, float volumePosition, float pitchPosition, SoundCategory category) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = Math.max(0.0F, volume);
        this.pitch = Math.max(0.01F, pitch);
        this.localPlayer = localPlayer;
        this.category = category == null ? SoundCategory.PLAYERS : category;
        this.validity = validity;
        this.volumePosition = volumePosition;
        this.pitchPosition = pitchPosition;
    }

    public AcousticContext scale(float volumeScale, float pitchScale) {
        return new AcousticContext(
            x,
            y,
            z,
            volume * volumeScale,
            pitch * pitchScale,
            localPlayer,
            validity,
            volumePosition,
            pitchPosition,
            category);
    }

    public AcousticContext withRangePosition(float volumePosition, float pitchPosition) {
        return new AcousticContext(
            x,
            y,
            z,
            volume,
            pitch,
            localPlayer,
            validity,
            volumePosition,
            pitchPosition,
            category);
    }

    public AcousticContext withCategory(SoundCategory category) {
        return new AcousticContext(
            x,
            y,
            z,
            volume,
            pitch,
            localPlayer,
            validity,
            volumePosition,
            pitchPosition,
            category);
    }

    public boolean isValid() {
        return validity.getAsBoolean();
    }
}
