package industries.angelic.pastfootsteps.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Optional cosmetic server authority. It has no client-only imports and is therefore safe to initialize on a
 * dedicated server. Normal Forge connection compatibility remains unrestricted; only clients that register this
 * channel and reply with this protocol version receive frames.
 */
public final class ContactSync {

    public static final int PROTOCOL = 2;
    private static final String CHANNEL_NAME = "pf_motion";
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
    private static final Map<UUID, Integer> CLIENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REGISTERED_CLIENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> RECIPIENT_ACTOR_CURSORS = new ConcurrentHashMap<>();
    private static final Map<UUID, ActorRecord> ACTORS = new LinkedHashMap<>(64, 0.75f, true);
    private static final int ACTOR_RETENTION_TICKS = 80, MAX_ACTOR_RECORDS = 4096, HELLO_RETRY_TICKS = 100;
    private static boolean initialized;
    private static int serverTick, recipientCursor, nextGeneration;

    private ContactSync() {}

    /** Register networking and lifecycle listeners once from common initialization. */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(ServerCapabilityHandler.class, CapabilityMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientCapabilityHandler.class, CapabilityMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ClientMotionHandler.class, MotionMessage.class, 2, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new ContactSyncEvents());
        MinecraftForge.EVENT_BUS.register(new ContactSyncEvents());
    }

    private static boolean compatible(int version) {
        return version == PROTOCOL;
    }

    private static void acceptClient(EntityPlayerMP player, int version) {
        if (player == null) return;
        if (compatible(version)) CLIENTS.put(player.getPersistentID(), version);
        else CLIENTS.remove(player.getPersistentID());
    }

    private static void tickServer() {
        serverTick++;
        if ((serverTick & 31) == 0) pruneActors();
        announceCapabilities();
        if (!SyncSettings.enabled || (serverTick & 1) != 0 || CLIENTS.isEmpty()) return;
        int budget = SyncSettings.maxPacketsPerTick;
        List<EntityPlayerMP> recipients = compatiblePlayers();
        if (recipients.isEmpty()) return;
        List<List<EntityLivingBase>> candidates = new ArrayList<>();
        int[] next = new int[recipients.size()], sent = new int[recipients.size()];
        for (EntityPlayerMP recipient : recipients) {
            List<EntityLivingBase> actors = nearbyActors(recipient);
            if (!actors.isEmpty()) Collections.rotate(
                actors,
                -Math.floorMod(
                    RECIPIENT_ACTOR_CURSORS.containsKey(recipient.getPersistentID())
                        ? RECIPIENT_ACTOR_CURSORS.get(recipient.getPersistentID())
                        : 0,
                    actors.size()));
            candidates.add(actors);
        }
        Map<UUID, MotionFrame> passFrames = new LinkedHashMap<>();
        int start = recipientCursor % recipients.size();
        boolean progressed = true;
        // One actor per recipient per round prevents a crowded early recipient from consuming the global budget.
        while (budget > 0 && progressed) {
            progressed = false;
            for (int offset = 0; offset < recipients.size() && budget > 0; offset++) {
                int index = (start + offset) % recipients.size();
                List<EntityLivingBase> actors = candidates.get(index);
                while (next[index] < actors.size()
                    && (actors.get(next[index]) == recipients.get(index) || actors.get(next[index]).isDead))
                    next[index]++;
                if (sent[index] == SyncSettings.maxActors || next[index] == actors.size()) continue;
                EntityLivingBase actor = actors.get(next[index]++);
                MotionFrame sample = passFrames.get(actor.getPersistentID());
                if (sample == null) {
                    sample = frame(actor);
                    passFrames.put(actor.getPersistentID(), sample);
                }
                CHANNEL.sendTo(new MotionMessage(sample), recipients.get(index));
                sent[index]++;
                budget--;
                progressed = true;
            }
        }
        for (int i = 0; i < recipients.size(); i++) {
            UUID id = recipients.get(i)
                .getPersistentID();
            if (next[i] != 0) RECIPIENT_ACTOR_CURSORS
                .put(id, (RECIPIENT_ACTOR_CURSORS.containsKey(id) ? RECIPIENT_ACTOR_CURSORS.get(id) : 0) + next[i]);
        }
        recipientCursor = (start + 1) % recipients.size();
    }

    private static void announceCapabilities() {
        for (EntityPlayerMP player : compatiblePlayers(false)) {
            UUID id = player.getPersistentID();
            Integer lastHello = REGISTERED_CLIENTS.get(id);
            if (lastHello == null || CLIENTS.containsKey(id) || serverTick - lastHello < HELLO_RETRY_TICKS) continue;
            CHANNEL.sendTo(new CapabilityMessage(PROTOCOL), player);
            REGISTERED_CLIENTS.put(id, serverTick);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> compatiblePlayers() {
        return compatiblePlayers(true);
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> compatiblePlayers(boolean compatibleOnly) {
        List<EntityPlayerMP> result = new ArrayList<>();
        for (Object value : net.minecraft.server.MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (!compatibleOnly || CLIENTS.containsKey(player.getPersistentID())) result.add(player);
        }
        return result;
    }

    private static List<EntityLivingBase> nearbyActors(final EntityPlayerMP recipient) {
        int range = SyncSettings.range;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            recipient.posX - range,
            recipient.posY - range,
            recipient.posZ - range,
            recipient.posX + range,
            recipient.posY + range,
            recipient.posZ + range);
        List<EntityLivingBase> actors = new ArrayList<>(
            recipient.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box));
        Collections.sort(actors, new Comparator<EntityLivingBase>() {

            @Override
            public int compare(EntityLivingBase first, EntityLivingBase second) {
                return Double.compare(recipient.getDistanceSqToEntity(first), recipient.getDistanceSqToEntity(second));
            }
        });
        return actors;
    }

    private static MotionFrame frame(EntityLivingBase actor) {
        UUID id = actor.getPersistentID();
        ActorRecord record = ACTORS.get(id);
        if (record == null) {
            if (ACTORS.size() == MAX_ACTOR_RECORDS) ACTORS.remove(
                ACTORS.keySet()
                    .iterator()
                    .next());
            record = new ActorRecord(actor.getEntityId(), actor.worldObj.provider.dimensionId, ++nextGeneration);
            ACTORS.put(id, record);
        } else if (record.entityId != actor.getEntityId() || record.dimension != actor.worldObj.provider.dimensionId) {
            record.entityId = actor.getEntityId();
            record.dimension = actor.worldObj.provider.dimensionId;
            record.generation = ++nextGeneration;
            record.sequence = 0;
        }
        record.lastSeenTick = serverTick;
        return new MotionFrame(
            id,
            actor.getEntityId(),
            actor instanceof EntityPlayer ? -1 : EntityList.getEntityID(actor),
            record.generation,
            ++record.sequence,
            serverTick,
            actor.worldObj.provider.dimensionId,
            actor.posX,
            actor.boundingBox.minY,
            actor.posZ,
            actor.onGround,
            actor.isOnLadder(),
            actor.isInWater(),
            actor.isSprinting());
    }

    private static void pruneActors() {
        Iterator<Map.Entry<UUID, ActorRecord>> entries = ACTORS.entrySet()
            .iterator();
        while (entries.hasNext() && serverTick - entries.next()
            .getValue().lastSeenTick > ACTOR_RETENTION_TICKS) entries.remove();
    }

    /** Generation changes if this server observes the same persistent actor under a newly allocated entity ID. */
    private static final class ActorRecord {

        int entityId;
        int dimension;
        int generation;
        int sequence;
        int lastSeenTick;

        ActorRecord(int entityId, int dimension, int generation) {
            this.entityId = entityId;
            this.dimension = dimension;
            this.generation = generation;
        }
    }

    public static final class ServerCapabilityHandler implements IMessageHandler<CapabilityMessage, IMessage> {

        @Override
        public IMessage onMessage(CapabilityMessage message, MessageContext context) {
            acceptClient(context.getServerHandler().playerEntity, message.protocol);
            return null;
        }
    }

    public static final class ClientCapabilityHandler implements IMessageHandler<CapabilityMessage, IMessage> {

        @Override
        public IMessage onMessage(CapabilityMessage message, MessageContext context) {
            RemoteMotion.queueServerHello(message.protocol);
            if (compatible(message.protocol)) CHANNEL.sendToServer(new CapabilityMessage(PROTOCOL));
            return null;
        }
    }

    public static final class ClientMotionHandler implements IMessageHandler<MotionMessage, IMessage> {

        @Override
        public IMessage onMessage(MotionMessage message, MessageContext context) {
            RemoteMotion.receive(message.frame);
            return null;
        }
    }

    public static final class ContactSyncEvents {

        @SubscribeEvent
        public void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) RemoteMotion.tick();
        }

        @SubscribeEvent
        public void serverTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) tickServer();
        }

        @SubscribeEvent
        public void clientChannelRegistered(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
            if (event.side != Side.SERVER || !"REGISTER".equals(event.operation)
                || !event.registrations.contains(CHANNEL_NAME)) return;
            if (event.handler instanceof NetHandlerPlayServer) {
                EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
                if (player != null) REGISTERED_CLIENTS.put(player.getPersistentID(), serverTick - HELLO_RETRY_TICKS);
            }
        }

        @SubscribeEvent
        public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            RemoteMotion.disconnect();
        }

        @SubscribeEvent
        public void playerLeft(PlayerEvent.PlayerLoggedOutEvent event) {
            CLIENTS.remove(event.player.getPersistentID());
            REGISTERED_CLIENTS.remove(event.player.getPersistentID());
            RECIPIENT_ACTOR_CURSORS.remove(event.player.getPersistentID());
        }

        @SubscribeEvent
        public void serverDisconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) {
            if (event.handler instanceof NetHandlerPlayServer) {
                EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
                if (player != null) {
                    UUID id = player.getPersistentID();
                    CLIENTS.remove(id);
                    REGISTERED_CLIENTS.remove(id);
                    RECIPIENT_ACTOR_CURSORS.remove(id);
                }
            }
        }

        @SubscribeEvent
        public void worldUnload(WorldEvent.Unload event) {
            if (event.world.isRemote) {
                RemoteMotion.clearFrames();
            } else {
                int dimension = event.world.provider.dimensionId;
                Iterator<Map.Entry<UUID, ActorRecord>> entries = ACTORS.entrySet()
                    .iterator();
                while (entries.hasNext()) if (entries.next()
                    .getValue().dimension == dimension) entries.remove();
            }
        }
    }
}
