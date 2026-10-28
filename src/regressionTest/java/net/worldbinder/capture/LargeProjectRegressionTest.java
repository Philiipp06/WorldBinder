package net.worldbinder.capture;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.worldbinder.WorldBinder;
import net.worldbinder.platform.*;
import net.worldbinder.scene.*;
import net.worldbinder.export.common.ExportCompatibility;
import net.worldbinder.export.common.ExportFiltering;
import net.worldbinder.validation.ExportValidator;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** A measured 10,000-chunk/2,560,000-block file workload, then resume in a separate JVM. No visual claim. */
public final class LargeProjectRegressionTest {
    private static long peakHeap, started;
    private static final Map<String,Long> timings=new LinkedHashMap<>();
    public static void main(String[] args) throws Exception {
        boolean resume=args.length>0&&args[0].equals("resume");Path base=Path.of(".").toAbsolutePath().normalize();Files.createDirectories(base);
        Path root;
        if(resume)root=Path.of(Files.readString(base.resolve("latest.txt")));
        else {root=base.resolve("run-"+System.currentTimeMillis());Files.createDirectories(root);Files.writeString(base.resolve("latest.txt"),root.toString());}
        Path profile=root;
        Platform.install(new ClientPlatform(){
            public Path gameDirectory(){return profile;}public Path configDirectory(){return profile.resolve("config");}public boolean modLoaded(String id){return false;}
            public KeyMapping registerKey(KeyMapping key){return key;}public void endTick(Consumer<Minecraft> task){}public void hud(BiConsumer<GuiGraphicsExtractor,DeltaTracker> task){}
            public void joined(Runnable task){}public void disconnected(Runnable task){}
        });
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();WorldBinder.init();WorldBinder.config().includeServerResourcePack=false;WorldBinder.config().zipWorldExport=false;
        var monitor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"QA heap monitor");t.setDaemon(true);return t;});
        monitor.scheduleAtFixedRate(()->peakHeap=Math.max(peakHeap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()),0,100,java.util.concurrent.TimeUnit.MILLISECONDS);
        started=System.nanoTime();var library=new SceneLibrary();Path previous=root.resolve("world");
        if(!resume){
            var scene=new WorldScene();scene.name="QA 10000 chunks";scene.worldIdentity="qa-large";scene.dimension="minecraft:overworld";scene.minecraftVersion="26.3";scene.targetMinecraftVersion="26.3";
            scene.originY=64;scene.sizeX=1600;scene.sizeY=1;scene.sizeZ=1600;
            var store=new CaptureBlockStore();store.start("large");
            for(int z=0;z<100;z++)for(int x=0;x<100;x++){
                long key=ProjectData.chunkKey(x,z);var snap=snapshot(x,z);
                for(int lz=0;lz<16;lz++)for(int lx=0;lx<16;lx++){
                    store.stage(key,new BlockRecord(x*16+lx,0,z*16+lz,"minecraft:stone",false,null));snap.sample(lx,lz,64,"minecraft:stone",0xff777777);
                }
                scene.chunkSnapshots.put(x+","+z,snap);store.commit(key,scene,snap);
                if((z*100+x)%128==0)store.cache().flushWrites();
            }
            measure("captureToDurableCache");
            var frozen=CaptureSceneSnapshotFactory.copyForStorage(scene,true,store.cache(),Map.of(),store.hotChunks(),Map.of(),key->true);measure("freeze");
            require(frozen.blocks.size()==2_560_000,"Large capture block count");review(frozen);ExportCompatibility.analyze(frozen);measure("exportPreview");
            var barrier=store.cache().snapshotAsync();measure("queueRecoveryBarrier");
            var recovery=CaptureSceneSnapshotFactory.copyFromCommitted(scene,barrier.join(),store.hotChunks(),Map.of());measure("recoveryPreparation");
            require(recovery.blocks.size()==2_560_000,"Large recovery barrier retained every committed chunk");recovery=null;
            save(library,frozen,previous);measure("saveAndReadback");require(ExportValidator.validate(frozen,previous).filesValid,"Large export read-back");measure("additionalReadback");store.clear();
        } else {
            Path original=previous.resolve("worldbinder/worldbinder_archive.json");String hash=hash(original);
            var scene=library.read(previous);measure("restartRead");require(scene.blocks.size()==2_560_000&&scene.chunkSnapshots.size()==10000,"Process restart restored complete project");
            var store=new CaptureBlockStore();store.resume(scene,"large-resume");var update=snapshot(0,0);
            store.stage(0L,new BlockRecord(0,0,0,"minecraft:dirt",false,null));store.commit(0L,scene,update);scene.chunkSnapshots.put("0,0",update);measure("resumeAndUpdate");
            var frozen=CaptureSceneSnapshotFactory.copyForStorage(scene,true,store.cache(),Map.of(),store.hotChunks(),Map.of(),key->true);measure("freeze");
            require(frozen.blocks.size()==2_560_000-255,"Large update includes removed blocks");review(frozen);ExportCompatibility.analyze(frozen);measure("exportPreview");
            Path next=save(library,frozen,previous);measure("saveAndReadback");require(!next.equals(previous)&&hash.equals(hash(original)),"Previous large project retained");store.clear();
        }
        monitor.shutdown();var result=new LinkedHashMap<String,Object>();result.put("phase",resume?"separateJvmResume":"capture");result.put("chunks",10000);result.put("initialBlocks",2560000);result.put("stageMilliseconds",timings);result.put("peakObservedHeapMiB",peakHeap/(1024*1024));result.put("maxHeapMiB",Runtime.getRuntime().maxMemory()/(1024*1024));result.put("status","PASS");result.put("scope","Synthetic durable capture/export workload; no client rendering or server-session longevity claim.");
        Files.writeString(root.resolve(resume?"resume-RESULT.json":"capture-RESULT.json"),WorldBinder.GSON.toJson(result));System.out.println("WORLDBINDER_LARGE_PROJECT_PASS "+WorldBinder.GSON.toJson(result));
    }
    private static ChunkSnapshot snapshot(int x,int z){var snap=new ChunkSnapshot(x,z);snap.captureMinY=64;snap.captureMaxY=64;snap.fullChunkColumns=true;snap.lightEstimated=false;snap.hasBiomeData=true;snap.markDone();var section=new ChunkSectionData();section.biomes=new String[64];Arrays.fill(section.biomes,"minecraft:plains");section.blockLight=new byte[2048];section.skyLight=new byte[2048];Arrays.fill(section.skyLight,(byte)255);snap.sections.put(4,section);return snap;}
    private static Path save(SceneLibrary library,WorldScene scene,Path target)throws Exception{
        var progress=new net.worldbinder.storage.StorageProgress();progress.start(target,scene,false);Path saved=library.saveWorldFolder(scene,target,progress);progress.finish(saved);
        net.worldbinder.profiling.StorageProfiler.stageTimesSnapshot().forEach((stage,time)->timings.put("storage."+stage.name(),time));return saved;
    }
    private static void measure(String stage){long now=System.nanoTime();long ms=(now-started)/1_000_000;timings.put(stage,ms);started=now;System.out.println("LARGE_STAGE "+stage+" "+ms+" ms");}
    private static String hash(Path path)throws Exception{var sha=java.security.MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] data=new byte[65536];for(int n;(n=in.read(data))>=0;)sha.update(data,0,n);}return java.util.HexFormat.of().formatHex(sha.digest());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void review(WorldScene scene)throws Exception{var method=net.worldbinder.ui.WorldBinderExportScreen.class.getDeclaredMethod("reviewCopy",WorldScene.class);method.setAccessible(true);long start=System.nanoTime();var copy=(WorldScene)method.invoke(null,scene);long ms=(System.nanoTime()-start)/1_000_000;timings.put("clientPreviewPreparation",ms);require(copy.blocks.size()==scene.blocks.size()&&ms<1000,"Large preview preparation must stay below one second and retain every block");ExportFiltering.count(copy,copy.exportSettings.filters);}
}
