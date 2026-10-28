package net.worldbinder.version;

import net.minecraft.SharedConstants;

import java.util.List;
import java.util.Locale;

public final class TargetMinecraftVersion {
    public static final String CURRENT = "26.3";

    public enum GenerationProfile {
        OCEAN_1_13("worldbinder.generation_profile.ocean_1_13"),
        VILLAGE_1_14("worldbinder.generation_profile.village_1_14"),
        NETHER_1_16("worldbinder.generation_profile.nether_1_16"),
        CAVES_1_18("worldbinder.generation_profile.caves_1_18"),
        WILD_1_19("worldbinder.generation_profile.wild_1_19"),
        TRAILS_1_20("worldbinder.generation_profile.trails_1_20"),
        TRIALS_1_21("worldbinder.generation_profile.trials_1_21"),
        CURRENT_26("worldbinder.generation_profile.current_26");

        private final String label;

        GenerationProfile(String label) {
            this.label = label;
        }

        public String label(String language) {
            return net.worldbinder.util.Lang.in(language, label);
        }

        public String label() {
            return net.worldbinder.util.Lang.string(label);
        }
    }

    public record Entry(String name, int dataVersion, GenerationProfile profile) {
        public int effectiveDataVersion() {
            return dataVersion > 0 ? dataVersion : SharedConstants.getCurrentVersion().dataVersion().version();
        }

        public VersionProfile versionProfile() {
            return VersionRegistry.resolve(this);
        }

        public boolean usesGameRulesFile() {
            return versionProfile().writesGameRulesFile();
        }

        public boolean usesModernDimensionFolders() {
            return versionProfile().writesModernDimensionFolders();
        }

        public boolean usesPoiFiles() {
            return versionProfile().writesPoiRegions();
        }

        public boolean usesSeparateEntityFiles() {
            return versionProfile().writesSeparateEntityRegions();
        }

        public boolean supportsModernItemComponents() {
            return versionProfile().writesModernItemComponents();
        }
    }

    public static final List<Entry> FINAL_RELEASES = List.of(
            entry("1.13", 1519, GenerationProfile.OCEAN_1_13),
            entry("1.13.1", 1628, GenerationProfile.OCEAN_1_13),
            entry("1.13.2", 1631, GenerationProfile.OCEAN_1_13),
            entry("1.14", 1952, GenerationProfile.VILLAGE_1_14),
            entry("1.14.1", 1957, GenerationProfile.VILLAGE_1_14),
            entry("1.14.2", 1963, GenerationProfile.VILLAGE_1_14),
            entry("1.14.3", 1968, GenerationProfile.VILLAGE_1_14),
            entry("1.14.4", 1976, GenerationProfile.VILLAGE_1_14),
            entry("1.15", 2225, GenerationProfile.VILLAGE_1_14),
            entry("1.15.1", 2227, GenerationProfile.VILLAGE_1_14),
            entry("1.15.2", 2230, GenerationProfile.VILLAGE_1_14),
            entry("1.16", 2566, GenerationProfile.NETHER_1_16),
            entry("1.16.1", 2567, GenerationProfile.NETHER_1_16),
            entry("1.16.2", 2578, GenerationProfile.NETHER_1_16),
            entry("1.16.3", 2580, GenerationProfile.NETHER_1_16),
            entry("1.16.4", 2584, GenerationProfile.NETHER_1_16),
            entry("1.16.5", 2586, GenerationProfile.NETHER_1_16),
            entry("1.17", 2724, GenerationProfile.NETHER_1_16),
            entry("1.17.1", 2730, GenerationProfile.NETHER_1_16),
            entry("1.18", 2860, GenerationProfile.CAVES_1_18),
            entry("1.18.1", 2865, GenerationProfile.CAVES_1_18),
            entry("1.18.2", 2975, GenerationProfile.CAVES_1_18),
            entry("1.19", 3105, GenerationProfile.WILD_1_19),
            entry("1.19.1", 3117, GenerationProfile.WILD_1_19),
            entry("1.19.2", 3120, GenerationProfile.WILD_1_19),
            entry("1.19.3", 3218, GenerationProfile.WILD_1_19),
            entry("1.19.4", 3337, GenerationProfile.WILD_1_19),
            entry("1.20", 3463, GenerationProfile.TRAILS_1_20),
            entry("1.20.1", 3465, GenerationProfile.TRAILS_1_20),
            entry("1.20.2", 3578, GenerationProfile.TRAILS_1_20),
            entry("1.20.3", 3698, GenerationProfile.TRAILS_1_20),
            entry("1.20.4", 3700, GenerationProfile.TRAILS_1_20),
            entry("1.20.5", 3837, GenerationProfile.TRAILS_1_20),
            entry("1.20.6", 3839, GenerationProfile.TRAILS_1_20),
            entry("1.21", 3953, GenerationProfile.TRIALS_1_21),
            entry("1.21.1", 3955, GenerationProfile.TRIALS_1_21),
            entry("1.21.2", 4080, GenerationProfile.TRIALS_1_21),
            entry("1.21.3", 4082, GenerationProfile.TRIALS_1_21),
            entry("1.21.4", 4189, GenerationProfile.TRIALS_1_21),
            entry("1.21.5", 4325, GenerationProfile.TRIALS_1_21),
            entry("1.21.6", 4435, GenerationProfile.TRIALS_1_21),
            entry("1.21.7", 4438, GenerationProfile.TRIALS_1_21),
            entry("1.21.8", 4440, GenerationProfile.TRIALS_1_21),
            entry("26.1", 4786, GenerationProfile.CURRENT_26),
            entry("26.1.1", 4788, GenerationProfile.CURRENT_26),
            entry("26.1.2", 4790, GenerationProfile.CURRENT_26),
            entry("26.2", 4903, GenerationProfile.CURRENT_26),
            entry(CURRENT, 5023, GenerationProfile.CURRENT_26)
    );

    private TargetMinecraftVersion() {
    }

    private static Entry entry(String name, int dataVersion, GenerationProfile profile) {
        return new Entry(name, dataVersion, profile);
    }

    public static Entry resolve(String raw) {
        String cleaned = clean(raw);
        for (Entry entry : FINAL_RELEASES) {
            if (entry.name.equals(cleaned)) {
                return entry;
            }
        }
        return FINAL_RELEASES.get(FINAL_RELEASES.size() - 1);
    }

    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return CURRENT;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("v")) {
            value = value.substring(1);
        }
        return value;
    }

    public static String normalize(String raw) {
        return resolve(raw).name;
    }

    public static String next(String raw) {
        int index = indexOf(raw);
        return FINAL_RELEASES.get((index + 1) % FINAL_RELEASES.size()).name;
    }

    public static String previous(String raw) {
        int index = indexOf(raw);
        return FINAL_RELEASES.get((index - 1 + FINAL_RELEASES.size()) % FINAL_RELEASES.size()).name;
    }

    private static int indexOf(String raw) {
        String cleaned = clean(raw);
        for (int i = 0; i < FINAL_RELEASES.size(); i++) {
            if (FINAL_RELEASES.get(i).name.equals(cleaned)) {
                return i;
            }
        }
        return FINAL_RELEASES.size() - 1;
    }

    public static String profileLabel(String raw) {
        return resolve(raw).profile.label();
    }

    public static String profileTranslationKey(String raw) {
        return "worldbinder.generation_profile." + resolve(raw).profile.name().toLowerCase(Locale.ROOT);
    }
}
