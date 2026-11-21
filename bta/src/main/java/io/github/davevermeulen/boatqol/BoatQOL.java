package io.github.davevermeulen.boatqol;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoatQOL implements ModInitializer {
    public static final String MOD_ID = "boatqol";
    public static final Logger LOGGER = LoggerFactory.getLogger("BoatQOL");

    /**
     * Gives the movement input of the player on this game client, or null when
     * the entity isn't that player. Set by the client entrypoint; always null on
     * a dedicated server.
     */
    public static volatile LocalInput localInput = rider -> null;

    @Override
    public void onInitialize() {
        BoatQOLConfig.load();
        LOGGER.info("BoatQOL loaded (use modern ice slide: {})", BoatQOLConfig.useModernIceSlide);
    }

    public interface LocalInput {
        /** @return {forward, sideways} with sideways > 0 meaning left, or null. */
        float[] get(Entity rider);
    }
}
