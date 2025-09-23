package kernitus.plugin.OldCombatMechanics.module;

import io.papermc.paper.configuration.WorldConfiguration;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;

/**
 * This disables the relative projectile velocity. In other words,
 * the projectile will not be affected by the player's movement,
 * such as jumping.
 */
public class ModuleOldProjectileTrajectory extends OCMModule {

    public ModuleOldProjectileTrajectory(OCMMain plugin) {
        super(plugin, "old-projectile-trajectory");
    }

    // Ensure that this event will be called before the event on the ModuleFixBowShoot event by
    // changing the EventPriority to HIGH instead of HIGHEST
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityShootBow(EntityShootBowEvent event) {
        final LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player player)) return;
        if (!isEnabled(player)) return;
        if (event.isCancelled()) return;

        if (hasDisableRelativeProjectileVelocity(player.getWorld())) return;

        ItemStack bow = event.getBow();
        if (bow != null && bow.getType() == Material.BOW)
            changeMovement(player, event.getProjectile());
    }

    private void changeMovement(Player player, Entity proj) {
        Vec3 knownMovement = getMovement(player);
        double x = knownMovement.x();
        double y = knownMovement.y();
        double z = knownMovement.z();

        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return;

        boolean onGround = player.isOnGround();
        Vector vec3 = new Vector(x, onGround ? 0.0 : y, z).multiply(-1);
        Vector deltaMovement = proj.getVelocity();
        proj.setVelocity(deltaMovement.add(vec3));
    }

    private Vec3 getMovement(Entity entity) {
        if (entity instanceof Player player) {
            if (ReflectorUtil.isLatestVersionedPackage())
                return ((CraftPlayer) player).getHandle().getKnownMovement();

            if (Reflector.versionIsNewerOrEqualTo(1, 21, 0)) {
                Object obj = VersionCompatUtils.getCraftHandle(player);
                Method method = Reflector.getMethod(obj.getClass(), "getKnownMovement");
                return Reflector.invokeMethod(method, obj);
            }
        }

        Vector vec = entity.getVelocity();
        return new Vec3(vec.getX(), vec.getY(), vec.getZ());
    }

    private boolean hasDisableRelativeProjectileVelocity(World world) {
        WorldConfiguration.Misc misc = ReflectorUtil.getWorldConfigurationMisc(world);
        return misc.disableRelativeProjectileVelocity;
    }
}
