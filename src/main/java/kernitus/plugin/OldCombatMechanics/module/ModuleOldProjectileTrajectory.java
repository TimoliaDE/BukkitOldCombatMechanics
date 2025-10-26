package kernitus.plugin.OldCombatMechanics.module;

import io.papermc.paper.configuration.WorldConfiguration;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.versions.ReflectorUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * This disables the relative projectile velocity. In other words,
 * projectiles such as arrows or thrown potions will no longer be
 * affected by the player's movement, e.g. when jumping.
 */
public class ModuleOldProjectileTrajectory extends OCMModule {

    public ModuleOldProjectileTrajectory(OCMMain plugin) {
        super(plugin, "old-projectile-trajectory");
        reload();
    }

    @Override
    public void reload() {
        Bukkit.getOnlinePlayers().forEach(this::handleProjectileVelocity);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        handleProjectileVelocity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        handleProjectileVelocity(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        handleProjectileVelocity(event.getPlayer());
    }

    private void handleProjectileVelocity(Player player) {
        handleProjectileVelocity(player.getWorld());
    }

    private void handleProjectileVelocity(World world) {
        if (!hasDisabledRelativeProjectileVelocity(world))
            disableRelativeProjectileVelocity(world);
    }

    private boolean hasDisabledRelativeProjectileVelocity(World world) {
        WorldConfiguration.Misc misc = ReflectorUtil.getWorldConfigurationMisc(world);
        return misc.disableRelativeProjectileVelocity;
    }

    private void disableRelativeProjectileVelocity(World world) {
        WorldConfiguration.Misc misc = ReflectorUtil.getWorldConfigurationMisc(world);
        misc.disableRelativeProjectileVelocity = true;
    }
}
