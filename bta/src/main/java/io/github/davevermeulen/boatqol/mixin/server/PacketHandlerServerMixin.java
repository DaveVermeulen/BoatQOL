package io.github.davevermeulen.boatqol.mixin.server;

import io.github.davevermeulen.boatqol.BoatState;
import io.github.davevermeulen.boatqol.ModdedClients;
import io.github.davevermeulen.boatqol.net.BoatPayloads;
import net.minecraft.core.entity.vehicle.EntityBoat;
import net.minecraft.core.net.packet.PacketCustomPayload;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.net.handler.PacketHandlerServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketHandlerServer.class)
public abstract class PacketHandlerServerMixin {
    @Shadow private PlayerServer playerEntity;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void boatqol$onPayload(PacketCustomPayload packet, CallbackInfo ci) {
        if (BoatPayloads.HELLO.equals(packet.channel)) {
            ci.cancel();
            ModdedClients.set(this.playerEntity.username, true);
        } else if (BoatPayloads.MOVE.equals(packet.channel)) {
            ci.cancel();
            if (!ModdedClients.isModded(this.playerEntity)) {
                return;
            }
            BoatPayloads.Move move = BoatPayloads.Move.read(packet.data);
            // Only the boat this player is sitting in, nothing else.
            if (move != null && this.playerEntity.vehicle instanceof EntityBoat) {
                EntityBoat boat = (EntityBoat) this.playerEntity.vehicle;
                if (boat.id == move.entityId && boat.passenger == this.playerEntity) {
                    ((BoatState) boat).boatqol$acceptClientMove(move.x, move.y, move.z, move.yRot);
                }
            }
        }
    }
}
