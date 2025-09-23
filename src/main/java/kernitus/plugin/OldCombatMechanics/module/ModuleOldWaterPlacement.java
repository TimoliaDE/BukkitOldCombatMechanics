package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

import static org.bukkit.block.BlockFace.*;

/**
 *  This matches the old water placement in 1.8 by placing the water next to
 *  the waterlogged block instead of directly on it.
 */
public class ModuleOldWaterPlacement extends OCMModule {

    private static final Set<BlockFace> faces = Set.of(NORTH, SOUTH, WEST, EAST, DOWN);

    public ModuleOldWaterPlacement(OCMMain plugin) {
        super(plugin, "old-water-placement");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handlePlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        boolean usingWater = event.getBucket() == Material.WATER_BUCKET;
        boolean isWaterlogged = block.getBlockData() instanceof Waterlogged;
        boolean hasOffset = usingWater && isWaterlogged && block.getType() != Material.LIGHT;

        // The following if-Blocks considers that no block can be placed on water

        if (hasOffset) {
            Block relBlock = block.getRelative(event.getBlockFace());
            event.setCancelled(true);

            if (relBlock.isEmpty() || relBlock.getType() == Material.LIGHT) {
                relBlock.setType(Material.WATER, true);
                clearAdjacentLightBlocks(relBlock);

                ItemStack iStack = event.getItemStack();
                if (iStack != null && player.getGameMode() != GameMode.CREATIVE) {
                    iStack.setType(Material.BUCKET);
                    // This method is used instead of "setItemStack" because the event is cancelled
                    player.getInventory().setItem(event.getHand(), iStack);
                }
            }
        }
    }

    // This method is useful to make water flow correctly for worlds with many light blocks
    private void clearAdjacentLightBlocks(Block block) {
        faces.forEach(face -> {
            Block relBlock = block.getRelative(face);

            if (relBlock.getType() == Material.LIGHT)
                relBlock.setType(Material.AIR, false);
        });
    }
}
