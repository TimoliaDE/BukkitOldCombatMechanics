package kernitus.plugin.OldCombatMechanics.versions;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.datacomponent.item.*;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodData;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftAbstractArrow;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static kernitus.plugin.OldCombatMechanics.versions.Version.*;

public class ReflectorUtil {

    private static final Map<Version, String> soundVolume;
    private static final Map<Version, String> random;
    private static final Map<Version, String> foodData;

    private static final Map<Version, String> blockState;
    private static final Map<Version, String> suffocating;

    private static final Map<Version, String> damageSources;
    private static final Map<Version, String> inWall;
    private static final Map<Version, String> hurt;

    static {
        soundVolume = new HashMap<>();

        soundVolume.put(V_1_20_5_plus, "getSoundVolume");
        soundVolume.put(V_1_20_4, "eW");
        soundVolume.put(V_1_20_2, "eV");
        soundVolume.put(V_1_20_1, "eR");

        soundVolume.put(V_1_19_4, "eN");
        soundVolume.put(V_1_19_3, "eI");
        soundVolume.put(V_1_19_2, "eC");
        soundVolume.put(V_1_19_1, "eC");

        random = new HashMap<>();

        random.put(V_1_20_5_plus, "random");
        random.put(V_1_20_4, "ag");
        random.put(V_1_20_2, "ag");
        random.put(V_1_20_1, "af");

        random.put(V_1_19_4, "af");
        random.put(V_1_19_3, "R");
        random.put(V_1_19_2, "R");
        random.put(V_1_19_1, "R");

        foodData = new HashMap<>();

        foodData.put(V_1_20_5_plus, "getFoodData");
        foodData.put(V_1_20_4, "gc");
        foodData.put(V_1_20_2, "gb");
        foodData.put(V_1_20_1, "fX");

        foodData.put(V_1_19_4, "fT");
        foodData.put(V_1_19_3, "fO");
        foodData.put(V_1_19_2, "fL");
        foodData.put(V_1_19_1, "fL");

        blockState = new HashMap<>();

        blockState.put(V_1_20_5_plus, "getBlockState");
        blockState.put(V_1_20_4, "a_");
        blockState.put(V_1_20_2, "a_");
        blockState.put(V_1_20_1, "a_");

        blockState.put(V_1_19_4, "a_");
        blockState.put(V_1_19_3, "a_");
        blockState.put(V_1_19_2, "a_");
        blockState.put(V_1_19_1, "a_");

        suffocating = new HashMap<>();

        suffocating.put(V_1_20_5_plus, "isSuffocating");
        suffocating.put(V_1_20_4, "r");
        suffocating.put(V_1_20_2, "r");
        suffocating.put(V_1_20_1, "r");

        suffocating.put(V_1_19_4, "o");
        suffocating.put(V_1_19_3, "o");
        suffocating.put(V_1_19_2, "o");
        suffocating.put(V_1_19_1, "o");

        damageSources = new HashMap<>();

        damageSources.put(V_1_20_5_plus, "damageSources");
        damageSources.put(V_1_20_4, "dN");
        damageSources.put(V_1_20_2, "dM");
        damageSources.put(V_1_20_1, "dJ");

        damageSources.put(V_1_19_4, "dG");

        inWall = new HashMap<>();

        inWall.put(V_1_20_5_plus, "inWall");
        inWall.put(V_1_20_4, "g");
        inWall.put(V_1_20_2, "g");
        inWall.put(V_1_20_1, "g");

        inWall.put(V_1_19_4, "g");
        inWall.put(V_1_19_3, "f");
        inWall.put(V_1_19_2, "f");
        inWall.put(V_1_19_1, "f");

        hurt = new HashMap<>();

        hurt.put(V_1_20_5_plus, "hurt");
        hurt.put(V_1_20_4, "a");
        hurt.put(V_1_20_2, "a");
        hurt.put(V_1_20_1, "a");

        hurt.put(V_1_19_4, "a");
        hurt.put(V_1_19_3, "a");
        hurt.put(V_1_19_2, "a");
        hurt.put(V_1_19_1, "a");
    }

    public static boolean isLatestVersionedPackage() {
        return Reflector.versionIsNewerOrEqualTo(1, 21, 6);
    }

