package net.worldbinder.export.api;

import java.nio.file.Path;

public record ExportLayoutSpec(
        String label,
        Path dimensionFolder,
        Path regionFolder,
        Path entityFolder,
        Path poiFolder,
        boolean writeEntities,
        boolean writePoi,
        boolean embedEntitiesInChunks
) {
}
