/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import kernitus.plugin.OldCombatMechanics.commands.OCMCommandCompleter;
import kernitus.plugin.OldCombatMechanics.commands.OCMCommandHandler;
import kernitus.plugin.OldCombatMechanics.hooks.PlaceholderAPIHook;
import kernitus.plugin.OldCombatMechanics.hooks.api.Hook;
import kernitus.plugin.OldCombatMechanics.module.*;
import kernitus.plugin.OldCombatMechanics.updater.ModuleUpdateChecker;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.damage.AttackCooldownTracker;
import kernitus.plugin.OldCombatMechanics.utilities.damage.EntityDamageByEntityListener;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.storage.ModesetListener;
import kernitus.plugin.OldCombatMechanics.utilities.storage.PlayerStorage;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventException;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OCMMain extends JavaPlugin {

    private static OCMMain INSTANCE;
    private final Logger logger = getLogger();
    private final OCMConfigHandler CH = new OCMConfigHandler(this);
    private final List<Runnable> disableListeners = new ArrayList<>();
    private final List<Runnable> enableListeners = new ArrayList<>();
    private final List<Hook> hooks = new ArrayList<>();
    private boolean isLatestNmsPackage;
    private ProtocolManager protocolManager;

    public OCMMain() {
        super();
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        // Checks if the server is using the latest NMS package
        // (e.g., "v1_21_R6" for Minecraft versions 1.21.9 and 1.21.10)
        isLatestNmsPackage = checkLatestNmsPackage();

        // Setting up config.yml
        CH.setupConfigIfNotPresent();

        // Initialise persistent player storage
        PlayerStorage.initialise(this);

        // Initialise ModuleLoader utility
        ModuleLoader.initialise(this);

        // Initialise Config utility
        Config.initialise(this);

        // Initialise the Messenger utility
        Messenger.initialise(this);

        try {
            @Nullable Plugin plugin = getServer().getPluginManager().getPlugin("ProtocolLib");
            if (plugin != null && plugin.isEnabled())
                protocolManager = ProtocolLibrary.getProtocolManager();
        } catch (Exception e) {
            Messenger.warn("No ProtocolLib detected, some features might be disabled");
        }

        // Register all the modules
        registerModules();

        // Register all hooks for integrating with other plugins
        registerHooks();

        // Initialise all the hooks
        hooks.forEach(hook -> hook.init(this));

        registerCommand("OldCombatMechanics", new OCMCommandHandler(this),
                new OCMCommandCompleter(), List.of("ocm"));
        // Set up the command handler
//        getCommand("OldCombatMechanics").setExecutor(new OCMCommandHandler(this));
        // Set up command tab completer
//        getCommand("OldCombatMechanics").setTabCompleter(new OCMCommandCompleter());

        Config.reload();

        // BStats Metrics
        final Metrics metrics = new Metrics(this, 53);

        metrics.addCustomChart(new SimplePie("server_software", () -> {
            final String name = Bukkit.getServer().getName();
            if (name == null || name.isEmpty()) return "Unknown";
            final String cleaned = name.split("\\s", 2)[0].trim();
            return cleaned.isEmpty() ? "Unknown" : cleaned;
        }));

        // Simple bar chart (kept in case bStats re-enables bar display)
        metrics.addCustomChart(
                new SimpleBarChart(
                        "enabled_modules",
                        () -> ModuleLoader.getModules().stream()
                                .filter(OCMModule::isEnabled)
                                .collect(Collectors.toMap(OCMModule::toString, module -> 1))));

        // Pie chart of enabled/disabled for each module
        ModuleLoader.getModules().forEach(module -> metrics.addCustomChart(
                new SimplePie(module.getModuleName() + "_pie",
                        () -> module.isEnabled() ? "enabled" : "disabled")));

        // Simple pie: exact count of enabled modules per server (as a string key).
        metrics.addCustomChart(new SimplePie("enabled_modules_count", () -> {
            int count = (int) ModuleLoader.getModules().stream().filter(OCMModule::isEnabled).count();
            return Integer.toString(count);
        }));

        enableListeners.forEach(Runnable::run);

        // Properly handle Plugman load/unload.
        final List<RegisteredListener> joinListeners = Arrays
                .stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                .filter(registeredListener -> registeredListener.getPlugin().equals(this))
                .collect(Collectors.toList());

        Bukkit.getOnlinePlayers().forEach(player -> {
            final PlayerJoinEvent event = new PlayerJoinEvent(player, "");

            // Trick all the modules into thinking the player just joined in case the plugin
            // was loaded with Plugman.
            // This way attack speeds, item modifications, etc. will be applied immediately
            // instead of after a re-log.
            joinListeners.forEach(registeredListener -> {
                try {
                    registeredListener.callEvent(event);
                } catch (EventException e) {
                    e.printStackTrace();
                }
            });
        });

        // Logging to console the enabling of OCM
        final PluginDescriptionFile pdfFile = this.getDescription();
        logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been enabled");

        if (Config.moduleEnabled("update-checker"))
            Bukkit.getScheduler().runTaskLaterAsynchronously(this,
                    () -> new UpdateChecker(this).performUpdate(), 20L);

        metrics.addCustomChart(new SimplePie("auto_update_pie",
                () -> Config.moduleSettingEnabled("update-checker",
                        "auto-update") ? "enabled" : "disabled"));
    }

    private boolean checkLatestNmsPackage() {
        try {
            org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftCopy(new ItemStack(Material.STONE));
            return true;

        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isLatestNmsPackage() {
        return isLatestNmsPackage;
    }

    @Override
    public void onDisable() {
        final PluginDescriptionFile pdfFile = this.getDescription();

        disableListeners.forEach(Runnable::run);

        // Properly handle Plugman load/unload.
        final List<RegisteredListener> quitListeners = Arrays
                .stream(PlayerQuitEvent.getHandlerList().getRegisteredListeners())
                .filter(registeredListener -> registeredListener.getPlugin().equals(this))
                .collect(Collectors.toList());

        // Trick all the modules into thinking the player just quit in case the plugin
        // was unloaded with Plugman.
        // This way attack speeds, item modifications, etc. will be restored immediately
        // instead of after a disconnect.
        Bukkit.getOnlinePlayers().forEach(player -> {
            final PlayerQuitEvent event = new PlayerQuitEvent(player, "");

            quitListeners.forEach(registeredListener -> {
                try {
                    registeredListener.callEvent(event);
                } catch (EventException e) {
                    e.printStackTrace();
                }
            });
        });

        PlayerStorage.instantSave();

        // Logging to console the disabling of OCM
        logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been disabled");
    }

    private void registerModules() {
        // Update Checker (also a module, so we can use the dynamic
        // registering/unregistering)
        ModuleLoader.addModule(new ModuleUpdateChecker(this));

        // Modeset listener, for when player joins or changes world
        ModuleLoader.addModule(new ModesetListener(this));

        // Module listeners
        ModuleLoader.addModule(new ModuleAttackCooldown(this));
        ModuleLoader.addModule(new ModuleAttackRange(this));

        // If below 1.16, we need to keep track of player attack cooldown ourselves
        if (Reflector.getMethod(HumanEntity.class, "getAttackCooldown", 0) == null) {
            ModuleLoader.addModule(new AttackCooldownTracker(this));
        }

        // Listeners registered later with same priority are called later

        // These four listen to OCMEntityDamageByEntityEvent:
        ModuleLoader.addModule(new ModuleOldToolDamage(this));
        ModuleLoader.addModule(new ModuleSwordSweep(this));
        ModuleLoader.addModule(new ModuleOldPotionEffects(this));
        ModuleLoader.addModule(new ModuleOldCriticalHits(this));

        // Next block are all on LOWEST priority, so will be called in the following
        // order:
        // Damage order: base -> potion effects -> critical hit -> enchantments
        // Defence order: overdamage -> blocking -> armour -> resistance -> armour enchs
        // -> absorption
        // EntityDamageByEntityListener calls OCMEntityDamageByEntityEvent, see modules
        // above
        // For everything from base to overdamage
        ModuleLoader.addModule(new EntityDamageByEntityListener(this));
        // ModuleSwordBlocking to calculate blocking
        ModuleLoader.addModule(new ModuleShieldDamageReduction(this));
        // OldArmourStrength for armour -> resistance -> armour enchs -> absorption
        ModuleLoader.addModule(new ModuleOldArmourStrength(this));

        ModuleLoader.addModule(new ModuleSwordBlocking(this));
        ModuleLoader.addModule(new ModuleOldArmourDurability(this));

        ModuleLoader.addModule(new ModuleGoldenApple(this));
        ModuleLoader.addModule(new ModuleFishingKnockback(this));
        ModuleLoader.addModule(new ModulePlayerKnockback(this));
        ModuleLoader.addModule(new ModuleOldProjectileTrajectory(this));
        ModuleLoader.addModule(new ModuleOldBowDamage(this));
        ModuleLoader.addModule(new ModuleFixBowShoot(this));
        ModuleLoader.addModule(new ModuleOldFallDamage(this));
        ModuleLoader.addModule(new ModuleOldBucketPlacement(this));
        ModuleLoader.addModule(new ModuleDamageInsideWall(this));
        ModuleLoader.addModule(new ModulePlayerRegen(this));

        ModuleLoader.addModule(new ModuleDisableCrafting(this));
        ModuleLoader.addModule(new ModuleDisableOffHand(this));
        ModuleLoader.addModule(new ModuleOldBrewingStand(this));
        ModuleLoader.addModule(new ModuleProjectileKnockback(this));
        ModuleLoader.addModule(new ModuleDisableEnderpearlCooldown(this));
        ModuleLoader.addModule(new ModuleChorusFruit(this));

        ModuleLoader.addModule(new ModuleOldBurnDelay(this));
        ModuleLoader.addModule(new ModuleAttackFrequency(this));
        ModuleLoader.addModule(new ModuleFishingRodVelocity(this));

        // These modules require ProtocolLib
        if (protocolManager != null) {
            ModuleLoader.addModule(new ModuleAttackSounds(this));
            ModuleLoader.addModule(new ModuleNewAttackParticles(this));
        } else {
            Messenger.warn("No ProtocolLib detected, attack-sounds and new-attack-particles modules " +
                    "will be disabled");
        }

        // These modules require ViaVersion
        if (hasViaVersion()) {
            ModuleLoader.addModule(new ModuleFixSounds(this));
            ModuleLoader.addModule(new ModuleFixBlockBreak(this));
        } else {
            Messenger.warn("No ViaVersion detected, fix-sounds and fix-block-break modules " +
                    "will be disabled");
        }
    }

    public boolean hasViaVersion() {
        @Nullable Plugin plugin = getServer().getPluginManager().getPlugin("ViaVersion");
        return plugin != null && plugin.isEnabled();
    }

    public void registerCommand(String name, Object executor, Object completer, List<String> aliases) {
        try {
            // PluginCommand erzeugen
            Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);

            PluginCommand command = constructor.newInstance(name, this);

            // Wenn das Objekt CommandExecutor ist
            if (executor instanceof CommandExecutor exec) {
                command.setExecutor(exec);
            }

            // Wenn das Objekt TabCompleter ist
            if (completer instanceof TabCompleter tab) {
                command.setTabCompleter(tab);
            }

            // Aliases
            if (aliases != null && !aliases.isEmpty()) {
                command.setAliases(aliases);
            }

            // Registrierung
            getServer().getCommandMap().register(name, command);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerHooks() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"))
            hooks.add(new PlaceholderAPIHook());
    }

    public void upgradeConfig() {
        CH.upgradeConfig();
    }

    public boolean doesConfigExist() {
        return CH.doesConfigExist();
    }

    /**
     * Registers a runnable to run when the plugin gets disabled.
     *
     * @param action the {@link Runnable} to run when the plugin gets disabled
     */
    public void addDisableListener(Runnable action) {
        disableListeners.add(action);
    }

    /**
     * Registers a runnable to run when the plugin gets enabled.
     *
     * @param action the {@link Runnable} to run when the plugin gets enabled
     */
    public void addEnableListener(Runnable action) {
        enableListeners.add(action);
    }

    /**
     * Get the plugin's JAR file
     *
     * @return The File object corresponding to this plugin
     */
    @NotNull
    @Override
    public File getFile() {
        return super.getFile();
    }

    public static OCMMain getInstance() {
        return INSTANCE;
    }

    public static String getVersion() {
        return INSTANCE.getDescription().getVersion();
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }
}
