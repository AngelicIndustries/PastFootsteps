package org.fentanylsolutions.pastfootsteps.network;

import net.minecraftforge.common.config.Configuration;

/** Server-owned limits for the optional cosmetic motion stream. */
public final class SyncSettings {

    public static boolean enabled = true;
    public static int range = 32, maxActors = 24, maxPacketsPerTick = 48;

    private SyncSettings() {}

    /** Call from the mod's normal configuration sync method. Safe on both physical sides. */
    public static void sync(Configuration configuration) {
        enabled = configuration.getBoolean(
            "motionSync",
            "serverSupport",
            true,
            "Send optional server-observed motion to compatible Past Footsteps clients.");
        range = configuration.getInt(
            "motionSyncRange",
            "serverSupport",
            32,
            8,
            96,
            "Maximum server distance in blocks for optional synchronized actor motion.");
        maxActors = configuration.getInt(
            "motionSyncActors",
            "serverSupport",
            24,
            1,
            64,
            "Maximum nearby actors sampled for each compatible client every two ticks.");
        maxPacketsPerTick = configuration.getInt(
            "motionSyncPackets",
            "serverSupport",
            48,
            1,
            256,
            "Global packet budget for optional motion synchronization every two server ticks.");
    }
}
