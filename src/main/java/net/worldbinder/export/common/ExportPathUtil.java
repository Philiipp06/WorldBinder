package net.worldbinder.export.common;

import net.worldbinder.scene.WorldScene;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExportPathUtil {
    private ExportPathUtil() {
    }

    public static List<String> serverImportKeys(WorldScene scene, Path worldFolder) {
        List<String> keys = new ArrayList<>();
        addServerImportKey(keys, scene == null ? null : scene.name);
        Path folderName = worldFolder == null ? null : worldFolder.getFileName();
        addServerImportKey(keys, folderName == null ? null : folderName.toString());
        return keys;
    }

    public static void addServerImportKey(List<String> keys, String raw) {
        String key = sanitizeServerWorldKey(raw);
        if (!key.isBlank() && !keys.contains(key)) {
            keys.add(key);
        }
    }

    public static String sanitizeServerWorldKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        boolean lastUnderscore = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
            if (allowed) {
                result.append(c);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                result.append('_');
                lastUnderscore = true;
            }
        }
        while (result.length() > 0 && result.charAt(0) == '_') {
            result.deleteCharAt(0);
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '_') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }
}
