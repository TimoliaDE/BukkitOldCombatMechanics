package kernitus.plugin.OldCombatMechanics.utilities;

import com.destroystokyo.paper.MaterialTags;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

// Delegate these functions to this class instead of the class "ModuleSwordBlocking" because it will not load
// because the package "net.minecraft.core.component" does not exist
public class ItemUtil {

    public static boolean isConsumableSword(ItemStack iStack) {
        return Reflector.versionIsNewerOrEqualTo(1, 21, 3) &&
                MaterialTags.SWORDS.isTagged(iStack.getType()) && ReflectorUtil.hasConsumable(iStack);
    }

    public static boolean isHoldingConsumableSword(Player player, boolean isOffhand) {
        PlayerInventory inv = player.getInventory();
        return isConsumableSword(isOffhand ? inv.getItemInOffHand() : inv.getItemInMainHand());
    }

    public static boolean isUsingConsumableSword(Player player) {
        return isConsumableSword(player.getActiveItem());
    }

    /*
     * Note: To simulate blocking, the "consumable" attribute is added to the sword
     * instead of the "blocks_attacks" attribute. This ensures that knockback is applied
     * while blocking with the sword, and that the damage sound and animation are played.
     */
    public static void addWeaponAttributes(ItemStack iStack) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 3)) return;

        Consumable.Builder builder = Consumable.consumable();
        Consumable consumable = builder.consumeSeconds(Float.MAX_VALUE)
                .animation(ItemUseAnimation.BLOCK)
                .sound(new NamespacedKey(OCMMain.getInstance(), "no_sound"))
                .hasConsumeParticles(false).build();
        ReflectorUtil.setConsumable(iStack, consumable);
    }

    public static void removeWeaponAttributes(ItemStack iStack) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 3)) return;

        ReflectorUtil.setConsumable(iStack, null);
    }
}