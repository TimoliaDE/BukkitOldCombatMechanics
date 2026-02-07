package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.versions.BlockUtil;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Prevents removed ghost blocks from appearing for
 * 1.8 clients when a block break action is cancelled.
 * Requires ViaVersion.
 */
public class ModuleFixBlockBreak extends OCMModule {

    public ModuleFixBlockBreak(OCMMain plugin) {
        super(plugin, "fix-block-break");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        if (event.isCancelled() && ViaVersionUtil.isLegacyClient(player)) {
            Block block = event.getBlock();
            BlockUtil.removeGhostBlock(player, block);
        }
    }
}