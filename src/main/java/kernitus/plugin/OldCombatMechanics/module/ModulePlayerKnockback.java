/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.cryptomorin.xseries.XAttribute;
import com.cryptomorin.xseries.XEnchantment;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
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

import javax.annotation.Nullable;
import java.util.*;

import static io.papermc.paper.event.entity.EntityKnockbackEvent.*;

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
    private double knockbackExtraMeleeVertical;
    private double knockbackExtraRangedHorizontal;
    private double knockbackExtraRangedVertical;
    private boolean disableSprinting;
    private boolean oldKnockbackResistancePlayers;
    private boolean oldKnockbackResistanceMobs;
    private boolean arrowHitSound;
    private boolean noDeflectArrow;

    private final Map<UUID, Vector> playerKnockback = new WeakHashMap<>();
    private final Map<UUID, Vector> playerRangedKnockback = new WeakHashMap<>();
    private final Set<UUID> ignoreKnockback = new HashSet<>();
    private final Map<UUID, Boolean> blockedBySword = new WeakHashMap<>();
    private final Set<UUID> moduleShield = new HashSet<>();
    private final Map<UUID, AbstractArrow> blockHitArrows = new WeakHashMap<>();

    public ModulePlayerKnockback(OCMMain plugin) {
        super(plugin, "old-player-knockback");
        reload();

        if (plugin.getProtocolManager() == null) {
            Messenger.warn("No ProtocolLib detected, the arrow-hit-sound " +
                    "option will be disabled in the old-player-knockback");
        }
    }

    public void reload() {
        knockbackHorizontal = module().getDouble("knockback-horizontal", 0.4);
        knockbackVertical = module().getDouble("knockback-vertical", 0.4);
        knockbackHorizontalFriction = module().getDouble("knockback-horizontal-friction", 0.5);
        knockbackVerticalFriction = module().getDouble("knockback-vertical-friction", 0.5);
        knockbackVerticalLimit = module().getDouble("knockback-vertical-limit", 0.4);
        knockbackExtraMeleeHorizontal = module().getDouble("knockback-extra-melee-horizontal", 0.5);
        knockbackExtraMeleeVertical = module().getDouble("knockback-extra-melee-vertical", 0.1);
        knockbackExtraRangedHorizontal = module().getDouble("knockback-extra-ranged-horizontal", 0.6);
        knockbackExtraRangedVertical = module().getDouble("knockback-extra-ranged-vertical", 0.1);
        disableSprinting = module().getBoolean("disable-sprinting", true);
        oldKnockbackResistancePlayers = module().getBoolean("old-knockback-resistance-players", true);
        oldKnockbackResistanceMobs = module().getBoolean("old-knockback-resistance-mobs", true);
        arrowHitSound = module().getBoolean("arrow-hit-sound", true) && plugin.getProtocolManager() != null;
        noDeflectArrow = module().getBoolean("no-deflect-arrow", true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        playerKnockback.remove(uuid);
        playerRangedKnockback.remove(uuid);
        ignoreKnockback.remove(uuid);
        blockedBySword.remove(uuid);
        blockHitArrows.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();

        final UUID uuid = player.getUniqueId();
        Vector velocity = playerRangedKnockback.remove(uuid);

        // Ensures that knockback from ranged player attacks matches 1.8 pvp
        if (velocity != null)
            event.setVelocity(velocity);
    }

    // Use the "EntityPushedByEntityAttackEvent" event instead of "EntityKnockbackByEntityEvent" in order
    // to fix the knockback from the projectiles (e.g. arrows)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPushedByEntityAttack(EntityPushedByEntityAttackEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        final UUID uuid = entity.getUniqueId();
        boolean usedModuleShield = isUsingModuleShield(uuid, event);

        if (ignoreKnockback.contains(uuid) || usedModuleShield) {
            event.setCancelled(true);
            return;
        }

        Vector velocity = playerKnockback.remove(uuid);
        if (velocity != null) {
            ignoreKnockback.add(uuid);
            entity.setVelocity(velocity);

            Vector vec = new Vector(0, 0, 0);
            if (Reflector.versionIsNewerOrEqualTo(1, 20, 4)) {
                if (Reflector.versionIsNewerOrEqualTo(1, 20, 6)) {
                    event.setKnockback(vec);
                } else
                    event.setAcceleration(vec);
            }

            event.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> ignoreKnockback.remove(uuid), 1);
        }

        applyKnockbackWithBlocking(livingEntity, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        final Entity damagee = event.getEntity();
        final Entity rodder = getRodder(damagee);
        boolean fromRod = damager.equals(rodder);

        if (!(damager instanceof LivingEntity) && !(damager instanceof Projectile) && !fromRod)
            return;

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

        // Prevent the player from receiving knockback when using an ender pearl
        if (damager instanceof EnderPearl enderPearl && damagee.equals(enderPearl.getShooter()))
            return;

        boolean useBlockHit = false;

        if (hasBlocked(event)) {
            if (hasFullBlockedModule(damagee) || hasFullBlockedVanilla(event))
                return;

            playHitSounds(damager, livingDamagee, isProjectile);
            useBlockHit = true;
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
        boolean usingCoincidence = oldKnockbackResistancePlayers && damagee instanceof Player ||
                oldKnockbackResistanceMobs && !(damagee instanceof Player);

        if (attribute != null) {
            double value = attribute.getValue();
            // Some plugins make entities immune to knockback by setting their knockback resistance
            // to a high base value. Therefore, this effect only applies if the base value is 100 or above:
            if (value >= 1 && attribute.getBaseValue() >= 100)
                return;

            // Allow netherite to affect the horizontal knockback. Each piece of armour
            // yields 10% resistance
            resistanceFactor = Math.max(0, 1 - value);

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
        if (damager instanceof LivingEntity attacker && isAttack && !fromRod) {
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
                            (float) bonusKnockback * knockbackExtraMeleeHorizontal), knockbackExtraMeleeVertical,
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
                        knockbackExtraRangedVertical,
                        -zDir * (float) bonusKnockback * knockbackExtraRangedHorizontal));
            }
        }

        if (!usingCoincidence && resistanceFactor < 1)
            playerVelocity.multiply(new Vector(resistanceFactor, 1, resistanceFactor));

        final UUID victimId = damagee.getUniqueId();
        Entity damagerSource = damager instanceof Projectile damagerProj &&
                damagerProj.getShooter() instanceof Player player ? player : damager;
        boolean fromPlayer = damagerSource instanceof Player;

        if (!fromPlayer) {
            handleBlockHit(damager, victimId, useBlockHit, false);
            return;

        } else if (!isEnabled(damagerSource))
            return;

        // Knockback is sent immediately in 1.8+, there is no reason to send packets
        // manually
        playerKnockback.put(victimId, playerVelocity);
        Bukkit.getScheduler().runTaskLater(plugin, () -> playerKnockback.remove(victimId), 1);

        // Sometimes PlayerVelocityEvent doesn't fire, remove data to not affect later
        // events if that happens
        if ((damager instanceof Projectile || fromRod) && damagee instanceof Player) {
            playerRangedKnockback.put(victimId, playerVelocity);
            Bukkit.getScheduler().runTaskLater(plugin, () -> playerRangedKnockback.remove(victimId), 2);
        }

        handleBlockHit(damager, victimId, useBlockHit, true);
    }

    private void handleBlockHit(Entity damager, UUID victimId, boolean useBlockhit, boolean fromPlayer) {
        if (useBlockhit) {
            blockedBySword.put(victimId, fromPlayer);
            // Sometimes EntityPushedByEntityAttackEvent doesn't fire, remove data to not affect later
            // events if that happens
            Bukkit.getScheduler().runTaskLater(plugin, () -> blockedBySword.remove(victimId), 1);

            if (!Reflector.versionIsNewerOrEqualTo(1, 20, 4)) {
                moduleShield.add(victimId);
                // Sometimes EntityPushedByEntityAttackEvent doesn't fire, remove data to not affect later
                // events if that happens
                Bukkit.getScheduler().runTaskLater(plugin, () -> moduleShield.remove(victimId), 1);
            }
        }

        if (damager instanceof AbstractArrow abstractArrow && !(damager instanceof Trident)) {
            blockHitArrows.put(victimId, abstractArrow);
            Bukkit.getScheduler().runTaskLater(plugin, () -> blockHitArrows.remove(victimId), 1);
        }
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

    private boolean hasBlockedModule(Entity damagee) {
        final UUID victimId = damagee.getUniqueId();
        Set<UUID> blockedPlayers = ModuleShieldDamageReduction.getBlockedPlayers();
        return blockedPlayers.contains(victimId);
    }

    private boolean hasBlocked(EntityDamageByEntityEvent event) {
        return event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) < 0 ||
                hasBlockedModule(event.getEntity());
    }

    private boolean hasFullBlockedModule(Entity damagee) {
        final UUID victimId = damagee.getUniqueId();
        Map<UUID, List<ItemStack>> fullyBlocked = ModuleShieldDamageReduction.getFullyBlocked();
        return fullyBlocked.containsKey(victimId);
    }

    private boolean hasFullBlockedVanilla(EntityDamageByEntityEvent event) {
        // First, check if the player is not using the module shield
        if (hasBlockedModule(event.getEntity()))
            return false;

        final double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE) +
                event.getDamage(EntityDamageEvent.DamageModifier.HARD_HAT);
        final double blockingDamage = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);

        return -blockingDamage >= baseDamage;
    }

    /*
     * Only applies if the player uses the module shield
     *(temporary shield from the "ModuleSwordBlocking" module),
     * which is only used up to version 1.21.2.
     */
    private void playHitSounds(Entity damager, LivingEntity livingDamagee, boolean isProjectile) {
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3) || !hasBlockedModule(livingDamagee))
            return;

        Sound hurtSound = livingDamagee.getHurtSound();
        if (hurtSound == null) return;

        float volume = ReflectorUtil.getSoundVolume(livingDamagee);
        RandomSource random = ReflectorUtil.getRandom(livingDamagee);

        float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
        livingDamagee.getWorld().playSound(livingDamagee.getLocation(), hurtSound, volume, pitch);

        if (arrowHitSound && damager instanceof AbstractArrow abstractArrow &&
                !(damager instanceof Trident) && abstractArrow.getShooter() instanceof Player player &&
                isProjectile && !abstractArrow.isSilent() && livingDamagee instanceof Player damageePlayer &&
                player != damageePlayer) {
            try {
                ProtocolManager manager = plugin.getProtocolManager();
                PacketContainer packet = manager.createPacket(PacketType.Play.Server.GAME_STATE_CHANGE);
                packet.getGameStateIDs().write(0, 6);
                packet.getFloat().write(0, 0.0F);
                manager.sendServerPacket(player, packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isUsingModuleShield(final UUID uuid, EntityPushedByEntityAttackEvent event) {
        if (Reflector.versionIsNewerOrEqualTo(1, 20, 4)) {
            Cause cause = event.getCause();
            return  blockedBySword.containsKey(uuid) && cause == Cause.SHIELD_BLOCK;
        }

        return moduleShield.remove(uuid);
    }

    /*
     * Only applies if the player uses the module shield
     *(temporary shield from the "ModuleSwordBlocking" module),
     * which is only used up to version 1.21.2.
     */
    private void applyKnockbackWithBlocking(LivingEntity livingEntity, EntityPushedByEntityAttackEvent event) {
        final UUID uuid = livingEntity.getUniqueId();
        Boolean fromPlayer = blockedBySword.remove(uuid);

        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3) || !hasBlockedModule(livingEntity))
            return;

        if (fromPlayer != null) {
            Vector knockback;
            if (Reflector.versionIsNewerOrEqualTo(1, 20, 6)) {
                knockback = event.getKnockback();
            } else
                knockback = event.getAcceleration();

            if (!fromPlayer)
                livingEntity.setVelocity(knockback);

            Location loc = new Location(livingEntity.getWorld(), 0, 0, 0);
            loc.setDirection(knockback);
            float yaw = loc.getYaw();

            AbstractArrow abstractArrow = blockHitArrows.remove(uuid);
            if (noDeflectArrow && abstractArrow != null) {
                abstractArrow.remove();
                livingEntity.setArrowsInBody(livingEntity.getArrowsInBody() + 1);
            }

            livingEntity.playHurtAnimation(yaw);
        }
    }

    @Nullable
    private Player getRodder(Entity damagee) {
        final UUID victimId = damagee.getUniqueId();
        Map<UUID, Player> rodEntities = ModuleFishingKnockback.getRodEntities();
        return rodEntities.remove(victimId);
    }
}