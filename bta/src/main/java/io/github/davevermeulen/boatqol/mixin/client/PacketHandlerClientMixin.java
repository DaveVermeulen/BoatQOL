package io.github.davevermeulen.boatqol.mixin.client;

import io.github.davevermeulen.boatqol.BoatQOL;
import io.github.davevermeulen.boatqol.RemoteServer;
import io.github.davevermeulen.boatqol.net.BoatPayloads;
import net.minecraft.client.net.handler.PacketHandlerClient;
import net.minecraft.core.net.packet.PacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketHandlerClient.class)
public abstract class PacketHandlerClientMixin {
    /** The server says it has BoatQOL: remember its settings and say we have it too. */
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void boatqol$onPayload(PacketCustomPayload packet, CallbackInfo ci) {
        if (!BoatPayloads.CONFIG.equals(packet.channel)) {
            return;
        }
        ci.cancel();
        if (packet.data == null || packet.data.length < 2) {
            return;
        }
        boolean modernIce = packet.data[1] != 0;
        PacketHandlerClient self = (PacketHandlerClient) (Object) this;
        RemoteServer.onServerConfig(self, modernIce);
        self.addToSendQueue(BoatPayloads.hello());
        BoatQOL.LOGGER.info("Server has BoatQOL (use modern ice slide: {})", modernIce);
    }
}
