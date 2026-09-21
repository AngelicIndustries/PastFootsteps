package org.fentanylsolutions.pastfootsteps.client.compat;

import static org.junit.Assert.*;

import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntitySpider;

import org.fentanylsolutions.pastfootsteps.profile.EntityProfile;
import org.fentanylsolutions.pastfootsteps.profile.GaitDefinition;
import org.junit.Test;

public class EvenMoBendsCompatTest {

    @Test
    public void regularSpiderAdaptsOnlyWhileItsRendererIsActiveAndKeepsProfileTuning() {
        EvenMoBendsCompat compat = new EvenMoBendsCompat();
        EntitySpider spider = new EntitySpider(null);
        EntityProfile original = profile("spider");
        GaitDefinition replacement = gait("bends_spider", 0.8);
        assertSame(original, compat.adapt(spider, original, replacement, false));
        EntityProfile adapted = compat.adapt(spider, original, replacement, true);
        assertSame(replacement, adapted.gait);
        assertSame(original.selector, adapted.selector);
        assertEquals(original.id, adapted.id);
        assertEquals(original.materialMap, adapted.materialMap);
        assertEquals(original.strideMultiplier, adapted.strideMultiplier, 0);
        assertEquals(original.volume, adapted.volume, 0);
        assertEquals(original.runSpeed, adapted.runSpeed, 0);
        assertEquals(original.referenceWidth, adapted.referenceWidth, 0);
        assertEquals(original.childScale, adapted.childScale, 0);
        assertSame(adapted, compat.adapt(spider, original, replacement, true));
        assertSame(original, compat.adapt(spider, original, replacement, false));
    }

    @Test
    public void subclassesAndExplicitCustomGaitsRemainUntouched() {
        EvenMoBendsCompat compat = new EvenMoBendsCompat();
        GaitDefinition replacement = gait("bends_spider", 0.8);
        EntityProfile original = profile("spider");
        assertSame(original, compat.adapt(new EntityCaveSpider(null) {}, original, replacement, true));
        assertSame(original, compat.adapt(new EntitySpider(null) {}, original, replacement, true));
        EntityProfile custom = profile("pack_spider");
        assertSame(custom, compat.adapt(new EntitySpider(null), custom, replacement, true));
        assertSame(original, compat.adapt(new EntitySpider(null), original, null, true));
        assertNull(compat.adapt(new EntitySpider(null), null, replacement, true));
    }

    @Test
    public void caveSpiderSelectsItsScaledLayoutWhilePreservingItsQuieterShorterStride() {
        EvenMoBendsCompat compat = new EvenMoBendsCompat();
        EntityCaveSpider actor = new EntityCaveSpider(null);
        EntityProfile original = new EntityProfile("CaveSpider", gait("spider", 0.4), 0.65, 0.5, 0.2, 0.3F, 0.7);
        assertEquals("bends_cave_spider", EvenMoBendsCompat.layoutId(actor));
        assertEquals("bends_spider", EvenMoBendsCompat.layoutId(new EntitySpider(null)));
        GaitDefinition replacement = gait(EvenMoBendsCompat.layoutId(actor), 1.064);
        assertSame(original, compat.adapt(actor, original, replacement, false));
        EntityProfile result = compat.adapt(actor, original, replacement, true);
        assertSame(replacement, result.gait);
        assertEquals(0.65, result.strideMultiplier, 0);
        assertEquals(0.3F, result.volume, 0);
        assertEquals(0.7, result.referenceWidth, 0);
        assertSame(original, compat.adapt(actor, original, replacement, false));
    }

    @Test
    public void reloadAndResetCannotReuseStaleLayoutOrProfileIdentity() {
        EvenMoBendsCompat compat = new EvenMoBendsCompat();
        EntitySpider spider = new EntitySpider(null);
        EntityProfile original = profile("spider");
        GaitDefinition first = gait("bends_spider", 0.8);
        EntityProfile adapted = compat.adapt(spider, original, first, true);
        GaitDefinition reloaded = gait("bends_spider", 0.9);
        EntityProfile next = compat.adapt(spider, original, reloaded, true);
        assertNotSame(adapted, next);
        assertSame(reloaded, next.gait);
        compat.clear();
        assertNotSame(next, compat.adapt(spider, original, reloaded, true));
        EntityProfile edited = profile("spider");
        assertNotSame(next, compat.adapt(spider, edited, reloaded, true));
    }

    private static EntityProfile profile(String gait) {
        return new EntityProfile("Spider", gait(gait, 0.4), 1.3, 0.6, 0.24, 0.3F, 1.4, "custom");
    }

    private static GaitDefinition gait(String id, double lateral) {
        GaitDefinition.Contact[] feet = { new GaitDefinition.Contact("left", 0.5, lateral, 0),
            new GaitDefinition.Contact("right", 1, -lateral, 0) };
        return new GaitDefinition(id, feet, feet);
    }
}
