package net.worldbinder.export.family.v113_114;

import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.family.AbstractWorldExportModule;
import net.worldbinder.version.FormatFamily;

import java.util.List;

public final class V113114Exporter extends AbstractWorldExportModule {
    @Override
    public FormatFamily family() {
        return FormatFamily.V1_13_1_14;
    }

    @Override
    protected void addPrimaryLayouts(ExportContext context, List<ExportLayoutSpec> layouts) {
        layouts.add(classicRoot(context, false, context.profile().writesPoiRegions(), true));
    }
}
