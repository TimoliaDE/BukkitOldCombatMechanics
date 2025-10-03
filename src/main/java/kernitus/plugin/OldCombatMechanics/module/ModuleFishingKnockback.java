/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import com.cryptomorin.xseries.XEntityType;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Brings back the old fishing-rod knockback.
 */
public class ModuleFishingKnockback extends OCMModule {

    private final SpigotFunctionChooser<PlayerFishEvent, Object, Entity> getHookFunction;
    private final SpigotFunctionChooser<ProjectileHitEvent, Object, Entity> getHitEntityFunction;

    private boolean knockbackNonPlayerEntities;
    private double damage;
    private String cancelDraggingIn;

    private final static Map<UUID, Player> rodEntities = new HashMap<>();

    public ModuleFishingKnockback(OCMMain plugin) {
        super(plugin, "old-fishing-knockback");
        reload();

        getHookFunction = SpigotFunctionChooser.apiCompatReflectionCall((e, params) -> e.getHook(),
                PlayerFishEvent.class, "getHook");
        getHitEntityFunction = SpigotFunctionChooser.apiCompatCall((e, params) -> e.getHitEntity(), (e, params) -> {
            final Entity hookEntity = e.getEntity();
            return hookEntity.getWorld().getNearbyEntities(hookEntity.getLocation(), 0.25, 0.25, 0.25).stream()
                    .filter(entity -> !knockbackNonPlayerEntities && entity instanceof Player)
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    public void reload() {
        knockbackNonPlayerEntities = isSettingEnabled("knockbackNonPlayerEntities");
        damage = module().getDouble("damage", 0.0001);
        cancelDraggingIn = module().getString("cancelDraggingIn", "players");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        rodEntities.remove(uuid);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRodLand(ProjectileHitEvent event) {
        final Entity hookEntity = event.getEntity();

        final EntityType fishingBobberType = XEntityType.FISHING_BOBBER.get();
        if (fishingBobberType == null || event.getEntityType() != fishingBobberType)
            return;

        final FishHook hook = (FishHook) hookEntity;

        if (!(hook.getShooter() instanceof Player rodder))
            return;
        if (!isEnabled(rodder))
            return;

        final Entity hitEntity = getHitEntityFunction.apply(event);

        if (hitEntity == null)
            return; // If no entity was hit
        if (!(hitEntity instanceof LivingEntity livingEntity))
            return;
        if (hitEntity instanceof Player player && player.getGameMode() == GameMode.CREATIVE)
            return;

        if (!knockbackNonPlayerEntities && !(hitEntity instanceof Player))
            return;

        // Do not move Citizens NPCs
        // See https://wiki.citizensnpcs.co/API#Checking_if_an_entity_is_a_Citizens_NPC
        if (hitEntity.hasMetadata("NPC"))
            return;

        // Check if cooldown time has elapsed
        if (livingEntity.getNoDamageTicks() > livingEntity.getMaximumNoDamageTicks() / 2f)
            return;

        if (damage <= 0) return;

        final UUID uuid = hitEntity.getUniqueId();
        rodEntities.put(uuid, rodder);
        livingEntity.damage(damage, rodder);
        Bukkit.getScheduler().runTaskLater(plugin, () -> rodEntities.remove(uuid), 1L);
    }

    /**
     * This is to cancel dragging the entity closer when you reel in
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onReelIn(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY)
            return;
        if (!isEnabled(e.getPlayer()))
            return;

        final boolean isPlayer = e.getCaught() instanceof HumanEntity;
        if ((cancelDraggingIn.equals("players") && isPlayer) ||
                cancelDraggingIn.equals("mobs") && !isPlayer ||
                cancelDraggingIn.equals("all")) {
            getHookFunction.apply(e).remove(); // Remove the bobber and don't do anything else
            e.setCancelled(true);
        }
    }

    public static Map<UUID, Player> getRodEntities() {
        return rodEntities;
    }
}
