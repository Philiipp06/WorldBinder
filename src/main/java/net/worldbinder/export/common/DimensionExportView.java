package net.worldbinder.export.common;

import net.minecraft.nbt.*;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import java.util.*;

/** Map IDs are dimension-scoped in an archive but global in a vanilla save. Remap only the export copy. */
public final class DimensionExportView {
    private DimensionExportView(){}
    public static WorldScene prepare(WorldScene source){
        if(source.dimensions.isEmpty()||source.dimensionMapsPrepared)return source;
        var root=ProjectDimensions.dimensionOnly(source);root.dimensions=new LinkedHashMap<>(source.dimensions);
        root.filtersApplied=source.filtersApplied;root.filterCounts=source.filterCounts;root.exportRelightChunks.addAll(source.exportRelightChunks);
        root.dimensionMapsPrepared=true;
        var used=new HashSet<Integer>();for(var scope:ProjectDimensions.all(source)){used.addAll(scope.mapIds);used.addAll(scope.maps.keySet());}
        int next=used.stream().mapToInt(Integer::intValue).max().orElse(-1)+1;
        var assigned=new HashSet<Integer>();
        var images=new LinkedHashMap<Integer,CapturedMap>();
        var scopes=new ArrayList<>(ProjectDimensions.all(source));scopes.sort(Comparator.comparing(s->Objects.toString(ProjectDimensions.canonical(s.dimension),"")));
        for(var original:scopes){
            var ids=new TreeSet<Integer>(original.mapIds);ids.addAll(original.maps.keySet());var aliases=new LinkedHashMap<Integer,Integer>();
            for(int id:ids){
                var image=original.maps.get(id);var existing=images.get(id);
                boolean conflict=existing!=null&&existing.receivedPixels()>0&&image!=null&&image.receivedPixels()>0&&!sameImage(existing,image);
                if(assigned.add(id)||!conflict){if(image!=null&&(existing==null||existing.receivedPixels()<image.receivedPixels()))images.put(id,image);continue;}
                while(used.contains(next)||assigned.contains(next))next++;if(next<0)throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.dimensions.map_ids_exhausted"));aliases.put(id,next);assigned.add(next++);
            }
            var view=aliases.isEmpty()?ProjectDimensions.dimensionOnly(original):remap(original,aliases);
            view.filtersApplied=original.filtersApplied;view.filterCounts=original.filterCounts;view.exportRelightChunks.addAll(original.exportRelightChunks);
            view.targetMinecraftVersion=source.targetMinecraftVersion;view.exportSettings=source.exportSettings;
            if(original==source){
                root.blocks=view.blocks;root.entities=view.entities;root.containers=view.containers;root.maps=view.maps;root.mapIds=view.mapIds;root.player=view.player;
            }else root.dimensions.put(ProjectDimensions.canonical(original.dimension),view);
        }
        var global=new LinkedHashMap<Integer,CapturedMap>();var globalIds=new TreeSet<Integer>();
        for(var scope:ProjectDimensions.all(root)){
            globalIds.addAll(scope.mapIds);globalIds.addAll(scope.maps.keySet());
            scope.maps.forEach((id,map)->{var old=global.get(id);if(old==null||old.receivedPixels()<map.receivedPixels())global.put(id,map);});
        }
        for(var scope:root.dimensions.values()){var ids=new HashSet<>(scope.mapIds);ids.addAll(scope.maps.keySet());scope.maps=new LinkedHashMap<>();for(int id:ids)if(global.containsKey(id))scope.maps.put(id,global.get(id));}
        root.maps=global;root.mapIds=new ArrayList<>(globalIds);
        return root;
    }
    private static boolean sameImage(CapturedMap left,CapturedMap right){return Arrays.equals(left.colors,right.colors)&&Arrays.equals(left.received,right.received)&&Objects.equals(left.nbt,right.nbt)&&Objects.equals(left.decorationsNbt,right.decorationsNbt);}
    private static WorldScene remap(WorldScene source,Map<Integer,Integer> ids){
        var copy=ProjectDimensions.dimensionOnly(source);
        copy.mapIds=new ArrayList<>(source.mapIds.stream().map(id->ids.getOrDefault(id,id)).toList());copy.maps=new LinkedHashMap<>();
        source.maps.forEach((id,map)->{var changed=clone(map,CapturedMap.class);changed.id=ids.getOrDefault(id,id);copy.maps.put(changed.id,changed);});
        copy.blocks=new ArrayList<>();for(var block:source.blocks){if(block.blockEntityNbt==null){copy.blocks.add(block);continue;}var b=clone(block,BlockRecord.class);b.blockEntityNbt=nbt(b.blockEntityNbt,ids);copy.blocks.add(b);}
        copy.entities=new ArrayList<>();for(var entity:source.entities){if(entity.fullNbt==null){copy.entities.add(entity);continue;}var e=clone(entity,EntityRecord.class);e.fullNbt=nbt(e.fullNbt,ids);copy.entities.add(e);}
        copy.containers=new LinkedHashMap<>();source.containers.forEach((key,c)->{var changed=clone(c,ContainerRecord.class);changed.items=changed.items.stream().map(s->nbt(s,ids)).toList();copy.containers.put(key,changed);});
        if(source.player!=null){copy.player=clone(source.player,CapturedPlayer.class);copy.player.nbt=nbt(copy.player.nbt,ids);if(copy.player.enderChest!=null)copy.player.enderChest.items=copy.player.enderChest.items.stream().map(s->nbt(s,ids)).toList();}
        return copy;
    }
    private static <T>T clone(T value,Class<T> type){return WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(value),type);}
    private static String nbt(String snbt,Map<Integer,Integer> ids){if(snbt==null||!snbt.contains("map"))return snbt;var tag=AuxiliaryDataWriter.parse(snbt);visit(tag,ids);return tag.toString();}
    private static void visit(Tag tag,Map<Integer,Integer> ids){
        if(tag instanceof CompoundTag compound){
            if(compound.get("components") instanceof CompoundTag components){Integer id=components.getInt("minecraft:map_id").orElse(null);if(id!=null&&ids.containsKey(id))components.putInt("minecraft:map_id",ids.get(id));}
            if(compound.getString("id").orElse("").equals("minecraft:filled_map")&&compound.get("tag") instanceof CompoundTag legacy){Integer id=legacy.getInt("map").orElse(null);if(id!=null&&ids.containsKey(id))legacy.putInt("map",ids.get(id));}
            for(String key:compound.keySet())visit(compound.get(key),ids);
        }else if(tag instanceof ListTag list)for(var entry:list)visit(entry,ids);
    }
}
