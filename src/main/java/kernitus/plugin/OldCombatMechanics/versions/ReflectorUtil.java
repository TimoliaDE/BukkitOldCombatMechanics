package kernitus.plugin.OldCombatMechanics.versions;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.PaperBlocksAttacks;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.VersionCompatUtils;
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
import org.spigotmc.SpigotWorldConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static kernitus.plugin.OldCombatMechanics.versions.Version.*;

public class ReflectorUtil {

    private static final Map<Version, String> soundVolume;
    private static final Map<Version, String> random;
    private static final Map<Version, String> foodData;

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

    public static SpigotWorldConfig getSpigotConfig(World world) {
        if (isLatestVersionedPackage()) {
            ServerLevel nmsWorld = ((CraftWorld) world).getHandle();
            return nmsWorld.spigotConfig;
        }

        Object nmsWorld = VersionCompatUtils.getCraftHandle(world);
        Field field = Reflector.getField(nmsWorld.getClass(), "spigotConfig");
        return Reflector.getFieldValue(field, nmsWorld);
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

        Object nmsWorld = VersionCompatUtils.getCraftHandle(world);
        Object nmsArrow = VersionCompatUtils.getCraftHandle(arrow);
        Reflector.invokeMethod(Reflector.getMethod(nmsArrow.getClass(), "spawnProjectile",
                3), nmsArrow, nmsArrow, nmsWorld, CraftItemStack.asNMSCopy(bow));
    }

    public static Object getNmsItemStack(ItemStack iStack) {
        return Reflector.getFieldValue(Reflector.getField(iStack.getClass(), "handle"), iStack);
    }

    public static boolean hasBlocksAttacks(ItemStack iStack) {
        if (isLatestVersionedPackage())
            return CraftItemStack.unwrap(iStack).has(DataComponents.BLOCKS_ATTACKS);

        Object nmsStack = getNmsItemStack(iStack);
        Method method = Reflector.getMethod(nmsStack.getClass(), "has", 1);
        return Reflector.invokeMethod(method, nmsStack, getComponentBlocksAttacks());
    }

    public static void setBlocksAttacks(ItemStack iStack, BlocksAttacks blocksAttacks) {
        if (isLatestVersionedPackage()) {
            net.minecraft.world.item.component.BlocksAttacks nmsBlocksAttacks =
                    ((PaperBlocksAttacks) blocksAttacks).getHandle();
            CraftItemStack.unwrap(iStack).set(DataComponents.BLOCKS_ATTACKS, nmsBlocksAttacks);
            return;
        }

        Object nmsStack = getNmsItemStack(iStack);
        Method method = Reflector.getMethod(nmsStack.getClass(), "set", 2);
        Object nmsBlocksAttacks = VersionCompatUtils.getCraftHandle(blocksAttacks);
        Reflector.invokeMethod(method, nmsStack, getComponentBlocksAttacks(), nmsBlocksAttacks);
    }

    private static Object getComponentBlocksAttacks() {
        return net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS;
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
}
