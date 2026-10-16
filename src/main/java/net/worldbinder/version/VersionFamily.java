package net.worldbinder.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Official Minecraft update / game drop each export target belongs to, with its release year, color and emblem.
 * Used by the target version picker to explain what a version stands for.
 */
public enum VersionFamily {
    AQUATIC("aquatic", 2018, 0xFF3D9BE0, "WAVE", Set.of("1.13", "1.13.1", "1.13.2")),
    VILLAGE("village", 2019, 0xFFD69A4E, "HOUSE", Set.of("1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4")),
    BEES("bees", 2019, 0xFFF2C94C, "BEE", Set.of("1.15", "1.15.1", "1.15.2")),
    NETHER("nether", 2020, 0xFFE0574F, "FLAME", Set.of("1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5")),
    CAVES_ONE("caves_one", 2021, 0xFF9C7BE0, "CRYSTAL", Set.of("1.17", "1.17.1")),
    CAVES_TWO("caves_two", 2021, 0xFF6FB36A, "MOUNTAIN", Set.of("1.18", "1.18.1", "1.18.2")),
    WILD("wild", 2022, 0xFF5C9E6B, "FROG", Set.of("1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4")),
    TRAILS("trails", 2023, 0xFFF29AC0, "BRUSH", Set.of("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6")),
    TRIALS("trials", 2024, 0xFFD9824A, "TRIAL_KEY", Set.of("1.21", "1.21.1")),
    BUNDLES("bundles", 2024, 0xFFB98A5A, "BUNDLE", Set.of("1.21.2", "1.21.3")),
    GARDEN("garden", 2024, 0xFF9AA6A0, "SPROUT", Set.of("1.21.4")),
    SPRING("spring", 2025, 0xFF8BD06A, "SPROUT", Set.of("1.21.5")),
    SKIES("skies", 2025, 0xFF8FCBF2, "CLOUD", Set.of("1.21.6", "1.21.7", "1.21.8")),
    DROPS_2026("drops_2026", 2026, 0xFF62C3EA, "STAR", Set.of());

    public final String id;
    public final int year;
    public final int color;
    public final String iconName;
    private final Set<String> versions;

    VersionFamily(String id, int year, int color, String iconName, Set<String> versions) {
        this.id = id;
        this.year = year;
        this.color = color;
        this.iconName = iconName;
        this.versions = versions;
    }

    public String nameKey() {
        return "worldbinder.version_family." + id;
    }

    public String descriptionKey() {
        return nameKey() + ".description";
    }

    public static VersionFamily of(String version) {
        for (VersionFamily family : values()) {
            if (family.versions.contains(version)) {
                return family;
            }
        }
        return DROPS_2026;
    }

    /** Export targets of this family, newest first. */
    public List<TargetMinecraftVersion.Entry> entries() {
        List<TargetMinecraftVersion.Entry> result = new ArrayList<>();
        for (TargetMinecraftVersion.Entry entry : TargetMinecraftVersion.FINAL_RELEASES) {
            if (of(entry.name()) == this) {
                result.addFirst(entry);
            }
        }
        return result;
    }
}
