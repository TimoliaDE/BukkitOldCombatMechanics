package kernitus.plugin.OldCombatMechanics.utilities;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class NametagUtils {
    /**
     * Erstellt ein Fake-Team, um den Spieler for alle anderen unsichtbar zu machen.
     *
     * @param target         Spieler, dessen Nametag versteckt werden soll
     * @param visibleForSelf true, wenn der Spieler sich selbst noch sehen soll
     */
    public static void createFakeInvisibleTeam(Player target, boolean visibleForSelf) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target) && visibleForSelf) continue;

            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(""), null, null,
                    WrapperPlayServerTeams.NameTagVisibility.NEVER, WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.FRIENDLY_CAN_SEE_INVISIBLE);

            WrapperPlayServerTeams teams =
                    new WrapperPlayServerTeams("invis", WrapperPlayServerTeams.TeamMode.CREATE, teamInfo,
                            target.getName(), target.getUniqueId().toString());

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teams);
        }
    }

    /**
     * Entfernt das Fake-Team wieder
     */
    public static void removeFakeTeam(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.text(""), null, null,
                    WrapperPlayServerTeams.NameTagVisibility.NEVER, WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.WHITE, WrapperPlayServerTeams.OptionData.FRIENDLY_CAN_SEE_INVISIBLE);

            WrapperPlayServerTeams teams =
                    new WrapperPlayServerTeams("invis", WrapperPlayServerTeams.TeamMode.REMOVE,
                            (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                            target.getName(), target.getUniqueId().toString());

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teams);
        }
    }
}
