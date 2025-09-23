package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XEnchantment;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 *  This matches the bow damage to 1.8 pvp.
 */
public class ModuleOldBowDamage extends OCMModule {

    private boolean includeCrossbow = false;

    public ModuleOldBowDamage(OCMMain plugin) {
        super(plugin, "old-bow-damage");
        reload();
    }

    @Override
    public void reload() {
        includeCrossbow = module().getBoolean("includeCrossbow", false);
    }

    // Ensure that this event will be called before the event on the ModuleFixBowShoot event by
    // changing the EventPriority to HIGH instead of HIGHEST
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityShootBow(EntityShootBowEvent event) {
        final LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player player)) return;
        if (!isEnabled(player)) return;
        if (event.isCancelled()) return;

        Entity proj = event.getProjectile();
        ItemStack bow = event.getBow();
        if (bow == null) return;

        if ((bow.getType() == Material.BOW || includeCrossbow && bow.getType() == Material.CROSSBOW) &&
                proj instanceof AbstractArrow abstractArrow && !(proj instanceof Trident)) {
            int power = bow.getEnchantmentLevel(XEnchantment.POWER.get());
            float force = event.getForce();

            // 1.20: 1
            // 1.20.1: 1
            // 1.20.2: 1
            // 1.20.4: 1
            // 1.20.5: 3
            // 1.21.4: 3
            // 1.21.5: 1
            // 1.21.6: 1
            // 1.21.7: 1
            // 1.21.8: 1

            System.out.println("Force vor: " + force);

            // Since 1.20.5?
            // For the versions 1.20.5 to 1.21.4, the force was changed from [0,3] to [0,1]
            if (Reflector.versionIsNewerOrEqualTo(1, 20, 5) &&
                    !Reflector.versionIsNewerOrEqualTo(1, 21, 5)) {
                force /= 3.0F;
            }

            System.out.println("Force nach: " + force);

            double powerDmg = power > 0 ? (double) power * 0.5 + 0.5 : 0;
            abstractArrow.setDamage(force * 2.0 + powerDmg);
            abstractArrow.setCritical(force >= 1.0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile proj)) return;
        if (!(proj.getShooter() instanceof Player player)) return;
        if (!isEnabled(player)) return;
        if (event.isCancelled()) return;

        if (proj instanceof AbstractArrow abstractArrow && !(proj instanceof Trident)) {
            boolean fromBow;
            if (Reflector.versionIsNewerOrEqualTo(1, 21, 0)) {
                final ItemStack weapon = abstractArrow.getWeapon();
                fromBow = weapon != null && (weapon.getType() == Material.BOW ||
                    includeCrossbow && weapon.getType() == Material.CROSSBOW);
            } else
                fromBow = !abstractArrow.isShotFromCrossbow() || includeCrossbow;

            if (fromBow) {
                float velocityLength = (float) abstractArrow.getVelocity().length();
                int finalDamage = (int) Math.ceil((double) velocityLength * abstractArrow.getDamage());

                if (abstractArrow.isCritical())
                    finalDamage += new Random().nextInt(finalDamage / 2 + 2);

                event.setDamage(finalDamage);
            }
        }
    }
}
