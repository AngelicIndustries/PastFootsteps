package industries.angelic.pastfootsteps.network;

import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Compact fixed-point wire form for a MotionFrame. Position precision is 1/32 block, matching 1.7 entity packets. */
public final class MotionMessage implements IMessage {

    private static final int GROUND = 1, CLIMBING = 2, SWIMMING = 4, SPRINTING = 8;
    public MotionFrame frame;

    public MotionMessage() {}

    MotionMessage(MotionFrame frame) {
        this.frame = frame;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        UUID actor = new UUID(buffer.readLong(), buffer.readLong());
        int entityId = buffer.readInt(), entityType = buffer.readInt(), generation = buffer.readInt(),
            sequence = buffer.readInt(), tick = buffer.readInt(), dimension = buffer.readInt();
        double x = buffer.readInt() / 32.0, feetY = buffer.readInt() / 32.0, z = buffer.readInt() / 32.0;
        int flags = buffer.readUnsignedByte();
        frame = new MotionFrame(
            actor,
            entityId,
            entityType,
            generation,
            sequence,
            tick,
            dimension,
            x,
            feetY,
            z,
            (flags & GROUND) != 0,
            (flags & CLIMBING) != 0,
            (flags & SWIMMING) != 0,
            (flags & SPRINTING) != 0);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        MotionFrame sample = frame;
        buffer.writeLong(sample.actorId.getMostSignificantBits());
        buffer.writeLong(sample.actorId.getLeastSignificantBits());
        buffer.writeInt(sample.entityId);
        buffer.writeInt(sample.entityType);
        buffer.writeInt(sample.generation);
        buffer.writeInt(sample.sequence);
        buffer.writeInt(sample.serverTick);
        buffer.writeInt(sample.dimension);
        buffer.writeInt(encode(sample.x));
        buffer.writeInt(encode(sample.feetY));
        buffer.writeInt(encode(sample.z));
        int flags = sample.grounded ? GROUND : 0;
        if (sample.climbing) flags |= CLIMBING;
        if (sample.swimming) flags |= SWIMMING;
        if (sample.sprinting) flags |= SPRINTING;
        buffer.writeByte(flags);
    }

    private static int encode(double coordinate) {
        if (!Double.isFinite(coordinate)) return 0;
        double scaled = Math.rint(coordinate * 32);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
    }
}
