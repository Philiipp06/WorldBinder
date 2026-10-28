package net.worldbinder.capture;

import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.BlockRecordChunkCache;
import net.worldbinder.util.FileNames;
import net.worldbinder.util.Lang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CaptureBlockStore {
    private final Map<Long, List<BlockRecord>> hotChunks = new LinkedHashMap<>();
    private final Map<Long, List<BlockRecord>> stagedChunks = new LinkedHashMap<>();
    private final Set<Long> cachedPayloadChunks = new LinkedHashSet<>();
    private final Map<Long, List<int[]>> ranges = new LinkedHashMap<>();
    private final Map<Long, List<BlockRecord>> baseline = new LinkedHashMap<>();
    private final Map<Long, Integer> committedSizes = new LinkedHashMap<>();
    private BlockRecordChunkCache cache;
    private int recordCount;

    String start(String archiveName) {
        clear();
        cache = createCache(archiveName);
        return cacheFolderName();
    }

    String resume(WorldScene scene, String archiveName) {
        clear();
        for (BlockRecord block : scene.blocks) baseline.computeIfAbsent(net.worldbinder.scene.ProjectData.chunkKey(scene, block), k -> new ArrayList<>()).add(block);
        Path existing = resolveRecoveryCacheRoot(scene);
        if (existing != null && Files.isDirectory(existing)) {
            try {
                cache = new BlockRecordChunkCache(existing);
            } catch (IOException exception) {
                WorldBinder.LOGGER.warn(
                        Lang.string("worldbinder.log.chunk_cache.recovery_open_failed", existing.getFileName()),
                        exception);
            }
        }
        if (cache == null) {
            cache = createCache(archiveName);
        }
        return cacheFolderName();
    }

    void stage(long chunkKey, BlockRecord record) {
        stagedChunks.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(record);
    }

    boolean commit(long chunkKey, WorldScene scene, net.worldbinder.scene.ChunkSnapshot snapshot) {
        List<BlockRecord> staged = stagedChunks.remove(chunkKey);
        if (staged == null) staged = new ArrayList<>();
        if (cachedPayloadChunks.contains(chunkKey)) {
            try {
                List<BlockRecord> previous = hotChunks.containsKey(chunkKey) ? hotChunks.get(chunkKey) : cache.readChunk(chunkKey);
                staged = net.worldbinder.scene.ProjectData.mergeChunk(scene, previous, staged, snapshot);
            } catch (IOException e) { throw new net.worldbinder.validation.FindingStateException(net.worldbinder.validation.Finding.of("worldbinder.error.retain_committed_chunk"), e); }
        } else if (baseline.containsKey(chunkKey)) staged = net.worldbinder.scene.ProjectData.mergeChunk(scene, baseline.get(chunkKey), staged, snapshot);
        List<int[]> coverage = ranges.computeIfAbsent(chunkKey, k -> new ArrayList<>());
        if(snapshot.captureBox==null)coverage.add(new int[]{snapshot.captureMinY, snapshot.captureMaxY});
        snapshot.replacementRanges = new ArrayList<>(coverage);
        var priorSnapshot=scene.chunkSnapshots.get(ChunkPos.getX(chunkKey)+","+ChunkPos.getZ(chunkKey));
        scene.recordedChunkChanges.putIfAbsent(ChunkPos.getX(chunkKey)+","+ChunkPos.getZ(chunkKey),priorSnapshot!=null&&priorSnapshot.isDone()||baseline.containsKey(chunkKey));
        if(priorSnapshot!=null&&priorSnapshot.coverageBoxes!=null)for(var box:priorSnapshot.coverageBoxes)snapshot.coverageBoxes.add(box.clone());
        if(priorSnapshot!=null&&priorSnapshot.fullChunkColumns){
            snapshot.coverageBoxes.add(new int[]{ChunkPos.getX(chunkKey)*16,priorSnapshot.captureMinY,ChunkPos.getZ(chunkKey)*16,ChunkPos.getX(chunkKey)*16+15,priorSnapshot.captureMaxY,ChunkPos.getZ(chunkKey)*16+15});
            if(priorSnapshot.replacementRanges!=null)for(var range:priorSnapshot.replacementRanges)if(range.length==2)snapshot.coverageBoxes.add(new int[]{ChunkPos.getX(chunkKey)*16,range[0],ChunkPos.getZ(chunkKey)*16,ChunkPos.getX(chunkKey)*16+15,range[1],ChunkPos.getZ(chunkKey)*16+15});
        }
        if(snapshot.captureBox!=null)snapshot.coverageBoxes.add(snapshot.captureBox.clone());
        mergeMapPreview(scene,chunkKey,snapshot,staged);
        invalidateRemovedContainers(scene, chunkKey, staged, snapshot);
        recordCount -= committedSizes.getOrDefault(chunkKey, 0);
        committedSizes.put(chunkKey, staged.size());
        if (cache != null) {
            try {
                cache.writeChunk(chunkKey, staged);
                hotChunks.remove(chunkKey);
                cachedPayloadChunks.add(chunkKey);
                recordCount += staged.size();
                return true;
            } catch (IOException exception) {
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.commit_failed",
                        ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)), exception);
            }
        }
        hotChunks.put(chunkKey, staged);
        cachedPayloadChunks.add(chunkKey);
        recordCount += staged.size();
        return true;
    }

    void discard(long chunkKey) {
        stagedChunks.remove(chunkKey);
    }

    void clear() {
        if (cache != null) cache.stopWriter();
        stagedChunks.clear();
        hotChunks.clear();
        cachedPayloadChunks.clear();
        ranges.clear();
        baseline.clear();
        committedSizes.clear();
        cache = null;
        recordCount = 0;
    }

    int recordCount() {
        return recordCount;
    }

    int replacedBaselineCount() {
        int count = 0;
        for (long key : committedSizes.keySet()) count += baseline.getOrDefault(key, List.of()).size();
        return count;
    }

    private void invalidateRemovedContainers(WorldScene scene, long key, List<BlockRecord> current, net.worldbinder.scene.ChunkSnapshot snapshot) {
        Map<String, String> present = new LinkedHashMap<>();
        for (BlockRecord block : current) if (block.hasBlockEntity) present.put(net.worldbinder.scene.ContainerRecord.blockKey(
                scene.originX+block.x, scene.originY+block.y, scene.originZ+block.z), net.worldbinder.export.common.ExportCompatibility.id(block.state));
        scene.containers.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith("block:")) return false;
            String[] parts = entry.getKey().substring(6).split(",");
            if (parts.length != 3) return false;
            int x=Integer.parseInt(parts[0]),y=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);
            if(snapshot.captureBox!=null&&!net.worldbinder.selection.CaptureBounds.from(snapshot.captureBox).contains(x,y,z))return false;
            return net.worldbinder.scene.ProjectData.chunkKey(x>>4,z>>4)==key && y>=snapshot.captureMinY && y<=snapshot.captureMaxY
                    && !entry.getValue().type.equals(present.get(entry.getKey()));
        });
    }

    private void mergeMapPreview(WorldScene scene,long key,net.worldbinder.scene.ChunkSnapshot snapshot,List<BlockRecord> records) {
        var before=scene.chunkSnapshots.get(ChunkPos.getX(key)+","+ChunkPos.getZ(key));
        if(before!=null)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(before.hasSample(x,z)){
            int y=before.heights[z*16+x];
            if(y<snapshot.captureMinY||y>snapshot.captureMaxY)snapshot.sample(x,z,y,null,before.colorAt(x,z));
        }
        BlockRecord[] top=new BlockRecord[256];
        Map<String,net.minecraft.world.level.block.state.BlockState> states=new LinkedHashMap<>();
        var client=net.minecraft.client.Minecraft.getInstance();
        var cursor=new net.minecraft.core.BlockPos.MutableBlockPos();
        for(BlockRecord block:records){
            if(block.state.equals("minecraft:air")||block.state.equals("minecraft:cave_air")||block.state.equals("minecraft:void_air"))continue;
            snapshot.verifiedEmpty=false;
            try{
                var state=states.computeIfAbsent(block.state,net.worldbinder.util.BlockStateStrings::parse);
                cursor.set(scene.originX+block.x,scene.originY+block.y,scene.originZ+block.z);
                if(net.worldbinder.render.BlockMapColors.markerColor(state)!=0||client!=null&&client.level!=null
                        &&net.worldbinder.render.BlockMapColors.isMapTransparent(state,client.level,cursor))continue;
            }catch(RuntimeException ignored){}
            int index=((scene.originZ+block.z)&15)*16+((scene.originX+block.x)&15);
            if(top[index]==null||block.y>top[index].y)top[index]=block;
        }
        for(int i=0;i<256;i++)if(top[i]!=null){
            BlockRecord block=top[i];int y=scene.originY+block.y;
            if(snapshot.hasSample(i&15,i>>4)&&snapshot.heights[i]>=y)continue;
            int color=CaptureBlockScanner.blockPreviewColor(block.state);
            try{var state=net.worldbinder.util.BlockStateStrings.parse(block.state);color=CaptureBlockScanner.blockPreviewColor(state,
                    new net.minecraft.core.BlockPos(scene.originX+block.x,y,scene.originZ+block.z),block.state);}catch(RuntimeException ignored){}
            snapshot.sample(i&15,i>>4,y,block.state,color);
        }
    }

    BlockRecordChunkCache cache() {
        return cache;
    }

    Map<Long, List<BlockRecord>> stagedChunks() {
        return stagedChunks;
    }

    Map<Long, List<BlockRecord>> hotChunks() {
        return hotChunks;
    }

    private String cacheFolderName() {
        return cache == null || cache.root() == null || cache.root().getFileName() == null
                ? null : cache.root().getFileName().toString();
    }

    private BlockRecordChunkCache createCache(String archiveName) {
        try {
            WorldBinderPaths.ensureBaseFolders();
            String baseName = FileNames.cleanBaseName(
                    archiveName == null || archiveName.isBlank() ? "capture" : archiveName);
            Path root = WorldBinderPaths.CACHE_ROOT.resolve(baseName + "_" + System.currentTimeMillis());
            return new BlockRecordChunkCache(root);
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.fallback_memory"), exception);
            return null;
        }
    }

    private Path resolveRecoveryCacheRoot(WorldScene scene) {
        if (scene == null || scene.chunkCacheFolder == null || scene.chunkCacheFolder.isBlank()) {
            return null;
        }
        String name = scene.chunkCacheFolder.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9_.-]", "");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return null;
        }
        return WorldBinderPaths.CACHE_ROOT.resolve(name).normalize();
    }
}
