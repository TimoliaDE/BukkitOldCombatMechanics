/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.RegisteredListener;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Exact-event attribution for synchronous OCM damage. Accessed on the server thread. */
public final class CombatDamageProvenance {

    private static final List<WeakReference<EntityDamageByEntityEvent>> chips = new ArrayList<>();
    private static final ArrayDeque<RodAttempt> attempts = new ArrayDeque<>();
    private static final String LISTENER_CLASS_NAME = RegisteredListener.class.getName();

    private CombatDamageProvenance() {
    }

    // Bukkit dispatches plugin callbacks through this stable boundary. Inspect it only
    // during scoped rod damage, so an earlier LOWEST listener cannot claim a nested hit.
    private static int dispatchDepth() {
        int count = 0;

        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (LISTENER_CLASS_NAME.equals(element.getClassName())
                    && "callEvent".equals(element.getMethodName())) {
                count++;
            }
        }

        return count;
    }

    public static final class RodAttempt {

        private final Player rodder;
        private final LivingEntity victim;
        private final boolean chip;
        private final int expectedDepth;

        private EntityDamageByEntityEvent event;

        private RodAttempt(Player rodder, LivingEntity victim, boolean chip, int expectedDepth) {
            this.rodder = rodder;
            this.victim = victim;
            this.chip = chip;
            this.expectedDepth = expectedDepth;
        }

        public EntityDamageByEntityEvent getEvent() {
            return event;
        }
    }

    public static void markChip(EntityDamageByEntityEvent event) {
        chips.removeIf(reference -> reference.get() == null);
        chips.add(new WeakReference<>(event));
    }

    public static boolean isChip(EntityDamageByEntityEvent event) {
        return chips.stream().anyMatch(reference -> reference.get() == event);
    }

    /** Attribution alone cannot exempt damage subsequently changed by another listener. */
    public static boolean isUnchangedChip(EntityDamageByEntityEvent event, double incomingDamage) {
        return isChip(event)
                && (incomingDamage == 0.0001 || incomingDamage == (double) 0.0001f);
    }

    public static RodAttempt beginRod(Player rodder, LivingEntity victim, double damage) {
        RodAttempt attempt = new RodAttempt(
                rodder,
                victim,
                damage == 0.0001,
                dispatchDepth() + 1
        );

        attempts.push(attempt);
        return attempt;
    }

    public static RodAttempt damageRod(Player rodder, LivingEntity victim, double damage) {
        RodAttempt attempt = beginRod(rodder, victim, damage);

        try {
            victim.damage(damage, rodder);
            return attempt;
        } finally {
            endRod(attempt);
        }
    }

    public static void endRod(RodAttempt attempt) {
        if (attempts.peek() != attempt) {
            throw new IllegalStateException();
        }

        attempts.pop();
    }

    /** Claim before the damage pipeline can emit nested plugin events. */
    public static void claimRod(EntityDamageByEntityEvent event) {
        RodAttempt attempt = attempts.peek();

        if (attempt == null
                || attempt.event != null
                || dispatchDepth() != attempt.expectedDepth
                || !event.getEntity().getUniqueId().equals(attempt.victim.getUniqueId())
                || !event.getDamager().getUniqueId().equals(attempt.rodder.getUniqueId())) {
            return;
        }

        attempt.event = event;

        if (attempt.chip) {
            markChip(event);
        }
    }

    public static boolean isRod(EntityDamageByEntityEvent event) {
        return attempts.stream().anyMatch(attempt -> attempt.event == event);
    }
}
