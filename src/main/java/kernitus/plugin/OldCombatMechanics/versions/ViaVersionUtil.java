package kernitus.plugin.OldCombatMechanics.versions;

import com.viaversion.viaversion.api.Via;
import org.bukkit.entity.Player;

public class ViaVersionUtil {

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    public static boolean isLegacyClient(Player player) {
        return Via.getAPI().getPlayerVersion(player) <= 47;
    }
}
