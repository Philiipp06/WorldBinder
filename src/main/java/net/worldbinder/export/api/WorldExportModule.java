package net.worldbinder.export.api;

import net.worldbinder.version.FormatFamily;

import java.util.List;

public interface WorldExportModule {
    FormatFamily family();

    List<ExportLayoutSpec> layouts(ExportContext context);
}
