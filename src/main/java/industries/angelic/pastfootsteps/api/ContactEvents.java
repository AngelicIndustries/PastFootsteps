package industries.angelic.pastfootsteps.api;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import industries.angelic.pastfootsteps.PastFootsteps;
import industries.angelic.pastfootsteps.client.ResolvedContact;

/** Client-thread observation of resolved cosmetic contacts. Not an authoritative gameplay event. */
public final class ContactEvents {

    private static final CopyOnWriteArrayList<Consumer<ResolvedContact>> LISTENERS = new CopyOnWriteArrayList<>();

    private ContactEvents() {}

    /** Register once during client initialization. Close the returned handle to unregister. */
    public static AutoCloseable listen(Consumer<ResolvedContact> listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        LISTENERS.addIfAbsent(listener);
        return () -> LISTENERS.remove(listener);
    }

    /** Engine dispatch. A failed integration is removed so it cannot flood the log every step. */
    public static void publish(ResolvedContact contact) {
        for (Consumer<ResolvedContact> listener : LISTENERS) {
            try {
                listener.accept(contact);
            } catch (RuntimeException error) {
                LISTENERS.remove(listener);
                PastFootsteps.LOG.error("Disabled failing PF contact listener", error);
            }
        }
    }
}
