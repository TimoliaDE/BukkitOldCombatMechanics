/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;

/**
 * Adds knockback to eggs, snowballs and ender pearls.
 */
public class ModuleProjectileKnockback extends OCMModule {

    public ModuleProjectileKnockback(OCMMain plugin) {
        super(plugin, "projectile-knockback");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity damagee = event.getEntity();
        if (!isEnabled(damager, damagee)) return;

        final EntityType type = damager.getType();

        switch (type) {
            case SNOWBALL: case EGG: case ENDER_PEARL:
                if (event.getDamage() == 0.0) { // So we don't override enderpearl fall damage
                    event.setDamage(module().getDouble("damage." + type.toString().toLowerCase(Locale.ROOT)));
                    if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
                        event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, 0);
                }
        }
    }
}