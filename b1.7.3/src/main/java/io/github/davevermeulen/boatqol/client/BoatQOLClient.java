package io.github.davevermeulen.boatqol.client;

import io.github.davevermeulen.boatqol.BoatQOL;
import io.github.davevermeulen.boatqol.RemoteServer;
import io.github.davevermeulen.boatqol.net.BoatNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.MultiplayerClientPlayerEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;

public class BoatQOLClient implements ClientModInitializer {
    private static Minecraft minecraft() {
        return (Minecraft) FabricLoader.getInstance().getGameInstance();
    }

    @Override
    public void onInitializeClient() {
        BoatQOL.localInput = rider -> {
            Minecraft mc = minecraft();
            ClientPlayerEntity player = mc == null ? null : mc.player;
            if (player == null || rider != player) {
                return null;
            }
            Input input = player.input;
            if (input == null) {
                return new float[] {0.0F, 0.0F};
            }
            return new float[] {input.movementForward, input.movementSideways};
        };

        RemoteServer.connection = new RemoteServer.Connection() {
            @Override
            public Object current() {
                Minecraft mc = minecraft();
                if (mc != null && mc.player instanceof MultiplayerClientPlayerEntity) {
                    return ((MultiplayerClientPlayerEntity) mc.player).networkHandler;
                }
                return null;
            }

            @Override
            public void send(Packet packet) {
                Minecraft mc = minecraft();
                if (mc != null && mc.player instanceof MultiplayerClientPlayerEntity) {
                    ((MultiplayerClientPlayerEntity) mc.player).networkHandler.sendPacket(packet);
                }
            }
        };

        BoatNetworking.clientHandler = (packet, handler) -> {
            RemoteServer.onServerConfig(handler, packet.useModernIceSlide);
            BoatQOL.LOGGER.info("Server has BoatQOL (use modern ice slide: {})", packet.useModernIceSlide);
        };
    }
}
