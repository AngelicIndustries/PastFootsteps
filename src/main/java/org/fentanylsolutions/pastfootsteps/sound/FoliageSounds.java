package org.fentanylsolutions.pastfootsteps.sound;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Tracks per-actor foliage acoustic occupancy and once-per-tick sound claims. */
public final class FoliageSounds {

    private static final long NO_TICK = Long.MIN_VALUE;
    private static final String[] NON_FOLIAGE = { "NOT_EMITTER", "NON_EMITTER", "MESSY_GROUND", "UNASSIGNED",
        "VANILLA" };

    private long generation = Long.MIN_VALUE;
    private long tick = NO_TICK;
    private long lastClaimTick = NO_TICK;
    private boolean observed;
    private Set<String> previousLow = new LinkedHashSet<String>();
    private Set<String> previousBody = new LinkedHashSet<String>();
    private Set<String> enteredLow = new LinkedHashSet<String>();
    private Set<String> enteredBody = new LinkedHashSet<String>();
    private Set<String> emitted = new HashSet<String>();

    /** Starts one actor tick. A repeated call for the same generation and tick is ignored. */
    public void begin(long tick, long generation, String messyNames) {
        begin(tick, generation, messyNames, null);
    }

    /** Starts one actor tick with low foliage and body foliage tracked as separate entry sources. */
    public void begin(long tick, long generation, String messyNames, String bodyNames) {
        if (this.tick == tick && this.generation == generation) return;

        Set<String> low = names(messyNames);
        Set<String> body = names(bodyNames);
        boolean reset = !observed || generation != this.generation || tick < this.tick;
        enteredLow = new LinkedHashSet<String>();
        enteredBody = new LinkedHashSet<String>();
        if (reset) lastClaimTick = NO_TICK;
        if (!reset) {
            enteredLow.addAll(low);
            enteredLow.removeAll(previousLow);
            enteredBody.addAll(body);
            enteredBody.removeAll(previousBody);
        }
        previousLow = low;
        previousBody = body;
        emitted.clear();
        this.tick = tick;
        this.generation = generation;
        observed = true;
    }

    /** Claims eligible names, returning the newly claimed comma-separated names or null. */
    public String claim(String names) {
        Set<String> candidates = names(names);
        candidates.removeAll(emitted);
        if (candidates.isEmpty()) return null;
        emitted.addAll(candidates);
        lastClaimTick = tick;
        return join(candidates);
    }

    /** Claims newly entered names when eligible and outside the foliage re-entry cooldown. */
    public String entry(boolean eligible) {
        return entry(eligible, false, false);
    }

    /** Claims body entries with the supplied movement gate and low entries only when boots permit them. */
    public String entry(boolean moving, boolean sneaking, boolean wearingBoots) {
        if (!moving || sneaking) return null;
        if (lastClaimTick != NO_TICK && tick - lastClaimTick < 2) return null;
        Set<String> candidates = new LinkedHashSet<String>(enteredBody);
        if (!wearingBoots) candidates.addAll(enteredLow);
        if (candidates.isEmpty()) return null;
        candidates.removeAll(emitted);
        if (candidates.isEmpty()) return null;
        emitted.addAll(candidates);
        lastClaimTick = tick;
        return join(candidates);
    }

    /** Plays claimed names with an optional body-only event, retaining their original deduplication keys. */
    public static void playClaimed(SoundEngine sound, String claimedNames, String bodyNames, String event,
        AcousticContext context) {
        Set<String> body = names(bodyNames);
        for (String name : names(claimedNames)) {
            String selected = body.contains(name) && sound.canPlay(name, "rustle") ? "rustle" : event;
            sound.play(name, selected, context);
        }
    }

    public static boolean contactAllowed(boolean wearingBoots, boolean sprinting) {
        return !wearingBoots || sprinting;
    }

    public static boolean entryAllowed(boolean moving, boolean sneaking, boolean wearingBoots) {
        return moving && !sneaking && !wearingBoots;
    }

    private static Set<String> names(String value) {
        Set<String> result = new LinkedHashSet<String>();
        if (value == null || value.trim()
            .isEmpty()) return result;
        String[] parts = value.split(",");
        for (String part : parts) {
            String name = part.trim();
            if (!name.isEmpty() && !isNonFoliage(name)) result.add(name);
        }
        return result;
    }

    private static boolean isNonFoliage(String name) {
        for (String token : NON_FOLIAGE) {
            if (token.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static String join(Set<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (result.length() > 0) result.append(',');
            result.append(name);
        }
        return result.toString();
    }
}
