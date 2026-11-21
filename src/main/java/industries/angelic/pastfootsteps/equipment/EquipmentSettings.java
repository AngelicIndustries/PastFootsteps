package industries.angelic.pastfootsteps.equipment;

import net.minecraftforge.common.config.Configuration;

/** Ordinary equipment controls stored with the rest of Past Footsteps' Forge settings. */
public final class EquipmentSettings {

    public static boolean footwear = true;
    public static float footwearVolume = 0.3F;
    public static boolean bodyArmor = true;
    public static String armorMode = "priority";
    public static float armorVolume = 0.3F;
    public static String defaultCategory = "heavy";
    public static String[] armorEntities = { "Player", "Zombie", "Skeleton", "PigZombie" };
    public static boolean rain = true;
    public static float rainVolume = 0.15F;
    public static String[] rainEntities = {};
    public static boolean rainEntityListIsBlacklist = true;

    private EquipmentSettings() {}

    /** Called by the owning configuration lifecycle after the main PF configuration has loaded. */
    public static void sync(Configuration configuration) {
        footwear = configuration
            .getBoolean("footwearEnabled", "equipment", true, "Play a boot-on-ground accent from resolved contacts.");
        footwearVolume = configuration
            .getFloat("footwearVolume", "equipment", 0.3F, 0, 2, "Boot-on-ground accent volume multiplier.");
        bodyArmor = configuration.getBoolean(
            "bodyArmorEnabled",
            "equipment",
            true,
            "Play chest and leg armor movement accents from resolved contacts.");
        armorMode = mode(
            configuration.getString(
                "armorMode",
                "equipment",
                "priority",
                "Mixed armor handling: priority selects one chest-or-leg category; mixed plays distinct categories."));
        armorVolume = configuration
            .getFloat("armorVolume", "equipment", 0.3F, 0, 2, "Body armor movement accent volume multiplier.");
        defaultCategory = category(
            configuration.getString(
                "defaultArmorCategory",
                "equipment",
                "heavy",
                "Category for unrecognised ItemArmor materials: light, medium, heavy, crystal, slime, elytra, or none."));
        armorEntities = configuration.getStringList(
            "armorEntities",
            "equipment",
            armorEntities,
            "Exact entity IDs that may play footwear and body armor accents. Player is the player alias.");
        rain = configuration.getBoolean(
            "rainEnabled",
            "rain",
            true,
            "Play a rain contact accent only where the resolved foot is exposed to precipitation.");
        rainVolume = configuration.getFloat(
            "rainVolume",
            "rain",
            0.15F,
            0,
            2,
            "Rain contact accent volume multiplier before rain-strength scaling.");
        rainEntities = configuration.getStringList(
            "rainEntities",
            "rain",
            rainEntities,
            "Entity IDs filtered for rain accents. The empty default matches every PF tracked actor.");
        rainEntityListIsBlacklist = configuration.getBoolean(
            "rainEntityListIsBlacklist",
            "rain",
            true,
            "Treat Rain Entities as an exclusion list. Disable it to make it an allow list.");
    }

    static String mode(String value) {
        return "mixed".equalsIgnoreCase(value) ? "mixed" : "priority";
    }

    static String category(String value) {
        if (value == null) return "none";
        String result = value.toLowerCase(java.util.Locale.ROOT);
        return "light".equals(result) || "medium".equals(result)
            || "heavy".equals(result)
            || "crystal".equals(result)
            || "slime".equals(result)
            || "elytra".equals(result)
            || "none".equals(result) ? result : "none";
    }
}
