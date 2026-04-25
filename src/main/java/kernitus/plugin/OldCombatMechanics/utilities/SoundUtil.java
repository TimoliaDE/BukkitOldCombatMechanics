package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public class SoundUtil {

    public static void playSound(OCMModule module, Location loc, String soundName, SoundCategory category,
                                 float volume, float pitch, Consumer<UUID> prepareFunction) {
        World world = loc.getWorld();
        if (world == null) return;

        world.getPlayers().stream().filter(module::isEnabled)
                .filter(ViaVersionUtil::isLegacyClient)
                .filter(player -> canHear(player, loc))
                .forEach(player -> {
                    prepareFunction.accept(player.getUniqueId());
                    player.playSound(loc, soundName, category, volume, pitch);
                });
    }

    public static void playSound(OCMModule module, Location loc, Sound sound, SoundCategory category,
                          float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) return;

        world.getPlayers().stream().filter(module::isEnabled)
                .filter(ViaVersionUtil::isLegacyClient)
                .filter(player -> canHear(player, loc))
                .forEach(player -> player.playSound(loc, sound, category, volume, pitch));
    }

    private static boolean canHear(Player player, Location loc) {
        if (!player.getWorld().equals(loc.getWorld())) return false;

        final double soundDistanceSquared = 16 * 16;
        return player.getLocation().distanceSquared(loc) <= soundDistanceSquared;
    }
}
