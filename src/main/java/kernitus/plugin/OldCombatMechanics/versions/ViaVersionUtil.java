package kernitus.plugin.OldCombatMechanics.versions;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

public class ViaVersionUtil {

    /*
     * Checks whether the player is using a 1.8 client or below.
     */
    public static boolean isLegacyClient(Player player) {
        try {
            return Via.getAPI().getPlayerVersion(player) <= 47;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /*
     * Checks whether the server allows 1.8 clients.
     */
    public static boolean isLegacyClientsAllowed() {
        // Protocol 47 = 1.8.x
        try {
            return Via.getAPI().getSupportedVersions().contains(ProtocolVersion.v1_8.getVersion());
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
