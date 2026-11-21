package io.github.davevermeulen.boatqol.mixin.client;

import io.github.davevermeulen.boatqol.BoatRiding;
import net.minecraft.core.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the boat look limit on every mouse move (1.12 does this in Entity.turn). */
@Mixin(Entity.class)
public abstract class EntityClientMixin {
    @Inject(method = "interpolateTurn", at = @At("TAIL"))
    private void boatqol$clampLook(float yRot, float xRot, CallbackInfo ci) {
        BoatRiding.clampLook((Entity) (Object) this);
    }
}
