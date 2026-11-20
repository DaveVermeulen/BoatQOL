package io.github.davevermeulen.boatqol.mixin.client;

import io.github.davevermeulen.boatqol.BoatState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While we steer a boat in multiplayer, the server's echo of our own past positions is ignored. */
@Mixin(BoatEntity.class)
public abstract class BoatEntityClientMixin extends Entity {
    /** Server positions further away than this (squared, 16 blocks) still win. */
    @Unique private static final double SERVER_CORRECTION_SQ = 256.0;

    protected BoatEntityClientMixin(World world) {
        super(world);
    }

    @Inject(method = "setPositionAndAnglesAvoidEntities", at = @At("HEAD"), cancellable = true)
    private void boatqol$ignoreServerPosition(double x, double y, double z, float yaw, float pitch, int steps, CallbackInfo ci) {
        if (((BoatState) this).boatqol$isLocallyControlled()) {
            double dx = x - this.x;
            double dy = y - this.y;
            double dz = z - this.z;
            if (dx * dx + dy * dy + dz * dz < SERVER_CORRECTION_SQ) {
                ci.cancel();
            } else {
                this.setPosition(x, y, z); // the server says we're far off: snap back
                ci.cancel();
            }
        }
    }

    @Inject(method = "setVelocityClient", at = @At("HEAD"), cancellable = true)
    private void boatqol$ignoreServerVelocity(double x, double y, double z, CallbackInfo ci) {
        if (((BoatState) this).boatqol$isLocallyControlled()) {
            ci.cancel();
        }
    }
}
