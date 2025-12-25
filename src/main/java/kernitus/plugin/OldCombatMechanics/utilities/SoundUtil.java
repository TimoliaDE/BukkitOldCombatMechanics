package kernitus.plugin.OldCombatMechanics.utilities;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.viaversion.viaversion.api.Via;
import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SoundUtil {

//    public static void playSound(Player player, Location loc, Sound sound,
//                                 SoundCategory source, float volume, float pitch) {
////        Holder<SoundEvent> x = CraftSound.bukkitToMinecraftHolder(sound);
////        Holder<SoundEvent> holder = Holder.direct(hold);
////
////        player.playSound();
//        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
//        long randomLong = serverPlayer.random.nextLong();
////        SoundSource source = SoundSource.valueOf(category.name());
//
//        serverPlayer.connection.send(new ClientboundSoundPacket(holder, sound, source,
//                loc.getX(), loc.getY(), loc.getZ(), volume, pitch, randomLong));
//    }

    public static void playSound(OCMModule module, Location loc, String soundName, SoundCategory category,
                                 float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) return;

        world.getPlayers().stream().filter(module::isEnabled)
                .filter(SoundUtil::isLegacyClient)
                .filter(player -> canHear(player, loc))
                .forEach(player -> player.playSound(loc, soundName, category, volume, pitch));
    }

    public static void playSound(OCMModule module, Location loc, Sound sound, SoundCategory category,
                          float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) return;

        world.getPlayers().stream().filter(module::isEnabled)
                .filter(SoundUtil::isLegacyClient)
                .filter(player -> canHear(player, loc))
                .forEach(player -> playSound(player, loc, sound, category, volume, pitch));
    }

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    private static boolean isLegacyClient(Player player) {
        return Via.getAPI().getPlayerVersion(player) <= 47;
    }

    private static boolean canHear(Player player, Location loc) {
        if (!player.getWorld().equals(loc.getWorld())) return false;

        final double soundDistanceSquared = 16 * 16;
        return player.getLocation().distanceSquared(loc) <= soundDistanceSquared;
    }

    public static void playSound(Player player, Location loc, Sound sound, SoundCategory category,
                                 float volume, float pitch) {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        PacketContainer packet = manager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);

        packet.getSoundEffects().write(0, sound);
        EnumWrappers.SoundCategory protocolCategory = EnumWrappers.SoundCategory.valueOf(category.name());
        packet.getSoundCategories().write(0, protocolCategory);

        packet.getIntegers()
                .write(0, (int) (loc.getX() * 8))
                .write(1, (int) (loc.getY() * 8))
                .write(2, (int) (loc.getZ() * 8));

        packet.getFloat().write(0, volume);
        packet.getFloat().write(1, pitch);

        try {
            manager.sendServerPacket(player, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
