package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.SoundUtil;
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
        SoundUtil.playSound(this, player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }
}
