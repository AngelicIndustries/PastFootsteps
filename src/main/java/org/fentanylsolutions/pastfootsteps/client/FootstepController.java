package org.fentanylsolutions.pastfootsteps.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;

import org.fentanylsolutions.pastfootsteps.PastFootsteps;
import org.fentanylsolutions.pastfootsteps.api.ContactGenerators;
import org.fentanylsolutions.pastfootsteps.config.Config;
import org.fentanylsolutions.pastfootsteps.equipment.EquipmentSounds;
import org.fentanylsolutions.pastfootsteps.footprint.Footprints;
import org.fentanylsolutions.pastfootsteps.movement.Contact;
import org.fentanylsolutions.pastfootsteps.movement.ContactGenerator;
import org.fentanylsolutions.pastfootsteps.movement.ContactKind;
import org.fentanylsolutions.pastfootsteps.movement.ContactTracker;
import org.fentanylsolutions.pastfootsteps.network.MotionFrame;
import org.fentanylsolutions.pastfootsteps.network.RemoteMotion;
import org.fentanylsolutions.pastfootsteps.profile.EntityProfile;
import org.fentanylsolutions.pastfootsteps.profile.ProfileLibrary;
import org.fentanylsolutions.pastfootsteps.sound.AcousticContext;
import org.fentanylsolutions.pastfootsteps.sound.ActorSound;
import org.fentanylsolutions.pastfootsteps.sound.ImpactSounds;
import org.fentanylsolutions.pastfootsteps.sound.LandingSoundGate;
import org.fentanylsolutions.pastfootsteps.sound.SoundEngine;
import org.fentanylsolutions.pastfootsteps.sound.SoundNames;
import org.fentanylsolutions.pastfootsteps.sound.SourceSoundScale;
import org.fentanylsolutions.pastfootsteps.sound.SwimmingSounds;
import org.fentanylsolutions.pastfootsteps.world.MaterialLibrary;
import org.fentanylsolutions.pastfootsteps.world.SurfaceResolver;
import org.fentanylsolutions.pastfootsteps.world.SurfaceResolver.Surface;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class FootstepController implements IResourceManagerReloadListener {

    public static final FootstepController INSTANCE = new FootstepController();
    private final Footprints footprints = new Footprints();
    private final EquipmentSounds equipment = new EquipmentSounds(this::emit);
    private final MaterialLibrary materials = new MaterialLibrary();
    private final SurfaceResolver resolver = new SurfaceResolver(materials);
    private final ProfileLibrary profiles = new ProfileLibrary();
    private final SoundEngine sound = new SoundEngine(this::emit);
    private final Map<EntityLivingBase, ActorState> actors = new LinkedHashMap<>();
    private final ContactDebugRenderer contactDebug = new ContactDebugRenderer();
    private World world;
    private int discoveryTicks, voicesThisTick;

    private FootstepController() {}

    /** Native suppression and contact production share the same selected actor state. */
    public static boolean shouldReplace(Entity entity) {
        if (!Config.surfaceSounds || !INSTANCE.sound.ready() || !ownsContacts(entity)) return false;
        ActorState state = INSTANCE.actors.get(entity);
        return SwimmingSounds.canReplace(state.liquidMode, Config.swimming, INSTANCE.sound);
    }

    public static boolean ownsContacts(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || !INSTANCE.eligible((EntityLivingBase) entity)) return false;
        ActorState state = INSTANCE.actors.get(entity);
        return state != null && state.active && state.surface != null;
    }

    public static boolean suppressSound(Entity entity, String name) {
        if (!shouldReplace(entity) || name == null) return false;
        String path = name.startsWith("minecraft:") ? name.substring(10) : name;
        ActorState state = INSTANCE.actors.get(entity);
        if (path.startsWith("step.")) return true;
        if (path.equals("game.player.swim") || path.equals("game.neutral.swim"))
            return state != null && state.liquidMode != SwimmingSounds.Mode.NONE;
        return state != null && state.surface != null && sameSound(name, state.surface.vanillaSound);
    }

    public static boolean interceptSound(Entity entity, String name, float volume, float pitch) {
        ActorState state = INSTANCE.actors.get(entity);
        if (state != null && landingSound(state, name)) return INSTANCE.deferLandingSound(
            state,
            name,
            entity.posX,
            entity.posY - entity.yOffset,
            entity.posZ,
            volume,
            pitch,
            true);
        return suppressSound(entity, name);
    }

    private static boolean landingSound(ActorState state, String name) {
        long tick = state.actor.worldObj.getTotalWorldTime();
        if (LandingSoundGate.isFallSound(name)) {
            state.fallSounds.nativeFall(tick);
            return true;
        }
        String path = name != null && name.startsWith("minecraft:") ? name.substring(10) : name;
        return path != null && state.fallSounds.nativeImpactTick(tick)
            && (path.startsWith("step.") || state.surface != null && sameSound(name, state.surface.vanillaSound));
    }

    private boolean deferLandingSound(ActorState state, String name, double x, double y, double z, float volume,
        float pitch, boolean entityEvent) {
        if (!Config.jumpLand || !Config.surfaceSounds
            || !sound.ready()
            || !state.active
            || !state.initialized
            || !eligible(state.actor)) return false;
        World sourceWorld = state.actor.worldObj;
        return state.fallSounds.defer(sourceWorld.getTotalWorldTime(), state.generation, x, y, z, () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != sourceWorld) return;
            String restoredName = name;
            if (entityEvent) {
                PlaySoundAtEntityEvent event = new PlaySoundAtEntityEvent(state.actor, name, volume, pitch);
                if (MinecraftForge.EVENT_BUS.post(event)) return;
                restoredName = event.name;
            }
            mc.theWorld.playSound(x, y, z, restoredName, volume, pitch, false);
        });
    }

    private static boolean sameSound(String first, String second) {
        if (first == null || second == null) return false;
        return (first.startsWith("minecraft:") ? first.substring(10) : first)
            .equals(second.startsWith("minecraft:") ? second.substring(10) : second);
    }

    /** 1.7 packets have no actor ID. Ambiguous nearby actors deliberately retain their native sound. */
    public static boolean suppressRemoteSound(String name, double x, double y, double z, float volume, float pitch) {
        World world = Minecraft.getMinecraft().theWorld;
        if (!Config.enabled || !Config.surfaceSounds || !INSTANCE.sound.ready() || world == null || name == null)
            return false;
        Entity closest = null;
        double best = 1, second = Double.MAX_VALUE;
        List<?> candidates = world.getEntitiesWithinAABB(
            EntityLivingBase.class,
            AxisAlignedBB.getBoundingBox(x - 1, y - 1, z - 1, x + 1, y + 2, z + 1));
        for (Object value : candidates) {
            Entity entity = (Entity) value;
            double dx = entity.posX - x, dy = entity.boundingBox.minY - y, dz = entity.posZ - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                second = best;
                best = distance;
                closest = entity;
            } else if (distance < second) second = distance;
        }
        // Unknown mobs also participate in attribution, so their sounds cannot be claimed by a nearby player.
        ActorState state = INSTANCE.actors.get(closest);
        if (LandingSoundGate.isFallSound(name) && second - best <= 0.04) return false;
        if (state != null && landingSound(state, name)) {
            // An impact needs one specific owner; never assign ambiguous packets to a guessed actor.
            return second - best > 0.04 && INSTANCE.deferLandingSound(state, name, x, y, z, volume, pitch, false);
        }
        if (closest == null || !suppressSound(closest, name)) return false;
        if (second - best > 0.04) return true;
        // Attribution among several owned actors is harmless: all already have replacement contacts.
        // Preserve native audio if any equally plausible source is unknown or outside our ownership.
        for (Object value : candidates) {
            Entity entity = (Entity) value;
            double dx = entity.posX - x, dy = entity.boundingBox.minY - y, dz = entity.posZ - z;
            if (dx * dx + dy * dy + dz * dz <= best + 0.04 && !suppressSound(entity, name)) return false;
        }
        return true;
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        reset();
        profiles.reload(manager, Config.directory);
        materials.reload(manager, Config.directory, profiles.materialMaps());
        resolver.reloadSupports(manager, Config.directory);
        footprints.reload(manager, Config.directory);
        equipment.reload(manager, Config.directory);
        Set<String> ids = new HashSet<>();
        try {
            for (IResource resource : manager
                .getAllResources(new ResourceLocation("presencefootsteps", "config/acoustics/index.json"))) {
                try (InputStreamReader reader = new InputStreamReader(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8)) {
                    for (JsonElement id : new JsonParser().parse(reader)
                        .getAsJsonArray()) ids.add(id.getAsString());
                } catch (Exception e) {
                    PastFootsteps.LOG.warn("Skipping invalid acoustic index layer", e);
                }
            }
        } catch (Exception e) {
            PastFootsteps.LOG.error("Unable to load acoustic index", e);
        }
        sound.reload(manager, ids, Config.directory);
        PastFootsteps.LOG.info("Loaded {} acoustic definitions for PastFootsteps", ids.size());
    }

    public void reset() {
        for (ActorState state : actors.values()) {
            state.fallSounds.tick(state.actor.worldObj.getTotalWorldTime(), state.generation, false);
            state.active = false;
        }
        actors.clear();
        sound.clear();
        footprints.reset();
        equipment.reset();
        RemoteMotion.reset();
        contactDebug.clear();
        discoveryTicks = 0;
        world = null;
    }

    public String diagnosticReport() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("version", org.fentanylsolutions.pastfootsteps.Tags.VERSION);
        root.addProperty("enabled", Config.enabled);
        root.addProperty("surfaceSounds", Config.surfaceSounds);
        root.addProperty("serverSynchronization", RemoteMotion.available());
        root.addProperty("actorBudget", Config.maxEntities);
        root.addProperty("soundBudget", Config.maxSoundsPerTick);
        root.addProperty("activePrints", footprints.count());
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
            .create();
        root.add("profileDiagnostics", gson.toJsonTree(profiles.diagnostics()));
        root.add("equipmentDiagnostics", gson.toJsonTree(equipment.diagnostics()));
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        for (ActorState state : actors.values()) {
            com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
            entry.addProperty("entityId", state.actor.getEntityId());
            entry.addProperty("profile", state.profile.id);
            entry.addProperty("gait", state.profile.gait.id);
            entry.addProperty("materialMap", state.profile.materialMap);
            entry.addProperty("phase", state.tracker.phase());
            entry.addProperty("material", state.lastMaterial);
            entry.addProperty("liquidMode", state.liquidMode.name());
            entry.addProperty("surfaceAudioOwned", shouldReplace(state.actor));
            entry.addProperty("movementVolumeScale", state.soundScale.volume(Config.runningBoost));
            entry.addProperty("sizePitchScale", sizePitch(state));
            entry.addProperty(
                "soundCategory",
                ActorSound.category(state.actor instanceof EntityPlayer, state.actor instanceof IMob)
                    .getCategoryName());
            entry.addProperty("serverMotion", state.synchronizedMotion);
            entry.addProperty("support", state.support == null ? "block" : EntityList.getEntityString(state.support));
            if (state.lastContact != null) {
                entry.addProperty("foot", state.lastContact.foot.footId);
                entry.addProperty("kind", state.lastContact.kind.name());
            }
            entry.addProperty("droppedContacts", state.tracker.droppedContacts());
            entries.add(entry);
        }
        root.add("actors", entries);
        return gson.toJson(root) + "\n";
    }

    public String debugText() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity focus = mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
        ActorState state = actors.get(focus);
        if (state == null) {
            double best = Double.MAX_VALUE;
            for (ActorState candidate : actors.values()) {
                if (candidate.actor == mc.thePlayer) continue;
                double distance = candidate.actor.getDistanceSqToEntity(mc.thePlayer);
                if (distance < best) {
                    best = distance;
                    state = candidate;
                }
            }
        }
        if (state == null) state = actors.get(mc.thePlayer);
        if (state == null) return "No supported actors nearby";
        Contact contact = state.lastContact;
        return actors.size() + "/"
            + Config.maxEntities
            + " actors | "
            + state.profile.id
            + "#"
            + state.actor.getEntityId()
            + " / "
            + state.profile.gait.id
            + " / "
            + state.tracker.kind()
            + " / phase "
            + String.format(java.util.Locale.ROOT, "%.2f", state.tracker.phase())
            + (contact == null ? " / waiting" : " / " + contact.foot.footId + " / " + state.lastMaterial)
            + " / dropped "
            + state.tracker.droppedContacts();
    }

    public void renderContacts(float partialTicks) {
        footprints.render(partialTicks);
        contactDebug.render(partialTicks);
    }

    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld) {
            reset();
            world = mc.theWorld;
        }
        if (mc.theWorld == null || mc.thePlayer == null || !Config.enabled) {
            if (!actors.isEmpty()) reset();
            return;
        }
        if (mc.isGamePaused()) return;
        voicesThisTick = 0;
        sound.beginTick();
        contactDebug.tick();
        footprints.tick(world);
        if (discoveryTicks-- <= 0) {
            discover();
            discoveryTicks = 4;
        }
        Iterator<ActorState> iterator = actors.values()
            .iterator();
        while (iterator.hasNext()) {
            ActorState state = iterator.next();
            if (!eligible(state.actor)) {
                state.fallSounds.tick(world.getTotalWorldTime(), state.generation, false);
                state.active = false;
                iterator.remove();
                continue;
            }
            if (state.child != state.actor.isChild()) {
                state.child = state.actor.isChild();
                equipment.forget(state.actor);
                state.tracker = ContactGenerators.create(state.actor, state.profile);
                state.generation++;
                state.initialized = false;
            }
            update(state);
            state.fallSounds.tick(
                world.getTotalWorldTime(),
                state.generation,
                Config.jumpLand && Config.surfaceSounds && sound.ready());
        }
        // Validate delayed owners after this tick's removals, teleports and dimension changes.
        sound.tick();
    }

    private boolean eligible(EntityLivingBase actor) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Config.enabled || mc.theWorld == null
            || mc.thePlayer == null
            || actor.worldObj != mc.theWorld
            || !actor.isEntityAlive()
            || actor.ridingEntity != null
            || !Double.isFinite(actor.posX)
            || !Double.isFinite(actor.posY)
            || !Double.isFinite(actor.posZ)
            || !actor.worldObj.blockExists((int) Math.floor(actor.posX), 64, (int) Math.floor(actor.posZ))
            || actor.getDistanceSqToEntity(mc.thePlayer) > Config.range * Config.range) return false;
        if (actor instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) actor;
            return (actor == mc.thePlayer || Config.otherPlayers) && !player.capabilities.isFlying
                && !player.isPlayerSleeping();
        }
        return Config.mobs;
    }

    private EntityProfile profile(EntityLivingBase actor) {
        return profiles.resolve(actor);
    }

    private void discover() {
        Minecraft mc = Minecraft.getMinecraft();
        List<EntityLivingBase> selected = new ArrayList<>();
        if (eligible(mc.thePlayer) && profile(mc.thePlayer) != null) selected.add(mc.thePlayer);
        List<EntityLivingBase> candidates = new ArrayList<>();
        for (Object value : world.getEntitiesWithinAABB(
            EntityLivingBase.class,
            mc.thePlayer.boundingBox.expand(Config.range, Config.range, Config.range))) {
            EntityLivingBase actor = (EntityLivingBase) value;
            if (actor != mc.thePlayer && eligible(actor) && profile(actor) != null) candidates.add(actor);
        }
        // A small retention preference avoids replacing actors on every range/cap boundary crossing.
        candidates.sort(
            Comparator.comparingDouble(
                actor -> actor.getDistanceSqToEntity(mc.thePlayer) * (actors.containsKey(actor) ? 0.9 : 1)));
        for (EntityLivingBase actor : candidates) {
            if (selected.size() >= Config.maxEntities) break;
            selected.add(actor);
        }
        Map<EntityLivingBase, ActorState> ordered = new LinkedHashMap<>();
        for (EntityLivingBase actor : selected) {
            ActorState existing = actors.get(actor);
            EntityProfile selectedProfile = profile(actor);
            if (existing != null && existing.profile != selectedProfile) {
                existing.active = false;
                existing = null;
            }
            if (existing == null) equipment.forget(actor);
            ordered.put(actor, existing != null ? existing : new ActorState(actor, selectedProfile));
        }
        for (ActorState state : actors.values()) if (ordered.get(state.actor) != state) {
            state.fallSounds.tick(world.getTotalWorldTime(), state.generation, false);
            state.active = false;
        }
        actors.clear();
        actors.putAll(ordered);
    }

    private void update(ActorState state) {
        EntityLivingBase actor = state.actor;
        MotionFrame motion = actor == Minecraft.getMinecraft().thePlayer || state.support != null ? null
            : RemoteMotion.sample(actor);
        boolean synchronizedMotion = motion != null;
        if (state.synchronizedMotion != synchronizedMotion
            || motion != null && state.motionGeneration != motion.generation) {
            equipment.forget(actor);
            state.tracker = ContactGenerators.create(actor, state.profile);
            state.generation++;
            state.initialized = false;
            state.synchronizedMotion = synchronizedMotion;
        }
        if (motion != null) state.motionGeneration = motion.generation;
        double motionX = motion == null ? actor.posX : motion.x;
        double motionZ = motion == null ? actor.posZ : motion.z;
        double motionY = motion == null ? actor.boundingBox.minY : motion.feetY;
        Surface current = resolver.resolveAt(actor, motionX, motionY, motionZ, 0, 0, state.profile.materialMap);
        if (motion == null && actor != Minecraft.getMinecraft().thePlayer && (current == null || !current.grounded)) {
            current = remoteSupport(state, current);
        }
        state.surface = current;
        boolean grounded = motion != null ? motion.grounded
            : actor == Minecraft.getMinecraft().thePlayer ? actor.onGround : current != null && current.grounded;
        boolean inLava = !actor.isInWater() && resolver.inLava(actor);
        boolean swimming = motion != null ? motion.swimming : actor.isInWater() || inLava;
        state.liquidMode = SwimmingSounds
            .mode(swimming, inLava, current != null && current.grounded, actor.isInsideOfMaterial(Material.water));
        BiomeGenBase biome = actor.worldObj
            .getBiomeGenForCoords((int) Math.floor(actor.posX), (int) Math.floor(actor.posZ));
        state.pitch += (materials.biomePitch(biome) - state.pitch) * 0.05f;
        state.volume += (materials.biomeVolume(biome) - state.volume) * 0.05f;
        state.foliage = Config.surfaceSounds && Config.foliage ? resolver.foliage(actor, state.profile.materialMap)
            : null;
        state.stepped = false;
        double size = state.child ? state.profile.childScale : 1;
        double feetY = motion == null && actor != Minecraft.getMinecraft().thePlayer && grounded && current != null
            ? current.contactY
            : motionY;
        Entity support = current == null ? null : current.supportEntity;
        if (support != state.support) {
            equipment.forget(actor);
            state.tracker = ContactGenerators.create(actor, state.profile);
            state.generation++;
            state.initialized = false;
            state.support = support;
        }
        double contactX = support == null ? motionX : motionX - support.posX;
        double contactY = support == null ? feetY : feetY - support.posY;
        double contactZ = support == null ? motionZ : motionZ - support.posZ;
        state.soundScale.update(contactX, contactY, contactZ, state.generation, state.profile.runSpeed);
        int[] acceptedContacts = { 0 };
        try {
            state.tracker.update(
                contactX,
                contactY,
                contactZ,
                grounded,
                motion == null ? actor.isOnLadder() : motion.climbing,
                swimming,
                false, // Physical contacts still exist when sneaking; audio consumers filter them below.
                motion == null ? actor.isSprinting() : motion.sprinting,
                Config.stride * 2 * state.profile.strideMultiplier * size,
                Config.ladderStride * size,
                state.profile.runSpeed,
                true, // Landing contacts also feed footprints when landing audio is disabled.
                Config.wander,
                Config.stationary,
                contact -> {
                    if (acceptedContacts[0]++ < ContactTracker.MAX_CONTACTS_PER_TICK) resolveContact(state, contact);
                });
        } catch (RuntimeException error) {
            PastFootsteps.LOG.error("Reset failed contact generator for " + state.profile.id, error);
            equipment.forget(actor);
            state.tracker = new ContactTracker(state.profile.gait, actor.getEntityId());
            state.generation++;
            state.initialized = false;
            state.lastContact = null;
            return;
        }
        if (state.tracker.rebaselined()) {
            equipment.forget(actor);
            state.generation++;
            state.soundScale.update(contactX, contactY, contactZ, state.generation, state.profile.runSpeed);
            state.lastContact = null;
        }
        double dx = actor.posX - state.lastX, dz = actor.posZ - state.lastZ;
        if (state.initialized && !state.tracker.rebaselined()
            && dx * dx + dz * dz > 0.00001
            && !actor.isSneaking()
            && state.foliage != null
            && !state.foliage.equals(state.lastFoliage)
            && !state.stepped) {
            playMaterial(
                state,
                state.foliage,
                "walk",
                Config.foliageVolume,
                actor.posX,
                actor.boundingBox.minY,
                actor.posZ);
        }
        state.lastFoliage = state.foliage;
        state.lastSurface = current;
        state.lastX = actor.posX;
        state.lastZ = actor.posZ;
        state.initialized = true;
    }

    private Surface remoteSupport(ActorState state, Surface fallback) {
        EntityLivingBase actor = state.actor;
        if (!state.initialized || state.lastSurface == null || !state.lastSurface.grounded || actor.isInWater())
            return fallback;
        double targetX = actor.serverPosX / 32.0,
            targetY = actor.serverPosY / 32.0 + actor.boundingBox.minY - actor.posY, targetZ = actor.serverPosZ / 32.0;
        double dx = targetX - actor.posX, dy = targetY - actor.boundingBox.minY, dz = targetZ - actor.posZ;
        double movedX = actor.posX - state.lastX, movedZ = actor.posZ - state.lastZ;
        if (dx * dx + dz * dz > 2.25 || Math.abs(dy) > 0.625 || movedX * movedX + movedZ * movedZ < 0.00001)
            return fallback;
        // 1.7 movement packets have no ground bit. Confirm the interpolated step's destination against geometry.
        Surface target = resolver.resolveAt(actor, targetX, targetY, targetZ, 0, 0, state.profile.materialMap);
        return target != null && target.grounded && Math.abs(target.contactY - state.lastSurface.contactY) <= 0.625
            ? target
            : fallback;
    }

    private void resolveContact(ActorState state, Contact contact) {
        if (state.support != null) {
            contact = new Contact(
                contact.kind,
                contact.foot,
                contact.sequence,
                contact.group,
                contact.groupSize,
                contact.x + state.support.posX,
                contact.y + state.support.posY,
                contact.z + state.support.posZ,
                contact.phase,
                contact.speed);
        }
        double width = state.actor.width;
        Surface surface = resolver.resolveAt(
            state.actor,
            contact.x,
            contact.y,
            contact.z,
            contact.foot.lateral * width,
            contact.foot.forward * width,
            state.profile.materialMap);
        if (surface == null && state.surface != null && state.surface.grounded && !contact.kind.isJump()) {
            surface = resolver.resolveAt(
                state.actor,
                contact.x,
                state.surface.contactY,
                contact.z,
                contact.foot.lateral * width,
                contact.foot.forward * width,
                state.profile.materialMap);
        }
        if (surface == null) surface = contact.kind.isJump() ? state.lastSurface : state.surface;
        if (surface == null) return;
        ResolvedContact resolved = new ResolvedContact(state.actor, state.profile, contact, surface);
        state.lastContact = contact;
        state.lastMaterial = surface.material;
        state.stepped = true;
        consumeContact(state, resolved);
    }

    private void consumeContact(ActorState state, ResolvedContact resolved) {
        Contact contact = resolved.contact;
        Surface surface = resolved.surface;
        footprints.contact(resolved);
        contactDebug.add(resolved);
        org.fentanylsolutions.pastfootsteps.api.ContactEvents.publish(resolved);
        if (resolved.actor.isSneaking() && !state.liquidMode.swimming() && contact.kind != ContactKind.LAND) return;
        boolean impact = ImpactSounds.impact(contact.kind);
        if (!Config.jumpLand && impact) return;
        boolean selected = state.impacts.select(contact, state.generation);
        float groupVolume = impact ? 1 : (float) (1 / Math.sqrt(contact.groupSize));
        equipment.play(
            resolved,
            context(state, groupVolume, surface.contactX, surface.contactY, surface.contactZ),
            selected && (!impact || state.impacts.claim("equipment", "impact")));
        if (!selected || !Config.surfaceSounds || !sound.ready()) return;
        if (state.liquidMode.swimming()) {
            consumeSwimming(state, resolved);
            return;
        }
        String event = contact.kind.acousticKey;
        boolean nativeSurface = surface.material == null || SurfaceResolver.emitter(surface.material)
            && !(impact ? canPlay(surface.material, event) : canPlay(surface.material));
        String surfaceKey = nativeSurface
            ? surface.vanillaSound + ":" + surface.vanillaVolume + ":" + surface.vanillaPitch
            : surface.material;
        if (!impact || state.impacts.claim("surface", surfaceKey)) {
            if (nativeSurface && contact.kind != ContactKind.STAND) {
                AcousticContext context = context(
                    state,
                    groupVolume,
                    surface.contactX,
                    surface.contactY,
                    surface.contactZ);
                emit(
                    surface.vanillaSound,
                    surface.vanillaVolume * context.volume,
                    surface.vanillaPitch * context.pitch,
                    context);
            } else if (!nativeSurface) {
                playMaterial(
                    state,
                    surface.material,
                    event,
                    groupVolume,
                    surface.contactX,
                    surface.contactY,
                    surface.contactZ);
            }
        }
        if (contact.kind.isLanding() && surface.grounded
            && surface.material != null
            && canPlay(surface.material, event)) {
            state.fallSounds
                .replaced(state.actor.worldObj.getTotalWorldTime(), state.generation, contact.x, contact.y, contact.z);
        }
        if (Config.wet && surface.wet
            && !resolved.rainExposed
            && contact.kind != ContactKind.STAND
            && (!impact || state.impacts.claim("wet", surface.wetMaterial))) {
            playMaterial(
                state,
                surface.wetMaterial,
                event,
                groupVolume * Config.wetVolume,
                surface.contactX,
                surface.contactY,
                surface.contactZ);
        }
        if (state.foliage != null && contact.kind != ContactKind.STAND
            && (!impact || state.impacts.claim("foliage", state.foliage))) {
            playMaterial(
                state,
                state.foliage,
                event,
                groupVolume * Config.foliageVolume,
                surface.contactX,
                surface.contactY,
                surface.contactZ);
        }
    }

    private void consumeSwimming(ActorState state, ResolvedContact resolved) {
        Contact contact = resolved.contact;
        if (contact.kind != ContactKind.SWIM || !SwimmingSounds.canReplace(state.liquidMode, Config.swimming, sound)
            || state.lastSwimGroup == contact.group && state.lastSwimGeneration == state.generation) return;
        // Paired feet are one stroke, not two overlapping swim samples.
        state.lastSwimGroup = contact.group;
        state.lastSwimGeneration = state.generation;
        Surface surface = resolved.surface;
        SwimmingSounds.play(
            sound,
            state.liquidMode,
            contact.speed,
            context(state, Config.swimmingVolume, surface.contactX, surface.contactY, surface.contactZ));
        if (state.foliage != null) playMaterial(
            state,
            state.foliage,
            "walk",
            Config.foliageVolume,
            surface.contactX,
            surface.contactY,
            surface.contactZ);
    }

    private boolean canPlay(String names) {
        for (String name : names.split(",")) if (sound.canPlay(name.trim())) return true;
        return false;
    }

    private boolean canPlay(String names, String event) {
        for (String name : names.split(",")) if (sound.canPlay(name.trim(), event)) return true;
        return false;
    }

    private void playMaterial(ActorState state, String names, String event, float volume, double x, double y,
        double z) {
        if (!SurfaceResolver.emitter(names) || volume <= 0) return;
        Set<String> unique = new HashSet<>();
        AcousticContext context = context(state, volume, x, y, z);
        for (String name : names.split(",")) if (unique.add(name.trim())) sound.play(name.trim(), event, context);
    }

    private AcousticContext context(ActorState state, float volume, double x, double y, double z) {
        EntityLivingBase actor = state.actor;
        float source = actor == Minecraft.getMinecraft().thePlayer ? Config.ownVolume
            : actor instanceof EntityPlayer ? Config.remoteVolume
                : actor instanceof IMob ? Config.hostileVolume : Config.passiveVolume;
        long generation = state.generation;
        return new AcousticContext(
            x,
            y,
            z,
            volume * Config.volume
                * source
                * state.volume
                * state.profile.volume
                * state.soundScale.volume(Config.runningBoost),
            state.pitch * sizePitch(state),
            actor == Minecraft.getMinecraft().thePlayer,
            () -> state.active && state.generation == generation
                && eligible(actor)
                && actor.worldObj.getEntityByID(actor.getEntityId()) == actor)
                    .withCategory(ActorSound.category(actor instanceof EntityPlayer, actor instanceof IMob));
    }

    private static float sizePitch(ActorState state) {
        return Config.sizePitch ? SourceSoundScale.pitch(state.actor.width, state.profile.referenceWidth) : 1;
    }

    private void emit(String name, float volume, float pitch, AcousticContext context) {
        if (!Config.enabled || Minecraft.getMinecraft().theWorld == null
            || volume <= 0
            || context.volume <= 0
            || !context.isValid()
            || name == null
            || voicesThisTick >= Config.maxSoundsPerTick) return;
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation soundName = SoundNames.resolve(
            name,
            context.localPlayer,
            location -> mc.getSoundHandler()
                .getSound(location) != null);
        voicesThisTick++;
        mc.getSoundHandler()
            .playSound(new ActorSound(soundName, volume, pitch, context));
    }

    private static final class ActorState {

        final EntityLivingBase actor;
        final EntityProfile profile;
        ContactGenerator tracker;
        Entity support;
        Surface surface, lastSurface;
        Contact lastContact;
        final ImpactSounds impacts = new ImpactSounds();
        final LandingSoundGate fallSounds = new LandingSoundGate();
        final SourceSoundScale soundScale = new SourceSoundScale();
        SwimmingSounds.Mode liquidMode = SwimmingSounds.Mode.NONE;
        long lastSwimGroup = Long.MIN_VALUE, lastSwimGeneration = Long.MIN_VALUE;
        String foliage, lastFoliage, lastMaterial;
        boolean active = true, child, stepped, initialized, synchronizedMotion;
        int motionGeneration;
        long generation;
        double lastX, lastZ;
        float pitch = 1, volume = 1;

        ActorState(EntityLivingBase actor, EntityProfile profile) {
            this.actor = actor;
            this.profile = profile;
            this.child = actor.isChild();
            this.tracker = ContactGenerators.create(actor, profile);
        }
    }
}
