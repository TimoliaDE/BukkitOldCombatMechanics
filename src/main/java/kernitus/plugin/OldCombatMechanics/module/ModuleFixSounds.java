package kernitus.plugin.OldCombatMechanics.module;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.SoundUtil;
import kernitus.plugin.OldCombatMechanics.utilities.TextUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixes several incorrect sound mappings for 1.8 clients:
 * - Cobwebs incorrectly play the stone sound instead of the leaf sound
 *   when placed.
 * - Fire placed using flint and steel or a fire charge plays the correct
 *   item sound instead of the wool sound.
 * - Replaces the incorrect "minecraft:music.disc.ward" sound with the
 *   correct "minecraft:ui.button.click" sound.
 * - Adds the missing arrow pickup sound.
 *
 * Requires ViaVersion.
 */
public class ModuleFixSounds extends OCMModule {

    private final SoundListener soundListener = new SoundListener();
    private static final Set<UUID> blockedSoundUUIDs = new HashSet<>();

    public ModuleFixSounds(OCMMain plugin) {
        super(plugin, "fix-sounds");
        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            PacketEvents.getAPI().getEventManager().registerListener(soundListener);
        else
            PacketEvents.getAPI().getEventManager().unregisterListener(soundListener);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        Random random = new Random();
        SoundUtil.playSound(this, player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS,
                0.2F, ((random.nextFloat() - random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        Block block = event.getBlockPlaced();
        Material item = event.getItemInHand().getType();

        if (block.getType() != Material.FIRE) return;
        if (item != Material.FLINT_AND_STEEL && item != Material.FIRE_CHARGE) return;
        if (block.getBlockData().getSoundGroup().getPlaceSound() != Sound.BLOCK_WOOL_PLACE) return;

        boolean fireCharge = item == Material.FIRE_CHARGE;
        String soundName = fireCharge ? "minecraft:item.firecharge.use" : "minecraft:item.flintandsteel.use";

        SoundUtil.playSound(this, block.getLocation(), soundName, SoundCategory.BLOCKS, 1.0F,
                ThreadLocalRandom.current().nextFloat() * 0.4F + 0.8F, uuid -> {
                    Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> blockedSoundUUIDs.remove(uuid));
                    blockedSoundUUIDs.add(uuid);
                });
    }

    /**
     * Replaces the sound if needed.
     */
    private class SoundListener extends PacketListenerAbstract {

        private static final Set<String> fireSoundNames = Set.of("ITEM_FIRECHARGE_USE", "ITEM_FLINTANDSTEEL_USE");
        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketSendEvent packetEvent) {
            if (disabledDueToError || packetEvent.isCancelled())
                return;

            final Object playerObject = packetEvent.getPlayer();
            if (!(playerObject instanceof Player))
                return;

            final Player player = (Player) playerObject;
            if (!isEnabled(player))
                return;

            final Object packetType = packetEvent.getPacketType();
            if (!Play.Server.NAMED_SOUND_EFFECT.equals(packetType)
                    && !Play.Server.ENTITY_SOUND_EFFECT.equals(packetType)) {
                return;
            }

            try {
                WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(packetEvent);
                com.github.retrooper.packetevents.protocol.sound.Sound sound = wrapper.getSound();
                if (sound == null || sound.getSoundId() == null)
                    return;

                String soundName = TextUtils.getFormattedString(sound.getSoundId().toString());
                UUID uuid = player.getUniqueId();

                if (!fireSoundNames.contains(soundName) && blockedSoundUUIDs.remove(uuid)) {
                    debug("Replaced wool sound with fire sound", player);
                    packetEvent.setCancelled(true);
                    return;
                }

                boolean cobwebPlaceSound = soundName.equals("BLOCK_COBWEB_PLACE");
                boolean uiButtonClick = soundName.equals("UI_BUTTON_CLICK");
                if (!cobwebPlaceSound && !uiButtonClick) return;

                Vector3i effectPos = wrapper.getEffectPosition();
                int x = effectPos.getX();
                int y = effectPos.getY();
                int z = effectPos.getZ();

                Location loc = new Location(player.getWorld(), x / 8.0, y / 8.0, z / 8.0);

                float volume = wrapper.getVolume();
                float pitch = wrapper.getPitch();

                packetEvent.setCancelled(true);

                if (cobwebPlaceSound) {
                    player.playSound(loc, Sound.BLOCK_STONE_PLACE, volume, pitch);
                    debug("Replaced cobweb place sound with stone place sound", player);

                } else {
                    player.playSound(loc, "minecraft:ui.button.click", volume, pitch);
                    debug("Replaced music disc ward sound with ui button click sound", player);
                }

            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting named sound effect packets.");
            }
        }
    }
}
