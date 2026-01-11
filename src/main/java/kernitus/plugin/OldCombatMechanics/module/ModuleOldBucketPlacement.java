package kernitus.plugin.OldCombatMechanics.module;

import com.mojang.datafixers.util.Pair;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.BucketUtil;
import kernitus.plugin.OldCombatMechanics.versions.BlockUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Matches water placement behavior of version 1.8 by placing water
 * next to a waterlogged block instead of directly on it.
 * Also fixes water placement for 1.8 clients when using newer server versions.
 */
public class ModuleOldBucketPlacement extends OCMModule {

    private static final Map<UUID, ItemStack> filledBuckets = new HashMap<>();
    private static final Map<UUID, Pair<ItemStack, Material>> emptyBuckets = new HashMap<>();

    public ModuleOldBucketPlacement(OCMMain plugin) {
        super(plugin, "old-bucket-placement");
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
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        Action action = event.getAction();
        if (!action.isRightClick()) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Block clicked = event.getClickedBlock();
        if (clicked != null && Tag.CAULDRONS.isTagged(clicked.getType())) return;

        UUID uuid = player.getUniqueId();
        ItemStack filledBucket = filledBuckets.remove(uuid);
        EquipmentSlot hand = event.getHand();

        if (filledBucket != null) {
            BucketUtil.giveEmptyBucket(player, hand, filledBucket, true);
            return;
        }

        Pair<ItemStack, Material> emptyBucket = emptyBuckets.remove(uuid);
        if (emptyBucket != null) {
            BucketUtil.giveFilledBucket(player, hand, emptyBucket.getFirst(), emptyBucket.getSecond(), true);
            return;
        }

        Material itemType = event.getMaterial();
        boolean usedFilled = isFilledBucket(item);
        boolean usedEmpty = isEmptyBucket(itemType);

        if (usedFilled || usedEmpty) {
            Block target = getSelectedTarget(event, clicked);

            if (target == null) return;

            if (usedFilled) {
                handleFilledBucket(event, target);
            } else {
                handleEmptyBucket(event, target);
            }
        }
    }

    private Block getSelectedTarget(PlayerInteractEvent event, Block clicked) {
        Player player = event.getPlayer();
        FluidCollisionMode collisionMode = getCollisionMode(event.getItem());

        Block rayTraceTarget = getRayTraceTarget(player, collisionMode, 4.5, false);

        if (clicked == null || rayTraceTarget == null) {
            return getRayTraceTarget(player, collisionMode, 4.99969, player.isSneaking());
        }

        if (collisionMode == FluidCollisionMode.SOURCE_ONLY && hasSourceFluid(rayTraceTarget)) {
            return rayTraceTarget;
        }

        return getPlacementBlock(event, clicked);
    }

    private FluidCollisionMode getCollisionMode(ItemStack item) {
        return isFilledBucket(item) ? FluidCollisionMode.NEVER : FluidCollisionMode.SOURCE_ONLY;
    }

    private Block getPlacementBlock(PlayerInteractEvent event, Block clicked) {
        return clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
    }

    private void handleFilledBucket(PlayerInteractEvent event, Block target) {
        Player player = event.getPlayer();

        if (target.isReplaceable() || getWaterlogged(target) != null) {
            setFluid(event, player, target);
        } else {
            event.setUseItemInHand(Event.Result.DENY);
            BlockUtil.removeGhostAirAndFluid(player, target);
        }
    }

