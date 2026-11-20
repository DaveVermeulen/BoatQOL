package io.github.davevermeulen.boatqol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Server side: which connected players have BoatQOL on their client. Filled in
 * from the login packet, where a modded client puts {@link #LOGIN_MAGIC} in a
 * field the vanilla client leaves at 0 and the vanilla server ignores.
 */
public final class ModdedClients {
    public static final long LOGIN_MAGIC = 0x426F6174514F4C01L; // "BoatQOL" + 1

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
        return entity instanceof PlayerEntity
                && ((PlayerEntity) entity).name != null
                && NAMES.contains(((PlayerEntity) entity).name.toLowerCase(Locale.ROOT));
    }
}
