package io.github.davevermeulen.boatqol.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.davevermeulen.boatqol.BoatQOLConfig;
import io.github.davevermeulen.boatqol.ModdedClients;
import io.github.davevermeulen.boatqol.net.ServerConfigS2CPacket;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.login.LoginHelloPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class ServerLoginNetworkHandlerMixin {
    @Inject(method = "accept", at = @At("HEAD"))
    private void boatqol$recordModded(LoginHelloPacket packet, CallbackInfo ci) {
        ModdedClients.set(packet.username, packet.worldSeed == ModdedClients.LOGIN_MAGIC);
    }

    /** Tell a modded client that this server has BoatQOL, right after the login reply. */
    @Inject(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ServerPlayerEntity;initScreenHandler()V"))
    private void boatqol$greet(LoginHelloPacket packet, CallbackInfo ci, @Local ServerPlayerEntity player) {
        if (ModdedClients.isModded(player)) {
            player.networkHandler.sendPacket(new ServerConfigS2CPacket(BoatQOLConfig.useModernIceSlide));
        }
    }
}