    private void setFluid(PlayerInteractEvent event, Player player, Block target) {
        ItemStack bucket = event.getItem();
        event.setCancelled(true);

        Material fluid = getFluidType(bucket);
        boolean animalBucket = BucketUtil.isAnimalBucket(bucket);

        if (target.isReplaceable()) {
            target.setType(fluid, true);
        }

        BlockUtil.clearAdjacentLightBlocks(target);
        BlockUtil.removeGhostAirAndFluid(player, target);

        if (animalBucket) {
            BucketUtil.spawnAquaticMob(bucket, target);
        }

        UUID uuid = player.getUniqueId();
        filledBuckets.put(uuid, bucket);

        EquipmentSlot hand = event.getHand();
        player.getInventory().setItem(hand, bucket);

        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> {
            if (filledBuckets.remove(uuid) != null) {
                BucketUtil.giveEmptyBucket(player, hand, bucket, false);
            }
        });
    }

    private void handleEmptyBucket(PlayerInteractEvent event, Block target) {
        Player player = event.getPlayer();

        if (hasSourceFluid(target)) {
            removeFluid(event, player, target);
        } else {
            event.setUseItemInHand(Event.Result.DENY);
            BlockUtil.removeGhostAirAndFluid(player, target);
        }
    }

    private void removeFluid(PlayerInteractEvent event, Player player, Block target) {
        ItemStack emptyBucket = event.getItem();
        Material filledBucket = getBucketType(target);

        event.setCancelled(true);

        Waterlogged waterTarget = getWaterlogged(target);
        if (waterTarget != null) {
            waterTarget.setWaterlogged(false);
            target.setBlockData(waterTarget);
        } else {
            target.setType(Material.AIR, true);
        }

        BlockUtil.removeGhostAirAndFluid(player, target);

        UUID uuid = player.getUniqueId();
        emptyBuckets.put(uuid, new Pair<>(emptyBucket, filledBucket));

        EquipmentSlot hand = event.getHand();
        player.getInventory().setItem(hand, emptyBucket);

        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> {
            if (emptyBuckets.remove(uuid) != null) {
                BucketUtil.giveFilledBucket(player, hand, emptyBucket, filledBucket, false);
            }
        });
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
        return target.getType() == Material.WATER || getWaterlogged(target) != null ?
                Material.WATER_BUCKET : Material.LAVA_BUCKET;
    }

    private boolean isSourceFluid(Block target) {
        return (target.getType() == Material.WATER || target.getType() == Material.LAVA) &&
                target.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    private boolean hasSourceFluid(Block target) {
        return isSourceFluid(target) || getWaterlogged(target) != null;
    }

    private RayTraceResult rayTraceBlocks(Player player, double maxDistance,
                                          FluidCollisionMode fluidCollisionMode, boolean ignorePose) {
        Location eyeLocation = player.getLocation();
        eyeLocation.setY(eyeLocation.getY() + player.getEyeHeight(ignorePose));
        Vector direction = eyeLocation.getDirection();

        World world = player.getWorld();
        return world.rayTraceBlocks(eyeLocation, direction, maxDistance, fluidCollisionMode,
                false);
    }

    private Block getRayTraceTarget(Player player, FluidCollisionMode mode, double maxDistance,
                                    boolean ignorePose) {
        RayTraceResult result = rayTraceBlocks(player, maxDistance, mode, ignorePose);
        if (result == null) return null;

        Block hitBlock = result.getHitBlock();
        BlockFace hitBlockFace = result.getHitBlockFace();
        if (hitBlock == null || hitBlockFace == null) return null;

        return hasSourceFluid(hitBlock) || hitBlock.isReplaceable() ? hitBlock :
                hitBlock.getRelative(hitBlockFace);
    }

    public static void fixAir(Player player, Block block) {
        if (block.isEmpty() || block.getType() == Material.LIGHT) {
            BlockData data = block.getBlockData();
            player.sendBlockChange(block.getLocation(), data);
        }
    }

    public static void fixFluid(Player player, Block block) {
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) {
            BlockData data = block.getBlockData();
            player.sendBlockChange(block.getLocation(), data);
        }
    }

    @Nullable
    private Waterlogged getWaterlogged(Block block) {
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged() ?
                waterlogged : null;
    }

    private boolean isWaterloggableAndNotWaterlogged(Block block, boolean checkWaterState) {
        return block.getBlockData() instanceof Waterlogged waterlogged && block.getType() != Material.LIGHT
                && (!checkWaterState || !waterlogged.isWaterlogged());
    }
}