package net.worldbinder.scene;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.common.ExportFiltering;

/** Project-owned input policy. Export filters remain independent and never change this policy. */
public final class CaptureSettings {
    public boolean multiDimension = true;
    public boolean liveEntityFilters;
    public ExportFilterSettings entities = new ExportFilterSettings();
    public boolean manualContainerMerge;
    public ContainerMerging.Strategy containerStrategy = ContainerMerging.Strategy.OVERWRITE;

    public boolean excludes(Entity entity) {
        return liveEntityFilters && ExportFiltering.excludedType(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), entities);
    }
    public CaptureSettings copy() { return WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(this),CaptureSettings.class); }
    public void normalize() {
        if(entities==null)entities=new ExportFilterSettings();
        if(entities.entityIds==null)entities.entityIds=new java.util.LinkedHashSet<>();
        if(entities.blockIds==null)entities.blockIds=new java.util.LinkedHashSet<>();
        if(containerStrategy==null)containerStrategy=ContainerMerging.Strategy.OVERWRITE;
    }
}
