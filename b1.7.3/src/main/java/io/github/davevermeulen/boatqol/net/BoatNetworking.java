package io.github.davevermeulen.boatqol.net;

import net.minecraft.network.NetworkHandler;

/** Packet ids and the side-specific handlers installed by the entrypoints. */
public final class BoatNetworking {
    /** Client to server: the rider's boat position. */
    public static final int VEHICLE_MOVE_ID = 211;
    /** Server to client: the server has BoatQOL, plus its settings. */
    public static final int SERVER_CONFIG_ID = 212;

    public interface Handler<P> {
        void handle(P packet, NetworkHandler networkHandler);
    }

    public static volatile Handler<VehicleMoveC2SPacket> serverHandler = null;
    public static volatile Handler<ServerConfigS2CPacket> clientHandler = null;

    private BoatNetworking() {
    }
}