    public static float getSoundVolume(LivingEntity livingEntity) {
        if (isLatestVersionedPackage()) {
            net.minecraft.world.entity.LivingEntity nmsLivingEntity = ((CraftLivingEntity) livingEntity).getHandle();
            return nmsLivingEntity.getSoundVolume();
        }

        Version ver = Version.get(Reflector.getVersion());
        if (ver == null) throw new RuntimeException();

        String name = soundVolume.get(ver);
        Object nmsLivingEntity = VersionCompatUtils.getCraftHandle(livingEntity);
        return Reflector.invokeMethod(Reflector.getMethod(nmsLivingEntity.getClass(), name), nmsLivingEntity);
    }

    public static RandomSource getRandom(Entity entity) {
        if (isLatestVersionedPackage()) {
            net.minecraft.world.entity.Entity nmsLivingEntity = ((CraftEntity) entity).getHandle();
            return nmsLivingEntity.random;
        }

        Version ver = Version.get(Reflector.getVersion());
        if (ver == null) throw new RuntimeException();

        String name = random.get(ver);
        Object nmsEntity = VersionCompatUtils.getCraftHandle(entity);
        return Reflector.getFieldValue(Reflector.getField(nmsEntity.getClass(), name), nmsEntity);
    }

    public static WorldConfiguration.Misc getWorldConfigurationMisc(World world) {
        if (isLatestVersionedPackage()) {
            ServerLevel nmsWorld = ((CraftWorld) world).getHandle();
            return nmsWorld.paperConfig().misc;
        }

        Object nmsWorld = VersionCompatUtils.getCraftHandle(world);
        Method methodPaperConfig = Reflector.getMethod(nmsWorld.getClass(), "paperConfig", 0);
        WorldConfiguration config = Reflector.invokeMethod(methodPaperConfig, nmsWorld);
        return config.misc;
    }

    public static Object getAsNmsCopy(ItemStack iStack) {
        Class<?> classItemStack = Reflector.getClass(ClassType.CRAFTBUKKIT, "inventory.CraftItemStack");
        Method nmsCopyMethod = Reflector.getMethod(classItemStack, "asNMSCopy", 1, ItemStack.class);
        return Reflector.invokeMethod(nmsCopyMethod, null, iStack);
    }

    public static void spawnProjectile(World world, AbstractArrow arrow, ItemStack bow) {
        if (isLatestVersionedPackage()) {
            net.minecraft.world.entity.projectile.AbstractArrow nmsArrow =
                    ((CraftAbstractArrow) arrow).getHandle();
            ServerLevel nmsWorld = ((CraftWorld) world).getHandle();
            net.minecraft.world.entity.projectile.Projectile.spawnProjectile(nmsArrow, nmsWorld,
                    CraftItemStack.asNMSCopy(bow));
            return;
        }

        Object nmsBowCopy = getAsNmsCopy(bow);
        Object nmsWorld = VersionCompatUtils.getCraftHandle(world);
        Object nmsArrow = VersionCompatUtils.getCraftHandle(arrow);
        Reflector.invokeMethod(Reflector.getMethod(nmsArrow.getClass(), "spawnProjectile",
                3), nmsArrow, nmsArrow, nmsWorld, nmsBowCopy);
    }

    public static Object getNmsItemStack(ItemStack iStack) {
        Class<?> classItemStack = Reflector.getClass(ClassType.CRAFTBUKKIT, "inventory.CraftItemStack");
        Method nmsCopyMethod = Reflector.getMethod(classItemStack, "unwrap", 1, ItemStack.class);
        return Reflector.invokeMethod(nmsCopyMethod, null, iStack);
    }

    private static boolean hasDataComponent(@Nullable ItemStack iStack, DataComponentType<?> dataComponent,
                                            String dataComponentName) {
        if (iStack == null)
            return false;

        if (isLatestVersionedPackage())
            return CraftItemStack.unwrap(iStack).has(dataComponent);

        Object nmsStack = getNmsItemStack(iStack);
        Method method = Reflector.getMethod(nmsStack.getClass(), "has", 1);
        DataComponentType<?> givenDataComponent = getDataComponent(dataComponentName);
        return Reflector.invokeMethod(method, nmsStack, givenDataComponent);
    }

    public static boolean hasConsumable(@Nullable ItemStack iStack) {
        return hasDataComponent(iStack, DataComponents.CONSUMABLE, "CONSUMABLE");
    }

