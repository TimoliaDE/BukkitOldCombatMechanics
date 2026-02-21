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

    /*
     * Checks whether the server allows 1.8 clients.
     */
    public static boolean isLegacyClientsAllowed() {
        int lowestSupported = Via.getAPI().getServerVersion().lowestSupportedVersion();

        // Protocol 47 = 1.8.x
        return lowestSupported <= 47;
    }

}
