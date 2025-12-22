package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This fixes a bug where the arrow sometimes does not appear to fly client-side
 * even though it works server-side when using a bow. This does not apply to crossbows.
 * However, the arrow may be launched slightly delayed.
 * Only supported in version 1.21.3 and later.
 */
public class ModuleFixBowShoot extends OCMModule {

    public ModuleFixBowShoot(OCMMain plugin) {
        super(plugin, "fix-bow-shoot");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 3)) return;
        final LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player player)) return;
        if (!isEnabled(player)) return;

        Entity proj = event.getProjectile();
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.BOW) return;

        if (proj instanceof AbstractArrow abstractArrow && !(proj instanceof Trident)) {
            World world = abstractArrow.getWorld();
            event.setProjectile(livingEntity);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity projectile = Reflector.versionIsNewerOrEqualTo(1, 21, 11) ?
                        ReflectorUtil.Projectile.spawnProjectile(world, abstractArrow, bow) :
                        ReflectorUtil.spawnProjectile(world, abstractArrow, bow);
                event.setProjectile(projectile);
            });
        }
    }
}
