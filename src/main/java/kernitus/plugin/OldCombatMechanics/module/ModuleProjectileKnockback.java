/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Adds knockback to eggs, snowballs and ender pearls.
 */
public class ModuleProjectileKnockback extends OCMModule {

    private boolean noDamageWhenBlocking;

    public ModuleProjectileKnockback(OCMMain plugin) {
        super(plugin, "projectile-knockback");
        reload();
    }

    @Override
    public void reload() {
        noDamageWhenBlocking = module().getBoolean("noDamageWhenBlocking", true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity damagee = event.getEntity();
        if (!isEnabled(damager, damagee)) return;

        if (noDamageWhenBlocking && damagee instanceof Player player && isBlockingWithVanillaShield(player))
            return;

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

    // Check if the player is currently using the vanilla shield
    private boolean isBlockingWithVanillaShield(Player player) {
        ItemStack activeItem = player.getActiveItem();
        ItemMeta iMeta = activeItem.getItemMeta();
        boolean hasNoModuleShield = iMeta == null ||
                !iMeta.getPersistentDataContainer().has(ModuleSwordBlocking.KEY, PersistentDataType.STRING);
        return player.isBlocking() && activeItem.getType() == Material.SHIELD && hasNoModuleShield;
    }
}