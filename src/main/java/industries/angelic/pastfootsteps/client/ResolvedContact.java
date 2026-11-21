package industries.angelic.pastfootsteps.client;

import net.minecraft.entity.EntityLivingBase;

import industries.angelic.pastfootsteps.movement.Contact;
import industries.angelic.pastfootsteps.profile.EntityProfile;
import industries.angelic.pastfootsteps.world.SurfaceResolver.Surface;

/** Shared contact data for the audio consumer and diagnostics; footprints and equipment consume this same value. */
public final class ResolvedContact {

    public final EntityLivingBase actor;
    public final EntityProfile profile;
    public final Contact contact;
    public final Surface surface;
    public final boolean rainExposed, snowExposed;

    ResolvedContact(EntityLivingBase actor, EntityProfile profile, Contact contact, Surface surface) {
        this.actor = actor;
        this.profile = profile;
        this.contact = contact;
        this.surface = surface;
        net.minecraft.world.World world = actor.worldObj;
        int x = (int) Math.floor(surface.contactX), y = (int) Math.floor(surface.contactY + 0.1),
            z = (int) Math.floor(surface.contactZ);
        boolean exposed = world != null && world.blockExists(x, y, z)
            && world.isRaining()
            && !actor.isInWater()
            && world.canBlockSeeTheSky(x, y, z);
        net.minecraft.world.biome.BiomeGenBase biome = exposed ? world.getBiomeGenForCoords(x, z) : null;
        snowExposed = biome != null
            && (biome.getEnableSnow() || biome.canSpawnLightningBolt() && biome.getFloatTemperature(x, y, z) < 0.15f);
        rainExposed = biome != null && !snowExposed && biome.canSpawnLightningBolt();
    }
}
