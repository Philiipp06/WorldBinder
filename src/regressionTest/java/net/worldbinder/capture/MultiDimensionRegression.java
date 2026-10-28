package net.worldbinder.capture;

import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import net.worldbinder.export.*;
import net.worldbinder.export.common.*;
import net.worldbinder.validation.*;
import java.nio.file.*;
import java.util.*;

/** Data/archive/file checks. Actual loader/proxy/replay playback is tested separately. */
final class MultiDimensionRegression {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static WorldScene fixture(){
        var root=ProjectRegressionTest.fixture();root.backendId=UUID.randomUUID().toString();root.backendName="Survival";
        var nether=ProjectRegressionTest.fixture();nether.dimension="minecraft:the_nether";nether.dimensionType=nether.dimension;nether.dimensionMinY=0;nether.dimensionHeight=256;
        nether.projectId=root.projectId;nether.backendId=root.backendId;nether.worldIdentity=root.worldIdentity;
        nether.blocks.set(0,new BlockRecord(1,64,1,"minecraft:gold_block",false,null));
        nether.maps.get(7).colors[0]=99;nether.maps.get(7).nbt=nether.maps.get(7).nbt.replace("overworld","the_nether");
        root.dimensions.put(nether.dimension,nether);return root;
    }
    static int run(Path folder)throws Exception{
        checks=0;var library=new SceneLibrary();var source=fixture();String before=WorldBinder.GSON.toJson(source);
        Path archive=folder.resolve("multi-dimension.json");library.save(source,archive);var loaded=library.read(archive);
        check(loaded.dimensions.size()==1&&ProjectDimensions.find(loaded,"minecraft:the_nether").blocks.getFirst().state.equals("minecraft:gold_block"),"Two dimensions with identical coordinates survive a complete archive round trip");
        var switched=ProjectDimensions.activate(loaded,"minecraft:the_nether",null);
        check(switched.dimension.equals("minecraft:the_nether")&&switched.dimensions.get("minecraft:overworld").blocks.getFirst().state.equals("minecraft:stone"),"Dimension activation reuses the correct old coordinate space");
        check(switched.dimensions.values().stream().allMatch(d->d.dimensions.isEmpty()),"Frozen dimensions remain flat and cannot form serialization cycles");
        check(RecoveryProjects.folder(folder,loaded).equals(RecoveryProjects.folder(folder,switched))&&RecoveryProjects.sameOwner(loaded,switched),"A multi-dimension backend has one durable project recovery identity");
        var lobby=fixture();lobby.projectId=loaded.projectId;lobby.backendId=UUID.randomUUID().toString();lobby.backendName="Lobby";lobby.dimensions.clear();
        check(!RecoveryProjects.folder(folder,loaded).equals(RecoveryProjects.folder(folder,lobby))&&!RecoveryProjects.sameOwner(loaded,lobby),"Independent proxy backends never share recovery ownership even with equal project names and dimension IDs");
        var prepared=DimensionExportView.prepare(ExportFiltering.apply(loaded));var nether=prepared.dimensions.get("minecraft:the_nether");
        check(nether.mapIds.getFirst()!=7&&prepared.maps.size()==2&&prepared.maps.get(nether.mapIds.getFirst()).colors[0]==99,"Conflicting dimension map IDs are deterministically remapped with their received images");
        check(nether.containers.get("block:2,64,2").items.getFirst().contains("minecraft:map_id")&&!nether.containers.get("block:2,64,2").items.getFirst().contains("map_id\":7"),"Container map references follow the remapped dimension image");
        check(WorldBinder.GSON.toJson(source).equals(before),"Preparing the multi-dimension export leaves all original records byte-for-byte equivalent");
        var unknown=fixture();unknown.dimensions.get("minecraft:the_nether").maps.clear();var knownElsewhere=DimensionExportView.prepare(ExportFiltering.apply(unknown));
        check(knownElsewhere.dimensions.get("minecraft:the_nether").mapIds.getFirst()==7&&knownElsewhere.dimensions.get("minecraft:the_nether").maps.get(7).receivedPixels()==16384,"A map reference with no new image reuses the same backend's previously received image instead of becoming a missing remapped image");
        switched.player.enderChest.items.add("{Slot:0b,id:\"minecraft:emerald\",count:4}");var returned=ProjectDimensions.activate(switched,"minecraft:overworld",null);
        check(returned.player.enderChest.items.size()==1&&returned.player!=switched.player,"Returning dimensions keeps the latest received global player/ender chest data without mutating the frozen player object");
        var reviewMethod=net.worldbinder.ui.WorldBinderExportScreen.class.getDeclaredMethod("reviewCopy",WorldScene.class);reviewMethod.setAccessible(true);var review=(WorldScene)reviewMethod.invoke(null,source);
        check(review.dimensions.size()==1&&review.blocks==source.blocks,"Preparing a version/filter review keeps every dimension and shares immutable block payloads");
        for(String version:List.of("26.3","1.20.4","1.13.2")){
            loaded.targetMinecraftVersion=version;Path world=folder.resolve("multi-"+version);VanillaWorldExporter.export(loaded,world);AuxiliaryDataWriter.write(DimensionExportView.prepare(ExportFiltering.apply(loaded)),world);
            var report=ExportValidator.validate(loaded,world);
            check(report.filesValid&&report.expectedChunks==2&&report.chunks==2,"Read-back verifies both separate dimension files for "+version+": "+report.errors);
            Path region=world.resolve(version.equals("26.3")?"dimensions/minecraft/the_nether/region/r.0.0.mca":"DIM-1/region/r.0.0.mca");
            check(Files.isRegularFile(region),"The target version receives the correct Nether storage layout "+version);
            byte[] bytes=Files.readAllBytes(region);Files.delete(region);var broken=ExportValidator.validate(loaded,world);
            check(!broken.filesValid&&broken.errors.stream().anyMatch(f->f.toString().contains("the_nether")),"A missing secondary dimension is reported with its dimension instead of passing "+version);Files.write(region,bytes);
        }
        var area=AreaExport.crop(loaded,new net.worldbinder.selection.CaptureBounds(0,0,0,15,255,15));check(area.dimensions.isEmpty(),"An area export cannot leak sibling dimensions into the selected world or schematic");
        Path recovery=RecoveryProjects.folder(folder,loaded);library.saveRecoverySnapshot(loaded,recovery);byte[] saved=Files.readAllBytes(recovery.resolve("worldbinder/worldbinder_archive.json"));
        var corrupt=WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(loaded),WorldScene.class);corrupt.dimensions.get("minecraft:the_nether").backendId="foreign";
        boolean rejected=false;try{library.saveRecoverySnapshot(corrupt,recovery);}catch(java.io.IOException expected){rejected=true;}
        check(rejected&&Arrays.equals(saved,Files.readAllBytes(recovery.resolve("worldbinder/worldbinder_archive.json"))),"An invalid child backend cannot replace the previous complete recovery");
        var custom=fixture();var child=custom.dimensions.remove("minecraft:the_nether");child.dimension="worldbinder:received_custom";child.dimensionType="minecraft:the_nether";custom.dimensions.put(child.dimension,child);custom.targetMinecraftVersion="1.20.4";
        Path world=folder.resolve("multi-custom");VanillaWorldExporter.export(custom,world);AuxiliaryDataWriter.write(DimensionExportView.prepare(ExportFiltering.apply(custom)),world);
        check(Files.isRegularFile(world.resolve("dimensions/worldbinder/received_custom/region/r.0.0.mca"))&&ExportValidator.validate(custom,world).filesValid,"A custom dimension using a received vanilla type exports into its own namespace path");
        return checks;
    }
}
