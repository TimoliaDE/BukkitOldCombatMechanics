/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.destroystokyo.paper.MaterialTags;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ItemUtil;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ModuleSwordBlocking extends OCMModule {

    public static final NamespacedKey KEY = new NamespacedKey(OCMMain.getInstance(),
            "ocm.module_sword_blocking");

    private static final ItemStack SHIELD;

    static {
        ItemStack iStack = new ItemStack(Material.SHIELD);
        ItemMeta iMeta = iStack.getItemMeta();
        iMeta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "");
        iStack.setItemMeta(iMeta);
        SHIELD = iStack;
    }

    // Not using WeakHashMaps here, for extra reliability
    private final Map<UUID, ItemStack> storedItems = new HashMap<>();
    private final Map<UUID, Collection<BukkitTask>> correspondingTasks = new HashMap<>();
    private int restoreDelay;

    // Only used <1.13, where BlockCanBuildEvent.getPlayer() is not available
    private Map<Location, UUID> lastInteractedBlocks;

    public ModuleSwordBlocking(OCMMain plugin) {
        super(plugin, "sword-blocking");

        if (!Reflector.versionIsNewerOrEqualTo(1, 13, 0)) {
            lastInteractedBlocks = new WeakHashMap<>();
        }
    }

    @Override
    public void reload() {
        restoreDelay = module().getInt("restoreDelay", 40);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockCanBuildEvent e) {
        if (e.isBuildable()) return;

        Player player;

        // If <1.13 get player who last interacted with block
        if (lastInteractedBlocks != null) {
            final Location blockLocation = e.getBlock().getLocation();
            final UUID uuid = lastInteractedBlocks.remove(blockLocation);
            player = Bukkit.getServer().getPlayer(uuid);
        } else player = e.getPlayer();

        if (player == null || !isEnabled(player)) return;

        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        doShieldBlock(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent e) {
        final Action action = e.getAction();
        final Player player = e.getPlayer();

        if (!isEnabled(player)) return;

        boolean usingHand = e.getHand() == EquipmentSlot.HAND;

        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            // Ensures that the offhand shield is priorizied when using a blocking sword
            // in the mainhand
            if (hasShieldNoCooldown(player) && usingHand && ItemUtil.isHoldingConsumableSword(player, false)) {
                removeBlockAttributesFromHoldingSword(player);
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> player.startUsingItem(EquipmentSlot.OFF_HAND));
                return;
            }

            giveBlockAttributesToSwords(player);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        // If they clicked on an interactive block, the 2nd event with the offhand won't fire
        // This is also the case if the main hand item was used, e.g. a bow
        // TODO right-clicking on a mob also only fires one hand
        if (action == Action.RIGHT_CLICK_BLOCK && usingHand) return;
        if (e.isBlockInHand()) {
            if (lastInteractedBlocks != null) {
                final Block clickedBlock = e.getClickedBlock();
                if (clickedBlock != null)
                    lastInteractedBlocks.put(clickedBlock.getLocation(), player.getUniqueId());
            }
            return; // Handle failed block place in separate listener
        }

        doShieldBlock(player);
    }

    private void doShieldBlock(Player player) {
        final PlayerInventory inventory = player.getInventory();

        final ItemStack mainHandItem = inventory.getItemInMainHand();
        final ItemStack offHandItem = inventory.getItemInOffHand();

        if (!isHoldingSword(mainHandItem.getType())) return;

        if (module().getBoolean("use-permission") &&
                !player.hasPermission("oldcombatmechanics.swordblock")) return;

        final UUID id = player.getUniqueId();

        if (!isPlayerBlocking(player)) {
            if (hasShield(inventory)) return;
            debug("Storing " + offHandItem, player);
            storedItems.put(id, offHandItem);

            inventory.setItemInOffHand(SHIELD);
            // Force an inventory update to avoid ghost items
            player.updateInventory();
        }
        scheduleRestore(player);
    }

    @EventHandler
    public void onHotBarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        restore(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        restore(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        restore(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        final UUID id = player.getUniqueId();
        if (!areItemsStored(id)) return;

        //TODO what if they legitimately had a shield?
        event.getDrops().replaceAll(item ->
                item.getType() == Material.SHIELD ?
                        storedItems.remove(id) : item
        );

        // Handle keepInventory = true
        restore(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        final Player player = event.getPlayer();
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        if (areItemsStored(player.getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
                giveBlockAttributesToSwords(player);
                return;
            }

            if (areItemsStored(player.getUniqueId())) {
                final ItemStack cursor = e.getCursor();
                final ItemStack current = e.getCurrentItem();
                if (cursor.getType() == Material.SHIELD || current != null &&
                        current.getType() == Material.SHIELD) {
                    e.setCancelled(true);
                    restore(player, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        final Item is = event.getItemDrop();
        final Player player = event.getPlayer();

        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(player);
            return;
        }

        if (areItemsStored(player.getUniqueId()) && is.getItemStack().getType() == Material.SHIELD) {
            event.setCancelled(true);
            restore(player);
        }
    }

    private void restore(Player player) {
        restore(player, false);
    }

    private void restore(Player p, boolean force) {
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 3)) {
            giveBlockAttributesToSwords(p);
            return;
        }

        final UUID id = p.getUniqueId();

        tryCancelTask(id);

        if (!areItemsStored(id)) return;

        // If they are still blocking with the shield, postpone restoring
        if (!force && isPlayerBlocking(p)) scheduleRestore(p);
        else p.getInventory().setItemInOffHand(storedItems.remove(id));
    }

    private void tryCancelTask(UUID id) {
        Optional.ofNullable(correspondingTasks.remove(id))
                .ifPresent(tasks -> tasks.forEach(BukkitTask::cancel));
    }

    private void scheduleRestore(Player p) {
        final UUID id = p.getUniqueId();
        tryCancelTask(id);

        final BukkitTask removeItem = Bukkit.getScheduler()
                .runTaskLater(plugin, () -> restore(p), restoreDelay);

        final BukkitTask checkBlocking = Bukkit.getScheduler()
                .runTaskTimer(plugin, () -> {
                    if (!isPlayerBlocking(p))
                        restore(p);
                }, 10L, 2L);

        final List<BukkitTask> tasks = new ArrayList<>(2);
        tasks.add(removeItem);
        tasks.add(checkBlocking);
        correspondingTasks.put(p.getUniqueId(), tasks);
    }

    private boolean areItemsStored(UUID uuid) {
        return storedItems.containsKey(uuid);
    }

    /**
     * Checks whether player is blocking or they have just begun to and shield is not fully up yet.
     */
    private boolean isPlayerBlocking(Player player) {
        return player.isBlocking() ||
                (Reflector.versionIsNewerOrEqualTo(1, 11, 0) && player.isHandRaised()
                        && hasShield(player.getInventory())
                );
    }

    private boolean hasShield(PlayerInventory inventory) {
        return inventory.getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean hasShieldNoCooldown(Player player) {
        PlayerInventory inv = player.getInventory();
        return hasShield(inv) && !player.hasCooldown(inv.getItemInOffHand());
    }

    private boolean isHoldingSword(Material mat) {
        return mat.toString().endsWith("_SWORD");
    }

    public void giveBlockAttributesToSwords(Player player) {
        Inventory inv = player.getInventory();
        boolean updatedSwords = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack iStack = inv.getItem(i);
            if (iStack == null) continue;

            if (MaterialTags.SWORDS.isTagged(iStack) && !ItemUtil.isConsumableSword(iStack)) {
                ItemUtil.addWeaponAttributes(iStack);
                inv.setItem(i, iStack);
                updatedSwords = true;
            }
        }

        if (updatedSwords)
            player.updateInventory();
    }

    public void removeBlockAttributesFromHoldingSword(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack mainItem = inv.getItemInMainHand();

        if (ItemUtil.isHoldingConsumableSword(player, false)) {
            ItemUtil.removeWeaponAttributes(mainItem);
            inv.setItem(EquipmentSlot.HAND, mainItem);
        }
    }
}