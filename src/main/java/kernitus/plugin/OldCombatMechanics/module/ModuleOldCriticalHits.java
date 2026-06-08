/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class ModuleOldCriticalHits extends OCMModule {

    private boolean allowSprinting;
    private double multiplier;

    public ModuleOldCriticalHits(OCMMain plugin) {
        super(plugin, "old-critical-hits");
        reload();
    }

    @Override
    public void reload() {
        allowSprinting = module().getBoolean("allowSprinting", true);
        multiplier = module().getDouble("multiplier", 1.5);
    }

    @EventHandler
    public void onOCMDamage(OCMEntityDamageByEntityEvent e) {
        if (!isEnabled(e.getDamager(), e.getDamagee())) return;

        boolean isCritical = e.was1_8Crit();
        if (!isCritical && e.getDamager() instanceof HumanEntity) {
            isCritical = DamageUtils.isCriticalHit1_8((HumanEntity) e.getDamager());
        }

        boolean wasSprinting = e.wasSprinting();
        // In 1.9, a critical hit requires the player not to be sprinting
        if (isCritical && (allowSprinting || !wasSprinting)) {
            Entity damagee = e.getDamagee();
            // This ensures that sprint crits show particles for all clients
            if (wasSprinting && damagee instanceof LivingEntity) {
                WrapperPlayServerEntityAnimation packet = new WrapperPlayServerEntityAnimation(
                        damagee.getEntityId(),
                        WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT
                );

                PlayerManager manager = PacketEvents.getAPI().getPlayerManager();
                for (Player viewer : damagee.getWorld().getPlayers()) {
                    manager.sendPacket(viewer, packet);
                }
            }

            e.setCriticalMultiplier(multiplier);
        }
    }
}
