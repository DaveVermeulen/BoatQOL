package io.github.davevermeulen.boatqol;

import net.minecraft.entity.Entity;

public class CommonProxy {
    public void init() {
    }

    /**
     * The rider's input as {forward, sideways}, if this game instance is the
     * rider's client; null otherwise. Positive sideways means left.
     */
    public float[] localInput(Entity rider) {
        return null;
    }

    /** Client: the server's "use modern ice slide" setting. */
    public boolean remoteModernIce() {
        return false;
    }

    public void setRemoteModernIce(boolean value) {
    }
}
