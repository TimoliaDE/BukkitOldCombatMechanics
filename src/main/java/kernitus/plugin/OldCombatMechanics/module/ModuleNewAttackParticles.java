/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.TextUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * A module to disable the sword sweep and damage indicator particles.
 */
public class ModuleNewAttackParticles extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final ParticleListener particleListener = new ParticleListener(plugin);

    public ModuleNewAttackParticles(OCMMain plugin) {
        super(plugin, "disable-new-attack-particles");
        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            protocolManager.addPacketListener(particleListener);
        else
            protocolManager.removePacketListener(particleListener);
    }

    /**
     * Hides sweep particles.
     */
    private class ParticleListener extends PacketAdapter {

        private boolean disabledDueToError;

        public ParticleListener(Plugin plugin) {
            super(plugin, PacketType.Play.Server.WORLD_PARTICLES);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            Player player = event.getPlayer();
            if (disabledDueToError || !isEnabled(player.getWorld()))
                return;

            PacketContainer packet = event.getPacket();
            try {
                NamespacedKey namespacedKey;
                if (Reflector.versionIsNewerOrEqualTo(1, 20, 5)) {
                    namespacedKey = Registry.PARTICLE_TYPE.getKey(packet.getNewParticles()
                            .read(0).getParticle());
                } else {
                    namespacedKey = packet.getNewParticles().read(0).getParticle().getKey();
                }

                if (namespacedKey == null) return;

                String particleName = TextUtils.getFormattedString(namespacedKey.getKey());
                boolean isSweepParticle = particleName.contains("SWEEP");

                if (isSweepParticle || particleName.equals("DAMAGE_INDICATOR")) {
                    event.setCancelled(true);
                    debug("Cancelled " + (isSweepParticle ? "sweep" : "damage indicator") + " particles",
                            player);
                }
            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting sweep or damage indicator packets. " +
                        "Sweep cancellation should still work, but particles might show up.");
            }
        }
    }
}
