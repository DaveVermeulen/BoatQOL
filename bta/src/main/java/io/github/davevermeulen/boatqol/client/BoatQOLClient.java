package io.github.davevermeulen.boatqol.client;

import io.github.davevermeulen.boatqol.BoatQOL;
import io.github.davevermeulen.boatqol.RemoteServer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.PlayerLocal;
import net.minecraft.client.entity.player.PlayerLocalMultiplayer;
import net.minecraft.client.input.PlayerInput;
import net.minecraft.core.net.packet.Packet;

public class BoatQOLClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BoatQOL.localInput = rider -> {
            Minecraft mc = Minecraft.getMinecraft();
            PlayerLocal player = mc == null ? null : mc.thePlayer;
            if (player == null || rider != player) {
                return null;
            }
            PlayerInput input = player.input;
            if (input == null) {
                return new float[] {0.0F, 0.0F};
            }
            return new float[] {input.moveForward, input.moveStrafe};
        };

        RemoteServer.connection = new RemoteServer.Connection() {
            @Override
            public Object current() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.thePlayer instanceof PlayerLocalMultiplayer) {
                    return ((PlayerLocalMultiplayer) mc.thePlayer).sendQueue;
                }
                return null;
            }

            @Override
            public void send(Packet packet) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.thePlayer instanceof PlayerLocalMultiplayer) {
                    ((PlayerLocalMultiplayer) mc.thePlayer).sendQueue.addToSendQueue(packet);
                }
            }
        };
    }
}
