package io.github.davevermeulen.boatqol.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Client to server: where the rider's client moved its boat. */
public class MoveMessage implements IMessage {
    /** Moves waiting for the server thread (handlers run on the network thread in 1.7.10). */
    public static final Queue<Object[]> PENDING = new ConcurrentLinkedQueue<Object[]>();

    public int entityId;
    public double x;
    public double y;
    public double z;
    public float yaw;

    public MoveMessage() {
    }

    public MoveMessage(int entityId, double x, double y, double z, float yaw) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yaw);
    }

    public boolean isValid() {
        return !Double.isNaN(this.x) && !Double.isInfinite(this.x)
                && !Double.isNaN(this.y) && !Double.isInfinite(this.y)
                && !Double.isNaN(this.z) && !Double.isInfinite(this.z)
                && !Float.isNaN(this.yaw) && !Float.isInfinite(this.yaw);
    }

    public static class Handler implements IMessageHandler<MoveMessage, IMessage> {
        @Override
        public IMessage onMessage(MoveMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (message.isValid()) {
                PENDING.add(new Object[] {player, message});
            }
            return null;
        }
    }
}
