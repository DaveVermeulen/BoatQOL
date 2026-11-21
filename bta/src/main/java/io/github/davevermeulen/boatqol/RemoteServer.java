package io.github.davevermeulen.boatqol;

import net.minecraft.core.net.packet.Packet;

/**
 * Client side: what we know about the multiplayer server we're connected to.
 * Filled in by the client entrypoint; on a dedicated server it stays empty.
 */
public final class RemoteServer {
    public interface Connection {
        /** The current multiplayer connection, or null in singleplayer / menus. */
        Object current();

        void send(Packet packet);
    }

    public static volatile Connection connection = null;

    /** The connection that told us the server has BoatQOL. */
    private static volatile Object moddedConnection = null;
    private static volatile boolean serverModernIce = false;

    private RemoteServer() {
    }

    public static void onServerConfig(Object conn, boolean useModernIceSlide) {
        moddedConnection = conn;
        serverModernIce = useModernIceSlide;
    }

    /** True if we are in multiplayer on a server that has BoatQOL. */
    public static boolean hasMod() {
        Connection c = connection;
        Object current = c == null ? null : c.current();
        return current != null && current == moddedConnection;
    }

    /** The ice setting that applies to a boat simulated in this game instance. */
    public static boolean useModernIceSlide(boolean remoteWorld) {
        return remoteWorld && hasMod() ? serverModernIce : BoatQOLConfig.useModernIceSlide;
    }

    public static void send(Packet packet) {
        Connection c = connection;
        if (c != null) {
            c.send(packet);
        }
    }
}
