package io.github.davevermeulen.boatqol.mixin.client;

import io.github.davevermeulen.boatqol.ModdedClients;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client's login packet carries an unused world seed field (always 0, and
 * ignored by the server). We put a marker there so a BoatQOL server knows this
 * client has the mod. Vanilla servers don't read it, so nothing changes there.
 */
@Mixin(LoginHelloPacket.class)
public abstract class LoginHelloPacketMixin {
    @Shadow public long worldSeed;

    @Inject(method = "<init>(Ljava/lang/String;I)V", at = @At("TAIL"))
    private void boatqol$markModded(String username, int protocolVersion, CallbackInfo ci) {
        this.worldSeed = ModdedClients.LOGIN_MAGIC;
    }
}
