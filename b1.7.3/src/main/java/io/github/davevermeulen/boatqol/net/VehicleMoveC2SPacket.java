package io.github.davevermeulen.boatqol.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;

/** Sent by the client that steers a boat, every tick the boat moves. */
public class VehicleMoveC2SPacket extends Packet {
    public int entityId;
    public double x;
    public double y;
    public double z;
    public float yaw;

    public VehicleMoveC2SPacket() {
    }

    public VehicleMoveC2SPacket(int entityId, double x, double y, double z, float yaw) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    @Override
    public void read(DataInputStream stream) {
        try {
            this.entityId = stream.readInt();
            this.x = stream.readDouble();
            this.y = stream.readDouble();
            this.z = stream.readDouble();
            this.yaw = stream.readFloat();
        } catch (IOException e) {
            throw Sneaky.rethrow(e);
        }
    }

    @Override
    public void write(DataOutputStream stream) {
        try {
            stream.writeInt(this.entityId);
            stream.writeDouble(this.x);
            stream.writeDouble(this.y);
            stream.writeDouble(this.z);
            stream.writeFloat(this.yaw);
        } catch (IOException e) {
            throw Sneaky.rethrow(e);
        }
    }

    @Override
    public void apply(NetworkHandler networkHandler) {
        BoatNetworking.Handler<VehicleMoveC2SPacket> handler = BoatNetworking.serverHandler;
        if (handler != null) {
            handler.handle(this, networkHandler);
        }
    }

    @Override
    public int size() {
        return 4 + 8 * 3 + 4;
    }
}
