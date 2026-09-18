package org.fentanylsolutions.pastfootsteps.sound;

import java.util.function.Predicate;

import net.minecraft.util.ResourceLocation;

/** Legacy sound aliases and MIT PF's local-stereo/other-actor-mono selection. */
public final class SoundNames {

    private SoundNames() {}

    public static ResourceLocation resolve(String name, boolean localPlayer, Predicate<ResourceLocation> available) {
        String sound = name.startsWith("@") ? name.substring(1) : name;
        switch (sound) {
            case "minecraft:block.ladder.step":
                sound = "minecraft:step.ladder";
                break;
            case "minecraft:entity.player.swim":
                sound = "minecraft:game.player.swim";
                break;
            case "minecraft:block.fire.extinguish":
                sound = "minecraft:random.fizz";
                break;
            case "minecraft:block.copper.step":
            case "minecraft:block.copper.break":
            case "minecraft:block.heavy_core.step":
                sound = "pf_presence.metalbar.metalbar_walk";
                break;
            case "minecraft:block.amethyst_block.step":
            case "minecraft:block.amethyst_block.hit":
            case "minecraft:block.amethyst_block.break":
                sound = "pf_presence.glass.glass_hit";
                break;
            case "minecraft:block.soul_soil.step":
            case "minecraft:block.soul_soil.place":
                sound = "pf_presence.quicksand.quicksand_walk";
                break;
            case "minecraft:block.slime_block.step":
            case "minecraft:block.slime_block.break":
            case "minecraft:block.honey_block.step":
            case "minecraft:block.honey_block.break":
                sound = "pf_presence.mud.mud_walk";
                break;
            case "minecraft:block.scaffolding.step":
            case "minecraft:block.creaking_heart.step":
            case "minecraft:block.creaking_heart.break":
                sound = "pf_presence.squeakywood.squeakywood_walk";
                break;
            default:
                break;
        }
        // Explicit namespaces are a resource pack's choice. Only PF's relative names use automatic routing.
        if (sound.indexOf(':') < 0 && (sound.startsWith("pf_presence.") || sound.startsWith("equipment."))) {
            if (!localPlayer) {
                ResourceLocation mono = new ResourceLocation("presencefootstepsmono", sound);
                // Consult the live registry so resource reloads and pack-provided events need no second cache.
                if (available.test(mono)) return mono;
            }
            return new ResourceLocation("presencefootsteps", sound);
        }
        return new ResourceLocation(sound);
    }
}
