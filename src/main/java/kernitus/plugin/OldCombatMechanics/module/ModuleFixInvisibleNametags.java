package kernitus.plugin.OldCombatMechanics.module;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.versions.ViaVersionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Fixes a bug for 1.8 clients where the nametags of other
 * invisible players would still be visible above their heads.
 * This occurs when invisible players are in a team with
 * name tag visibility enabled.
 * Requires ViaVersion that allows 1.8 clients.
 */
public class ModuleFixInvisibleNametags extends OCMModule {

    private final TeamPacketListener teamPacketListener = new TeamPacketListener();
    private static final Set<Player> invisiblePlayers = new HashSet<>();

    public ModuleFixInvisibleNametags(OCMMain plugin) {
        super(plugin, "fix-invisible-nametags");
        reload();
    }

    @Override
    public void reload() {
        if (isEnabled())
            PacketEvents.getAPI().getEventManager().registerListener(teamPacketListener);
        else
            PacketEvents.getAPI().getEventManager().unregisterListener(teamPacketListener);
    }

    @EventHandler
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        @Nullable PotionEffect oldEffect = event.getOldEffect();
        @Nullable PotionEffect newEffect = event.getNewEffect();

        if (newEffect != null && newEffect.getType() == PotionEffectType.INVISIBILITY) {
            hideNameTag(player);
            invisiblePlayers.add(player);

        } else if (oldEffect != null && oldEffect.getType() == PotionEffectType.INVISIBILITY &&
                newEffect == null) {
            showNameTag(player);
            invisiblePlayers.remove(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed()) return;

        Player viewer = event.getPlayer();
        if (!isEnabled(viewer)) return;

        invisiblePlayers.forEach(invis -> hideNameTag(invis, viewer));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!ViaVersionUtil.isLegacyClientsAllowed()) return;

        Player viewer = event.getPlayer();
        if (!isEnabled(viewer)) return;

        invisiblePlayers.forEach(invis -> showNameTag(invis, viewer));
    }

    private void hideNameTag(Player target) {
        changeNameTag(target, Bukkit.getOnlinePlayers(), true);
    }

    private void hideNameTag(Player target, Player viewer) {
        changeNameTag(target, Set.of(viewer), true);
    }

    private void showNameTag(Player target) {
        changeNameTag(target, Bukkit.getOnlinePlayers(), false);
    }

    private void showNameTag(Player target, Player viewer) {
        changeNameTag(target, Set.of(viewer), false);
    }

    private void changeNameTag(Player player, Collection<? extends Player> viewers, boolean invisibility) {
        final Team.OptionStatus status = invisibility ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS;
        final String name = player.getName();

        for (final Player other : viewers) {
            Team otherTeam = other.getScoreboard().getEntryTeam(name);

            if (otherTeam != null) {
                otherTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, status);
            }
        }
    }

    private class TeamPacketListener extends PacketListenerAbstract {

        private boolean disabledDueToError;

        @Override
        public void onPacketSend(PacketSendEvent packetEvent) {
            if (!ViaVersionUtil.isLegacyClientsAllowed()) return;

            if (disabledDueToError || packetEvent.isCancelled())
                return;

            try {
                if (!PacketType.Play.Server.TEAMS.equals(packetEvent.getPacketType()))
                    return;

                final Object playerObject = packetEvent.getPlayer();
                if (!(playerObject instanceof Player player))
                    return;

                if (!isEnabled(player))
                    return;

                WrapperPlayServerTeams wrapper = new WrapperPlayServerTeams(packetEvent);
                WrapperPlayServerTeams.TeamMode mode = wrapper.getTeamMode();

                if (mode == WrapperPlayServerTeams.TeamMode.REMOVE) return;

                WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = wrapper.getTeamInfo().orElse(null);
                if (teamInfo == null) return;
                if (teamInfo.getTagVisibility() == WrapperPlayServerTeams.NameTagVisibility.NEVER) return;

                teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                packetEvent.markForReEncode(true);
                debug("Changed nametag when updating teams", player);

            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting player invisibility.");
            }
        }
    }
}
