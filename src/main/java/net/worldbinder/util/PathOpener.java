package net.worldbinder.util;

import net.worldbinder.WorldBinder;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PathOpener {
    private PathOpener() {
    }

    public static void open(Path path) {
        if (path == null) {
            return;
        }
        Path target = Files.isDirectory(path) ? path : path.getParent();
        if (target == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(target.toFile());
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("explorer", target.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", target.toAbsolutePath().toString()).start();
            } else {
                new ProcessBuilder("xdg-open", target.toAbsolutePath().toString()).start();
            }
        } catch (IOException | RuntimeException exception) {
            WorldBinder.LOGGER.warn("Failed to open path {}", target, exception);
        }
    }
}
