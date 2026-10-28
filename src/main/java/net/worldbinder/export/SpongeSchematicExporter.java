package net.worldbinder.export;

import net.minecraft.nbt.*;
import net.worldbinder.export.common.*;
import net.worldbinder.scene.*;
import net.worldbinder.selection.*;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Sponge Schematic v3: gzip NBT, root Schematic, X/Z/Y varints and relative block/entity positions. */
public final class SpongeSchematicExporter {
    public static final int MAX_BLOCKS=16_777_216;
    private SpongeSchematicExporter(){}
    public record Result(Path file,long blocks,int blockEntities,int entities,List<String> warnings){}
    public static Result write(WorldScene original,CaptureBounds bounds,Path target)throws IOException{
        if(Files.exists(target)||Files.exists(target.resolveSibling(target.getFileName()+".txt")))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.schematic.exists",target));
        long unknown=bounds.volume()-RecordedCoverage.knownVolume(original,bounds);
        if(unknown>0)throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.schematic.incomplete",unknown));
        if(bounds.volume()>MAX_BLOCKS||bounds.width()>65535||bounds.height()>65535||bounds.length()>65535)throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.schematic.too_large",MAX_BLOCKS));
        WorldScene scene=ExportFiltering.apply(AreaExport.crop(original,bounds));
        int version=TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
        int[] grid=new int[Math.toIntExact(bounds.volume())];Map<String,Integer> palette=new LinkedHashMap<>();palette.put("minecraft:air",0);
        ListTag blockEntities=new ListTag(),entities=new ListTag();List<String> warnings=new ArrayList<>();
        for(var block:scene.blocks){
            String mapped=ExportCompatibility.state(scene,block.state);int index=block.x+block.z*bounds.width()+block.y*bounds.width()*bounds.length();
            grid[index]=palette.computeIfAbsent(mapped,k->palette.size());
            if(block.hasBlockEntity&&block.blockEntityNbt!=null&&net.worldbinder.export.common.BlockEntityDataConverter.compatible(ExportCompatibility.id(block.state),ExportCompatibility.id(mapped),version)){
                try{
                    var data=net.worldbinder.export.common.BlockEntityDataConverter.convert(TagParser.parseCompoundFully(block.blockEntityNbt),version);var entry=new CompoundTag();entry.putString("Id",data.getString("id").orElse(ExportCompatibility.id(mapped)));
                    entry.putIntArray("Pos",new int[]{block.x,block.y,block.z});data.remove("id");data.remove("x");data.remove("y");data.remove("z");data.remove("keepPacked");
                    var contents=scene.containers.get(ContainerRecord.blockKey(bounds.minX()+block.x,bounds.minY()+block.y,bounds.minZ()+block.z));
                    if(contents!=null&&contents.type.equals(ExportCompatibility.id(block.state))){data.put("Items",ItemDataConverter.items(contents,version,data.getList("Items").orElseGet(ListTag::new)));data.remove("LootTable");data.remove("LootTableSeed");}
                    ItemDataConverter.inventoryLists(data,version);entry.put("Data",data);blockEntities.add(entry);
                }catch(com.mojang.brigadier.exceptions.CommandSyntaxException exception){throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.schematic.bad_nbt",block.x,block.y,block.z), exception);}
            }
        }
        for(var entity:scene.entities){
            var data=WorldExportEngine.normalizedEntityNbt(scene,entity);if(data==null)continue;
            relocate(data,bounds);var entry=new CompoundTag();entry.putString("Id",data.getString("id").orElse(entity.type));data.remove("id");
            ListTag pos=new ListTag();pos.add(DoubleTag.valueOf(entity.x));pos.add(DoubleTag.valueOf(entity.y));pos.add(DoubleTag.valueOf(entity.z));entry.put("Pos",pos);data.remove("Pos");entry.put("Data",data);entities.add(entry);
        }
        CompoundTag schematic=new CompoundTag();schematic.putInt("Version",3);schematic.putInt("DataVersion",version);
        schematic.putShort("Width",(short)bounds.width());schematic.putShort("Height",(short)bounds.height());schematic.putShort("Length",(short)bounds.length());schematic.putIntArray("Offset",new int[]{0,0,0});
        CompoundTag metadata=new CompoundTag();metadata.putString("Name",scene.name);metadata.putString("Author","WorldBinder");metadata.putLong("Date",System.currentTimeMillis());metadata.putString("WorldBinderProjectId",original.projectId);metadata.putIntArray("WorldBinderSourceMin",new int[]{bounds.minX(),bounds.minY(),bounds.minZ()});schematic.put("Metadata",metadata);
        CompoundTag blocks=new CompoundTag(),states=new CompoundTag();palette.forEach(states::putInt);blocks.put("Palette",states);blocks.putByteArray("Data",varints(grid));blocks.put("BlockEntities",blockEntities);schematic.put("Blocks",blocks);schematic.put("Entities",entities);
        // Biomes are optional. Emit them only when every source sample was actually received.
        int[] biomes=new int[grid.length];Map<String,Integer> biomePalette=new LinkedHashMap<>();boolean complete=true;
        outer:for(int y=0;y<bounds.height();y++)for(int z=0;z<bounds.length();z++)for(int x=0;x<bounds.width();x++){
            int ax=bounds.minX()+x,ay=bounds.minY()+y,az=bounds.minZ()+z;var chunk=scene.chunkSnapshots.get((ax>>4)+","+(az>>4));var section=chunk==null?null:chunk.sections.get(ay>>4);
            if(section==null||!section.hasBiomes()){complete=false;break outer;}
            String biome=ExportCompatibility.biome(section.biomes[((ay&15)>>2)*16+((az&15)>>2)*4+((ax&15)>>2)],version);
            biomes[x+z*bounds.width()+y*bounds.width()*bounds.length()]=biomePalette.computeIfAbsent(biome,k->biomePalette.size());
        }
        if(complete){var data=new CompoundTag();var statesTag=new CompoundTag();biomePalette.forEach(statesTag::putInt);data.put("Palette",statesTag);data.putByteArray("Data",varints(biomes));schematic.put("Biomes",data);}else warnings.add(Lang.string("worldbinder.schematic.biomes_missing"));
        warnings.add(Lang.string("worldbinder.schematic.extra_data"));
        if(!scene.mapIds.isEmpty())warnings.add(Lang.string("worldbinder.schematic.maps",scene.mapIds.size()));
        if(scene.blocks.stream().anyMatch(b->b.hasBlockEntity&&b.blockEntityNbt==null))warnings.add(Lang.string("worldbinder.schematic.block_entities_missing"));
        long unknownContainers=scene.blocks.stream().filter(b->net.worldbinder.validation.ExportValidator.isContainer(ExportCompatibility.id(b.state))&&!scene.containers.containsKey(ContainerRecord.blockKey(scene.originX+b.x,scene.originY+b.y,scene.originZ+b.z))).count()
                +scene.entities.stream().filter(e->net.worldbinder.validation.ExportValidator.isContainer(e.type)&&!scene.containers.containsKey(ContainerRecord.entityKey(e.uuid))).count();
        metadata.putLong("WorldBinderUnknownContainers",unknownContainers);
        if(unknownContainers>0)warnings.add(Lang.string("worldbinder.schematic.unknown_containers",unknownContainers));
        var root=new CompoundTag();root.put("Schematic",schematic);Files.createDirectories(target.toAbsolutePath().getParent());Path temp=target.resolveSibling(target.getFileName()+".tmp-"+UUID.randomUUID());
        NbtIo.writeCompressed(root,temp);
        var read=NbtIo.readCompressed(temp,NbtAccounter.unlimitedHeap());if(!root.equals(read))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.schematic.readback_failed"));
        String report=Lang.string("worldbinder.schematic.report",bounds.width(),bounds.height(),bounds.length(),blockEntities.size(),entities.size())+"\n\n"+String.join("\n",warnings)+"\n"+ExportLegalNotice.text();
        Files.writeString(temp.resolveSibling(temp.getFileName()+".txt"),report);
        Files.move(temp,target); // No REPLACE_EXISTING: a racing export must not overwrite a complete file.
        Files.move(temp.resolveSibling(temp.getFileName()+".txt"),target.resolveSibling(target.getFileName()+".txt"));
        return new Result(target,bounds.volume(),blockEntities.size(),entities.size(),warnings);
    }
    private static byte[] varints(int[] values){var output=new ByteArrayOutputStream(values.length);for(int value:values){do{int bits=value&127;value>>>=7;output.write(bits|(value==0?0:128));}while(value!=0);}return output.toByteArray();}
    private static void relocate(CompoundTag data,CaptureBounds bounds){
        data.remove("UUID");data.remove("UUIDMost");data.remove("UUIDLeast");
        if(data.get("Pos") instanceof ListTag pos&&pos.size()==3){var moved=new ListTag();moved.add(DoubleTag.valueOf(pos.getDouble(0).orElseThrow()-bounds.minX()));moved.add(DoubleTag.valueOf(pos.getDouble(1).orElseThrow()-bounds.minY()));moved.add(DoubleTag.valueOf(pos.getDouble(2).orElseThrow()-bounds.minZ()));data.put("Pos",moved);}
        int[] anchor=data.getIntArray("block_pos").orElse(null);if(anchor!=null&&anchor.length==3)data.putIntArray("block_pos",new int[]{anchor[0]-bounds.minX(),anchor[1]-bounds.minY(),anchor[2]-bounds.minZ()});
        for(var entry:Map.of("TileX",bounds.minX(),"TileY",bounds.minY(),"TileZ",bounds.minZ()).entrySet())if(data.getInt(entry.getKey()).isPresent())data.putInt(entry.getKey(),data.getInt(entry.getKey()).orElseThrow()-entry.getValue());
        if(data.get("Passengers") instanceof ListTag list)for(var child:list)if(child instanceof CompoundTag passenger)relocate(passenger,bounds);
    }
}
