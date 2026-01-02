package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.BucketUtil;
import kernitus.plugin.OldCombatMechanics.versions.BlockUtil;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * This matches the old water placement in 1.8 by placing the water
 * next to the waterlogged block instead of directly on it.
 * When using ViaVersion, this behavior is only applied to 1.9+ clients.
 */
public class ModuleOldWaterPlacement extends OCMModule {

    public ModuleOldWaterPlacement(OCMMain plugin) {
        super(plugin, "old-water-placement");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;
        if (event.isCancelled()) return;

        Block clicked = event.getBlockClicked();

        // Cauldrons handle water placement differently and should be ignored
        if (Tag.CAULDRONS.isTagged(clicked.getType())) return;

        BlockFace clickedFace = event.getBlockFace();
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(clickedFace);

        // Prevent placing water directly into non-waterlogged waterloggable blocks
        if (isWaterloggableAndNotWaterlogged(target, true)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.hasViaVersion() && ViaVersionUtil.isLegacyClient(player)) return;

        // Apply old 1.8 behavior for modern clients
        if (isWaterloggableAndNotWaterlogged(clicked, false) && BlockUtil.canPlaceFluid(target)) {
            event.setCancelled(true);
            target.setType(Material.WATER, true);
            BlockUtil.clearAdjacentLightBlocks(target);

            EquipmentSlot hand = event.getHand();
            PlayerInventory inv = player.getInventory();
            ItemStack bucket = inv.getItem(hand);

            BucketUtil.giveEmptyBucket(player, hand, bucket);

            if (BucketUtil.isAnimalBucket(bucket)) {
                BucketUtil.spawnAquaticMob(bucket, target);
            }
        }
    }

    private boolean isWaterloggableAndNotWaterlogged(Block block, boolean checkWaterState) {
        return block.getBlockData() instanceof Waterlogged waterlogged && block.getType() != Material.LIGHT
                && (!checkWaterState || !waterlogged.isWaterlogged());
    }
}