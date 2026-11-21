package industries.angelic.pastfootsteps.api;

import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import industries.angelic.pastfootsteps.world.SurfaceResolver.Surface;

/** Small opt-in registry for integrations with known walkable entity supports. */
public final class SurfaceSupportProviders {

    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<Entry>();

    private SurfaceSupportProviders() {}

    /** Registers one concrete entity class. Closing the returned handle removes only this registration. */
    public static AutoCloseable register(Class<? extends Entity> entityClass, SurfaceSupportProvider provider) {
        if (entityClass == null || provider == null)
            throw new IllegalArgumentException("entityClass and provider are required");
        Entry entry = new Entry(entityClass, provider);
        ENTRIES.addIfAbsent(entry);
        return () -> ENTRIES.remove(entry);
    }

    /** Resolver hook. Provider failures are isolated by SurfaceResolver. */
    public static Surface resolve(EntityLivingBase actor, Entity support, double footX, double feetY, double footZ) {
        for (Entry entry : ENTRIES) if (entry.entityClass.isInstance(support))
            return entry.provider.resolve(actor, support, footX, feetY, footZ);
        return null;
    }

    public static boolean handles(Entity support) {
        for (Entry entry : ENTRIES) if (entry.entityClass.isInstance(support)) return true;
        return false;
    }

    private static final class Entry {

        final Class<? extends Entity> entityClass;
        final SurfaceSupportProvider provider;

        Entry(Class<? extends Entity> entityClass, SurfaceSupportProvider provider) {
            this.entityClass = entityClass;
            this.provider = provider;
        }
    }
}
