package org.fentanylsolutions.pastfootsteps.sound;

/** Liquid movement acoustics adapted from MIT PF's TerrestrialStepSoundGenerator. */
public final class SwimmingSounds {

    public enum Mode {

        NONE(null),
        WADING(null),
        WATER("swim_water"),
        LAVA("swim_lava");

        public final String acoustic;

        Mode(String acoustic) {
            this.acoustic = acoustic;
        }

        public boolean swimming() {
            return acoustic != null;
        }
    }

    private SwimmingSounds() {}

    public static Mode mode(boolean inLiquid, boolean inLava, boolean supported, boolean submerged) {
        if (!inLiquid) return Mode.NONE;
        if (inLava) return Mode.LAVA;
        return supported && !submerged ? Mode.WADING : Mode.WATER;
    }

    /** Shared by replacement hooks and playback so disabled/missing definitions retain native audio. */
    public static boolean canReplace(Mode mode, boolean enabled, SoundEngine sound) {
        return !mode.swimming() || enabled && sound.canPlay(mode.acoustic);
    }

    public static void play(SoundEngine sound, Mode mode, double speed, AcousticContext context) {
        if (!mode.swimming()) return;
        float intensity = Double.isFinite(speed) ? (float) Math.max(0, Math.min(1, speed * 0.35)) : 0;
        // Upstream keeps other swimmers quieter than one's own strokes, on top of source-volume settings.
        sound.play(
            mode.acoustic,
            "swim",
            context.scale(context.localPlayer ? 1 : 0.125F, 1)
                .withRangePosition(intensity, Float.NaN));
    }
}
