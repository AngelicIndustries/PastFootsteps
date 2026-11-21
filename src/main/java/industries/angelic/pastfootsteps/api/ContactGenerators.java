package industries.angelic.pastfootsteps.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import net.minecraft.entity.EntityLivingBase;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.movement.ContactGenerator;
import industries.angelic.pastfootsteps.movement.ContactTracker;
import industries.angelic.pastfootsteps.profile.EntityProfile;

/** Explicit client-side generators for a named profile gait. */
public final class ContactGenerators {

    private static final Pattern GAIT_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final ConcurrentMap<String, Factory> FACTORIES = new ConcurrentHashMap<String, Factory>();

    private ContactGenerators() {}

    @FunctionalInterface
    public interface Factory {

        /** Construct a new generator for one tracked actor. Returning null selects vanilla fallback. */
        ContactGenerator create(EntityLivingBase actor, EntityProfile profile);
    }

    /**
     * Registers the sole generator factory for an exact gait ID. Closing the returned handle removes
     * only this registration. Registrations must occur during client initialization.
     */
    public static AutoCloseable register(String gaitId, Factory factory) {
        if (gaitId == null || !GAIT_ID.matcher(gaitId)
            .matches()) throw new IllegalArgumentException("gaitId must be a sane exact gait ID");
        if (factory == null) throw new IllegalArgumentException("factory");
        if (FACTORIES.putIfAbsent(gaitId, factory) != null)
            throw new IllegalStateException("A ContactGenerator factory is already registered for " + gaitId);
        return () -> FACTORIES.remove(gaitId, factory);
    }

    /**
     * Creates the exact gait's registered generator, or the standard distance-based tracker when
     * absent, failing, or declined. Runtime update failures are deliberately left to the caller.
     */
    public static ContactGenerator create(EntityLivingBase actor, EntityProfile profile) {
        if (actor == null) throw new IllegalArgumentException("actor");
        if (profile == null) throw new IllegalArgumentException("profile");
        Factory factory = FACTORIES.get(profile.gait.id);
        if (factory != null) try {
            ContactGenerator generator = factory.create(actor, profile);
            if (generator != null) return generator;
        } catch (RuntimeException error) {
            PastFootsteps.LOG
                .warn("ContactGenerator factory failed for gait " + profile.gait.id + "; using vanilla tracker", error);
        }
        return new ContactTracker(profile.gait, actor.getEntityId());
    }
}
