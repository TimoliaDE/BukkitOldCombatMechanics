/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import com.cryptomorin.xseries.XEnchantment;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import net.minecraft.util.RandomSource;
import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Reverts knockback formula to 1.8.
 * Also disables netherite knockback resistance.
 * <p>
 * Improvements to knockback behaviour:
 * - Knockback from player projectiles matches the 1.8 version
 * - Players blocking with a sword receive knockback when getting hurt
 * - Knockback combos are closer to the 1.8 version
 * - Knockback resistance determines the chance of applying the base knockback
 *   instead of reduces knockback by a relative amount. This option only works
 *   for living entities other than players to make netherite armor work for
 *   players correctly.
 * <p>
 * Note: Knockback caused by mobs remains unchanged and follow the 1.9 version.
 */
public class ModulePlayerKnockback extends OCMModule {

    private double knockbackHorizontal;
    private double knockbackVertical;
    private double knockbackHorizontalFriction;
    private double knockbackVerticalFriction;
    private double knockbackVerticalLimit;
    private double knockbackExtraMeleeHorizontal;
    private double knockbackExtraRangedHorizontal;
    private double knockbackExtraVertical;
    private boolean disableSprinting;
    private boolean oldKnockbackResistancePlayers;
    private boolean oldKnockbackResistanceMobs;

    private final Map<UUID, Vector> playerKnockback = new WeakHashMap<>();
    private final Map<UUID, Vector> playerKnockbackCopy = new WeakHashMap<>();
    private final Set<UUID> ignoreKnockbackHashMap = new HashSet<>();
    private final Map<UUID, Boolean> blockHitSet = new WeakHashMap<>();

    public ModulePlayerKnockback(OCMMain plugin) {
        super(plugin, "old-player-knockback");
        reload();
    }

    public void reload() {
        knockbackHorizontal = module().getDouble("knockback-horizontal", 0.4);
        knockbackVertical = module().getDouble("knockback-vertical", 0.4);
        knockbackHorizontalFriction = module().getDouble("knockback-horizontal-friction", 0.5);
        knockbackVerticalFriction = module().getDouble("knockback-vertical-friction", 0.5);
        knockbackVerticalLimit = module().getDouble("knockback-vertical-limit", 0.4);
        knockbackExtraMeleeHorizontal = module().getDouble("knockback-extra-melee-horizontal", 0.5);
        knockbackExtraRangedHorizontal = module().getDouble("knockback-extra-ranged-horizontal", 0.6);
        knockbackExtraVertical = module().getDouble("knockback-extra-vertical", 0.1);
        disableSprinting = module().getBoolean("disable-sprinting", true);
        oldKnockbackResistancePlayers = module().getBoolean("old-knockback-resistance-players", true);
        oldKnockbackResistanceMobs = module().getBoolean("old-knockback-resistance-mobs", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        playerKnockback.remove(uuid);
        playerKnockbackCopy.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void handlePlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();

        final UUID uuid = player.getUniqueId();
        Vector velocity = playerKnockbackCopy.get(uuid);

        if (velocity != null) {
            event.setVelocity(velocity);
            playerKnockbackCopy.remove(uuid);
        }
    }

    // Use the "EntityPushedByEntityAttackEvent" event instead of "EntityKnockbackByEntityEvent" in order
    // to fix the knockback from the projectiles (e. g. arrows)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleEntityPushedByEntityAttackEvent(EntityPushedByEntityAttackEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        final UUID uuid = entity.getUniqueId();
        if (ignoreKnockbackHashMap.contains(uuid)) {
            event.setCancelled(true);
            return;
        }

        Boolean fromPlayer = blockHitSet.get(uuid);
        Vector velocity = playerKnockback.get(uuid);

        if (velocity != null) {
            playerKnockback.remove(uuid);
            ignoreKnockbackHashMap.add(uuid);
            entity.setVelocity(velocity);

            Vector vec = new Vector(0, 0, 0);
            if (Reflector.versionIsNewerOrEqualTo(1, 20, 4)) {
                if (Reflector.versionIsNewerOrEqualTo(1, 20, 6)) {
                    event.setKnockback(vec);
                } else
                    event.setAcceleration(vec);
            }

            event.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> ignoreKnockbackHashMap.remove(uuid), 1);
        }

