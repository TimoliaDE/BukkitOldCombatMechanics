/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.event.entity.EntityDamageEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads live damage tags without linking legacy servers to modern Bukkit types. */
public final class DamageTypeTags {

    private static final List<String> NAMES = Arrays.asList(
            "bypasses_armor", "bypasses_resistance", "bypasses_effects",
            "bypasses_enchantments", "is_fire", "is_explosion", "is_projectile", "is_fall");

    private static final Access ACCESS = createAccess();

    private static final DamageTypeTags UNAVAILABLE = new DamageTypeTags(null, null);

    private final Object damageType;
    private final String key;

    private DamageTypeTags(Object damageType, String key) {
        this.damageType = damageType;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /**
     * Null means the server cannot answer; false means the tag exists but excludes this type.
     */
    public Boolean contains(String tag) {
        if (ACCESS == null || damageType == null) {
            return null;
        }

        try {
            Object tagKey = ACCESS.keys.get(tag);
            if (tagKey == null) {
                return null;
            }

            // Resolve tags afresh so datapack reloads cannot leave cached membership behind.
            Object liveTag = ACCESS.getTag.invoke(null, ACCESS.registry, tagKey, ACCESS.damageTypeClass);

            if (liveTag == null) {
                return null;
            }

            return (Boolean) ACCESS.isTagged.invoke(liveTag, damageType);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public boolean matches(String tag, boolean fallback) {
        Boolean result = contains(tag);
        return result != null ? result : fallback;
    }

    public static DamageTypeTags from(EntityDamageEvent event) {
        if (ACCESS == null) {
            return UNAVAILABLE;
        }

        try {
            Object source = ACCESS.getSource.invoke(event);
            if (source == null) {
                return UNAVAILABLE;
            }

            Object type = ACCESS.getType.invoke(source);
            if (type == null) {
                return UNAVAILABLE;
            }

            String key = ACCESS.getKey.invoke(type).toString();

            // Deprecated event constructors supply GENERIC irrespective of their supplied cause.
            if ("minecraft:generic".equals(key)
                    && event.getCause() != EntityDamageEvent.DamageCause.CUSTOM) {
                return UNAVAILABLE;
            }

            return new DamageTypeTags(type, key);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return UNAVAILABLE;
        }
    }

    private static Access createAccess() {
        try {
            Class<?> type = Class.forName("org.bukkit.damage.DamageType");
            Class<?> source = Class.forName("org.bukkit.damage.DamageSource");
            Class<?> key = Class.forName("org.bukkit.NamespacedKey");

            Method getTag = Bukkit.class.getMethod("getTag", String.class, key, Class.class);

            Method minecraft = key.getMethod("minecraft", String.class);

            Map<String, Object> keys = new LinkedHashMap<>();
            for (String name : NAMES) {
                keys.put(name, minecraft.invoke(null, name));
            }

            String registry = null;

            for (String candidate : Arrays.asList("damage_type", "damage_types")) {
                try {
                    Object result = getTag.invoke(null, candidate, keys.get("bypasses_armor"), type);

                    if (result != null) {
                        registry = candidate;
                        break;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the next registry name.
                }
            }

            if (registry == null) {
                return null;
            }

            return new Access(EntityDamageEvent.class.getMethod("getDamageSource"),
                    source.getMethod("getDamageType"), type.getMethod("getKey"),
                    getTag, Reflector.getMethod(Class.forName("org.bukkit.Tag"),
                    "isTagged", 1), type, registry, keys);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static final class Access {

        private final Method getSource;
        private final Method getType;
        private final Method getKey;
        private final Method getTag;
        private final Method isTagged;
        private final Class<?> damageTypeClass;
        private final String registry;
        private final Map<String, Object> keys;

        private Access(Method getSource, Method getType, Method getKey, Method getTag, Method isTagged,
                Class<?> damageTypeClass, String registry, Map<String, Object> keys) {
            this.getSource = getSource;
            this.getType = getType;
            this.getKey = getKey;
            this.getTag = getTag;
            this.isTagged = isTagged;
            this.damageTypeClass = damageTypeClass;
            this.registry = registry;
            this.keys = keys;
        }
    }
}
