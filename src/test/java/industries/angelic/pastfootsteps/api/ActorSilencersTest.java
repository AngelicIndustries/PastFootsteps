package industries.angelic.pastfootsteps.api;

import static org.junit.Assert.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;

import org.junit.Test;

public class ActorSilencersTest {

    @Test
    public void silencesOnlyWhatAPredicateNamesUntilClosed() throws Exception {
        Entity cow = new EntityCow(null), pig = new EntityPig(null);
        assertFalse(ActorSilencers.isSilenced(cow));
        AutoCloseable handle = ActorSilencers.register(actor -> actor instanceof EntityCow);
        try {
            assertTrue(ActorSilencers.isSilenced(cow));
            assertFalse(ActorSilencers.isSilenced(pig));
            assertFalse(ActorSilencers.isSilenced(null));
        } finally {
            handle.close();
        }
        assertFalse(ActorSilencers.isSilenced(cow));
    }

    @Test
    public void aFailingPredicateIsRemoved() throws Exception {
        Entity cow = new EntityCow(null);
        int[] calls = { 0 };
        AutoCloseable failing = ActorSilencers.register(actor -> {
            calls[0]++;
            throw new IllegalStateException("broken integration");
        });
        AutoCloseable working = ActorSilencers.register(actor -> true);
        try {
            assertTrue(ActorSilencers.isSilenced(cow));
            assertTrue(ActorSilencers.isSilenced(cow));
            assertEquals(1, calls[0]);
        } finally {
            failing.close();
            working.close();
        }
        assertFalse(ActorSilencers.isSilenced(cow));
    }
}
