package io.github.davevermeulen.boatqol.mixin;

import io.github.davevermeulen.boatqol.BoatRiding;
import io.github.davevermeulen.boatqol.BoatState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
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
    @Shadow public Entity vehicle;
    @Shadow public float yaw;
    @Shadow public float prevYaw;
    @Shadow private double vehicleYawDelta;

    @Inject(method = "tickRiding", at = @At("TAIL"))
    private void boatqol$followBoat(CallbackInfo ci) {
        if (!(this.vehicle instanceof BoatEntity) || !((BoatState) this.vehicle).boatqol$isSteeredHere()) {
            return;
        }
        this.yaw = (float) (this.yaw + this.vehicleYawDelta);
        this.vehicleYawDelta = 0.0;

        BoatRiding.clampLook((Entity) (Object) this);
    }
}
