package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.viaversion.viaversion.api.Via;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Fixes incorrect cobweb placement sound for 1.8 clients on newer versions
 * by replacing it with the stone place sound.
 * Requires ViaVersion.
 */
public class ModuleFixCobwebPlaceSound extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final SoundListener soundListener = new SoundListener(plugin);

    public ModuleFixCobwebPlaceSound(OCMMain plugin) {
        super(plugin, "fix-cobweb-place-sound");
        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            protocolManager.addPacketListener(soundListener);
        else
            protocolManager.removePacketListener(soundListener);
    }

    private String getFormattedString(String soundName) {
        return soundName.toUpperCase(Locale.ROOT)
                .replaceFirst("MINECRAFT:", "").replace('.', '_');
    }

    /**
     * Replaces the sound if needed.
     */
    private class SoundListener extends PacketAdapter {

        private boolean disabledDueToError;

        public SoundListener(Plugin plugin) {
            super(plugin, ListenerPriority.HIGH, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            Player player = event.getPlayer();
            if (disabledDueToError || event.isCancelled() || !isLegacyClient(player) || !isEnabled(player.getWorld()))
                return;

            PacketContainer packet = event.getPacket();
            String soundName;

            try {
                NamespacedKey namespacedKey;
                if (Reflector.versionIsNewerOrEqualTo(1, 20, 5)) {
                    namespacedKey = Registry.SOUND_EVENT.getKey(packet.getSoundEffects().read(0));
                } else {
                    namespacedKey = packet.getSoundEffects().read(0).getKey();
                }

                if (namespacedKey == null) return;

                soundName = getFormattedString(namespacedKey.getKey());
                if (!soundName.equals("BLOCK_COBWEB_PLACE")) return;

                int x = packet.getIntegers().read(0);
                int y = packet.getIntegers().read(1);
                int z = packet.getIntegers().read(2);

                Location loc = new Location(player.getWorld(), x / 8.0, y / 8.0, z / 8.0);

                float volume = packet.getFloat().read(0);
                float pitch = packet.getFloat().read(1);

                event.setCancelled(true);

                player.playSound(loc, Sound.BLOCK_STONE_PLACE, volume, pitch);
                debug("Replaced cobweb place sound with stone place sound");

            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting named sound effect packets.");
            }
        }
    }

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    private boolean isLegacyClient(Player player) {
        return Via.getAPI().getPlayerVersion(player) <= 47;
    }
}
