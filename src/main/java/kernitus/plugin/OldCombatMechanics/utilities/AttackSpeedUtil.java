package kernitus.plugin.OldCombatMechanics.utilities;

import net.minecraft.core.component.DataComponents;
import org.bukkit.inventory.ItemStack;

public class AttackSpeedUtil {

    public static double getMinimumAttackCharge(ItemStack item) {
        if (item == null || !item.getType().name().endsWith("_SPEAR")) {
            return 1.0;
        }

        net.minecraft.world.item.ItemStack nmsItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        Float minimumAttackCharge = nmsItem.get(DataComponents.MINIMUM_ATTACK_CHARGE);
        return minimumAttackCharge != null ? minimumAttackCharge : 1.0;
    }

    public static void setMinimumAttackCharge(ItemStack item, double minimumAttackCharge) {
        if (item == null || !item.getType().name().endsWith("_SPEAR")) {
            return;
        }

        minimumAttackCharge = Math.clamp(minimumAttackCharge, 0.0, 1.0);
        net.minecraft.world.item.ItemStack nmsItem =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        nmsItem.set(DataComponents.MINIMUM_ATTACK_CHARGE, (float) minimumAttackCharge);

        org.bukkit.inventory.ItemStack updated =
                org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsItem);
        item.setItemMeta(updated.getItemMeta());
    }
}
