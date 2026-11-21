package io.github.davevermeulen.boatqol.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.github.davevermeulen.boatqol.BoatQOL;
import io.netty.buffer.ByteBuf;

/** Server to client on login: the server's settings. */
public class ConfigMessage implements IMessage {
    public boolean useModernIceSlide;

    public ConfigMessage() {
    }

    public ConfigMessage(boolean useModernIceSlide) {
        this.useModernIceSlide = useModernIceSlide;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.useModernIceSlide = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.useModernIceSlide);
    }

    public static class Handler implements IMessageHandler<ConfigMessage, IMessage> {
        @Override
        public IMessage onMessage(ConfigMessage message, MessageContext ctx) {
            BoatQOL.proxy.setRemoteModernIce(message.useModernIceSlide);
            return null;
        }
    }
}
