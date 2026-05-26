package net.worldbinder.export.family.v261;

import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.common.ExportPathUtil;
import net.worldbinder.export.family.AbstractWorldExportModule;
import net.worldbinder.version.FormatFamily;

import java.nio.file.Path;
import java.util.List;

public final class V261Exporter extends AbstractWorldExportModule {
    @Override
    public FormatFamily family() {
        return FormatFamily.V26_X;
    }

    @Override
    protected void addPrimaryLayouts(ExportContext context, List<ExportLayoutSpec> layouts) {
        layouts.add(classicRoot(context, true, true, false));

        Path overworld = context.worldFolder().resolve("dimensions").resolve("minecraft").resolve("overworld");
        layouts.add(modernDimension("26x/overworld", overworld));

        for (String key : ExportPathUtil.serverImportKeys(context.scene(), context.worldFolder())) {
            if (!"overworld".equals(key)) {
                Path serverDimension = context.worldFolder().resolve("dimensions").resolve("minecraft").resolve(key);
                layouts.add(modernDimension("server/" + key, serverDimension));
            }
        }
    }
}
