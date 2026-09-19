package org.fentanylsolutions.pastfootsteps.sound;

import java.util.function.BooleanSupplier;

import net.minecraft.client.audio.SoundCategory;

/** Immutable call data supplied by the movement/block solver. */
public final class AcousticContext {

    public final double x, y, z;
    public final float volume, pitch;
    public final boolean localPlayer;
    public final SoundCategory category;
    /** NaN keeps random variation; finite values select or extrapolate along the acoustic's range. */
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

    /**
     * Selects range fractions: 0 is min, 1 is max, and finite values outside that interval extrapolate.
     * NaN preserves random sampling independently for either axis, as when no MIT gliding option is supplied.
     */
    public AcousticContext withRangePosition(float volumePosition, float pitchPosition) {
        if (Float.isInfinite(volumePosition) || Float.isInfinite(pitchPosition))
            throw new IllegalArgumentException("Range positions must be finite or NaN for random sampling");
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
