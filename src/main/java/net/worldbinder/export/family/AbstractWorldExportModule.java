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
        String dimension = net.worldbinder.scene.ProjectDimensions.canonical(context.scene().dimension);
        if(dimension!=null&&!dimension.startsWith("minecraft:")){
            if(context.profile().dataVersion()<2566)throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.dimensions.unsupported_target",dimension,context.profile().dataVersion()));
            String[] parts=dimension.split(":",2);Path folder=context.worldFolder().resolve("dimensions").resolve(parts[0]).resolve(parts[1]).normalize();
            if(!folder.startsWith(context.worldFolder().resolve("dimensions").normalize()))throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.dimensions.invalid",dimension));
            boolean entities=context.profile().dataVersion()>=2724,poi=context.profile().dataVersion()>=1952;
            return List.of(new ExportLayoutSpec(dimension,folder,folder.resolve("region"),entities?folder.resolve("entities"):null,poi?folder.resolve("poi"):null,entities,poi,!entities));
        }
        if ("minecraft:the_nether".equals(dimension) || "minecraft:the_end".equals(dimension)) {
            Path folder = context.profile().dataVersion() >= 4786
                    ? context.worldFolder().resolve("dimensions/minecraft/" + dimension.substring(10))
                    : context.worldFolder().resolve(dimension.endsWith("the_nether") ? "DIM-1" : "DIM1");
            boolean entities = context.profile().dataVersion() >= 2724;
            boolean poi = context.profile().dataVersion() >= 1952;
            return List.of(new ExportLayoutSpec(label(), folder, folder.resolve("region"), entities ? folder.resolve("entities") : null,
                    poi ? folder.resolve("poi") : null, entities, poi, !entities));
        }
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
