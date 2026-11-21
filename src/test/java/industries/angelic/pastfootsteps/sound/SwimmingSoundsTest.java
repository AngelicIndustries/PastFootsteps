package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.google.gson.JsonParser;

public class SwimmingSoundsTest {

    @Test
    public void classifiesLiquidMovement() {
        assertEquals(SwimmingSounds.Mode.NONE, SwimmingSounds.mode(false, false, false, false));
        assertEquals(SwimmingSounds.Mode.WADING, SwimmingSounds.mode(true, false, true, false));
        assertEquals(SwimmingSounds.Mode.WATER, SwimmingSounds.mode(true, false, true, true));
        assertEquals(SwimmingSounds.Mode.WATER, SwimmingSounds.mode(true, false, false, false));
        assertEquals(SwimmingSounds.Mode.LAVA, SwimmingSounds.mode(true, true, true, false));
    }

    @Test
    public void disabledOrMissingSwimmingFallsBackButExplicitSilenceRemainsOwned() {
        SoundEngine sound = new SoundEngine((name, volume, pitch, context) -> {});
        assertFalse(SwimmingSounds.canReplace(SwimmingSounds.Mode.WATER, false, sound));
        assertFalse(SwimmingSounds.canReplace(SwimmingSounds.Mode.WATER, true, sound));
        assertTrue(SwimmingSounds.canReplace(SwimmingSounds.Mode.NONE, false, sound));
        assertTrue(SwimmingSounds.canReplace(SwimmingSounds.Mode.WADING, false, sound));
        sound.load("swim_water", new JsonParser().parse("{\"type\":\"empty\"}"));
        assertTrue(SwimmingSounds.canReplace(SwimmingSounds.Mode.WATER, true, sound));
        assertFalse(SwimmingSounds.canReplace(SwimmingSounds.Mode.WATER, false, sound));
        assertFalse(SwimmingSounds.canReplace(SwimmingSounds.Mode.LAVA, true, sound));
    }

    @Test
    public void bundledWaterAndLavaUseSpeedPositionAndRemoteScaling() throws Exception {
        final String[] heard = new String[1];
        final float[] values = new float[2];
        final AcousticContext[] received = new AcousticContext[1];
        SoundEngine sound = new SoundEngine((name, volume, pitch, context) -> {
            heard[0] = name;
            values[0] = volume;
            values[1] = pitch;
            received[0] = context;
        });
        loadBundled(sound, "swim_water");
        loadBundled(sound, "swim_lava");

        AcousticContext local = new AcousticContext(1, 2, 3, 0.8F, 1.0F);
        SwimmingSounds.play(sound, SwimmingSounds.Mode.WATER, 2.0, local);
        assertEquals("minecraft:entity.player.swim", heard[0]);
        assertEquals(0.8F * (0.1F + 0.9F * 0.7F), values[0], 0.00001F);
        assertTrue(values[1] >= 0.9F && values[1] <= 1.1F);

        // Generic acoustic positions may extrapolate, but swim intensity stays bounded at its source.
        SwimmingSounds.play(sound, SwimmingSounds.Mode.WATER, 100, local);
        assertEquals(0.8F, values[0], 0.00001F);
        SwimmingSounds.play(sound, SwimmingSounds.Mode.WATER, -1, local);
        assertEquals(0.08F, values[0], 0.00001F);

        AtomicBoolean alive = new AtomicBoolean(true);
        AcousticContext remote = new AcousticContext(4, 5, 6, 0.8F, 1.0F, false, alive::get);
        SwimmingSounds.play(sound, SwimmingSounds.Mode.LAVA, 1.0, remote);
        assertEquals(0.8F * 0.125F * (0.1F + 0.9F * 0.35F), values[0], 0.00001F);
        assertTrue(values[1] >= 0.09F && values[1] <= 0.11F);
        assertEquals(4.0, received[0].x, 0.0);
        assertFalse(received[0].localPlayer);
        assertTrue(received[0].isValid());
        alive.set(false);
        assertFalse(received[0].isValid());
    }

    private static void loadBundled(SoundEngine sound, String name) throws Exception {
        String path = "/assets/presencefootsteps/config/acoustics/" + name + ".json";
        try (InputStreamReader reader = new InputStreamReader(
            SwimmingSoundsTest.class.getResourceAsStream(path),
            StandardCharsets.UTF_8)) {
            sound.load(name, new JsonParser().parse(reader));
        }
    }
}
