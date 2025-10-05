package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.IntTriple;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * This causes the player to take 1 damage every 10 ticks
 * if their head is inside a wall in a standing pose,
 * as seen from the perspective of clients running versions 1.8 to 1.13.2.
 * It is recommended to enable this only if the server supports
 * these legacy clients, since in newer versions, the player appears
 * crawling instead, with their head no longer inside the wall.
 */
public class ModuleDamageInsideWall extends OCMModule {

    private boolean withoutSwimming;

    public ModuleDamageInsideWall(OCMMain plugin) {
        super(plugin, "damage-inside-wall");
        reload();
    }

    @Override
    public void reload() {
        withoutSwimming = module().getBoolean("withoutSwimming", false);
    }

    @EventHandler
    public void onEntityPoseChange(EntityPoseChangeEvent event) {
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (!isEnabled(player)) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                Pose pose = player.getPose();
                Block block = player.getLocation().getBlock();
                boolean isSwimming = player.isSprinting() && player.isUnderWater() &&
                        !player.isInsideVehicle() && block.getType() == Material.WATER;

                if (!player.isOnline() || pose != Pose.SWIMMING || !isEnabled(player)) {
                    this.cancel();

                } else if (isInLegacyWall(player) && !(withoutSwimming && isSwimming))
                    damage(player);
            }

        }.runTaskTimer(plugin, 2, 1);
    }

    private void damage(Player player) {
        if (Reflector.versionIsNewerOrEqualTo(1, 20, 4)) {
            player.damage(1.0F, DamageSource.builder(DamageType.IN_WALL).build());
            return;
        }

        ReflectorUtil.damage(player);
    }


    private boolean isInLegacyWall(Player player) {
        if (player.isSleeping()) return false;

        final float width = 0.6F;
        final float headHeight = 1.62F;
        final int minValue = Integer.MIN_VALUE;

        IntTriple intTriple = new IntTriple(minValue, minValue, minValue);
        World world = player.getWorld();

        for (int i = 0; i < 8; ++i) {
            int x = (int) Math.floor(player.getX() + (double) ((float) ((i >> 1) % 2) * width * 0.8F -
                    0.5F * width * 0.8F));
            int y = (int) Math.floor(player.getY() + (double) (((float) (i % 2) - 0.5F) * 0.1F) +
                    headHeight);
            int z = (int) Math.floor(player.getZ() + (double) ((float) ((i >> 2) % 2) * width * 0.8F -
                    0.5F * width * 0.8F));

            if (!(intTriple.is(x, y, z))) {
                intTriple.set(x, y, z);

                if (ReflectorUtil.isSuffocating(world, x, y, z))
                    return true;
            }
        }

        return false;
    }
}