        if (fromPlayer != null) {
            Vector knockback;
            if (Reflector.versionIsNewerOrEqualTo(1, 20, 6)) {
                knockback = event.getKnockback();
            } else
                knockback = event.getAcceleration();

            if (!fromPlayer)
                entity.setVelocity(knockback);

            Location loc = new Location(entity.getWorld(), 0, 0, 0);
            loc.setDirection(knockback);
            float yaw = loc.getYaw();
            livingEntity.playHurtAnimation(yaw);
            blockHitSet.remove(uuid);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        final Entity damager = event.getDamager();
        if (!(damager instanceof LivingEntity) && !(damager instanceof Projectile))
            return;

        final Entity damagee = event.getEntity();
        if (!(damagee instanceof LivingEntity livingDamagee))
            return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean isAttack = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK;
        boolean isProjectile = cause == EntityDamageEvent.DamageCause.PROJECTILE;

        if (!isAttack && !isProjectile)
            return;

        // This condition is taken from the method "hurtServer" of the class "LivingEntity"
        // Sometimes, the ticks between two hits are much lower than about 10 ticks
        if (livingDamagee.getNoDamageTicks() > (float) livingDamagee.getMaximumNoDamageTicks() / 2.0F) {
            event.setCancelled(true);
            return;
        }

        boolean useBlockHit = false;
        double blockingDamage = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);

        if (blockingDamage < 0) {
            if (-blockingDamage >= event.getDamage())
                return;

            Sound hurtSound = livingDamagee.getHurtSound();
            if (hurtSound != null) {
                float volume = ReflectorUtil.getSoundVolume(livingDamagee);
                RandomSource random = ReflectorUtil.getRandom(livingDamagee);

                float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
                livingDamagee.getWorld().playSound(livingDamagee.getLocation(), hurtSound, volume, pitch);
                useBlockHit = true;
            }
        }

        // Figure out base knockback direction
        Location attackerLocation = damager.getLocation();
        Location victimLocation = damagee.getLocation();
        double d0 = attackerLocation.getX() - victimLocation.getX();
        double d1;

        for (d1 = attackerLocation.getZ() - victimLocation.getZ(); d0 * d0
                + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
            d0 = (Math.random() - Math.random()) * 0.01D;
        }

        final double magnitude = Math.sqrt(d0 * d0 + d1 * d1);
        final double xDir;
        final double zDir;

        // Get player knockback before any friction is applied
        final Vector playerVelocity = damagee.getVelocity();

        if (damager instanceof Projectile proj) {
            Vector vec = getHorizontalDirection(proj);
            xDir = vec.getX();
            zDir = vec.getZ();

        } else {
            xDir = d0 / magnitude;
            zDir = d1 / magnitude;
        }

        double resistanceFactor = 1;
        AttributeInstance attribute = livingDamagee.getAttribute(XAttribute.KNOCKBACK_RESISTANCE.get());
        boolean hasBaseKnockback = true;
        boolean usingCoincidence = oldKnockbackResistancePlayers && damager instanceof Player ||
                oldKnockbackResistanceMobs && !(damager instanceof Player);

        if (attribute != null) {
            // Allow netherite to affect the horizontal knockback. Each piece of armour
            // yields 10% resistance
            resistanceFactor = Math.max(0, 1 - attribute.getValue());

            if (usingCoincidence)
                hasBaseKnockback = new Random().nextDouble() < resistanceFactor;
        }

        // Apply friction, then add base knockback
        if (hasBaseKnockback) {
            playerVelocity.setX(knockbackHorizontalFriction * playerVelocity.getX() - (xDir * knockbackHorizontal));
            playerVelocity.setY(knockbackVerticalFriction * playerVelocity.getY() + knockbackVertical);
            playerVelocity.setZ(knockbackHorizontalFriction * playerVelocity.getZ() - (zDir * knockbackHorizontal));
        }

