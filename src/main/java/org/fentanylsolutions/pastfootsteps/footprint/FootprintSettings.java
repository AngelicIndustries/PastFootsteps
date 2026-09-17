package org.fentanylsolutions.pastfootsteps.footprint;

import net.minecraftforge.common.config.Configuration;

/** Ordinary visual preferences. Entity and substrate rules live in footprints.json. */
public final class FootprintSettings {

    public static boolean enabled = true;
    public static int maximum = 2000, lifetime = 200, distance = 32;
    public static float opacity = 0.4f, size = 1, density = 1, rainLifetime = 0.4f, snowLifetime = 0.6f;

    private FootprintSettings() {}

    public static void sync(Configuration config) {
        String category = "footprints";
        enabled = config.getBoolean("enabled", category, true, "Leave prints at supported PF foot contacts.");
        maximum = config.getInt("maximum", category, 2000, 0, 10000, "Maximum retained prints. Zero disables prints.");
        lifetime = config
            .getInt("lifetime", category, 200, 1, 12000, "Default print lifetime in ticks (20 per second).");
        distance = config.getInt("distance", category, 32, 8, 96, "Maximum distance for drawing prints.");
        opacity = config
            .getFloat("opacity", category, 0.4f, 0, 1, "Default opacity before surface-specific overrides.");
        size = config
            .getFloat("size", category, 1, 0.1f, 4, "Print size multiplier. Foot positions still follow the gait.");
        density = config.getFloat(
            "density",
            category,
            1,
            0,
            1,
            "Fraction of contacts that leave a print; never creates extra steps.");
        rainLifetime = config
            .getFloat("rainLifetime", category, 0.4f, 0.01f, 1, "Lifetime multiplier in exposed rain.");
        snowLifetime = config
            .getFloat("snowLifetime", category, 0.6f, 0.01f, 1, "Lifetime multiplier in exposed snowfall.");
    }
}
