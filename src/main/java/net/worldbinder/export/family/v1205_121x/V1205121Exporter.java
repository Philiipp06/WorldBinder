package net.worldbinder.export.family.v1205_121x;

import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.family.AbstractWorldExportModule;
import net.worldbinder.version.FormatFamily;

import java.util.List;

public final class V1205121Exporter extends AbstractWorldExportModule {
    @Override
    public FormatFamily family() {
        return FormatFamily.V1_20_5_1_21;
    }

    @Override
    protected void addPrimaryLayouts(ExportContext context, List<ExportLayoutSpec> layouts) {
        layouts.add(classicRoot(context, true, true, false));
    }
}
