package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import net.minecraft.world.food.FoodData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.tag.DamageTypeTags;
import org.spigotmc.SpigotWorldConfig;

import java.util.HashSet;
import java.util.Set;

import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.*;

/**
 * This is to make players' regeneration from saturation and
 * the rate at which you lose hunger points to 1.8.
 */
public class ModulePlayerRegen extends OCMModule {

    private static final Set<EntityDamageEvent.DamageCause> bypassesArmorCauses;

    static {
        bypassesArmorCauses = new HashSet<>();
        bypassesArmorCauses.addAll(Set.of(CRAMMING, DRAGON_BREATH, DROWNING, DRYOUT, FALL, FLY_INTO_WALL,
                FREEZE, HOT_FLOOR, LIGHTNING, MAGIC, POISON, SONIC_BOOM, STARVATION, SUFFOCATION, SUICIDE, VOID,
                WITHER));

        if (Reflector.versionIsNewerOrEqualTo(1, 20, 0)) {
            bypassesArmorCauses.add(WORLD_BORDER);

            if (Reflector.versionIsNewerOrEqualTo(1, 21, 0))
                bypassesArmorCauses.add(CAMPFIRE);
        }
    }

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
        reload();
    }

    @Override
    public void reload() {
        Bukkit.getOnlinePlayers().forEach(this::adjustSaturatedRegenRate);
    }

    private void adjustSaturatedRegenRate(Player player) {
        FoodData foodData = ReflectorUtil.getFoodData(player);

        if (isEnabled(player)) {
            foodData.saturatedRegenRate = foodData.unsaturatedRegenRate;
        } else
            foodData.saturatedRegenRate = 10;
    }

    @Override
    public void onModesetChange(Player player) {
        adjustSaturatedRegenRate(player);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        adjustSaturatedRegenRate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        adjustSaturatedRegenRate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        adjustSaturatedRegenRate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        adjustSaturatedRegenRate(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExhaustion(EntityExhaustionEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (!isEnabled(player)) return;

        EntityExhaustionEvent.ExhaustionReason reason = event.getExhaustionReason();
        float exhaustion = event.getExhaustion();
        SpigotWorldConfig spigotConfig = ReflectorUtil.getSpigotConfig(player.getWorld());

        float amount = switch (reason) {
            case BLOCK_MINED -> 0.025F;
            case HUNGER_EFFECT -> quotient(exhaustion, 0.025F, 0.005F);
            case ATTACK -> 0.3F;
            case REGEN -> 3F;
            case DAMAGED -> exhaustionByLastDamage(player);
            case UNKNOWN, CROUCH -> 0F;

            case SWIM, WALK_UNDERWATER, WALK_ON_WATER ->
                    quotient(exhaustion, 0.015F, spigotConfig.swimMultiplier);

            case SPRINT -> quotient(exhaustion, 0.099999994F, spigotConfig.sprintMultiplier);
            case WALK -> quotient(exhaustion, 0.01F, spigotConfig.otherMultiplier);
            case JUMP -> 0.2F;
            case JUMP_SPRINT -> 0.8F;
        };

        event.setExhaustion(amount);
    }

    private float quotient(float value, float oldCombatValue, float newCombatValue) {
        if (newCombatValue == 0) return 0;
        return value * (oldCombatValue / newCombatValue);
    }


    private float exhaustionByLastDamage(Player player) {
        EntityDamageEvent lastDamageCause = player.getLastDamageCause();
        if (lastDamageCause == null) return 0;

        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            org.bukkit.damage.DamageType damageType = lastDamageCause.getDamageSource().getDamageType();
            if (DamageTypeTags.BYPASSES_ARMOR.isTagged(damageType))
                return 0;

        } else if (bypassesArmorCauses.contains(lastDamageCause.getCause()))
            return 0;

        return 0.3F;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (!isEnabled(player)) return;

        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED && event.isFastRegen())
            event.setAmount(1.0F);
    }
}
