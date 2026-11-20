package io.github.davevermeulen.boatqol.mixin.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.server.entity.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Beta spawn packet for boats has no rotation, and the tracker only sends
 * rotation when it changes, so a boat that isn't moving shows up facing yaw 0 for
 * players who see it spawn or come into range. Send its real position and
 * rotation right after the spawn packet.
 */
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    @Shadow public Entity currentTrackedEntity;

    @Inject(method = "updateListener", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void boatqol$sendBoatRotation(ServerPlayerEntity player, CallbackInfo ci) {
        if (this.currentTrackedEntity instanceof BoatEntity) {
            player.networkHandler.sendPacket(new EntityPositionS2CPacket(this.currentTrackedEntity));
        }
    }
}
