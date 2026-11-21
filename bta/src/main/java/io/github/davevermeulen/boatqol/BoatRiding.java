package io.github.davevermeulen.boatqol;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.vehicle.EntityBoat;

public final class BoatRiding {
    /** How far (degrees) a rider can look away from the boat's bow, like 1.9+. */
    public static final float MAX_LOOK = 105.0F;

    private BoatRiding() {
    }

    /**
     * Keeps the rider's view within {@link #MAX_LOOK} of the bow, if this game
     * instance steers the boat. Called every tick and on every mouse move, so the
     * view stops at the limit instead of overshooting and snapping back.
     */
    public static void clampLook(Entity rider) {
        if (!(rider.vehicle instanceof EntityBoat)) {
            return;
        }
        EntityBoat boat = (EntityBoat) rider.vehicle;
        if (!((BoatState) boat).boatqol$isSteeredHere()) {
            return;
        }
        // A rider facing the boat's bow has yRot = boat yRot + 90.
        float facing = boat.yRot + 90.0F;
        float rel = wrap(rider.yRot - facing);
        float clamped = Math.max(-MAX_LOOK, Math.min(MAX_LOOK, rel));
        rider.yRotO += clamped - rel;
        rider.yRot += clamped - rel;
    }

    private static float wrap(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) {
            degrees -= 360.0F;
        }
        if (degrees < -180.0F) {
            degrees += 360.0F;
        }
        return degrees;
    }
}
