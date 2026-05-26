package net.worldbinder.version;

public final class VersionRegistry {
    private VersionRegistry() {
    }

    public static VersionProfile resolve(TargetMinecraftVersion.Entry target) {
        if (target == null) {
            target = TargetMinecraftVersion.resolve(TargetMinecraftVersion.CURRENT);
        }
        int dataVersion = target.effectiveDataVersion();
        FormatFamily family = family(dataVersion, target.profile());
        boolean separateEntities = dataVersion >= 2724;
        boolean poi = dataVersion >= 1952;
        boolean components = dataVersion >= 3837;
        boolean modern26 = family == FormatFamily.V26_X;
        return new VersionProfile(
                target,
                family,
                dataVersion,
                poi,
                separateEntities,
                components,
                modern26,
                modern26,
                modern26,
                !separateEntities,
                target.profile().label()
        );
    }

    private static FormatFamily family(int dataVersion, TargetMinecraftVersion.GenerationProfile profile) {
        if (profile == TargetMinecraftVersion.GenerationProfile.CURRENT_26) {
            return FormatFamily.V26_X;
        }
        if (dataVersion >= 3837) {
            return FormatFamily.V1_20_5_1_21;
        }
        if (dataVersion >= 2860) {
            return FormatFamily.V1_18_1_20_4;
        }
        if (dataVersion >= 2724) {
            return FormatFamily.V1_17;
        }
        if (dataVersion >= 2225) {
            return FormatFamily.V1_15_1_16;
        }
        return FormatFamily.V1_13_1_14;
    }
}
