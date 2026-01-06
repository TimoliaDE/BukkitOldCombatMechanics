package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.BucketUtil;
import kernitus.plugin.OldCombatMechanics.versions.BlockUtil;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Fixes visible ghost water for 1.8 clients when placing
 * water slightly outside the normal placement range.
 * Also corrects water placement one block away from the
 * intended block, especially while sneaking on 1.8 clients.
 * Requires ViaVersion.
 */
public class ModuleFixBucketPlacement extends OCMModule {

    public ModuleFixBucketPlacement(OCMMain plugin) {
        super(plugin, "fix-bucket-placement");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!ViaVersionUtil.isLegacyClient(player)) return;
        if (!event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Material itemType = event.getMaterial();
        if (isFilledBucket(item)) {
            handleFilledBucket(event, player, item);
        } else if (isEmptyBucket(itemType)) {
            handleEmptyBucket(event, player, item);
        }
    }

    private void handleFilledBucket(PlayerInteractEvent event, Player player, ItemStack item) {
        Block clicked = event.getClickedBlock();

        if (clicked != null) {
            handleFilledOnBlock(event, player, clicked);
        } else {
            handleFilledInAir(event, player);
        }
    }

    private void handleFilledOnBlock(PlayerInteractEvent event, Player player, Block clicked) {
        if (Tag.CAULDRONS.isTagged(clicked.getType())) return;

        BlockFace face = event.getBlockFace();
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        ItemStack bucket = event.getItem();

        if (isSourceFluid(target, bucket)) return;
        if (!BlockUtil.canPlaceFluid(target)) return;

        event.setCancelled(true);
        Material fluid = getFluidType(bucket);
        boolean animalBucket = BucketUtil.isAnimalBucket(bucket);
        BucketUtil.giveEmptyBucket(player, event.getHand(), bucket);

        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> {
            target.setType(fluid, true);
            BlockUtil.removeGhostAirAndFluid(player, target);

            if (animalBucket) {
                BucketUtil.spawnAquaticMob(bucket, target);
            }
        });
    }

    private void handleFilledInAir(PlayerInteractEvent event, Player player) {
        Block target = getRaytraceTarget(player);
        if (target == null || !target.isEmpty()) return;

        event.setUseItemInHand(Event.Result.DENY);
        BlockUtil.removeGhostAirAndFluid(player, target);
    }

    private void handleEmptyBucket(PlayerInteractEvent event, Player player, ItemStack item) {
        Block clicked = event.getClickedBlock();

        if (clicked != null) {
            handleEmptyOnBlock(event, player, item, clicked);
        } else {
            handleEmptyInAir(event, player);
        }
    }

    private void handleEmptyOnBlock(PlayerInteractEvent event, Player player, ItemStack item, Block clicked) {
        if (Tag.CAULDRONS.isTagged(clicked.getType())) return;

        BlockFace face = event.getBlockFace();
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        Material bucket = getBucketType(target);

        if (!isSourceFluid(target)) return;

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> {
            // The following method should be within this task because otherwise
            // when the player sneaks, the water is replaced again
            BucketUtil.giveFilledBucket(player, event.getHand(), item, bucket);
            target.setType(Material.AIR, true);
            BlockUtil.removeGhostAirAndFluid(player, target);
        });
    }

    private void handleEmptyInAir(PlayerInteractEvent event, Player player) {
        Block target = getRaytraceTarget(player);
        if (target == null) return;

        if (!isSourceFluid(target)) return;

        event.setUseItemInHand(Event.Result.DENY);
        BlockUtil.removeGhostAirAndFluid(player, target);
    }

    private boolean isFilledBucket(ItemStack iStack) {
        Material itemType = iStack.getType();
        return itemType == Material.WATER_BUCKET || itemType == Material.LAVA_BUCKET ||
                BucketUtil.isAnimalBucket(iStack);
    }

    private boolean isEmptyBucket(Material itemType) {
        return itemType == Material.BUCKET;
    }

    private Material getFluidType(ItemStack iStack) {
        return iStack.getType() == Material.WATER_BUCKET || BucketUtil.isAnimalBucket(iStack) ?
                Material.WATER : Material.LAVA;
    }

    private Material getBucketType(Block target) {
        return target.getType() == Material.WATER ? Material.WATER_BUCKET : Material.LAVA_BUCKET;
    }

    private boolean isSourceFluid(Block target, ItemStack iStack) {
        Material fluid = getFluidType(iStack);
        return target.getType() == fluid && target.getBlockData() instanceof Levelled levelled &&
                levelled.getLevel() == 0;
    }

    private boolean isSourceFluid(Block target) {
        return (target.getType() == Material.WATER || target.getType() == Material.LAVA) &&
                target.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    private Block getRaytraceTarget(Player player) {
        RayTraceResult result = player.rayTraceBlocks(5);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) return null;
        return result.getHitBlock().getRelative(result.getHitBlockFace());
    }

    public static void fixAir(Player player, Block block) {
        if (block.isEmpty() || block.getType() == Material.LIGHT) {
            BlockData data = block.getBlockData().clone();
            player.sendBlockChange(block.getLocation(), data);
        }
    }

    public static void fixFluid(Player player, Block block) {
        if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
            BlockData data = block.getBlockData().clone();
            player.sendBlockChange(block.getLocation(), data);
        }
    }
}