        // Calculate bonus knockback for sprinting or knockback enchantment levels
        if (damager instanceof LivingEntity attacker && isAttack) {
            final EntityEquipment equipment = attacker.getEquipment();
            if (equipment != null) {
                ItemStack mainHandItem = equipment.getItemInMainHand();
                final ItemStack heldItem = mainHandItem.getType() == Material.AIR ? equipment.getItemInOffHand()
                        : mainHandItem;

                int bonusKnockback = heldItem.getEnchantmentLevel(XEnchantment.KNOCKBACK.get());
                if (attacker instanceof Player player && player.isSprinting()) {
                    ++bonusKnockback;

                    if (disableSprinting)
                        player.setSprinting(false);
                }

                if (playerVelocity.getY() > knockbackVerticalLimit)
                    playerVelocity.setY(knockbackVerticalLimit);

                if (bonusKnockback > 0) { // Apply bonus knockback
                    playerVelocity.add(new Vector((-Math.sin(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                            (float) bonusKnockback * knockbackExtraMeleeHorizontal), knockbackExtraVertical,
                            Math.cos(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) * (float) 
                                    bonusKnockback * knockbackExtraMeleeHorizontal));
                }
            }

        } else if (damager instanceof AbstractArrow abstractArrow && isProjectile) {
            int bonusKnockback;
            if (Reflector.versionIsNewerOrEqualTo(1, 21, 0)) {
                final ItemStack heldItem = abstractArrow.getWeapon();
                bonusKnockback = heldItem != null ? heldItem.getEnchantmentLevel(XEnchantment.PUNCH.get()) : 0;

            } else
                bonusKnockback = abstractArrow.getKnockbackStrength();

            if (playerVelocity.getY() > knockbackVerticalLimit)
                playerVelocity.setY(knockbackVerticalLimit);

            if (bonusKnockback > 0) { // Apply bonus knockback
                playerVelocity.add(new Vector(-xDir * (float) bonusKnockback * knockbackExtraRangedHorizontal,
                        knockbackExtraVertical,
                        -zDir * (float) bonusKnockback * knockbackExtraRangedHorizontal));
            }
        }

        if (!usingCoincidence && resistanceFactor < 1)
            playerVelocity.multiply(new Vector(resistanceFactor, 1, resistanceFactor));

        final UUID victimId = damagee.getUniqueId();

        boolean fromPlayer = damager instanceof Player || damager instanceof Projectile proj &&
                proj.getShooter() instanceof Player;
        boolean finalUseBlockHit = useBlockHit;

        if (!fromPlayer) {
            if (finalUseBlockHit)
                blockHitSet.put(victimId, false);

            // Sometimes EntityKnockbackByEntityEvent doesn't fire, remove data to not affect later
            // events if that happens
            Bukkit.getScheduler().runTaskLater(plugin, () -> blockHitSet.remove(victimId), 1);
            return;
        }

        Entity damagerSource = damager instanceof Projectile damagerProj &&
                damagerProj.getShooter() instanceof Player player ? player : damager;
        if (damagerSource instanceof Player player && !isEnabled(player)) return;

        // Knockback is sent immediately in 1.8+, there is no reason to send packets
        // manually
        playerKnockback.put(victimId, playerVelocity);
        Bukkit.getScheduler().runTaskLater(plugin, () -> playerKnockback.remove(victimId), 1);

        // Sometimes PlayerVelocityEvent doesn't fire, remove data to not affect later
        // events if that happens
        if (damagerSource instanceof Projectile && damagee instanceof Player) {
            playerKnockbackCopy.put(victimId, playerVelocity);
            Bukkit.getScheduler().runTaskLater(plugin, () -> playerKnockbackCopy.remove(victimId), 1);
        }

        if (useBlockHit)
            blockHitSet.put(victimId, true);
    }

    private static Vector getHorizontalDirection(Projectile proj) {
        Location loc = proj.getLocation();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        // in Radians umwandeln
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        // Richtung berechnen
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double z = -Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vector(x, 0, z).normalize();
    }
}