package org.fentanylsolutions.pastfootsteps.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/** Versioned two-way capability handshake; no client configuration is sent to the server. */
public final class CapabilityMessage implements IMessage {

    public int protocol;

    public CapabilityMessage() {}

    CapabilityMessage(int protocol) {
        this.protocol = protocol;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        protocol = buffer.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(protocol);
    }
}
