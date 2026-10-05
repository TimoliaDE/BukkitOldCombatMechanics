/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerKnockback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Optional event types stay behind a cached bridge so legacy servers can load the module.
 */
public class ArrowKnockbackCompat {

    /**
     * Native player attacks expose their pre-hit vector in PlayerVelocityEvent.
     */
    public static boolean immediateMeleeVelocity() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();

            if (className.startsWith("net.minecraft.")
                    && (className.endsWith(".EntityHuman") || className.endsWith(".Player"))
                    && ("d".equals(element.getMethodName())
                    || "attack".equals(element.getMethodName())
                    || "causeExtraKnockback".equals(element.getMethodName()))) {
                return true;
            }
        }

        return false;
    }

    private final OCMMain plugin;
    private final ModulePlayerKnockback module;

    private final Set<Projectile> corrected =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final class Hook {

        private final Class<? extends Event> type;
        private final boolean paper;

        private final Method source;
        private final Method velocity;
        private final Method raw;
        private final Method strength;
        private final Method setter;

        @SuppressWarnings("unchecked")
        private Hook(Class<?> type, boolean paper) throws NoSuchMethodException {
            this.type = (Class<? extends Event>) type;
            this.paper = paper;

            this.source = type.getMethod(paper ? "getHitBy" : "getSourceEntity");
            this.velocity = type.getMethod(paper ? "getAcceleration" : "getFinalKnockback");
            this.raw = type.getMethod(paper ? "getAcceleration" : "getKnockback");
            this.strength = type.getMethod(paper ? "getKnockbackStrength" : "getForce");

            this.setter = paper ? null : type.getMethod("setFinalKnockback", Vector.class);
        }

        private Vector outgoing(Event event, Vector before) throws ReflectiveOperationException {
            Vector result = ((Vector) velocity.invoke(event)).clone();

            if (paper) {
                result.add(before);
            }

            return result;
        }

        private void setOutgoing(Event event, Vector value, Vector before) throws ReflectiveOperationException {
            if (paper) {
                Vector acceleration = (Vector) velocity.invoke(event);
                acceleration.copy(value.clone().subtract(before));

            } else {
                setter.invoke(event, value);
            }
        }
    }

    public ArrowKnockbackCompat(OCMMain plugin, ModulePlayerKnockback module) {
        this.plugin = plugin;
        this.module = module;

        Hook bukkit = detect("org.bukkit.event.entity.EntityKnockbackByEntityEvent", false);
        Hook paper = detect("com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent", true);
        Hook correction = bukkit != null ? bukkit : paper;

        if (correction != null) {
            Listener listener = new Listener() {
            };

            Bukkit.getPluginManager().registerEvent(correction.type, listener, EventPriority.LOWEST,
                    (ignored, event) -> {
                        if (correction.type.isInstance(event)) {
                            correct(correction, event);
                        }
                    }, plugin, true);

            // Paper follows Bukkit on newer servers. Observe the final family,
            // including cancellation and foreign edits, without applying the
            // correction a second time.
            Hook finalHook = paper != null ? paper : correction;

            Bukkit.getPluginManager().registerEvent(finalHook.type, listener, EventPriority.MONITOR,
                    (ignored, event) -> {
                        if (finalHook.type.isInstance(event)) {
                            observe(finalHook, event);
                        }
                    }, plugin);
        } else {
            plugin.getLogger().info(
                    "Arrow base knockback hook unavailable; arrows retain native knockback and Punch."
            );
        }
    }

    private Hook detect(String name, boolean paper) {
        try {
            return new Hook(Class.forName(name).asSubclass(Event.class), paper);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private Projectile arrow(Event event, Entity source) {
        Entity victim = ((EntityEvent) event).getEntity();

        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) {
            return null;
        }

        if (!(damage.getDamager() instanceof Projectile arrow)) {
            return null;
        }

        // AbstractArrow is absent from legacy APIs; these native entity types are stable.
        if (!"ARROW".equals(arrow.getType().name()) && !"SPECTRAL_ARROW".equals(arrow.getType().name())) {
            return null;
        }

        if (damage.isCancelled() || damage.getCause() != EntityDamageEvent.DamageCause.PROJECTILE
                || (source != arrow && source != arrow.getShooter())) {
            return null;
        }

        // CraftEventFactory publishes lastDamageCause after damage listeners return.
        // Require the native arrow collision and exclude nested plugin knockback calls.
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        int dispatchDepth = 0;

        for (StackTraceElement element : stack) {
            if ("org.bukkit.plugin.RegisteredListener".equals(element.getClassName())
                    && "callEvent".equals(element.getMethodName())) {
                dispatchDepth++;
            }
        }

        if (dispatchDepth != 1) {
            return null;
        }

        for (StackTraceElement element : stack) {
            String className = element.getClassName();

            if (className.startsWith("net.minecraft.") && (className.endsWith(".EntityArrow")
                    || className.endsWith(".AbstractArrow")) && ("a".equals(element.getMethodName())
                    || "onHitEntity".equals(element.getMethodName()))) {
                return arrow;
            }
        }

        return null;
    }

    private void correct(Hook hook, Event event) {
        Entity entity = ((EntityEvent) event).getEntity();

        if (!(entity instanceof Player victim)) {
            return;
        }

        try {
            Entity source = (Entity) hook.source.invoke(event);
            Projectile arrow = arrow(event, source);

            if (arrow == null) {
                return;
            }

            if (!(arrow.getShooter() instanceof Entity shooter)) {
                return;
            }

            Vector before = victim.getVelocity();
//            Vector input = module.arrowInput(victim, before);
            Vector input = null;

            if (!module.isEnabled(shooter, victim) && input.equals(before)) {
                return;
            }

            Vector original;

            if (hook.paper) {
                // Paper 1.19.2 uses the shooter's current position for this base stage.
                Vector direction = shooter.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0.0);

                if (direction.lengthSquared() < 1.0E-4) {
                    return;
                }

                double strength = ((Number) hook.strength.invoke(event)).doubleValue();

                original = before.clone().multiply(new Vector(0.5, 1.0, 0.5))
                        .subtract(direction.normalize().multiply(strength));

                if (victim.isOnGround()) {
                    original.setY(Math.min(0.4, before.getY() / 2.0 + strength));
                }
            } else {
                original = before.clone()
                        .add((Vector) hook.raw.invoke(event));
            }

            Vector wanted;

            if (module.isEnabled(shooter, victim)) {
                //wanted = module.arrowBase(victim, shooter, input);
                wanted = null;

                if (wanted == null) {
                    return;
                }
            } else {
                // A modeset-disabled arrow still acts on the preceding corrected melee.
                // Reconstruct only this supported native boundary, leaving its direction intact.
                double strength = ((Number) hook.strength.invoke(event)).doubleValue();

                wanted = original.clone().add(
                        input.clone().subtract(before).multiply(new Vector(0.5, 0.0, 0.5)));

                wanted.setY(victim.isOnGround() ? Math.min(0.4, input.getY() / 2.0 + strength) : input.getY());
            }

            corrected.add(arrow);

            // Retain changes from earlier listeners, including Bukkit listeners
            // before Paper's final event. Punch executes later in EntityArrow/AbstractArrow.
            Vector outgoing = hook.outgoing(event, before);
            hook.setOutgoing(event, outgoing.add(wanted.clone().subtract(original)), before);

        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility API is unavailable at runtime.
        }
    }

    private void observe(Hook hook, Event event) {
        Entity entity = ((EntityEvent) event).getEntity();

        if (!(entity instanceof Player victim)) {
            return;
        }

        try {
            Entity source = (Entity) hook.source.invoke(event);
            Vector before = victim.getVelocity();
            boolean cancelled = ((Cancellable) event).isCancelled();
            Projectile arrow = arrow(event, source);

            if (arrow != null) {
                boolean applied = corrected.remove(arrow);

                if (cancelled || !applied) {
                    return;
                }
            }

            boolean nativeKnockback = false;

            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName();

                if (className.startsWith("net.minecraft.") && "knockback".equals(element.getMethodName())
                        && (className.endsWith(".EntityLiving") || className.endsWith(".LivingEntity"))) {
                    nativeKnockback = true;
                    break;
                }
            }

            if (!nativeKnockback) {
                return;
            }

            // module.observeNativeKnockback(victim, source, cancelled ? before : hook.outgoing(event, before),
            //        arrow != null);

        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility API is unavailable at runtime.
        }
    }
}
