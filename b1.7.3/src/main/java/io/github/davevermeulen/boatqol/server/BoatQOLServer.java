package io.github.davevermeulen.boatqol.server;

import io.github.davevermeulen.boatqol.BoatState;
import io.github.davevermeulen.boatqol.mixin.server.ServerPlayNetworkHandlerAccessor;
import io.github.davevermeulen.boatqol.net.BoatNetworking;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;

public class BoatQOLServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        BoatNetworking.serverHandler = (packet, handler) -> {
            if (!(handler instanceof ServerPlayNetworkHandler)) {
                return;
            }
            ServerPlayerEntity player = ((ServerPlayNetworkHandlerAccessor) handler).boatqol$getPlayer();
            Entity vehicle = player == null ? null : player.vehicle;
            // Only the boat this player is sitting in, nothing else.
            if (vehicle instanceof BoatEntity && vehicle.id == packet.entityId && vehicle.passenger == player) {
                ((BoatState) vehicle).boatqol$acceptClientMove(packet.x, packet.y, packet.z, packet.yaw);
            }
        };
    }
}
