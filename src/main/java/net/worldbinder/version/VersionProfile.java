package net.worldbinder.version;

public record VersionProfile(
        TargetMinecraftVersion.Entry target,
        FormatFamily family,
        int dataVersion,
        boolean writesPoiRegions,
        boolean writesSeparateEntityRegions,
        boolean writesModernItemComponents,
        boolean writesModernDimensionFolders,
        boolean writesGameRulesFile,
        boolean writesWorldGenSettingsFile,
        boolean embedsEntitiesInChunks,
        String label
) {
    public String versionName() {
        return target.name();
    }
}
