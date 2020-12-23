package me.deecaad.compatibility;

import me.deecaad.compatibility.block.BlockCompatibility;
import me.deecaad.compatibility.entity.EntityCompatibility;

public class CompatibilityAPI {

    private static double version;
    private static ICompatibility compatibility;

    static {
        compatibility = new CompatibilitySetup().getCompatibleVersion(ICompatibility.class, "me.deecaad.compatibility");
    }

    /**
     *
     * Example return values:
     * <pre>{@code
     * v1_8_R2 -> 1.082
     * v1_11_R1 -> 1.111
     * v1_13_R3 -> 1.133
     * }</pre>
     *
     * @return the server version as number
     */
    public static double getVersion() {
        if (version == 0.0) {
            VersionSetup versionSetup = new VersionSetup();
            version = versionSetup.getVersionAsNumber(versionSetup.getVersionAsString());
        }
        return version;
    }

    /**
     * If compatibility isn't set up this will automatically set it up
     *
     * @return the compatible version as ICompatibility
     */
    public static ICompatibility getCompatibility() {
        return compatibility;
    }

    public static EntityCompatibility getEntityCompatibility() {
        return compatibility.getEntityCompatibility();
    }

    public static BlockCompatibility getBlockCompatibility() {
        return compatibility.getBlockCompatibility();
    }
}