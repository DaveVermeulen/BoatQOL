package io.github.davevermeulen.boatqol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;

/**
 * Server side: which connected players have BoatQOL on their client. A client
 * says so by answering the server's "BoatQOL:Config" payload with "BoatQOL:Hello".
 */
public final class ModdedClients {
    private static final Set<String> NAMES = Collections.synchronizedSet(new HashSet<String>());

    private ModdedClients() {
    }

    public static void set(String name, boolean modded) {
        if (name == null) {
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (modded) {
            NAMES.add(key);
        } else {
            NAMES.remove(key);
        }
    }

    public static boolean isModded(Entity entity) {
        return entity instanceof Player
                && ((Player) entity).username != null
                && NAMES.contains(((Player) entity).username.toLowerCase(Locale.ROOT));
    }
}
