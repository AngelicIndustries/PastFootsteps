package org.fentanylsolutions.pastfootsteps.client.compat;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntitySpider;

import org.fentanylsolutions.pastfootsteps.profile.EntityProfile;
import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition;

/** Optional renderer-aware spider layout. No Even Mo' Bends classes are linked or initialized. */
public final class EvenMoBendsCompat {

    private static final String SPIDER_RENDERER = "net.gobbob.mobends.client.renderer.entity.RenderBendsSpider";
    private static final String CAVE_RENDERER = "net.gobbob.mobends.client.renderer.entity.RenderBendsCaveSpider";
    private final Map<EntityProfile, EntityProfile> adapted = new IdentityHashMap<>();

    public EntityProfile adapt(EntityLivingBase actor, EntityProfile source, GaitDefinition replacement) {
        if (!eligible(actor, source, replacement)) return source;
        String expectedRenderer = actor.getClass() == EntityCaveSpider.class ? CAVE_RENDERER : SPIDER_RENDERER;
        boolean active = false;
        try {
            Object renderer = RenderManager.instance.getEntityRenderObject(actor);
            // The actual renderer also accounts for disabled animations or another mod taking ownership.
            for (Class<?> type = renderer == null ? null : renderer.getClass(); type
                != null; type = type.getSuperclass()) {
                if (expectedRenderer.equals(type.getName())) {
                    active = true;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Optional rendering failures must not silence the ordinary PF gait.
        }
        return adapt(actor, source, replacement, active);
    }

    EntityProfile adapt(EntityLivingBase actor, EntityProfile source, GaitDefinition replacement, boolean active) {
        if (!active || !eligible(actor, source, replacement)) return source;
        EntityProfile result = adapted.get(source);
        if (result == null || result.gait != replacement) {
            result = source.withGait(replacement);
            adapted.put(source, result);
        }
        return result;
    }

    /** Layout offsets are normalized by hitbox width, which differs from the cave renderer's scale. */
    public static String layoutId(EntityLivingBase actor) {
        return actor != null && actor.getClass() == EntityCaveSpider.class ? "bends_cave_spider" : "bends_spider";
    }

    private static boolean eligible(EntityLivingBase actor, EntityProfile source, GaitDefinition replacement) {
        // Only the two known vanilla models opt in; modded subclasses keep their own layout.
        return actor != null && (actor.getClass() == EntitySpider.class || actor.getClass() == EntityCaveSpider.class)
            && source != null
            && "spider".equals(source.gait.id)
            && replacement != null;
    }

    public void clear() {
        adapted.clear();
    }
}
