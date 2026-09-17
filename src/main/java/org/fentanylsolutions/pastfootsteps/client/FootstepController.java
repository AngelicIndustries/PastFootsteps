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

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
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
import org.fentanylsolutions.pastfootsteps.sound.SoundEngine;
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
        return Config.surfaceSounds && INSTANCE.sound.ready() && ownsContacts(entity);
    }

    public static boolean ownsContacts(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || !INSTANCE.eligible((EntityLivingBase) entity)) return false;
        ActorState state = INSTANCE.actors.get(entity);
        return state != null && state.active && state.surface != null;
    }

    public static boolean suppressSound(Entity entity, String name) {
        if (!shouldReplace(entity) || name == null) return false;
        String path = name.startsWith("minecraft:") ? name.substring(10) : name;
        if (path.startsWith("step.") || path.equals("game.player.swim") || path.equals("game.neutral.swim"))
            return true;
        ActorState state = INSTANCE.actors.get(entity);
        return state != null && state.surface != null && sameSound(name, state.surface.vanillaSound);
    }

    private static boolean sameSound(String first, String second) {
        if (first == null || second == null) return false;
        return (first.startsWith("minecraft:") ? first.substring(10) : first)
            .equals(second.startsWith("minecraft:") ? second.substring(10) : second);
    }

    /** 1.7 packets have no actor ID. Ambiguous nearby actors deliberately retain their native sound. */
    public static boolean suppressRemoteSound(String name, double x, double y, double z) {
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
        materials.reload(manager, Config.directory);
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
        for (ActorState state : actors.values()) state.active = false;
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
            entry.addProperty("phase", state.tracker.phase());
            entry.addProperty("material", state.lastMaterial);
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
        for (ActorState state : actors.values()) if (!ordered.containsKey(state.actor)) state.active = false;
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
        Surface current = resolver.resolveAt(actor, motionX, motionY, motionZ, 0, 0);
        if (motion == null && actor != Minecraft.getMinecraft().thePlayer && (current == null || !current.grounded)) {
            current = remoteSupport(state, current);
        }
        state.surface = current;
        boolean grounded = motion != null ? motion.grounded
            : actor == Minecraft.getMinecraft().thePlayer ? actor.onGround : current != null && current.grounded;
        boolean swimming = motion != null ? motion.swimming : actor.isInWater() || resolver.inLava(actor);
        BiomeGenBase biome = actor.worldObj
            .getBiomeGenForCoords((int) Math.floor(actor.posX), (int) Math.floor(actor.posZ));
        state.pitch += (materials.biomePitch(biome) - state.pitch) * 0.05f;
        state.volume += (materials.biomeVolume(biome) - state.volume) * 0.05f;
        state.foliage = Config.surfaceSounds && Config.foliage ? resolver.foliage(actor) : null;
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
        int[] acceptedContacts = { 0 };
        try {
            state.tracker.update(
                support == null ? motionX : motionX - support.posX,
                support == null ? feetY : feetY - support.posY,
                support == null ? motionZ : motionZ - support.posZ,
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
        Surface target = resolver.resolveAt(actor, targetX, targetY, targetZ, 0, 0);
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
            contact.foot.forward * width);
        if (surface == null && state.surface != null && state.surface.grounded && contact.kind != ContactKind.JUMP) {
            surface = resolver.resolveAt(
                state.actor,
                contact.x,
                state.surface.contactY,
                contact.z,
                contact.foot.lateral * width,
                contact.foot.forward * width);
        }
        if (surface == null) surface = contact.kind == ContactKind.JUMP ? state.lastSurface : state.surface;
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
        if (resolved.actor.isSneaking()) return;
        if (!Config.jumpLand && (contact.kind == ContactKind.JUMP || contact.kind.isLanding())) return;
        float groupVolume = (float) (1 / Math.sqrt(contact.groupSize));
        equipment.play(resolved, context(state, groupVolume, surface.contactX, surface.contactY, surface.contactZ));
        if (!Config.surfaceSounds || !sound.ready()) return;
        String event = contact.kind.acousticKey;
        if (surface.material == null || SurfaceResolver.emitter(surface.material) && !canPlay(surface.material)) {
            if (contact.kind != ContactKind.STAND) {
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
            }
        } else {
            playMaterial(
                state,
                surface.material,
                event,
                groupVolume,
                surface.contactX,
                surface.contactY,
                surface.contactZ);
        }
        if (Config.wet && surface.wet && !resolved.rainExposed && contact.kind != ContactKind.STAND) {
            playMaterial(
                state,
                surface.wetMaterial,
                event,
                groupVolume * Config.wetVolume,
                surface.contactX,
                surface.contactY,
                surface.contactZ);
        }
        if (state.foliage != null && contact.kind != ContactKind.STAND) {
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

    private boolean canPlay(String names) {
        for (String name : names.split(",")) if (sound.canPlay(name.trim())) return true;
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
        float source = actor == Minecraft.getMinecraft().thePlayer ? 1
            : actor instanceof EntityPlayer ? Config.remoteVolume
                : actor instanceof IMob ? Config.hostileVolume : Config.passiveVolume;
        long generation = state.generation;
        return new AcousticContext(
            x,
            y,
            z,
            volume * Config.volume * source * state.volume * state.profile.volume,
            state.pitch,
            () -> state.active && state.generation == generation
                && eligible(actor)
                && actor.worldObj.getEntityByID(actor.getEntityId()) == actor);
    }

    private void emit(String name, float volume, float pitch, AcousticContext context) {
        if (!Config.enabled || Minecraft.getMinecraft().theWorld == null
            || volume <= 0
            || context.volume <= 0
            || !context.isValid()
            || name == null
            || voicesThisTick >= Config.maxSoundsPerTick) return;
        String soundName = name;
        if (soundName.startsWith("@")) soundName = soundName.substring(1);
        if (soundName.startsWith("pf_presence.")) soundName = "presencefootsteps:" + soundName;
        switch (soundName) {
            case "minecraft:block.ladder.step":
                soundName = "minecraft:step.ladder";
                break;
            case "minecraft:entity.player.swim":
                soundName = "minecraft:game.player.swim";
                break;
            case "minecraft:block.fire.extinguish":
                soundName = "minecraft:random.fizz";
                break;
            case "minecraft:block.copper.step":
            case "minecraft:block.copper.break":
            case "minecraft:block.heavy_core.step":
                soundName = "presencefootsteps:pf_presence.metalbar.metalbar_walk";
                break;
            case "minecraft:block.amethyst_block.step":
            case "minecraft:block.amethyst_block.hit":
            case "minecraft:block.amethyst_block.break":
                soundName = "presencefootsteps:pf_presence.glass.glass_hit";
                break;
            case "minecraft:block.soul_soil.step":
            case "minecraft:block.soul_soil.place":
                soundName = "presencefootsteps:pf_presence.quicksand.quicksand_walk";
                break;
            case "minecraft:block.slime_block.step":
            case "minecraft:block.slime_block.break":
            case "minecraft:block.honey_block.step":
            case "minecraft:block.honey_block.break":
                soundName = "presencefootsteps:pf_presence.mud.mud_walk";
                break;
            case "minecraft:block.scaffolding.step":
            case "minecraft:block.creaking_heart.step":
            case "minecraft:block.creaking_heart.break":
                soundName = "presencefootsteps:pf_presence.squeakywood.squeakywood_walk";
                break;
            default:
                break;
        }
        voicesThisTick++;
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(
                new PositionedSoundRecord(
                    new ResourceLocation(soundName),
                    volume,
                    pitch,
                    (float) context.x,
                    (float) context.y,
                    (float) context.z));
    }

    private static final class ActorState {

        final EntityLivingBase actor;
        final EntityProfile profile;
        ContactGenerator tracker;
        Entity support;
        Surface surface, lastSurface;
        Contact lastContact;
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
