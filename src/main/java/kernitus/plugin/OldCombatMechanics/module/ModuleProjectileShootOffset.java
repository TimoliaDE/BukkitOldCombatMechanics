package kernitus.plugin.OldCombatMechanics.module;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * This makes the projectile spawn closer to the hand that launches it
 * instead of the player's eye position, just like in 1.8.
 * Furthermore, the vertical offset when spawning a projectile is
 * modified to match 1.8 clients while sneaking, because it is
 * reduced much less in 1.8.
 */
public class ModuleProjectileShootOffset extends OCMModule {

    private double xOffset;
    private double yOffset;

    private double yOffsetSneak;
    private boolean sneakOffsetModern;

    public ModuleProjectileShootOffset(OCMMain plugin) {
        super(plugin, "projectile-shoot-offset");
        reload();
    }

    @Override
    public void reload() {
        xOffset = module().getDouble("xOffset", 0.16);
        yOffset = module().getDouble("yOffset", -0.1);
        yOffsetSneak = module().getDouble("yOffsetSneak", 0.17);
        sneakOffsetModern = module().getBoolean("sneakOffsetModern", false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        final LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Player player)) return;
        if (!isEnabled(player)) return;

        Entity proj = event.getProjectile();
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.BOW) return;

        EquipmentSlot hand = event.getHand();
        shiftProjectileSpawnLocation(player, proj, hand);
        event.setProjectile(proj);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Projectile proj = event.getProjectile();
        boolean isEntity1_8 = proj instanceof AbstractArrow && !(proj instanceof Trident) ||
                proj instanceof Egg || proj instanceof EnderPearl || proj instanceof ThrownExpBottle ||
                proj instanceof ThrownPotion || proj instanceof Snowball;

        if (isEntity1_8) {
            Player player = event.getPlayer();
            EquipmentSlot hand = event.getItemStack().equals(player.getInventory().getItemInOffHand()) ?
                    EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
            shiftProjectileSpawnLocation(player, proj, hand);
        }
    }

    private Location getProjectileSpawnLocation(Player player, EquipmentSlot hand) {
        Location loc = player.getEyeLocation();
        Vector right = loc.getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize();

        double verticalOffset = player.isSneaking() &&
                (ViaVersionUtil.isLegacyClient(player) || sneakOffsetModern)
                ? yOffsetSneak
                : yOffset;

        double horizontalOffset = hand == EquipmentSlot.HAND ? xOffset : -xOffset;
        return loc.clone().add(0, verticalOffset, 0).add(right.multiply(horizontalOffset));
    }

    private void shiftProjectileSpawnLocation(Player player, Entity proj, EquipmentSlot hand) {
        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) proj).getHandle();
        Location arrowSpawnLoc = getProjectileSpawnLocation(player, hand);
        nmsEntity.setPosRaw(arrowSpawnLoc.getX(), arrowSpawnLoc.getY(), arrowSpawnLoc.getZ());
    }
}
