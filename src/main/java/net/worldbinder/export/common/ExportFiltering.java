package net.worldbinder.export.common;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.MobCategory;
import net.worldbinder.scene.*;
import java.util.*;

/** Filters only an export view. The complete source and its received auxiliary data stay in the archive. */
public final class ExportFiltering {
    public record Counts(long blocks,long blockEntities,long entities) {}
    private ExportFiltering(){}
    public static boolean excluded(EntityRecord entity,ExportFilterSettings settings){
        return excludedType(entity.type,settings);
    }
    public static boolean excludedType(String type,ExportFilterSettings settings){
        if(settings.entityIds.contains(type))return true;
        if(settings.droppedItems&&type.equals("minecraft:item"))return true;
        if(settings.experienceOrbs&&type.equals("minecraft:experience_orb"))return true;
        if(settings.armorStands&&type.equals("minecraft:armor_stand"))return true;
        var id=Identifier.tryParse(type);
        return settings.hostileMobs&&id!=null&&BuiltInRegistries.ENTITY_TYPE.containsKey(id)&&BuiltInRegistries.ENTITY_TYPE.getValue(id).getCategory()==MobCategory.MONSTER;
    }
    public static Counts count(WorldScene scene,ExportFilterSettings settings){
        long blocks=0,blockEntities=0,entities=0;
        for(var dimension:ProjectDimensions.all(scene)){
            for(var block:dimension.blocks)if(settings.blockIds.contains(ExportCompatibility.id(block.state))){blocks++;if(block.hasBlockEntity)blockEntities++;}
            for(var entity:dimension.entities)if(excluded(entity,settings))entities++;
        }
        return new Counts(blocks,blockEntities,entities);
    }
    public static WorldScene apply(WorldScene source){
        if(source.filtersApplied)return source;
        var copy=ProjectData.metadataCopy(source);copy.filtersApplied=true;
        copy.filterCounts=count(source,source.exportSettings.filters);
        copy.dimensions.clear();
        for(var entry:source.dimensions.entrySet()){
            var dimension=ProjectDimensions.dimensionOnly(entry.getValue());
            dimension.exportSettings=source.exportSettings;dimension.targetMinecraftVersion=source.targetMinecraftVersion;
            copy.dimensions.put(entry.getKey(),apply(dimension));
        }
        copy.chunkSnapshots=new LinkedHashMap<>(source.chunkSnapshots);copy.pendingChunks=new LinkedHashMap<>(source.pendingChunks);
        if(source.exportRelightChunks!=null)copy.exportRelightChunks.addAll(source.exportRelightChunks);
        var settings=copy.exportSettings.filters;
        for(var block:source.blocks){
            if(settings.blockIds.contains(ExportCompatibility.id(block.state))){
                copy.exportRelightChunks.add(ProjectData.chunkKey(source,block));
                copy.containers.remove(ContainerRecord.blockKey(source.originX+block.x,source.originY+block.y,source.originZ+block.z));
            }else copy.blocks.add(block);
        }
        for(var entity:source.entities){if(excluded(entity,settings))copy.containers.remove(ContainerRecord.entityKey(entity.uuid));else copy.entities.add(entity);}
        Set<Long> terrain=terrainKeys(copy);int version=net.worldbinder.version.TargetMinecraftVersion.resolve(copy.targetMinecraftVersion).effectiveDataVersion();
        long orphaned=copy.entities.stream().filter(e->!terrain.contains(entityChunk(copy,e))).count();
        if(orphaned>0){
            copy.exportNotices.add(net.worldbinder.validation.Finding.of(version<2724?"worldbinder.export.orphan_legacy":"worldbinder.export.orphan_terrain",orphaned));
            if(version<2724)copy.entities.removeIf(e->{if(terrain.contains(entityChunk(copy,e)))return false;copy.containers.remove(ContainerRecord.entityKey(e.uuid));return true;});
        }
        return copy;
    }
    public static Set<Long> terrainKeys(WorldScene scene){
        Set<Long> keys=new HashSet<>();for(var block:scene.blocks)keys.add(ProjectData.chunkKey(scene,block));
        for(var snapshot:scene.chunkSnapshots.values())if(snapshot.isDone()||snapshot.verifiedEmpty)keys.add(ProjectData.chunkKey(snapshot.chunkX,snapshot.chunkZ));return keys;
    }
    public static long entityChunk(WorldScene scene,EntityRecord e){return ProjectData.chunkKey((scene.originX+(int)Math.floor(e.x))>>4,(scene.originZ+(int)Math.floor(e.z))>>4);}
    public static Set<String> parseIds(WorldScene scene,String text,boolean blocks){
        Set<String> result=new LinkedHashSet<>();
        for(String entry:text.split("[,\\s]+")){
            if(entry.isBlank())continue;var id=Identifier.tryParse(entry);
            boolean known=id!=null&&(blocks?BuiltInRegistries.BLOCK.containsKey(id):BuiltInRegistries.ENTITY_TYPE.containsKey(id));
            if(!known)known=blocks?ProjectDimensions.all(scene).stream().flatMap(s->s.blocks.stream()).anyMatch(b->ExportCompatibility.id(b.state).equals(entry)):ProjectDimensions.all(scene).stream().flatMap(s->s.entities.stream()).anyMatch(e->e.type.equals(entry));
            if(!known||id==null)throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.filters.invalid",entry));
            result.add(id.toString());
        }
        return result;
    }
}
