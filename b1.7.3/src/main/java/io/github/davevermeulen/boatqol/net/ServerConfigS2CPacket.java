package io.github.davevermeulen.boatqol.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;

/**
 * Sent once on login, only to clients that said they have BoatQOL. Tells the
 * client that the server has the mod (so it may steer boats itself) and which
 * ice setting to use.
 */
public class ServerConfigS2CPacket extends Packet {
    public int protocol = 1;
    public boolean useModernIceSlide;

    public ServerConfigS2CPacket() {
    }

    public ServerConfigS2CPacket(boolean useModernIceSlide) {
        this.useModernIceSlide = useModernIceSlide;
    }

    @Override
    public void read(DataInputStream stream) {
        try {
            this.protocol = stream.readByte();
            this.useModernIceSlide = stream.readBoolean();
        } catch (IOException e) {
            throw Sneaky.rethrow(e);
        }
    }

    @Override
    public void write(DataOutputStream stream) {
        try {
            stream.writeByte(this.protocol);
            stream.writeBoolean(this.useModernIceSlide);
        } catch (IOException e) {
            throw Sneaky.rethrow(e);
        }
    }

    @Override
    public void apply(NetworkHandler networkHandler) {
        BoatNetworking.Handler<ServerConfigS2CPacket> handler = BoatNetworking.clientHandler;
        if (handler != null) {
            handler.handle(this, networkHandler);
        }
    }

    @Override
    public int size() {
        return 2;
    }
}
