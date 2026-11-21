package industries.angelic.pastfootsteps.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import industries.angelic.pastfootsteps.world.SurfaceResolver.Surface;

/** Optional client-side adapter for a concrete mod entity that provides a walkable collision surface. */
@FunctionalInterface
public interface SurfaceSupportProvider {

    /**
     * Called only after the resolver has verified that the foot probe reaches the candidate's collision box top.
     * Return a grounded surface based on that actual collision geometry, or {@code null} to fall back to the next rule.
     */
    Surface resolve(EntityLivingBase actor, Entity support, double footX, double feetY, double footZ);
}
