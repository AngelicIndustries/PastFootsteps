package org.fentanylsolutions.pastfootsteps.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.minecraft.entity.Entity;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;

/**
 * Client-side silencing of actors, for example by a spell that muffles its target. A silenced actor makes no sound
 * through Past Footsteps: no steps, landings, wading or swimming strokes, foliage, wet or rain accents, gear or
 * equipment rustle, nor the native sounds Past Footsteps holds back and replays. Footprints and {@link ContactEvents}
 * are not silenced. The predicates are asked again when each sound would play, so a sound already queued or delayed is
 * dropped once its actor becomes silenced.
 */
public final class ActorSilencers {

    private static final CopyOnWriteArrayList<Predicate<Entity>> SILENCERS = new CopyOnWriteArrayList<>();

    private ActorSilencers() {}

    /**
     * Register once during client initialization; the predicate runs on the client thread, often, so keep it cheap.
     * Close the returned handle to unregister.
     */
    public static AutoCloseable register(Predicate<Entity> silenced) {
        if (silenced == null) throw new IllegalArgumentException("silenced");
        SILENCERS.addIfAbsent(silenced);
        return () -> SILENCERS.remove(silenced);
    }

    /**
     * Whether any registered predicate silences this actor. A failed predicate is removed so it cannot flood the log.
     */
    public static boolean isSilenced(Entity actor) {
        if (actor == null) return false;
        for (Predicate<Entity> silenced : SILENCERS) {
            try {
                if (silenced.test(actor)) return true;
            } catch (RuntimeException error) {
                SILENCERS.remove(silenced);
                PastFootsteps.LOG.error("Disabled failing PF actor silencer", error);
            }
        }
        return false;
    }
}
