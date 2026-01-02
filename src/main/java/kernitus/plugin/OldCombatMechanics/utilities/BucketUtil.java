package kernitus.plugin.OldCombatMechanics.utilities;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.util.BoundingBox;

public class BucketUtil {

    public static boolean isAnimalBucket(ItemStack iStack) {
        ItemMeta iMeta = iStack.getItemMeta();
        Material type = iStack.getType();

        return iMeta instanceof AxolotlBucketMeta || iMeta instanceof TropicalFishBucketMeta ||
                type == Material.COD_BUCKET || type == Material.PUFFERFISH_BUCKET ||
                type == Material.SALMON_BUCKET || type == Material.TADPOLE_BUCKET;
    }

    public static void giveEmptyBucket(Player player, EquipmentSlot hand, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack emptyBucket = new ItemStack(Material.BUCKET);
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(hand, emptyBucket);

        } else {
            item.setAmount(item.getAmount() - 1);
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            sp.getInventory().placeItemBackInInventory(CraftItemStack.asNMSCopy(emptyBucket));
        }
    }

    public static void giveFilledBucket(Player player, EquipmentSlot hand, ItemStack item, Material bucket) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack filledBucket = new ItemStack(bucket);
        if (item.getAmount() <= 1) {
            player.getInventory().setItem(hand, filledBucket);

        } else {
            item.setAmount(item.getAmount() - 1);
            ServerPlayer sp = ((CraftPlayer) player).getHandle();
            sp.getInventory().placeItemBackInInventory(CraftItemStack.asNMSCopy(filledBucket));
        }
    }

    // Note: A block's effective collision height can exceed 1.0 (e.g. fences),
    // so the height of the block below is also considered (offset by -1).
    private static double getBlockHeight(Block block) {
        double currentHeight = getBlockEffectiveHeight(block);

        Block below = block.getRelative(BlockFace.DOWN);
        if (!below.isEmpty()) {
            double belowHeight = getBlockEffectiveHeight(below) - 1.0;
            return Math.max(currentHeight, belowHeight);
        }

        return currentHeight;
    }

    private static double getBlockEffectiveHeight(Block block) {
        return Math.max(block.getBoundingBox().getHeight(),
                block.getCollisionShape().getBoundingBoxes().stream()
                        .mapToDouble(BoundingBox::getHeight)
                        .max()
                        .orElse(0.0));
    }

    public static void spawnAquaticMob(ItemStack iStack, Block block) {
        ItemMeta iMeta = iStack.getItemMeta();
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0, 0.5);
        loc.setY(block.getY() + getBlockHeight(block));
        Material type = iStack.getType();

        if (iMeta instanceof AxolotlBucketMeta axolotlMeta) {
            world.spawn(loc, Axolotl.class, CreatureSpawnEvent.SpawnReason.BUCKET, mob -> {
                mob.setFromBucket(true);
                ((CraftMob) mob).getHandle().playAmbientSound();

                if (axolotlMeta.hasVariant())
                    mob.setVariant(axolotlMeta.getVariant());
            });

        } else if (iMeta instanceof TropicalFishBucketMeta fishMeta) {
            world.spawn(loc, TropicalFish.class, CreatureSpawnEvent.SpawnReason.BUCKET, mob -> {
                mob.setFromBucket(true);
                ((CraftMob) mob).getHandle().playAmbientSound();

                if (fishMeta.hasVariant()) {
                    mob.setPattern(fishMeta.getPattern());
                    mob.setBodyColor(fishMeta.getBodyColor());
                    mob.setPatternColor(fishMeta.getPatternColor());
                }
            });

        } else if (type == Material.COD_BUCKET) {
            world.spawn(loc, Cod.class, CreatureSpawnEvent.SpawnReason.BUCKET);

        } else if (type == Material.PUFFERFISH_BUCKET) {
            world.spawn(loc, PufferFish.class, CreatureSpawnEvent.SpawnReason.BUCKET);

        } else if (type == Material.SALMON_BUCKET) {
            world.spawn(loc, Salmon.class, CreatureSpawnEvent.SpawnReason.BUCKET);

        } else if (type == Material.TADPOLE_BUCKET) {
            world.spawn(loc, Tadpole.class, CreatureSpawnEvent.SpawnReason.BUCKET);
        }
    }
}
