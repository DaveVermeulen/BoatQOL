package io.github.davevermeulen.boatqol.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.core.net.packet.PacketCustomPayload;

/**
 * BoatQOL's messages, sent as BTA custom payloads. Vanilla BTA ignores channels
 * it doesn't know, so a modded client or server never upsets a vanilla one.
 */
public final class BoatPayloads {
    /** Server to client, on login: the server has BoatQOL, plus its settings. */
    public static final String CONFIG = "BoatQOL:Config";
    /** Client to server, in reply to CONFIG: this client has BoatQOL. */
    public static final String HELLO = "BoatQOL:Hello";
    /** Client to server: the position of the boat this client steers. */
    public static final String MOVE = "BoatQOL:Move";

    public static final int PROTOCOL = 1;

    private BoatPayloads() {
    }

    public static PacketCustomPayload config(boolean useModernIceSlide) {
        return new PacketCustomPayload(CONFIG, new byte[] {(byte) PROTOCOL, (byte) (useModernIceSlide ? 1 : 0)});
    }

    public static PacketCustomPayload hello() {
        return new PacketCustomPayload(HELLO, new byte[] {(byte) PROTOCOL});
    }

    public static PacketCustomPayload move(int entityId, double x, double y, double z, float yRot) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeInt(entityId);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yRot);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new PacketCustomPayload(MOVE, bytes.toByteArray());
    }

    public static final class Move {
        public final int entityId;
        public final double x;
        public final double y;
        public final double z;
        public final float yRot;

        private Move(int entityId, double x, double y, double z, float yRot) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
        }

        /** @return the move, or null if the data is malformed. */
        public static Move read(byte[] data) {
            if (data == null || data.length != 4 + 8 * 3 + 4) {
                return null;
            }
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                Move m = new Move(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat());
                if (Double.isNaN(m.x) || Double.isNaN(m.y) || Double.isNaN(m.z) || Float.isNaN(m.yRot)
                        || Double.isInfinite(m.x) || Double.isInfinite(m.y) || Double.isInfinite(m.z) || Float.isInfinite(m.yRot)) {
                    return null;
                }
                return m;
            } catch (IOException e) {
                return null;
            }
        }
    }
}
