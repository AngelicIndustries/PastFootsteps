package industries.angelic.pastfootsteps.sound;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.google.gson.JsonParser;

public class SoundArrivalTest {

    private long now;
    private final SoundArrival arrival = new SoundArrival(() -> now);
    private final List<String> heard = new ArrayList<>();

    @Test
    public void distanceThresholdAndTickRoundingMatchMIT() {
        assertEquals(0, SoundArrival.delayMillis(100));
        assertEquals(250, SoundArrival.delayMillis(100.01));
        assertEquals(300, SoundArrival.delayMillis(144));
        assertEquals(600, SoundArrival.delayMillis(576));
        assertEquals(300, SoundArrival.delayMillis(169));
        arrival.play(100, () -> true, () -> heard.add("near"));
        arrival.play(144, () -> true, () -> heard.add("far"));
        now = 299;
        arrival.tick();
        assertEquals(Arrays.asList("near"), heard);
        now = 300;
        arrival.tick();
        assertEquals(Arrays.asList("near", "far"), heard);
    }

    @Test
    public void removalTeleportAndClearCancelArrivals() {
        AtomicBoolean alive = new AtomicBoolean(true);
        arrival.play(144, alive::get, () -> heard.add("removed"));
        arrival.beginTick();
        alive.set(false);
        now = 300;
        arrival.tick();
        alive.set(true);
        arrival.play(144, alive::get, () -> heard.add("cleared"));
        arrival.clear();
        now = 600;
        arrival.tick();
        assertTrue(heard.isEmpty());
    }

    @Test
    public void arrivalDoesNotBurstAfterStallButFreshSoundsStillPlay() {
        arrival.play(144, () -> true, () -> heard.add("stale"));
        now = 2000;
        arrival.tick();
        assertTrue(heard.isEmpty());
        arrival.play(144, () -> true, () -> heard.add("fresh"));
        now = 2300;
        arrival.tick();
        assertEquals(Arrays.asList("fresh"), heard);
    }

    @Test
    public void acousticAndArrivalDelaysEachApplyExactlyOnce() {
        AcousticEngine engine = new AcousticEngine(
            (name, volume, pitch, context) -> arrival.play(144, context::isValid, () -> heard.add(name)),
            () -> now);
        engine.load("test", new JsonParser().parse("{\"type\":\"delayed\",\"delay\":50,\"acoustic\":\"layer\"}"));
        engine.play("test", "walk", new AcousticContext(12, 0, 0, 1, 1));
        arrival.beginTick();
        now = 50;
        engine.tick();
        arrival.tick();
        assertTrue(heard.isEmpty());
        now = 349;
        arrival.tick();
        assertTrue(heard.isEmpty());
        now = 350;
        arrival.tick();
        assertEquals(Arrays.asList("layer"), heard);
        now = 650;
        arrival.tick();
        assertEquals(1, heard.size());
    }
}
