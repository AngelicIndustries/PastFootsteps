package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.util.ResourceLocation;

import org.junit.Test;

import com.google.gson.JsonParser;

public class SoundNamesTest {

    private static final String STONE = "pf_presence.stone.stone_walk";

    @Test
    public void otherActorsUseMonoWhileLocalAndExplicitSoundsKeepTheirNamespace() {
        assertEquals("presencefootsteps:" + STONE, resolve(STONE, true));
        assertEquals("presencefootstepsmono:" + STONE, resolve(STONE, false));
        assertEquals("presencefootstepsmono:equipment.rain", resolve("equipment.rain", false));
        assertEquals("presencefootsteps:equipment.rain", resolve("equipment.rain", true));
        assertEquals("presencefootsteps:" + STONE, resolve("presencefootsteps:" + STONE, false));
        assertEquals("example:custom", resolve("@example:custom", false));
        assertEquals("equipment.example:custom", resolve("equipment.example:custom", false));
        assertEquals("minecraft:step.stone", resolve("step.stone", false));
    }

    @Test
    public void missingMonoFallsBackAndRoutingFollowsTheLiveRegistryAfterReload() {
        Set<ResourceLocation> registry = new HashSet<>();
        assertEquals(
            "presencefootsteps:" + STONE,
            SoundNames.resolve(STONE, false, registry::contains)
                .toString());
        registry.add(new ResourceLocation("presencefootstepsmono", STONE));
        assertEquals(
            "presencefootstepsmono:" + STONE,
            SoundNames.resolve(STONE, false, registry::contains)
                .toString());
        registry.clear();
        assertEquals(
            "presencefootsteps:" + STONE,
            SoundNames.resolve(STONE, false, registry::contains)
                .toString());
    }

    @Test
    public void modernVanillaAliasesKeepNativeSoundsOrSelectTheSpatialReplacement() {
        assertEquals("minecraft:game.player.swim", resolve("minecraft:entity.player.swim", false));
        assertEquals("minecraft:step.ladder", resolve("minecraft:block.ladder.step", false));
        assertEquals(
            "presencefootstepsmono:pf_presence.metalbar.metalbar_walk",
            resolve("minecraft:block.copper.step", false));
        assertEquals(
            "presencefootsteps:pf_presence.metalbar.metalbar_walk",
            resolve("minecraft:block.copper.step", true));
    }

    @Test
    public void equipmentScalingAndDelayedPlaybackRetainSourceAndLifetime() {
        List<String> sounds = new ArrayList<>();
        final long[] now = { 0 };
        AcousticEngine engine = new AcousticEngine((name, volume, pitch, context) -> {
            sounds.add(resolve(name, context.localPlayer));
            assertEquals(0.5F, volume, 0.0001F);
            assertEquals(1.25F, pitch, 0.0001F);
        }, () -> now[0]);
        engine.load(
            "delayed",
            new JsonParser().parse(
                "{\"type\":\"delayed\",\"delay\":50,\"acoustic\":{\"type\":\"basic\",\"name\":\"" + STONE + "\"}}"));
        AtomicBoolean alive = new AtomicBoolean(true);
        AcousticContext context = new AcousticContext(1, 2, 3, 1, 1, false, alive::get).scale(0.5F, 1.25F);
        engine.play("delayed", "walk", context);
        assertTrue(sounds.isEmpty());
        now[0] += 50;
        engine.tick();
        assertEquals(java.util.Collections.singletonList("presencefootstepsmono:" + STONE), sounds);
        engine.play("delayed", "walk", context);
        alive.set(false);
        now[0] += 50;
        engine.tick();
        assertEquals(1, sounds.size());
    }

    private static String resolve(String name, boolean localPlayer) {
        return SoundNames.resolve(name, localPlayer, ignored -> true)
            .toString();
    }
}
