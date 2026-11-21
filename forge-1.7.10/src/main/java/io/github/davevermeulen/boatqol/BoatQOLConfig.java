package io.github.davevermeulen.boatqol;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** config/boatqol.cfg */
public final class BoatQOLConfig {
    /** Off: boats get no extra speed on ice, like vanilla 1.7.10. On: the fast 1.9+ ice slide. */
    public static boolean useModernIceSlide = false;

    private BoatQOLConfig() {
    }

    static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        useModernIceSlide = config.getBoolean("useModernIceSlide", Configuration.CATEGORY_GENERAL, false,
                "Use modern ice slide: boats glide fast on ice like 1.9+. Off keeps vanilla 1.7.10 behaviour"
                        + " (no extra speed on ice). In multiplayer the server's setting is used.");
        if (config.hasChanged()) {
            config.save();
        }
    }

    /** The setting that applies in this world: the server's own, or the one it sent us. */
    public static boolean useModernIceSlide(boolean remoteWorld) {
        return remoteWorld ? BoatQOL.proxy.remoteModernIce() : useModernIceSlide;
    }
}
