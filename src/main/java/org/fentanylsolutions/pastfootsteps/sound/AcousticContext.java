package org.fentanylsolutions.pastfootsteps.sound;

import java.util.function.BooleanSupplier;

/** Immutable call data supplied by the movement/block solver. */
public final class AcousticContext {

    public final double x, y, z;
    public final float volume, pitch;
    private final BooleanSupplier validity;

    public AcousticContext(double x, double y, double z, float volume, float pitch) {
        this(x, y, z, volume, pitch, () -> true);
    }

    public AcousticContext(double x, double y, double z, float volume, float pitch, BooleanSupplier validity) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = Math.max(0.0F, volume);
        this.pitch = Math.max(0.01F, pitch);
        this.validity = validity;
    }

    public boolean isValid() {
        return validity.getAsBoolean();
    }
}
