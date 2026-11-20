package io.github.davevermeulen.boatqol.mixin;

import io.github.davevermeulen.boatqol.net.BoatNetworking;
import io.github.davevermeulen.boatqol.net.ServerConfigS2CPacket;
import io.github.davevermeulen.boatqol.net.VehicleMoveC2SPacket;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Packet.class)
public abstract class PacketMixin {
    @Invoker("register")
    static void boatqol$register(int rawId, boolean clientBound, boolean serverBound, Class<?> type) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void boatqol$registerPackets(CallbackInfo ci) {
        boatqol$register(BoatNetworking.VEHICLE_MOVE_ID, false, true, VehicleMoveC2SPacket.class);
        boatqol$register(BoatNetworking.SERVER_CONFIG_ID, true, false, ServerConfigS2CPacket.class);
    }
}
