package net.worldbinder.capture;

import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.server.Bootstrap;
import net.worldbinder.WorldBinder;
import net.worldbinder.platform.*;
import net.worldbinder.profiling.StorageProfiler;
import net.worldbinder.scene.*;
import net.worldbinder.storage.StorageProgress;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Repeatable full archive + world + read-back workload. This is not an in-game benchmark. */
public final class ExportPerformanceTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("run-" + System.currentTimeMillis()).toAbsolutePath();
        Files.createDirectories(root);
        Platform.install(new ClientPlatform() {
            public Path gameDirectory(){return root;} public Path configDirectory(){return root.resolve("config");}
            public boolean modLoaded(String id){return false;} public KeyMapping registerKey(KeyMapping key){return key;}
            public void endTick(Consumer<Minecraft> task){} public void hud(BiConsumer<GuiGraphicsExtractor,DeltaTracker> task){}
            public void joined(Runnable task){} public void disconnected(Runnable task){}
        });
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); WorldBinder.init();
        WorldBinder.config().zipWorldExport=false; WorldBinder.config().includeServerResourcePack=false;
        int chunks = args.length == 0 ? 512 : Integer.parseInt(args[0]);
        WorldScene scene = fixture(chunks);
        var library = new SceneLibrary();
        var results = new ArrayList<Map<String,Object>>();
        for(int pass=0;pass<2;pass++) {
            var progress=new StorageProgress(); Path target=root.resolve("world-"+pass); progress.start(target);
            long start=System.nanoTime(); library.saveWorldFolder(scene,target,progress); progress.finish(target);
            long duration=(System.nanoTime()-start)/1_000_000;
            var result=new LinkedHashMap<String,Object>(); result.put("pass",pass); result.put("chunks",chunks);
            result.put("blocks",scene.blockCount()); result.put("milliseconds",duration);
            result.put("stageMilliseconds",StorageProfiler.stageTimesSnapshot());
            results.add(result); System.out.println("EXPORT_BENCHMARK "+WorldBinder.GSON.toJson(result));
        }
        Files.writeString(root.resolve("RESULT.json"),WorldBinder.GSON.toJson(results));
        Files.writeString(root.getParent().resolve("latest.txt"),root.toString());
    }

    private static WorldScene fixture(int count) {
        var scene=new WorldScene(); scene.name="Export performance"; scene.worldIdentity="qa-export-performance";
        scene.dimension="minecraft:overworld"; scene.minecraftVersion="26.3"; scene.targetMinecraftVersion="26.3";
        scene.originY=64; scene.sizeX=32*16; scene.sizeY=16; scene.sizeZ=((count+31)/32)*16;
        String[] states={"minecraft:stone","minecraft:dirt","minecraft:glass","minecraft:oak_planks","minecraft:glowstone"};
        for(int n=0;n<count;n++) {
            int cx=n%32,cz=n/32; var snap=new ChunkSnapshot(cx,cz);
            snap.captureMinY=64;snap.captureMaxY=79;snap.fullChunkColumns=true;snap.hasBiomeData=true;snap.lightEstimated=false;snap.markDone();
            var section=new ChunkSectionData();section.biomes=new String[64];Arrays.fill(section.biomes,"minecraft:forest");
            section.blockLight=new byte[2048];section.skyLight=new byte[2048];Arrays.fill(section.skyLight,(byte)255);snap.sections.put(4,section);
            scene.chunkSnapshots.put(cx+","+cz,snap);
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)
                scene.blocks.add(new BlockRecord(cx*16+x,y,cz*16+z,states[(x+z+y)%states.length],false,null));
        }
        return scene;
    }
}
