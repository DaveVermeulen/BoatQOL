package io.github.davevermeulen.boatqol;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;

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
        Entity vehicle = rider.vehicle;
        if (!(vehicle instanceof BoatEntity) || !((BoatState) vehicle).boatqol$isSteeredHere()) {
            return;
        }
        // A rider facing the old boat's bow has yaw = boat yaw + 90.
        float facing = vehicle.yaw + 90.0F;
        float rel = wrap(rider.yaw - facing);
        float clamped = Math.max(-MAX_LOOK, Math.min(MAX_LOOK, rel));
        rider.prevYaw += clamped - rel;
        rider.yaw += clamped - rel;
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
