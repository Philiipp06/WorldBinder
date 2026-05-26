package net.worldbinder.export;

import net.worldbinder.export.common.WorldExportEngine;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.StorageProgress;

import java.io.IOException;
import java.nio.file.Path;

public final class VanillaWorldExporter {
    private VanillaWorldExporter() {
    }

    public static ExportResult export(WorldScene scene, Path worldFolder) throws IOException {
        return export(scene, worldFolder, null);
    }

    public static ExportResult export(WorldScene scene, Path worldFolder, StorageProgress progress) throws IOException {
        return WorldExportEngine.export(scene, worldFolder, progress);
    }

    public static Path overworldFolder(Path worldFolder) {
        return worldFolder;
    }

    public record ExportResult(Path folder, int chunks, int blocks, int blockEntities, int entities) {
    }
}
