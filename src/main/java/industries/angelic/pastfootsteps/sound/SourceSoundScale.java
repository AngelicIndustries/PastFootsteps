package industries.angelic.pastfootsteps.sound;

/** Per-source movement envelope; inputs use the same support-relative positions as the contact tracker. */
public final class SourceSoundScale {

    private long generation = Long.MIN_VALUE;
    private double x, y, z;
    private float progress;

    public void update(double atX, double atY, double atZ, long currentGeneration, double runSpeed) {
        double dx = atX - x, dy = atY - y, dz = atZ - z;
        double squared = dx * dx + dy * dy + dz * dz;
        if (generation != currentGeneration || !Double.isFinite(squared) || squared > 16) {
            progress = 0;
        } else {
            // Our tracker measures displacement, not the local player's post-friction motion. Keep the MIT
            // squared-speed ramp, starting at the profile's running threshold so walking stays unboosted.
            double reference = Double.isFinite(runSpeed) ? Math.max(0.001, runSpeed) : 0.22;
            double begin = reference, end = reference * 1.25;
            float target = (float) Math.max(0, Math.min(1, (squared - begin * begin) / (end * end - begin * begin)));
            progress += (target - progress) * 0.35F;
        }
        generation = currentGeneration;
        x = atX;
        y = atY;
        z = atZ;
    }

    public float volume(float runningBoost) {
        float adjustment = Math.max(-1, Math.min(1, runningBoost));
        // Reserve headroom for positive adjustments. Loud samples otherwise hit Minecraft's gain ceiling
        // during both walking and running, erasing the requested contrast. Zero retains the old balance.
        return (1 + adjustment * progress) / (1 + Math.max(0, adjustment));
    }

    public static float pitch(double width, double referenceWidth) {
        if (!Double.isFinite(width) || !Double.isFinite(referenceWidth) || width <= 0 || referenceWidth <= 0) return 1;
        double scale = Math.max(0.01, Math.min(200, width / referenceWidth));
        return (float) (1 / (1 + (scale - 1) * 0.6));
    }
}
