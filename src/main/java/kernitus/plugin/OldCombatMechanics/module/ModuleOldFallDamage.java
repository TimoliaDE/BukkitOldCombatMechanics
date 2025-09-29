package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.annotation.Nullable;

public class ModuleOldFallDamage extends OCMModule {

    private double safeFallDistance;

    public ModuleOldFallDamage(OCMMain plugin) {
        super(plugin, "old-fall-damage");
        reload();
    }

    @Override
    public void reload() {
        safeFallDistance = module().getDouble("safeFallDistance", 2.834627);
        Bukkit.getOnlinePlayers().forEach(this::adjustSafeFallDistance);
    }

    private void adjustSafeFallDistance(Player player) {
        if (isEnabled(player)) {
            changeAttribute(player);
        } else
            resetAttribute(player);
    }

    @Override
    public void onModesetChange(Player player) {
        adjustSafeFallDistance(player);
    }

    private void changeAttribute(Player player) {
        setAttributeValue(player, safeFallDistance);
    }

    private void resetAttribute(Player player) {
        setAttributeValue(player, null);
    }

    private void setAttributeValue(Player player, @Nullable Double value) {
        Attribute attributeName = XAttribute.SAFE_FALL_DISTANCE.get();
        if (attributeName == null) return;

        final AttributeInstance attribute = player.getAttribute(attributeName);

        if (attribute != null) {
            attribute.getModifiers().forEach(attribute::removeModifier);
            attribute.setBaseValue(value != null ? value : attribute.getDefaultValue());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        adjustSafeFallDistance(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        adjustSafeFallDistance(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        adjustSafeFallDistance(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        adjustSafeFallDistance(event.getPlayer());
    }
}
