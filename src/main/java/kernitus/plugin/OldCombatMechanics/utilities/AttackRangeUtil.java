package kernitus.plugin.OldCombatMechanics.utilities;

import io.papermc.paper.configuration.GlobalConfiguration;
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackRange;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public class AttackRangeUtil {

    public static void onPlayerInteract(ModuleAttackRange range, PlayerInteractEvent event) {
        if (!event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        net.minecraft.world.item.component.AttackRange attackRange =
                new net.minecraft.world.item.component.AttackRange(range.minRange,
                        range.maxRange, range.minCreative, range.maxCreative,
                        range.hitboxMargin, range.mobFactor);

        Player player = event.getPlayer();
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        HitResult hitResult = attackRange.getClosesetHit(nmsPlayer, 1.0F, x -> true);

        if (hitResult instanceof EntityHitResult entityHitResult) {
            handleInteract(player, entityHitResult.getEntity().getBukkitEntity());
        }
    }

    // Adapted from the handleInteract method in ServerGamePacketListenerImpl,
    // using the attack logic from the onAttack method of ServerboundInteractPacket.Handler.
    private static void handleInteract(Player player, Entity target) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer nmsPlayer = craftPlayer.getHandle();
        net.minecraft.world.entity.Entity nmsTarget = ((CraftEntity) target).getHandle();

        if (nmsPlayer.isImmobile()) return;
        if (!nmsPlayer.connection.hasClientLoaded()) return;
        if (target.equals(player) && player.getGameMode() != GameMode.SPECTATOR) return;

        nmsPlayer.resetLastActionTime();

        if (!player.getWorld().getWorldBorder().isInside(target.getLocation().toBlockLocation())) {
            return;
        }

        AABB boundingBox = nmsTarget.getBoundingBox();

        if (nmsPlayer.isWithinAttackRange(boundingBox,
                GlobalConfiguration.get().misc.clientInteractionLeniencyDistance.or(3.0))) {
            if (target instanceof Item || target instanceof org.bukkit.entity.ExperienceOrb) return;
            if (nmsTarget instanceof AbstractArrow arrow && !arrow.isAttackable()) return;

            nmsPlayer.attack(nmsTarget);
        }
    }
}
