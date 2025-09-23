package kernitus.plugin.OldCombatMechanics.versions;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;

public enum Version {

    V_1_20_5_plus,
    V_1_20_4("1.20.4", "1.20.3"),
    V_1_20_2("1.20.2"),
    V_1_20_1("1.20.1", "1.20"),

    V_1_19_4("1.19.4"),
    V_1_19_3("1.19.3"),
    V_1_19_2("1.19.2"),
    V_1_19_1("1.19.1");

    private final String[] versions;

    Version(String... versions) {
        this.versions = versions;
    }

    public static Version get(String stringVer) {
        for (Version ver : Version.values()) {
            if (ver.contains(stringVer))
                return ver;
        }

        if (Reflector.versionIsNewerOrEqualTo(1, 20, 5))
            return V_1_20_5_plus;

        return null;
    }

    private boolean contains(String version) {
        for (String ver : versions) {
            if (version.equals(ver))
                return true;
        }

        return false;
    }
}
