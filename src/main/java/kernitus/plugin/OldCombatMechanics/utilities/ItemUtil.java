package kernitus.plugin.OldCombatMechanics.utilities;

import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.inventory.ItemStack;

// Delegate these functions to this class instead of the class "ModuleSwordBlocking" because it will not load
// because the package "net.minecraft.core.component" does not exist
public class ItemUtil {

    public static boolean hasBlockHit(ItemStack iStack) {
        return ReflectorUtil.hasBlocksAttacks(iStack);
    }

    // Note: The factor 0.0001F is used instead of 0.5f or 0.33f because the damage reduction is handled in
    // the class "ModuleShieldDamageReduction"
    public static void addWeaponAttributes(ItemStack iStack) {
        DamageReduction.Builder builder = DamageReduction.damageReduction();
        DamageReduction damageReduction = builder.horizontalBlockingAngle(90).type(null)
                .base(0).factor(0.0001F).build();

        io.papermc.paper.datacomponent.item.BlocksAttacks blocksAttacks =
                io.papermc.paper.datacomponent.item.BlocksAttacks.blocksAttacks()
                        .blockDelaySeconds(0)
                        .disableCooldownScale(0)
                        .addDamageReduction(damageReduction)
                        .bypassedBy(null)
                        .blockSound(null)
                        .disableSound(null).build();

        ReflectorUtil.setBlocksAttacks(iStack, blocksAttacks);
    }
}
