package kernitus.plugin.OldCombatMechanics.module;

import com.viaversion.viaversion.api.Via;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Fixes visible ghost water for 1.8 clients when placing water
 * slightly outside the normal placement range (at most 1 block).
 * Requires ViaVersion.
 */
public class ModuleFixBucketPlacement extends OCMModule {

    public ModuleFixBucketPlacement(OCMMain plugin) {
        super(plugin, "fix-bucket-placement");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isLegacyClient(player)) return;

        ItemStack iStack = event.getItem();
        if (event.isCancelled() && event.getAction() == Action.RIGHT_CLICK_AIR && iStack != null &&
                (iStack.getType() == Material.WATER_BUCKET || iStack.getType() == Material.LAVA_BUCKET)) {

            RayTraceResult result = player.rayTraceBlocks(5);
            if (result != null) {
                Block block = result.getHitBlock().getRelative(result.getHitBlockFace());
                event.setUseItemInHand(Event.Result.DENY);
                switchAir(block);
                Bukkit.getScheduler().runTask(OCMMain.getInstance(), () -> switchAir(block));
            }
        }
    }

    private void switchAir(Block block) {
        boolean air = block.getType() == Material.AIR;
        block.setType(air ? Material.CAVE_AIR : Material.AIR, false);
    }

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    private boolean isLegacyClient(Player player) {
        return Via.getAPI().getPlayerVersion(player) <= 47;
    }
}
