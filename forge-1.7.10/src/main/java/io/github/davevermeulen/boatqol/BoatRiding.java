package io.github.davevermeulen.boatqol;

import net.minecraft.entity.Entity;

public final class BoatRiding {
    /** How far (degrees) a rider can look away from the boat's bow, like 1.9+. */
    public static final float MAX_LOOK = 105.0F;

    private BoatRiding() {
    }

    /** The rider's yaw relative to the bow, -180..180. A rider facing the bow has yaw = boat yaw + 90. */
    public static float relativeYaw(Entity rider, Entity boat) {
        return wrap(rider.rotationYaw - (boat.rotationYaw + 90.0F));
    }

    /** Keeps the rider's view within {@link #MAX_LOOK} of the bow. */
    public static void clampLook(Entity rider, Entity boat) {
        float rel = relativeYaw(rider, boat);
        float clamped = Math.max(-MAX_LOOK, Math.min(MAX_LOOK, rel));
        rider.prevRotationYaw += clamped - rel;
        rider.rotationYaw += clamped - rel;
    }

    public static float wrap(float degrees) {
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
