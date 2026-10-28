package net.worldbinder.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.worldbinder.WorldBinder;
import net.worldbinder.client.WorldBinderClient;
import net.worldbinder.scene.*;
import net.worldbinder.export.common.*;
import net.worldbinder.util.BlockStateStrings;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Loads the real released target mods. Uses their readers, then pastes through WorldEdit into a native server world. */
final class SchematicImportClientTest {
    private static int checks;
    private static Class<?> type(String name)throws Exception{return Class.forName(name);}
    private static Object invoke(Object target,String method,Class<?>[] parameters,Object... values)throws Exception{return target.getClass().getMethod(method,parameters).invoke(target,values);}
    private static Object call(Object target,String method)throws Exception{return invoke(target,method,new Class<?>[0]);}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void run(ClientGameTestContext context){
        try{
            var fixture=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("worldbinder.importFixture")))).getAsJsonObject();
            Path file=Path.of(fixture.get("schematic").getAsString());WorldScene source=WorldBinderClient.scenes().read(Path.of(fixture.get("roaming").getAsString()));source.exportSettings.filters=new ExportFilterSettings();
            var nbt=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()).getCompound("Schematic").orElseThrow();
            int[] min=nbt.getCompound("Metadata").orElseThrow().getIntArray("WorldBinderSourceMin").orElseThrow();int width=nbt.getShort("Width").orElseThrow()&65535,height=nbt.getShort("Height").orElseThrow()&65535,length=nbt.getShort("Length").orElseThrow()&65535;
            Map<String,String> expected=new HashMap<>();for(var block:source.blocks){int x=source.originX+block.x-min[0],y=source.originY+block.y-min[1],z=source.originZ+block.z-min[2];if(x>=0&&x<width&&y>=0&&y<height&&z>=0&&z<length)expected.put(x+","+y+","+z,block.state);}
            var tsv=new StringBuilder("SIZE\t"+width+"\t"+height+"\t"+length+"\n");
            for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++)tsv.append("B\t").append(x).append('\t').append(y).append('\t').append(z).append('\t').append(expected.getOrDefault(x+","+y+","+z,"minecraft:air")).append('\n');
            for(var entry:source.containers.entrySet())if(entry.getKey().startsWith("block:")){
                var p=entry.getKey().substring(6).split(",");int x=Integer.parseInt(p[0])-min[0],y=Integer.parseInt(p[1])-min[1],z=Integer.parseInt(p[2])-min[2];if(x<0||x>=width||y<0||y>=height||z<0||z>=length)continue;
                var data=new CompoundTag();data.put("Items",ItemDataConverter.items(entry.getValue(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()));
                tsv.append("C\t").append(x).append('\t').append(y).append('\t').append(z).append('\t').append(Base64.getEncoder().encodeToString(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).append('\n');
            }
            for(var entity:source.entities){double x=source.originX+entity.x-min[0],y=source.originY+entity.y-min[1],z=source.originZ+entity.z-min[2];if(x<0||x>=width||y<0||y>=height||z<0||z>=length)continue;tsv.append("E\t").append(entity.type).append('\t').append(x).append('\t').append(y).append('\t').append(z).append('\n');}
            Files.writeString(net.worldbinder.io.WorldBinderPaths.GAME_DIR.resolve("expected.tsv"),tsv);
            try(var world=context.worldBuilder().create()){
                context.waitFor(client->client.player!=null&&client.level!=null);
                var future=new CompletableFuture<Void>();
                context.runOnClient(client->client.getSingleplayerServer().execute(()->{try{
                    var format=type("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats").getMethod("findByFile",java.io.File.class).invoke(null,file.toFile());check(format!=null,"WorldEdit recognizes the actual .schem format");
                    Object clipboard;try(var input=Files.newInputStream(file)){var reader=type("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat").getMethod("getReader",java.io.InputStream.class).invoke(format,input);try{clipboard=type("com.sk89q.worldedit.extent.clipboard.io.ClipboardReader").getMethod("read").invoke(reader);}finally{((AutoCloseable)reader).close();}}
                    var vectorType=type("com.sk89q.worldedit.math.BlockVector3");var at=vectorType.getMethod("at",int.class,int.class,int.class);var extent=type("com.sk89q.worldedit.extent.Extent");
                    for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++){
                        var block=extent.getMethod("getFullBlock",vectorType).invoke(clipboard,at.invoke(null,x,y,z));var state=call(block,"toImmutableState");String text=(String)call(state,"getAsString");
                        check(BlockStateStrings.parseStrict(text).equals(BlockStateStrings.parseStrict(expected.getOrDefault(x+","+y+","+z,"minecraft:air"))),"WorldEdit imported exact block state at "+x+","+y+","+z);
                    }
                    check(((List<?>)extent.getMethod("getEntities").invoke(clipboard)).size()==nbt.getList("Entities").orElseThrow().size(),"WorldEdit imported supported entities");
                    var adapter=type("com.sk89q.worldedit.fabric.FabricAdapter").getMethod("get").invoke(null);var serverLevel=client.getSingleplayerServer().overworld();var worldAdapter=invoke(adapter,"fromNativeWorld",new Class<?>[]{net.minecraft.world.level.Level.class},serverLevel);
                    var edit=type("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);var session=invoke(edit,"newEditSession",new Class<?>[]{type("com.sk89q.worldedit.world.World")},worldAdapter);
                    try{
                        var holder=type("com.sk89q.worldedit.session.ClipboardHolder").getConstructor(type("com.sk89q.worldedit.extent.clipboard.Clipboard")).newInstance(clipboard);
                        var paste=invoke(holder,"createPaste",new Class<?>[]{extent},session);invoke(paste,"to",new Class<?>[]{vectorType},at.invoke(null,32,90,32));invoke(paste,"copyEntities",new Class<?>[]{boolean.class},true);invoke(paste,"copyBiomes",new Class<?>[]{boolean.class},true);
                        var operation=call(paste,"build");type("com.sk89q.worldedit.function.operation.Operations").getMethod("complete",type("com.sk89q.worldedit.function.operation.Operation")).invoke(null,operation);
                    }finally{((AutoCloseable)session).close();}
                    for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++)check(serverLevel.getBlockState(new BlockPos(32+x,90+y,32+z)).equals(BlockStateStrings.parseStrict(expected.getOrDefault(x+","+y+","+z,"minecraft:air"))),"Native WorldEdit paste matches selected full block data");
                    for(var entry:source.containers.entrySet()){
                        if(!entry.getKey().startsWith("block:"))continue;var pos=entry.getKey().substring(6).split(",");int x=Integer.parseInt(pos[0])-min[0],y=Integer.parseInt(pos[1])-min[1],z=Integer.parseInt(pos[2])-min[2];if(x<0||x>=width||y<0||y>=height||z<0||z>=length)continue;
                        var blockEntity=serverLevel.getBlockEntity(new BlockPos(32+x,90+y,32+z));check(blockEntity!=null,"Pasted block entity exists");var tag=blockEntity.saveWithFullMetadata(serverLevel.registryAccess());
                        check(ItemDataConverter.items(entry.getValue(),net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()).equals(tag.get("Items")),"WorldEdit native paste preserves exact received slots, counts and item components");
                    }
                    future.complete(null);
                }catch(Throwable failure){future.completeExceptionally(failure);}}));
                context.waitFor(client->future.isDone());future.join();context.waitTicks(10);
                var entitiesChecked=new CompletableFuture<Void>();
                context.runOnClient(client->client.getSingleplayerServer().execute(()->{try{
                    var level=client.getSingleplayerServer().overworld();var actual=level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(32,90,32,32+width,90+height,32+length));
                    var wanted=source.entities.stream().filter(e->e.x+source.originX>=min[0]&&e.x+source.originX<min[0]+width&&e.y+source.originY>=min[1]&&e.y+source.originY<min[1]+height&&e.z+source.originZ>=min[2]&&e.z+source.originZ<min[2]+length).toList();
                    check(actual.size()==wanted.size(),"WorldEdit native paste retains all selected entities after world ticks");
                    for(var entity:wanted)check(actual.stream().anyMatch(e->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(entity.type)&&Math.abs(e.getX()-(32+source.originX+entity.x-min[0]))<.01&&Math.abs(e.getY()-(90+source.originY+entity.y-min[1]))<.01&&Math.abs(e.getZ()-(32+source.originZ+entity.z-min[2]))<.01),"WorldEdit pasted entity type/relative position, including hanging anchors");
                    entitiesChecked.complete(null);
                }catch(Throwable failure){entitiesChecked.completeExceptionally(failure);}}));
                context.waitFor(client->entitiesChecked.isDone());entitiesChecked.join();WorldBinder.LOGGER.info("WORLDBINDER_WORLDEDIT_NATIVE_IMPORT_PASS");context.takeScreenshot("worldedit-native-paste");
                context.runOnClient(client->{try{
                    var fileType=type("fi.dy.masa.litematica.util.FileType");var sponge=fileType.getField("SPONGE_SCHEMATIC").get(null);
                    var schematic=type("fi.dy.masa.litematica.schematic.LitematicaSchematic").getMethod("createFromFile",Path.class,String.class,fileType).invoke(null,file.getParent(),file.getFileName().toString(),sponge);check(schematic!=null,"Litematica imports the actual Sponge v3 file");
                    var areas=(Map<?,?>)call(schematic,"getAreaSizes");check(areas.size()==1,"Litematica imported one selected region");String region=areas.keySet().iterator().next().toString();var container=invoke(schematic,"getSubRegionContainer",new Class<?>[]{String.class},region);
                    for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++)check(invoke(container,"get",new Class<?>[]{int.class,int.class,int.class},x,y,z).equals(BlockStateStrings.parseStrict(expected.getOrDefault(x+","+y+","+z,"minecraft:air"))),"Litematica imported exact state including received air and hidden blocks");
                    var blockEntities=(Map<?,?>)invoke(schematic,"getBlockEntityMapForRegion",new Class<?>[]{String.class},region);check(blockEntities.size()==nbt.getCompound("Blocks").orElseThrow().getList("BlockEntities").orElseThrow().size(),"Litematica imported the expected block entities");
                    var entities=(List<?>)invoke(schematic,"getEntityListForRegion",new Class<?>[]{String.class},region);check(entities.size()==nbt.getList("Entities").orElseThrow().size(),"Litematica imported supported entities and relative positions");
                    for(var expectedEntity:source.entities){double x=source.originX+expectedEntity.x-min[0],y=source.originY+expectedEntity.y-min[1],z=source.originZ+expectedEntity.z-min[2];if(x<0||x>=width||y<0||y>=height||z<0||z>=length)continue;boolean found=false;for(var imported:entities){var pos=(net.minecraft.world.phys.Vec3)call(imported,"posVec");var data=call(imported,"nbt");if(invoke(data,"getString",new Class<?>[]{String.class},"id").equals(expectedEntity.type)&&Math.abs(pos.x-x)<1e-6&&Math.abs(pos.y-y)<1e-6&&Math.abs(pos.z-z)<1e-6)found=true;}check(found,"Litematica imported exact entity type and relative position");}
                    Object shulker=blockEntities.get(new BlockPos(2-min[0],64-min[1],3-min[2]));check(shulker!=null,"Litematica imported the received shulker data");var items=invoke(shulker,"getList",new Class<?>[]{String.class},"Items");var item=invoke(items,"getCompoundAt",new Class<?>[]{int.class},0);check(invoke(item,"getString",new Class<?>[]{String.class},"id").equals("minecraft:diamond_pickaxe"),"Litematica item ID retained");var components=invoke(item,"getCompound",new Class<?>[]{String.class},"components");check(((Number)invoke(components,"getInt",new Class<?>[]{String.class},"minecraft:damage")).intValue()==5,"Litematica full item damage component retained");
                    check(((Number)invoke(item,"getByte",new Class<?>[]{String.class},"Slot")).intValue()==2&&((Number)invoke(item,"getInt",new Class<?>[]{String.class},"count")).intValue()==1,"Litematica item slot and count retained");
                    check(invoke(components,"getString",new Class<?>[]{String.class},"minecraft:custom_name").equals("Schematic Tool"),"Litematica custom item name component retained");
                }catch(Exception failure){throw new AssertionError(failure);}});
            }
            Files.writeString(net.worldbinder.io.WorldBinderPaths.GAME_DIR.resolve("IMPORT-RESULT.txt"),"PASS WorldEdit 7.4.6 actual reader/native paste; Litematica 0.29.1 + MaLiLib 0.30.2 actual importer; "+checks+" checks\n");WorldBinder.LOGGER.info("WORLDBINDER_SCHEMATIC_IMPORT_PASS {} checks",checks);
        }catch(Exception failure){throw new AssertionError("Real schematic imports",failure);}
    }
}
