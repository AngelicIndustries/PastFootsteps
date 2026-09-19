package org.fentanylsolutions.pastfootsteps.sound;

import java.util.HashSet;
import java.util.Set;

import org.fentanylsolutions.pastfootsteps.movement.Contact;
import org.fentanylsolutions.pastfootsteps.movement.ContactKind;

/** Audio selection over physical contacts; never removes contacts from footprints or integrations. */
public final class ImpactSounds {

    private long generation = Long.MIN_VALUE, group = Long.MIN_VALUE;
    private final Set<String> layers = new HashSet<>();

    public static boolean impact(ContactKind kind) {
        return kind.isJump() || kind.isLanding();
    }

    public boolean select(Contact contact, long currentGeneration) {
        if (!impact(contact.kind)) return true;
        boolean first = generation != currentGeneration || group != contact.group;
        if (first) {
            generation = currentGeneration;
            group = contact.group;
            layers.clear();
        }
        return first || contact.kind == ContactKind.JUMP_STILL || contact.kind == ContactKind.LAND;
    }

    /** A grouped impact plays each distinct surface/layer once, including split-material landings. */
    public boolean claim(String layer, String material) {
        return layers.add(layer + ":" + material);
    }
}
