package net.worldbinder.export.family.v115_116;

import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.family.AbstractWorldExportModule;
import net.worldbinder.version.FormatFamily;

import java.util.List;

public final class V115116Exporter extends AbstractWorldExportModule {
    @Override
    public FormatFamily family() {
        return FormatFamily.V1_15_1_16;
    }

    @Override
    protected void addPrimaryLayouts(ExportContext context, List<ExportLayoutSpec> layouts) {
        layouts.add(classicRoot(context, false, true, true));
    }
}
