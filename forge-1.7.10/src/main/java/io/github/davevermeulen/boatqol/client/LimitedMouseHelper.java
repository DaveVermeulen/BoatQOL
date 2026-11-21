package io.github.davevermeulen.boatqol.client;

import io.github.davevermeulen.boatqol.BoatRiding;
import io.github.davevermeulen.boatqol.ModernBoat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MouseHelper;

/**
 * Stops the view at the boat's 105 degree look limit. The mouse movement is cut
 * before it turns the player, so the view never overshoots the limit and snaps
 * back (which looks like jitter).
 */
public class LimitedMouseHelper extends MouseHelper {
    private final Minecraft mc;

    public LimitedMouseHelper(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public void mouseXYChange() {
        super.mouseXYChange();
        EntityPlayerSP player = this.mc.thePlayer;
        if (player == null || !(player.ridingEntity instanceof ModernBoat)
                || !((ModernBoat) player.ridingEntity).isSteeredHere() || this.mc.gameSettings.smoothCamera) {
            return;
        }
        // Same scaling as EntityRenderer.updateCameraAndRender and Entity.setAngles.
        float s = this.mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float degreesPerUnit = s * s * s * 8.0F * 0.15F;
        float rel = BoatRiding.relativeYaw(player, player.ridingEntity);
        float after = rel + this.deltaX * degreesPerUnit;
        if (after > BoatRiding.MAX_LOOK) {
            this.deltaX = Math.max(0, (int) ((BoatRiding.MAX_LOOK - rel) / degreesPerUnit));
        } else if (after < -BoatRiding.MAX_LOOK) {
            this.deltaX = Math.min(0, (int) ((-BoatRiding.MAX_LOOK - rel) / degreesPerUnit));
        }
    }
}
