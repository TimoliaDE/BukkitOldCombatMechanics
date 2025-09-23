package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * This removes the projectile when it hits a living entity during the
 * 0.5 seconds invulnerability period caused by fire tick damage.
 * This matches the behavior from 1.8 pvp.
 */
public class ModuleNoDeflectFireProjectile extends OCMModule {

    public ModuleNoDeflectFireProjectile(OCMMain plugin) {
        super(plugin, "no-deflect-fire-projectile");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleProjectileHit(ProjectileHitEvent event) {
        Entity entity = event.getHitEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;
        if (!isEnabled(entity.getWorld())) return;

        Projectile proj = event.getEntity();
        @Nullable EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause != null && cause.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK &&
                entity.getFireTicks() > 0 &&
                livingEntity.getNoDamageTicks() > (float) livingEntity.getMaximumNoDamageTicks() / 2.0F) {
            event.setCancelled(true);
            proj.remove();
        }
    }
}
