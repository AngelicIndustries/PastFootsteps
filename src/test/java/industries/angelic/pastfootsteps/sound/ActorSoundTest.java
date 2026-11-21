package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.util.ResourceLocation;

import org.junit.Test;

import com.google.gson.JsonParser;

public class ActorSoundTest {

    @Test
    public void categoriesFollowActorsWithoutChangingSoundResourcesOrUnrelatedSounds() {
        assertEquals(SoundCategory.PLAYERS, ActorSound.category(true, false));
        assertEquals(SoundCategory.ANIMALS, ActorSound.category(false, false));
        assertEquals(SoundCategory.MOBS, ActorSound.category(false, true));
        ResourceLocation name = new ResourceLocation("presencefootstepsmono", "equipment.rain");
        ActorSound cow = new ActorSound(
            name,
            0.8F,
            1,
            new AcousticContext(1, 2, 3, 1, 1).withCategory(SoundCategory.ANIMALS));
        assertEquals(name, cow.getPositionedSoundLocation());
        assertEquals(SoundCategory.ANIMALS, ActorSound.category(cow, SoundCategory.PLAYERS));
        assertEquals(0.1, ActorSound.normalizedVolume(cow, 0.5, 0.25F), 0.0001);
        assertEquals(0, ActorSound.normalizedVolume(cow, 0.5, 0), 0);
        assertEquals(1, ActorSound.normalizedVolume(cow, 10, 1), 0);
        PositionedSoundRecord nativeSound = new PositionedSoundRecord(name, 1, 1, 1, 2, 3);
        assertEquals(SoundCategory.WEATHER, ActorSound.category(nativeSound, SoundCategory.WEATHER));
    }

    @Test
    public void fullVolumeSamplesKeepRunningContrastThroughMinecraftGainClamping() {
        SourceSoundScale walk = new SourceSoundScale(), run = new SourceSoundScale();
        for (int i = 0; i <= 30; i++) {
            walk.update(i * 0.216, 0, 0, 0, 0.22);
            run.update(i * 0.281, 0, 0, 0, 0.22);
        }
        ResourceLocation name = new ResourceLocation("presencefootsteps", "pf_presence.stone.stone_walk");
        AcousticContext context = new AcousticContext(0, 0, 0, 1, 1);
        for (float boost : new float[] { 0, 0.5F, 1 }) {
            ActorSound walking = new ActorSound(name, walk.volume(boost), 1, context);
            ActorSound running = new ActorSound(name, run.volume(boost), 1, context);
            for (float slider : new float[] { 0.25F, 1 }) {
                float walkingGain = ActorSound.normalizedVolume(walking, 1, slider);
                float runningGain = ActorSound.normalizedVolume(running, 1, slider);
                assertEquals(1 + boost, runningGain / walkingGain, 0.001);
                assertEquals(slider, runningGain, 0.001);
            }
        }
    }

    @Test
    public void categoryAndRangeControlsSurviveEquipmentScalingAndDelayedPlayback() {
        AtomicBoolean alive = new AtomicBoolean(true);
        final int[] plays = { 0 };
        final long[] now = { 0 };
        AcousticEngine engine = new AcousticEngine((name, volume, pitch, context) -> {
            plays[0]++;
            assertEquals(SoundCategory.MOBS, context.category);
            assertFalse(context.localPlayer);
            assertEquals(0.5F, volume, 0.0001F);
            assertEquals(1.25F, pitch, 0.0001F);
            assertEquals(0.25F, context.volumePosition, 0);
            assertTrue(context.isValid());
        }, () -> now[0]);
        engine.load(
            "test",
            new JsonParser().parse("{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"equipment.rain\"}"));
        AcousticContext context = new AcousticContext(1, 2, 3, 1, 1, false, alive::get).withCategory(SoundCategory.MOBS)
            .withRangePosition(0.25F, Float.NaN)
            .scale(0.5F, 1.25F);
        engine.play("test", "walk", context);
        now[0] += 50;
        engine.tick();
        assertEquals(1, plays[0]);
        engine.play("test", "walk", context);
        alive.set(false);
        now[0] += 50;
        engine.tick();
        assertEquals(1, plays[0]);
    }
}
