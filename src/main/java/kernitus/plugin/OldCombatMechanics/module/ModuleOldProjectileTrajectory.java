package kernitus.plugin.OldCombatMechanics.module;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * It changes the spread when shooting a projectile to match 1.8 behavior,
 * making projectiles shoot slightly more accurately than in newer versions.
 */
public class ModuleOldProjectileTrajectory extends OCMModule {

    private double spreadIntensity;

    public ModuleOldProjectileTrajectory(OCMMain plugin) {
        super(plugin, "old-projectile-trajectory");
        reload();
    }

    @Override
    public void reload() {
        spreadIntensity = module().getDouble("spreadIntensity", 0.0075);
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

        float power = event.getForce();

        if (proj instanceof AbstractArrow abstractArrow && !(proj instanceof Trident)) {
            float yaw = player.getLocation().getYaw();
            float pitch = player.getLocation().getPitch();
            modifyProjectileSpread(abstractArrow, yaw, pitch, power * 3, true);
            event.setProjectile(abstractArrow);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Projectile proj = event.getProjectile();
        boolean isEntity1_8 = proj instanceof AbstractArrow && !(proj instanceof Trident) ||
                proj instanceof Egg || proj instanceof EnderPearl || proj instanceof ThrownExpBottle ||
                proj instanceof ThrownPotion || proj instanceof Snowball;

        if (isEntity1_8) {
            Player player = event.getPlayer();
            float yaw = player.getLocation().getYaw();
            float pitch = player.getLocation().getPitch();
            float power = getPower(proj);

            modifyProjectileSpread(proj, yaw, pitch, power, false);
        }
    }

    private float getPower(Projectile proj) {
        if (proj instanceof ThrownPotion) {
            return  0.5f;
        } else if (proj instanceof ThrownExpBottle) {
            return  0.7f;
        }
        return 1.5f;
    }

    private static Vector getDirection(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vector(x, y, z);
    }

    // Calculates the random spread for projectiles to match 1.8.
    // Adapted from the NMS Projectile#getMovementToShoot implementation.
    private Vector getMovementToShoot(double x, double y, double z, float pow, boolean isArrow) {
        Random random = new Random();

        // 1. Calculate length and normalize
        double length = Math.sqrt(x * x + y * y + z * z);
        x /= length;
        y /= length;
        z /= length;

        // 2. Add the 1.8 spread to the arrows
        x += random.nextGaussian() * (!isArrow && random.nextBoolean() ? -1 : 1) * spreadIntensity;
        y += random.nextGaussian() * (!isArrow && random.nextBoolean() ? -1 : 1) * spreadIntensity;
        z += random.nextGaussian() * (!isArrow && random.nextBoolean() ? -1 : 1) * spreadIntensity;

        // 3. Apply velocity
        return new Vector(x, y, z).multiply(pow);
    }

    // Applies a spread when shooting players
    private void modifyProjectileSpread(Projectile proj, float yaw, float pitch, float power,
                                        boolean isArrow) {
        Vector direction = getDirection(yaw, pitch);
        Vector movement = getMovementToShoot(direction.getX(), direction.getY(), direction.getZ(),
                power, isArrow);

        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) proj).getHandle();
        nmsEntity.setDeltaMovement(CraftVector.toVec3(movement));
    }
}
