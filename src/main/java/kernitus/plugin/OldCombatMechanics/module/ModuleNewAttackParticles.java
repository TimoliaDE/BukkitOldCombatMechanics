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
import org.bukkit.plugin.Plugin;

import java.util.Locale;

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
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || !isEnabled(packetEvent.getPlayer().getWorld()))
                return;

            try {
                final PacketContainer packetContainer = packetEvent.getPacket();
                String particleName;
                try {
                    particleName = packetContainer.getNewParticles().read(0).getParticle().getKey().getKey();
                } catch (Exception exception) {
                    particleName = packetContainer.getParticles().read(0).getName(); // for pre 1.13
                }

                String particleId = particleName.toUpperCase(Locale.ROOT);
                boolean isSweepParticle = particleId.contains("SWEEP");
                if (isSweepParticle || particleId.equals("DAMAGE_INDICATOR")) {
                    packetEvent.setCancelled(true);
                    debug("Cancelled " + (isSweepParticle ? "sweep" : "damage indicator") + " particles",
                            packetEvent.getPlayer());
                }
            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting sweep packets. Sweep cancellation should " +
                        "still work, but particles might show up.");
            }
        }
    }
}
