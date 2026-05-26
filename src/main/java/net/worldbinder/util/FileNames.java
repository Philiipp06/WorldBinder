package net.worldbinder.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FileNames {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private FileNames() {
    }

    public static String sceneFileName(String rawName) {
        return archiveFileName(rawName);
    }

    public static String archiveFileName(String rawName) {
        return archiveFolderName(rawName) + ".json";
    }

    public static String archiveFileName(String rawName, boolean appendTimestamp) {
        return archiveFolderName(rawName, appendTimestamp) + ".json";
    }

    public static String archiveFolderName(String rawName) {
        return archiveFolderName(rawName, true);
    }

    public static String archiveFolderName(String rawName, boolean appendTimestamp) {
        String name = cleanBaseName(rawName);
        return appendTimestamp ? name + "_" + LocalDateTime.now().format(STAMP) : name;
    }

    public static String cleanBaseName(String rawName) {
        String name = rawName == null || rawName.isBlank() ? "worldbinder_export" : rawName;
        name = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\- ]", "")
                .trim()
                .replace(' ', '_');
        if (name.isBlank()) {
            name = "worldbinder_export";
        }
        return name;
    }
}
