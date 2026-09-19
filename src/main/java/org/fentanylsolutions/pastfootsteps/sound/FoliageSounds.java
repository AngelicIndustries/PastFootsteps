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
    private Set<String> previous = new LinkedHashSet<String>();
    private Set<String> entered = new LinkedHashSet<String>();
    private Set<String> emitted = new HashSet<String>();

    /** Starts one actor tick. A repeated call for the same generation and tick is ignored. */
    public void begin(long tick, long generation, String messyNames) {
        if (this.tick == tick && this.generation == generation) return;

        Set<String> current = names(messyNames);
        boolean reset = !observed || generation != this.generation || tick < this.tick;
        entered = new LinkedHashSet<String>();
        if (reset) lastClaimTick = NO_TICK;
        if (!reset) {
            entered.addAll(current);
            entered.removeAll(previous);
        }
        previous = current;
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
        if (!eligible || entered.isEmpty()) return null;
        if (lastClaimTick != NO_TICK && tick - lastClaimTick < 2) return null;
        Set<String> candidates = new LinkedHashSet<String>(entered);
        candidates.removeAll(emitted);
        if (candidates.isEmpty()) return null;
        emitted.addAll(candidates);
        lastClaimTick = tick;
        return join(candidates);
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
