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

    public static String archiveFolderName(String rawName) {
        String name = cleanBaseName(rawName);
        return name + "_" + LocalDateTime.now().format(STAMP);
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
