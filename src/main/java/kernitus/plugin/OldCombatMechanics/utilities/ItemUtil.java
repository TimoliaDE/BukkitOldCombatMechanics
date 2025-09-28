package kernitus.plugin.OldCombatMechanics.utilities;

import com.destroystokyo.paper.MaterialTags;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

// Delegate these functions to this class instead of the class "ModuleSwordBlocking" because it will not load
// because the package "net.minecraft.core.component" does not exist
public class ItemUtil {

    public static boolean isBlockingWithSword(ItemStack iStack) {
        boolean isLeast_1_21_5 = Reflector.versionIsNewerOrEqualTo(1, 21, 5);
        return isLeast_1_21_5 && MaterialTags.SWORDS.isTagged(iStack.getType()) &&
                        ReflectorUtil.hasBlocksAttacks(iStack) || !isLeast_1_21_5 && isConsumableSword(iStack);
    }

    public static boolean isConsumableSword(ItemStack iStack) {
        return Reflector.versionIsNewerOrEqualTo(1, 21, 3) &&
                MaterialTags.SWORDS.isTagged(iStack.getType()) && ReflectorUtil.hasConsumable(iStack);
    }

    // Note: The factor 0.0001F is used instead of 0.5f or 0.33f because the damage reduction is handled in
    // the class "ModuleShieldDamageReduction"
    public static void addWeaponAttributes(ItemStack iStack) {
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 5)) {
            DamageReduction.Builder builder = DamageReduction.damageReduction();
            DamageReduction damageReduction = builder.horizontalBlockingAngle(90).type(null)
                    .base(0).factor(0.0001F).build();

            // The ItemDamageFunction ensures that the sword does not lose any durability, just as in 1.8
            ItemDamageFunction.Builder damageBuilder = ItemDamageFunction.itemDamageFunction();
            ItemDamageFunction damageFunction = damageBuilder.threshold(Float.MAX_VALUE)
                    .base(0).factor(0).build();

            io.papermc.paper.datacomponent.item.BlocksAttacks blocksAttacks =
                    io.papermc.paper.datacomponent.item.BlocksAttacks.blocksAttacks()
                            .blockDelaySeconds(0)
                            .disableCooldownScale(0)
                            .addDamageReduction(damageReduction)
                            .bypassedBy(null)
                            .blockSound(null)
                            .disableSound(null)
                            .itemDamage(damageFunction).build();

            ReflectorUtil.setBlocksAttacks(iStack, blocksAttacks);
            return;
        }

        Consumable.Builder builder = Consumable.consumable();
        Consumable consumable = builder.consumeSeconds(Float.MAX_VALUE)
                .animation(ItemUseAnimation.BLOCK)
                .sound(new NamespacedKey(OCMMain.getInstance(), "no_sound"))
                .hasConsumeParticles(false).build();
        ReflectorUtil.setConsumable(iStack, consumable);
    }

    public static void removeWeaponAttributes(ItemStack iStack) {
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 5)) {
            ReflectorUtil.setBlocksAttacks(iStack, null);
        } else
            ReflectorUtil.setConsumable(iStack, null);
    }
}