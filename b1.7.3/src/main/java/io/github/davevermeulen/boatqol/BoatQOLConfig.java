package io.github.davevermeulen.boatqol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** config/boatqol.properties */
public final class BoatQOLConfig {
    private static final String ICE_KEY = "useModernIceSlide";

    /**
     * false (default): ice is treated like any other block, which is how the
     * Beta 1.7.3 boat behaves. true: ice uses its real slipperiness (0.98), giving
     * the fast 1.9+ ice slide.
     */
    public static boolean useModernIceSlide = false;

    private BoatQOLConfig() {
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("boatqol.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                BoatQOL.LOGGER.warn("Could not read {}, using defaults", file, e);
            }
        }
        useModernIceSlide = Boolean.parseBoolean(props.getProperty(ICE_KEY, "false").trim());
        props.setProperty(ICE_KEY, Boolean.toString(useModernIceSlide));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "BoatQOL\n"
                        + " useModernIceSlide: true makes boats slide fast on ice like 1.9+.\n"
                        + " false keeps Beta 1.7.3 behaviour (ice is no faster than other blocks).\n"
                        + " In multiplayer the server's value is used.");
            }
        } catch (IOException e) {
            BoatQOL.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
