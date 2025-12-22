package kernitus.plugin.OldCombatMechanics.module;

import com.viaversion.viaversion.api.Via;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerPickupArrowEvent;

import java.util.Random;

/**
 * Fixes missing arrow pickup sound for 1.8 clients on newer versions
 * Requires ViaVersion.
 */
public class ModuleFixArrowSound extends OCMModule {

    public ModuleFixArrowSound(OCMMain plugin) {
        super(plugin, "fix-arrow-sound");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        Random random = new Random();
        playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2F,
                ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    private void playSound(Location loc, Sound sound, SoundCategory category, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) return;

        world.getPlayers().stream().filter(this::isEnabled)
                .filter(this::isLegacyClient)
                .filter(player -> canHear(player, loc))
                .forEach(player -> player.playSound(loc, sound, category, volume, pitch));
    }

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    private boolean isLegacyClient(Player player) {
        return Via.getAPI().getPlayerVersion(player) <= 47;
    }

    private boolean canHear(Player player, Location loc) {
        if (!player.getWorld().equals(loc.getWorld())) return false;

        final double soundDistanceSquared = 16 * 16;
        return player.getLocation().distanceSquared(loc) <= soundDistanceSquared;
    }
}
