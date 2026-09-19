package org.fentanylsolutions.pastfootsteps.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;

public final class Config {

    public static Configuration configuration;
    public static File directory;
    public static boolean surfaceSounds = true;
    public static boolean swimming = true;
    public static boolean enabled = true, otherPlayers = true, mobs = true, foliage = true, wet = true;
    public static boolean jumpLand = true, wander = true, stationary = true, debug = false, debugContacts = false;
    public static float volume = 1, foliageVolume = 1, wetVolume = 1, remoteVolume = 0.7f;
    public static float passiveVolume = 0.7f, hostileVolume = 0.7f;
    public static float swimmingVolume = 1;
    public static float ownVolume = 1, runningBoost = 0;
    public static boolean sizePitch = true, distanceDelay = true;
    public static float stride = 1.2f, ladderStride = 0.65f;
    public static int range = 24, maxEntities = 64, maxSoundsPerTick = 32;

    private Config() {}

    public static void init(File configDirectory) {
        directory = new File(configDirectory, "pastfootsteps");
        configuration = new Configuration(new File(directory, "pastfootsteps.cfg"));
        configuration.load();
        sync();
    }

    public static void sync() {
        enabled = bool(
            "general",
            "enabled",
            true,
            "Enable PF sounds and footprints. Disabling restores the previous footstep engine.");
        surfaceSounds = bool(
            "effects",
            "surfaceSounds",
            true,
            "Replace native movement audio with material footsteps. Footprints and equipment have separate toggles.");
        otherPlayers = bool(
            "general",
            "otherPlayers",
            true,
            "Generate footsteps for other players using their observed movement.");
        mobs = bool(
            "general",
            "mobs",
            true,
            "Generate footsteps for mobs with a matching entity profile. Unmatched mobs keep native sounds.");
        range = configuration
            .getInt("range", "general", 24, 8, 64, "Maximum distance in blocks for other players and supported mobs.");
        maxEntities = configuration.getInt(
            "maxEntities",
            "general",
            64,
            1,
            256,
            "Maximum tracked actors, including the local player. Nearby actors take priority.");
        maxSoundsPerTick = configuration.getInt(
            "maxSoundsPerTick",
            "general",
            32,
            1,
            128,
            "Maximum enhanced sound samples started per client tick. Additional samples are skipped in crowds.");
        volume = number("volume", "master", 1, 0, 2, "Overall footstep volume multiplier.");
        ownVolume = number(
            "volume",
            "ownPlayer",
            1,
            0,
            2,
            "Additional volume multiplier for your own movement sounds.");
        runningBoost = number(
            "volume",
            "runningBoost",
            0,
            -1,
            1,
            "Movement-sensitive volume adjustment. Zero keeps constant volume. Positive values reserve headroom by softening walking: 0.5 makes running up to 50 percent louder than walking, 1 makes it twice as loud in gain. Running retains the configured volume. Negative values soften running instead: -0.5 halves its gain. Applies smoothly to all PF movement layers, including vertical movement.");
        foliageVolume = number("volume", "foliage", 1, 0, 2, "Grass, crops and other foliage volume multiplier.");
        wetVolume = number("volume", "wet", 1, 0, 2, "Shallow-water layer volume multiplier.");
        swimmingVolume = number("volume", "swimming", 1, 0, 2, "Water and lava swimming volume multiplier.");
        remoteVolume = number("volume", "otherPlayers", 0.7f, 0, 2, "Additional volume multiplier for other players.");
        passiveVolume = number(
            "volume",
            "passiveMobs",
            0.7f,
            0,
            2,
            "Additional volume multiplier for supported passive mobs.");
        hostileVolume = number(
            "volume",
            "hostileMobs",
            0.7f,
            0,
            2,
            "Additional volume multiplier for supported hostile mobs.");
        foliage = bool(
            "effects",
            "foliage",
            true,
            "Rustle vegetation at foot and body contacts. Boots suppress only the low foot layer; sprinting restores it. Plants touching the legs, torso or head still rustle while moving with boots. Sneaking suppresses body and entry rustles.");
        wet = bool("effects", "wet", true, "Layer wet footsteps in shallow water. Exposed rain has its own settings.");
        swimming = bool(
            "effects",
            "swimming",
            true,
            "Replace swimming audio in deep water and lava. Disable to retain vanilla or Dynamic Surroundings swimming sounds. Shallow-water footsteps still use the surface and wet settings.");
        jumpLand = bool("effects", "jumpAndLand", true, "Play takeoff and landing sounds.");
        wander = bool("effects", "shuffle", true, "Play a short shuffle when stopping or turning abruptly.");
        stationary = bool(
            "effects",
            "stationary",
            true,
            "Allow materials such as creaky wood to make occasional standing sounds.");
        distanceDelay = bool(
            "effects",
            "distanceDelay",
            true,
            "Delay movement sounds beyond 10 blocks by distance, matching MIT Presence Footsteps. Nearby sounds stay immediate.");
        sizePitch = bool(
            "effects",
            "sizePitch",
            true,
            "Adjust movement sound pitch to entity size using the profile's referenceWidth. Smaller entities, including babies, sound higher. Profiles without a reference width keep normal pitch.");
        stride = number(
            "movement",
            "stride",
            1.2f,
            0.3f,
            3,
            "Blocks between player walking footsteps. Other gaits scale their full cycle from twice this value.");
        ladderStride = number("movement", "ladderStride", 0.65f, 0.2f, 2, "Blocks climbed between ladder sounds.");
        debug = bool(
            "general",
            "debugHud",
            false,
            "Show tracked actors and the focused actor's profile, gait, foot, phase and material.");
        debugContacts = bool(
            "general",
            "debugContacts",
            false,
            "Draw short-lived contact markers at resolved foot positions. Cyan is left, orange is right.");
        org.fentanylsolutions.pastfootsteps.footprint.FootprintSettings.sync(configuration);
        org.fentanylsolutions.pastfootsteps.equipment.EquipmentSettings.sync(configuration);
        org.fentanylsolutions.pastfootsteps.network.SyncSettings.sync(configuration);
        for (String category : configuration.getCategoryNames()) {
            configuration.getCategory(category)
                .setLanguageKey("pastfootsteps.config." + category);
            for (Property property : configuration.getCategory(category)
                .getOrderedValues()) {
                property.setLanguageKey("pastfootsteps.config." + category + "." + property.getName());
            }
        }
        if (configuration.hasChanged()) configuration.save();
    }

    public static void toggle() {
        configuration.get("general", "enabled", true)
            .set(!enabled);
        sync();
        PastFootsteps.LOG.debug("Enhanced footsteps {}", enabled ? "enabled" : "disabled");
    }

    private static boolean bool(String category, String name, boolean value, String comment) {
        return configuration.getBoolean(name, category, value, comment);
    }

    private static float number(String category, String name, float value, float min, float max, String comment) {
        return configuration.getFloat(name, category, value, min, max, comment);
    }
}
