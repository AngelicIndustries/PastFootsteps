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
    final float delayMin, delayMax;
    final boolean keepLate;

    public AcousticContext(double x, double y, double z, float volume, float pitch) {
        this(x, y, z, volume, pitch, () -> true);
    }

    public AcousticContext(double x, double y, double z, float volume, float pitch, BooleanSupplier validity) {
        this(x, y, z, volume, pitch, true, validity);
    }

    public AcousticContext(double x, double y, double z, float volume, float pitch, boolean localPlayer,
        BooleanSupplier validity) {
        this(
            x,
            y,
            z,
            volume,
            pitch,
            localPlayer,
            validity,
            Float.NaN,
            Float.NaN,
            SoundCategory.PLAYERS,
            Float.NaN,
            Float.NaN,
            false);
    }

    private AcousticContext(double x, double y, double z, float volume, float pitch, boolean localPlayer,
        BooleanSupplier validity, float volumePosition, float pitchPosition, SoundCategory category, float delayMin,
        float delayMax, boolean keepLate) {
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
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        this.keepLate = keepLate;
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
            category,
            delayMin,
            delayMax,
            keepLate);
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
            category,
            delayMin,
            delayMax,
            keepLate);
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
            category,
            delayMin,
            delayMax,
            keepLate);
    }

    /** Inner MIT delay options override outer bounds; the legacy skippable key is inherited. */
    AcousticContext withDelay(float min, float max, boolean keepLate) {
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
            category,
            min,
            max,
            this.keepLate || keepLate);
    }

    /** Matches MIT's presence-only skippable option: false retains late acoustic layers. */
    public AcousticContext withLateSoundSkipping(boolean skip) {
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
            category,
            delayMin,
            delayMax,
            !skip);
    }

    public boolean isValid() {
        return validity.getAsBoolean();
    }
}