    private static void setDataComponent(ItemStack iStack, @Nullable Object paperComponent, String dataComponentName) {
        Object nmsStack = getNmsItemStack(iStack);
        Method method = Reflector.getMethod(nmsStack.getClass(), "set", 2);
        Object nmsComponent = paperComponent != null ? VersionCompatUtils.getCraftHandle(paperComponent) : null;
        DataComponentType<?> givenDataComponent = getDataComponent(dataComponentName);
        Reflector.invokeMethod(method, nmsStack, givenDataComponent, nmsComponent);
    }

    public static void setConsumable(ItemStack iStack, @Nullable Consumable consumable) {
        if (isLatestVersionedPackage()) {
            net.minecraft.world.item.component.Consumable nmsConsumable = consumable != null ?
                    ((PaperConsumable) consumable).getHandle() : null;
            CraftItemStack.unwrap(iStack).set(DataComponents.CONSUMABLE, nmsConsumable);
            return;
        }

        setDataComponent(iStack, consumable, "CONSUMABLE");
    }

    public static FoodData getFoodData(Player player) {
        if (isLatestVersionedPackage()) {
            ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            return nmsPlayer.getFoodData();
        }

        Version ver = Version.get(Reflector.getVersion());
        if (ver == null) throw new RuntimeException();

        String name = foodData.get(ver);
        Object nmsPlayer = VersionCompatUtils.getCraftHandle(player);
        return Reflector.invokeMethod(Reflector.getMethod(nmsPlayer.getClass(), name), nmsPlayer);
    }

    /*
     * The static fields of net.minecraft.core.component.DataComponents can mismatch
     * between compile time and runtime across different minecraft versions
     * (e.g. "DataComponent.FOOD" at compile time corresponds to
     * "DataComponent.INTANGIBLE_PROJECTILE" at runtime for 1.21.4 minecraft servers).
     * Therefore, reflection is used.
     */
    private static DataComponentType<?> getDataComponent(String name) {
        Class<?> dataComponentsClass = Reflector.getClass("net.minecraft.core.component.DataComponents");
        return Reflector.getFieldValue(Reflector.getField(dataComponentsClass, name), null);
    }

    public static boolean isSuffocating(World world, int x, int y, int z) {
        if (Reflector.versionIsNewerOrEqualTo(1, 21, 5))
            return world.getBlockState(x, y, z).isSuffocating();

        Version ver = Version.get(Reflector.getVersion());
        if (ver == null) throw new RuntimeException();

        String blockStateName = blockState.get(ver);
        String suffocatingName = suffocating.get(ver);

        Object nmsWorld = VersionCompatUtils.getCraftHandle(world);

        BlockPos pos = new BlockPos(x, y, z);
        Method blockStateMethod = Reflector.getMethod(nmsWorld.getClass(), blockStateName, 1);
        Object blockStateObject = Reflector.invokeMethod(blockStateMethod, nmsWorld, pos);
        Method suffocatingMethod = Reflector.getMethod(blockStateObject.getClass(), suffocatingName, 2);
        return Reflector.invokeMethod(suffocatingMethod, blockStateObject, nmsWorld, pos);
    }


    public static void damage(Player player) {
        Object nmsPlayer = VersionCompatUtils.getCraftHandle(player);
        Object inWallObject;

        Version ver = Version.get(Reflector.getVersion());
        if (ver == null) throw new RuntimeException();
        String inWallName = inWall.get(ver);

        if (Reflector.versionIsNewerOrEqualTo(1, 19, 4)) {
            String damageSourcesName = damageSources.get(ver);

            Method damageSourcesMethod = Reflector.getMethod(nmsPlayer.getClass(), damageSourcesName, 0);
            Object damageSourcesObject = Reflector.invokeMethod(damageSourcesMethod, nmsPlayer);

            Method inWallMethod = Reflector.getMethod(damageSourcesObject.getClass(), inWallName, 0);
            inWallObject = Reflector.invokeMethod(inWallMethod, damageSourcesObject);

        } else {
            Class<?> dataComponentsClass = Reflector.getClass("net.minecraft.world.damagesource.DamageSource");
            inWallObject = Reflector.getFieldValue(Reflector.getField(dataComponentsClass, inWallName), null);
        }

        String hurtName = hurt.get(ver);
        Method hurtMethod = Reflector.getMethod(nmsPlayer.getClass(), hurtName, 2,
                inWallObject.getClass(), float.class);
        Reflector.invokeMethod(hurtMethod, nmsPlayer, inWallObject, 1.0F);
    }
}
