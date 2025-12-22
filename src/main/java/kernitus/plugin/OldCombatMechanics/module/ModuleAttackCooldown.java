/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.cryptomorin.xseries.XAttribute;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Disables the attack cooldown.
 */
public class ModuleAttackCooldown extends OCMModule {

    private final double NEW_ATTACK_SPEED = 4;
    private boolean excludeNormalSpears;
    private boolean excludeLungeSpears;

    public ModuleAttackCooldown(OCMMain plugin) {
        super(plugin, "disable-attack-cooldown");
    }

    @Override
    public void reload() {
        excludeNormalSpears = module().getBoolean("excludeNormalSpears", false);
        excludeLungeSpears = module().getBoolean("excludeLungeSpears", true);
        Bukkit.getOnlinePlayers().forEach(this::adjustAttackSpeed);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerJoinEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        adjustAttackSpeed(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent e) {
        setAttackSpeed(e.getPlayer(), NEW_ATTACK_SPEED);
    }

    /**
     * Adjusts the attack speed to the default or configured value, depending on
     * whether the module is enabled.
     *
     * @param player the player to set the attack speed for
     */
    private void adjustAttackSpeed(Player player) {
        adjustAttackSpeed(player, player.getInventory().getItemInMainHand());
    }

    private void adjustAttackSpeed(Player player, @Nullable ItemStack iStack) {
        final double attackSpeed = isEnabled(player) && !isAffected(iStack) ?
                module().getDouble("generic-attack-speed") : NEW_ATTACK_SPEED;

        setAttackSpeed(player, attackSpeed);
    }

    @Override
    public void onModesetChange(Player player) {
        adjustAttackSpeed(player);
    }

    @EventHandler
    public void onPlayerInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        if (!excludeNormalSpears && !excludeLungeSpears) return;

        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        ItemStack oldItem = event.getOldItemStack();
        ItemStack newItem = event.getNewItemStack();

        boolean hasChanged = isAffected(oldItem) != isAffected(newItem);
        if (hasChanged)
            adjustAttackSpeed(player, newItem);
    }

    private boolean isAffected(ItemStack iStack) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return false;

        return iStack != null && (excludeNormalSpears && !hasLungeEffect(iStack) ||
                excludeLungeSpears && hasLungeEffect(iStack)) &&
                Tag.ITEMS_SPEARS.isTagged(iStack.getType());
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!excludeNormalSpears && !excludeLungeSpears) return;

        Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        PlayerInventory inv = player.getInventory();
        ItemStack oldItem = inv.getItem(event.getPreviousSlot());
        ItemStack newItem = inv.getItem(event.getNewSlot());

        boolean hasChanged = isAffected(oldItem) != isAffected(newItem);
        if (hasChanged)
            adjustAttackSpeed(player, newItem);
    }

    /**
     * Sets the attack speed to the given value.
     *
     * @param player      the player to set it for
     * @param attackSpeed the attack speed to set it to
     */
    public void setAttackSpeed(Player player, double attackSpeed) {
        final AttributeInstance attribute = player.getAttribute(XAttribute.ATTACK_SPEED.get());
        if (attribute == null)
            return;

        final double baseValue = attribute.getBaseValue();

        final String modesetName = PlayerStorage.getPlayerData(player.getUniqueId())
                .getModesetForWorld(player.getWorld().getUID());
        debug(String.format("Setting attack speed to %.2f (was: %.2f) for %s in mode %s", attackSpeed, baseValue,
                player.getName(), modesetName));

        if (baseValue != attackSpeed) {
            debug(String.format("Setting attack speed to %.2f (was: %.2f)", attackSpeed, baseValue), player);

            attribute.setBaseValue(attackSpeed);
            player.saveData();
        }
    }

    private boolean hasLungeEffect(ItemStack iStack) {
        if (!Reflector.versionIsNewerOrEqualTo(1, 21, 11)) return false;
        if (iStack == null) return false;
        if (!iStack.hasItemMeta()) return false;

        return iStack.getItemMeta().hasEnchant(Enchantment.LUNGE);
    }
}
