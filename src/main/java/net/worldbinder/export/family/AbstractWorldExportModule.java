package net.worldbinder.export.family;

import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.api.WorldExportModule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractWorldExportModule implements WorldExportModule {
    @Override
    public List<ExportLayoutSpec> layouts(ExportContext context) {
        List<ExportLayoutSpec> layouts = new ArrayList<>();
        addPrimaryLayouts(context, layouts);
        return layouts;
    }

    protected abstract void addPrimaryLayouts(ExportContext context, List<ExportLayoutSpec> layouts);

    protected ExportLayoutSpec classicRoot(ExportContext context, boolean entities, boolean poi, boolean embedEntities) {
        Path root = context.worldFolder();
        return new ExportLayoutSpec(
                label(),
                root,
                root.resolve("region"),
                entities ? root.resolve("entities") : null,
                poi ? root.resolve("poi") : null,
                entities,
                poi,
                embedEntities
        );
    }

    protected ExportLayoutSpec modernDimension(String label, Path dimensionFolder) {
        return new ExportLayoutSpec(
                label,
                dimensionFolder,
                dimensionFolder.resolve("region"),
                dimensionFolder.resolve("entities"),
                dimensionFolder.resolve("poi"),
                true,
                true,
                false
        );
    }

    protected String label() {
        return family().name().toLowerCase();
    }
}
