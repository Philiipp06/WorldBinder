package net.worldbinder.export.family;

import net.worldbinder.export.api.WorldExportModule;
import net.worldbinder.export.family.v113_114.V113114Exporter;
import net.worldbinder.export.family.v115_116.V115116Exporter;
import net.worldbinder.export.family.v117.V117Exporter;
import net.worldbinder.export.family.v118_1204.V1181204Exporter;
import net.worldbinder.export.family.v1205_121x.V1205121Exporter;
import net.worldbinder.export.family.v261.V261Exporter;
import net.worldbinder.version.FormatFamily;
import net.worldbinder.version.VersionProfile;

public final class WorldExportModules {
    private static final WorldExportModule V113_114 = new V113114Exporter();
    private static final WorldExportModule V115_116 = new V115116Exporter();
    private static final WorldExportModule V117 = new V117Exporter();
    private static final WorldExportModule V118_1204 = new V1181204Exporter();
    private static final WorldExportModule V1205_121X = new V1205121Exporter();
    private static final WorldExportModule V261 = new V261Exporter();

    private WorldExportModules() {
    }

    public static WorldExportModule forProfile(VersionProfile profile) {
        FormatFamily family = profile == null ? FormatFamily.V26_X : profile.family();
        return switch (family) {
            case V1_13_1_14 -> V113_114;
            case V1_15_1_16 -> V115_116;
            case V1_17 -> V117;
            case V1_18_1_20_4 -> V118_1204;
            case V1_20_5_1_21 -> V1205_121X;
            case V26_X -> V261;
        };
    }
}
