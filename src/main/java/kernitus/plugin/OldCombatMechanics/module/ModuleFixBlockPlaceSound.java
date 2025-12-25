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
import kernitus.plugin.OldCombatMechanics.utilities.SoundUtil;
import kernitus.plugin.OldCombatMechanics.utilities.TextUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixes incorrect placement sounds for 1.8 clients:
 * - Cobwebs use the stone sound instead of the leaf sound.
 * - Fire placed with flint and steel or a fire charge uses the correct item sound
 *   instead of the wool sound.
 * Requires ViaVersion.
 */
public class ModuleFixBlockPlaceSound extends OCMModule {

    private final ProtocolManager protocolManager = plugin.getProtocolManager();
    private final SoundListener soundListener = new SoundListener(plugin);
    private static final Set<UUID> blockedSoundUUIDs = new HashSet<>();

    public ModuleFixBlockPlaceSound(OCMMain plugin) {
        super(plugin, "fix-block-place-sound");
        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            protocolManager.addPacketListener(soundListener);
        else
            protocolManager.removePacketListener(soundListener);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isLegacyClient(player)) return;

        Block block = event.getBlockPlaced();
        Material item = event.getItemInHand().getType();

        if (block.getType() != Material.FIRE) return;
        if (item != Material.FLINT_AND_STEEL && item != Material.FIRE_CHARGE) return;
        if (block.getBlockData().getSoundGroup().getPlaceSound() != Sound.BLOCK_WOOL_PLACE) return;

        boolean fireCharge = item == Material.FIRE_CHARGE;
        String soundName = fireCharge ? "item.firecharge.use" : "item.flintandsteel.use";

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> blockedSoundUUIDs.remove(uuid));
        blockedSoundUUIDs.add(uuid);

        SoundUtil.playSound(this, block.getLocation(), soundName, SoundCategory.BLOCKS, 1.0F,
                ThreadLocalRandom.current().nextFloat() * 0.4F + 0.8F);
    }

    /**
     * Replaces the sound if needed.
     */
    private class SoundListener extends PacketAdapter {

        private static final Set<String> fireSoundNames = Set.of("ITEM_FIRECHARGE_USE", "ITEM_FLINTANDSTEEL_USE");
        private boolean disabledDueToError;

        public SoundListener(Plugin plugin) {
            super(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.NAMED_SOUND_EFFECT);
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            Player player = event.getPlayer();
            if (disabledDueToError || event.isCancelled() || !isLegacyClient(player) || !isEnabled(player.getWorld()))
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
                UUID uuid = player.getUniqueId();

                if (!fireSoundNames.contains(soundName) && blockedSoundUUIDs.remove(uuid)) {
                    debug("Replaced wool sound with fire sound", player);
                    event.setCancelled(true);
                    return;
                }

                if (!soundName.equals("BLOCK_COBWEB_PLACE")) return;

                int x = packet.getIntegers().read(0);
                int y = packet.getIntegers().read(1);
                int z = packet.getIntegers().read(2);

                Location loc = new Location(player.getWorld(), x / 8.0, y / 8.0, z / 8.0);

                float volume = packet.getFloat().read(0);
                float pitch = packet.getFloat().read(1);

                event.setCancelled(true);

                player.playSound(loc, Sound.BLOCK_STONE_PLACE, volume, pitch);
                debug("Replaced cobweb place sound with stone place sound", player);

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
