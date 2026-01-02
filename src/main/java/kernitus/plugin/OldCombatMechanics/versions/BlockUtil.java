package kernitus.plugin.OldCombatMechanics.versions;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.ModuleFixBucketPlacement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Set;

import static org.bukkit.block.BlockFace.*;

public class BlockUtil {

    private static final Set<BlockFace> faces = Set.of(NORTH, SOUTH, WEST, EAST, DOWN);

    public static boolean canPlaceFluid(Block target) {
        Material targetType = target.getType();
        return target.isReplaceable() || target.isEmpty() || targetType == Material.LIGHT;
    }

    // This method is useful to make water flow correctly for worlds with many light blocks
    public static void clearAdjacentLightBlocks(Block block) {
        faces.forEach(face -> {
            Block relBlock = block.getRelative(face);

            if (relBlock.getType() == Material.LIGHT)
                relBlock.setType(Material.AIR, false);
        });
    }

    public static void removeGhostBlock(Player player, Block block) {
        updateBlock(player, block);
        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> updateBlock(player, block));
    }

    private static void updateBlock(Player player, Block block) {
        BlockData data = block.getBlockData().clone();
        player.sendBlockChange(block.getLocation(), data);
    }

    public static void removeGhostAirAndFluid(Player player, Block block) {
        updateNearbyBlocks(player, block);

        // Run again next tick to ensure correct water flow updates
        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> updateNearbyBlocks(player, block));
    }

    /**
     * Removes ghost air and ghost fluid from all blocks in a 5x5x5 area
     * around the given block by forcing block data updates.
     */
    private static void updateNearbyBlocks(Player player, Block block) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block adjacent = block.getRelative(x, y, z);
                    ModuleFixBucketPlacement.fixAir(player, adjacent);
                    ModuleFixBucketPlacement.fixFluid(player, adjacent);
                }
            }
        }
    }
}
