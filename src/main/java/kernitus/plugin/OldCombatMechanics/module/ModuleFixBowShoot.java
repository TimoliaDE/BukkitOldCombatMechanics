package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftAbstractArrow;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * This fixes a bug where the arrow sometimes does not appear to fly client-side
 * even though it works server-side when using a bow. This does not apply to crossbows.
 * However, the arrow may be launched slightly delayed.
 * If enabled, the shooting location will be adapted to match 1.8 behavior when using default offset values.
 * Requires server version 1.21.3 or later.
 */
public class ModuleFixBowShoot extends OCMModule {

    private double xOffset;
    private double yOffset;
    private double yOffsetSneak;
    private boolean sneakOffsetModern;

    public ModuleFixBowShoot(OCMMain plugin) {
        super(plugin, "fix-bow-shoot");
    }

    @Override
    public void reload() {
        xOffset = module().getDouble("zOffset", 0.16);
        yOffset = module().getDouble("yOffset", -0.1);
        yOffsetSneak = module().getDouble("yOffsetSneak", 0.17);
        sneakOffsetModern = module().getBoolean("sneakOffsetModern", false);
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
            Location newLoc = getArrowSpawnLocation(player);

            Bukkit.getScheduler().runTask(plugin, () -> {
                net.minecraft.world.entity.projectile.arrow.AbstractArrow nmsArrow =
                        ((CraftAbstractArrow) abstractArrow).getHandle();
                nmsArrow.setPosRaw(newLoc.getX(), newLoc.getY(), newLoc.getZ());

                Entity projectile = Reflector.versionIsNewerOrEqualTo(1, 21, 11) ?
                        ReflectorUtil.Projectile.spawnProjectile(world, abstractArrow, bow) :
                        ReflectorUtil.spawnProjectile(world, abstractArrow, bow);
                event.setProjectile(projectile);
            });
        }
    }

    private Location getArrowSpawnLocation(Player player) {
        Location loc = player.getEyeLocation();
        Vector right = loc.getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize();

        double finalYOffset = player.isSneaking() &&
                (ViaVersionUtil.isLegacyClientsAllowed() || sneakOffsetModern)
                ? yOffsetSneak
                : yOffset;

        return loc.clone().add(0, finalYOffset, 0).add(right.multiply(xOffset));
    }
}
