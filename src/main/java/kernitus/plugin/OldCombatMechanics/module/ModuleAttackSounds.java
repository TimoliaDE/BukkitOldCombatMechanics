/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.cryptomorin.xseries.XSound;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.TextUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A module to disable the new attack sounds.
 */
public class ModuleAttackSounds extends OCMModule {

    private final SoundListener soundListener = new SoundListener();
    private final Set<String> blockedSounds = new HashSet<>();

    public ModuleAttackSounds(OCMMain plugin) {
        super(plugin, "disable-attack-sounds");
        reload();
    }

    @Override
    public void reload() {
        blockedSounds.clear();
        blockedSounds.addAll(getBlockedSounds());

        if (isEnabled() && !blockedSounds.isEmpty())
            PacketEvents.getAPI().getEventManager().registerListener(soundListener);
        else
            PacketEvents.getAPI().getEventManager().unregisterListener(soundListener);
    }

    private Collection<String> getBlockedSounds() {
        List<String> fromConfig = module().getStringList("blocked-sound-names");
        Set<String> processed = new HashSet<>();
        for (String soundName : fromConfig) {
            Optional<XSound> xSound = XSound.matchXSound(soundName);
            if (xSound.isPresent()) {
                Sound sound = xSound.get().parseSound();
                if (sound != null) {
                    // On modern versions, we can get the namespaced key directly
                    try {
                        Method getKeyMethod = Sound.class.getMethod("getKey");
                        Object key = getKeyMethod.invoke(sound);
                        String formattedKey = TextUtils.getFormattedString(key.toString());
                        processed.add(formattedKey);
                        continue;
                    } catch (Exception ignored) {
                        // This server version doesn't have the getKey method, so we fall back to the
                        // legacy name
                    }
                }
                // Fallback for older versions or if the sound is not in the Bukkit enum
                String formattedKey = TextUtils.getFormattedString(soundName);
                processed.add(formattedKey);
            } else {
                Messenger.warn("Invalid sound name in config: " + soundName);
            }
        }
        return processed;
    }

    /**
     * Disables attack sounds.
     */
    private class SoundListener extends PacketAdapter {

        private boolean disabledDueToError;

        public SoundListener(Plugin plugin) {
            super(plugin, ListenerPriority.HIGH, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        }

        @Override
        public void onPacketSending(PacketEvent packetEvent) {
            if (disabledDueToError || packetEvent.isCancelled() || !isEnabled(packetEvent.getPlayer()))
                return;
            if (blockedSounds.isEmpty())
                return;

            PacketContainer packet = event.getPacket();
            try {
                NamespacedKey namespacedKey;
                if (Reflector.versionIsNewerOrEqualTo(1, 20, 5)) {
                    namespacedKey = Registry.SOUND_EVENT.getKey(packet.getSoundEffects().read(0));
                } else {
                    namespacedKey = packet.getSoundEffects().read(0).getKey();
                }

                if (namespacedKey == null) return;

                String soundName = TextUtils.getFormattedString(namespacedKey.getKey());
                if (blockedSounds.contains(soundName)) {
                    event.setCancelled(true);
                    debug("Blocked sound " + soundName, player);
                }

            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting sound packets.");
            }
        }
    }
}