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
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Team;

import java.util.stream.Stream;

import static org.bukkit.potion.PotionEffectType.*;

/**
 * Fixes a bug for 1.8 clients where the nametags of other
 * invisible players would still be visible above their heads.
 * This occurs when invisible players are in a team with
 * name tag visibility enabled.
 * Requires ViaVersion that allows 1.8 clients.
 */
public class ModuleFixInvisibleNametags extends OCMModule {

    private final TeamPacketListener teamPacketListener = new TeamPacketListener();

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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        PotionEffect oldEffect = event.getOldEffect();
        PotionEffect newEffect = event.getNewEffect();
        boolean hasNewEffect = newEffect != null;

        if (hasNewEffect && newEffect.getType() == INVISIBILITY) {
            hideNameTag(player);

        } else if (oldEffect != null && oldEffect.getType() == INVISIBILITY && !hasNewEffect) {
            showNameTag(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        if (player.hasPotionEffect(INVISIBILITY)) {
            hideNameTag(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        if (player.hasPotionEffect(INVISIBILITY)) {
            showNameTag(player);
        }
    }

    private void hideNameTag(Player target) {
        changeNameTag(target, true);
    }

    private void showNameTag(Player target) {
        changeNameTag(target, false);
    }

    private void changeNameTag(Player target, boolean invisible) {
        final Team.OptionStatus status = invisible ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS;
        final String name = target.getName();

        // When invisible, exclude target from the viewers so they can see name tags of visible players
        Stream<? extends Player> otherPlayers = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.equals(target));

        otherPlayers.forEach(other -> {
            Team otherTeam = other.getScoreboard().getEntryTeam(name);
            if (otherTeam == null) return;

            otherTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, status);
        });
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
                if (!(playerObject instanceof Player viewer))
                    return;

                if (!isEnabled(viewer))
                    return;

                WrapperPlayServerTeams wrapper = new WrapperPlayServerTeams(packetEvent);
                WrapperPlayServerTeams.TeamMode mode = wrapper.getTeamMode();

                if (mode != WrapperPlayServerTeams.TeamMode.ADD_ENTITIES &&
                        mode != WrapperPlayServerTeams.TeamMode.CREATE) return;

                WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = wrapper.getTeamInfo().orElse(null);
                if (teamInfo == null) return;

                String targetName = wrapper.getTeamName();
                Player target = Bukkit.getPlayer(targetName);
                boolean isInvisible = target != null && target.hasPotionEffect(INVISIBILITY);

                if (isInvisible && !viewer.getName().equals(targetName) &&
                        teamInfo.getTagVisibility() != WrapperPlayServerTeams.NameTagVisibility.NEVER) {
                    teamInfo.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER);
                    packetEvent.markForReEncode(true);
                    debug("Set nametag visibility to never when changing teams", viewer);
                }

            } catch (Exception | ExceptionInInitializerError e) {
                disabledDueToError = true;
                Messenger.warn(e, "Error detecting player invisibility.");
            }
        }
    }
}
