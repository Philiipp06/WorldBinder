package net.worldbinder.io;

import net.fabricmc.loader.api.FabricLoader;
import net.worldbinder.WorldBinder;
import net.worldbinder.util.FileNames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorldBinderPaths {
    public static final Path BASE = FabricLoader.getInstance().getConfigDir().resolve(WorldBinder.MOD_ID);
    public static final Path ARCHIVES = BASE.resolve("archives");
    public static final Path SCENES = ARCHIVES.resolve("scenes");
    public static final Path GAME_DIR = FabricLoader.getInstance().getGameDir();
    public static final Path MINECRAFT_SAVES = GAME_DIR.resolve("saves");
    public static final Path RECOVERY_ROOT = MINECRAFT_SAVES.resolve("WorldBinder");
    public static final Path WORLDS = RECOVERY_ROOT;
    public static final Path CONFIG_FILE = BASE.resolve("config.json");

    private WorldBinderPaths() {
    }

    public static void ensureBaseFolders() {
        try {
            Files.createDirectories(BASE);
            Files.createDirectories(ARCHIVES);
            Files.createDirectories(SCENES);
            Files.createDirectories(MINECRAFT_SAVES);
            Files.createDirectories(RECOVERY_ROOT);
        } catch (IOException exception) {
            WorldBinder.LOGGER.error("Failed to create WorldBinder folders", exception);
        }
    }

    public static Path newWorldFolder(String requestedName) {
        return uniqueWorldFolder(FileNames.archiveFolderName(requestedName));
    }

    private static Path uniqueWorldFolder(String baseName) {
        Path candidate = MINECRAFT_SAVES.resolve(baseName);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = MINECRAFT_SAVES.resolve(baseName + "_" + index);
            index++;
        }
        return candidate;
    }
}
