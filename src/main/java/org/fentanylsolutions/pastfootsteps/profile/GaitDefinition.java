package org.fentanylsolutions.pastfootsteps.profile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable contact timing and normalized foot placement for one gait. */
public final class GaitDefinition {

    private static final int MAX_CONTACTS = 16;
    private static final double MAX_OFFSET = 4.0;
    static final double MIN_PHASE_GAP = 1.0E-6;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    public final String id;
    private final Contact[] walkingContacts;
    private final Contact[] runningContacts;

    public GaitDefinition(String id, Contact[] walkingContacts, Contact[] runningContacts) {
        requireId(id, "id");
        this.id = id;
        this.walkingContacts = sortedCopy(walkingContacts);
        this.runningContacts = sortedCopy(runningContacts);
        if (!footIds(this.walkingContacts).equals(footIds(this.runningContacts)))
            throw new IllegalArgumentException("walking and running contacts must use the same foot IDs");
    }

    /** Returns contacts sorted by ascending phase. The returned array may be freely modified. */
    public Contact[] contacts(boolean running) {
        Contact[] contacts = running ? runningContacts : walkingContacts;
        return contacts.clone();
    }

    private static Contact[] sortedCopy(Contact[] contacts) {
        if (contacts == null) throw new IllegalArgumentException("contacts must not be null");
        if (contacts.length < 1 || contacts.length > MAX_CONTACTS)
            throw new IllegalArgumentException("a contact cycle must contain 1 to " + MAX_CONTACTS + " contacts");
        Contact[] copy = contacts.clone();
        Set<String> footIds = new HashSet<String>();
        for (Contact contact : copy) {
            if (contact == null) throw new IllegalArgumentException("contacts must not contain null");
            if (!footIds.add(contact.footId))
                throw new IllegalArgumentException("duplicate foot ID in contact cycle: " + contact.footId);
        }
        Arrays.sort(copy, new Comparator<Contact>() {

            @Override
            public int compare(Contact left, Contact right) {
                return Double.compare(left.phase, right.phase);
            }
        });
        validatePhaseGaps(copy);
        return copy;
    }

    private static void validatePhaseGaps(Contact[] contacts) {
        double first = contacts[0].phase;
        for (int index = 1; index < contacts.length; index++) {
            double gap = contacts[index].phase - contacts[index - 1].phase;
            if (gap > 0.0 && gap < MIN_PHASE_GAP) throw new IllegalArgumentException(
                "distinct contact phases must be at least " + MIN_PHASE_GAP + " apart");
        }
        double wrapGap = first + 1.0 - contacts[contacts.length - 1].phase;
        if (wrapGap < MIN_PHASE_GAP) throw new IllegalArgumentException(
            "contact phases across the cycle boundary must be at least " + MIN_PHASE_GAP + " apart");
    }

    private static Set<String> footIds(Contact[] contacts) {
        Set<String> ids = new HashSet<String>();
        for (Contact contact : contacts) ids.add(contact.footId);
        return ids;
    }

    private static void requireId(String id, String field) {
        if (id == null || !ID.matcher(id)
            .matches()) throw new IllegalArgumentException(field + " must be a sane ID");
    }

    private static void requireFinite(double value, double minimum, double maximum, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException(field + " must be finite and between " + minimum + " and " + maximum);
    }

    /** Foot offsets are normalized values which callers multiply by the entity width. */
    public static final class Contact {

        public final String footId;
        public final double phase;
        public final double lateral;
        public final double forward;

        public Contact(String footId, double phase, double lateral, double forward) {
            requireId(footId, "footId");
            requireFinite(phase, MIN_PHASE_GAP, 1.0, "phase");
            requireFinite(lateral, -MAX_OFFSET, MAX_OFFSET, "lateral");
            requireFinite(forward, -MAX_OFFSET, MAX_OFFSET, "forward");
            this.footId = footId;
            this.phase = phase;
            this.lateral = lateral;
            this.forward = forward;
        }
    }
}
