package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
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

/*
 * This matches the old attack range (3.1125 blocks) in 1.8 pvp.
 * Only supported in version 1.20.5 or later.
 * When enabled, this applies to 1.8 clients and 1.20.5+ clients only.
 * However, 1.9–1.20.4 clients still have the new attack range
 * (3.0 blocks) due to technical limitations.
 */
public class ModuleOldAttackRange extends OCMModule {

    private double entityInteractionRange;

    public ModuleOldAttackRange(OCMMain plugin) {
        super(plugin, "old-attack-range");
        reload();
    }

    @Override
    public void reload() {
        entityInteractionRange = module().getDouble("entityInteractionRange", 3.1125);
        Bukkit.getOnlinePlayers().forEach(this::adjustEntityInteractionRange);
    }

    private void adjustEntityInteractionRange(Player player) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 20, 5)) return;

        if (isEnabled(player)) {
            changeAttribute(player);
        } else
            resetAttribute(player);
    }

    @Override
    public void onModesetChange(Player player) {
        adjustEntityInteractionRange(player);
    }

    private void changeAttribute(Player player) {
        setAttributeValue(player, entityInteractionRange);
    }

    private void resetAttribute(Player player) {
        setAttributeValue(player, null);
    }

    private void setAttributeValue(Player player, @Nullable Double value) {
        Attribute attributeName = XAttribute.ENTITY_INTERACTION_RANGE.get();
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

        adjustEntityInteractionRange(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        adjustEntityInteractionRange(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        adjustEntityInteractionRange(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        adjustEntityInteractionRange(event.getPlayer());
    }
}
