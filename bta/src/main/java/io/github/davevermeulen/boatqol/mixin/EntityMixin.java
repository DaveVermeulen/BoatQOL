package io.github.davevermeulen.boatqol.mixin;

import io.github.davevermeulen.boatqol.BoatRiding;
import io.github.davevermeulen.boatqol.BoatState;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.vehicle.EntityBoat;
import net.minecraft.core.world.IVehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rider of a boat we steer turns exactly with the boat (vanilla eases the
 * rider half way each tick), and can look at most 105 degrees away from the
 * boat's heading, like 1.9+.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public IVehicle vehicle;
    @Shadow public float yRot;
    @Shadow public boolean turnWithVehicle;
    @Shadow private double entityRiderYawDelta;

    @Inject(method = "rideTick", at = @At("TAIL"))
    private void boatqol$followBoat(CallbackInfo ci) {
        if (!(this.vehicle instanceof EntityBoat) || !((BoatState) this.vehicle).boatqol$isSteeredHere()) {
            return;
        }
        if (this.turnWithVehicle) {
            this.yRot = (float) (this.yRot + this.entityRiderYawDelta);
        }
        this.entityRiderYawDelta = 0.0;
        BoatRiding.clampLook((Entity) (Object) this);
    }
}
