package io.github.davevermeulen.boatqol.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.davevermeulen.boatqol.BoatQOLConfig;
import io.github.davevermeulen.boatqol.ModdedClients;
import io.github.davevermeulen.boatqol.net.BoatPayloads;
import net.minecraft.core.net.packet.PacketLogin;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.net.handler.PacketHandlerLogin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketHandlerLogin.class)
public abstract class PacketHandlerLoginMixin {
    /**
     * Tell every joining client this server has BoatQOL. Vanilla clients ignore the
     * payload; modded ones answer with a hello, which marks them as modded.
     */
    @Inject(method = "doLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/entity/player/PlayerServer;setupCraftingInventoryListener()V"))
    private void boatqol$greet(PacketLogin packet, CallbackInfo ci, @Local PlayerServer player) {
        ModdedClients.set(player.username, false);
        player.playerNetServerHandler.sendPacket(BoatPayloads.config(BoatQOLConfig.useModernIceSlide));
    }
}
