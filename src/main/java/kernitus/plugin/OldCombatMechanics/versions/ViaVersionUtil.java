package kernitus.plugin.OldCombatMechanics.versions;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

public class ViaVersionUtil {

    /*
     * Checks whether the player is using a client of the specified version or older.
     */
    public static boolean isLegacyClient(Player player, final int version) {
        try {
            return Via.getAPI().getPlayerVersion(player) <= version;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /*
     * Checks whether the player is using a 1.8 client or older.
     */
    public static boolean isLegacyClient(Player player) {
        try {
            return isLegacyClient(player, 47);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /*
     * Checks whether the server allows clients of a specific version or older.
     */
    public static boolean isLegacyClientsAllowed(final int version) {
        // Protocol 47 = 1.8.x
        try {
            return Via.getAPI().getSupportedVersions().contains(version);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /*
     * Checks whether the server allows 1.8 clients or older.
     */
    public static boolean isLegacyClientsAllowed() {
        // Protocol 47 = 1.8.x
        try {
            return isLegacyClientsAllowed(ProtocolVersion.v1_8.getVersion());
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
