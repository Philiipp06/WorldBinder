package net.worldbinder.capture;

import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import net.worldbinder.storage.BlockRecordChunkCache;
import java.util.*;
import java.util.function.LongPredicate;

final class CaptureSceneSnapshotFactory {
    private CaptureSceneSnapshotFactory() {}

    static WorldScene copyForStorage(WorldScene source, boolean completedOnly, BlockRecordChunkCache cache,
            Map<Long, List<BlockRecord>> staged, Map<Long, List<BlockRecord>> hot,
            Map<Long, Map<String, EntityRecord>> hotEntities, LongPredicate exportable) {
        Map<Long, List<BlockRecord>> committed = new LinkedHashMap<>();
        if (cache != null) {
            for (long key : cache.chunkKeys()) {
                try { committed.put(key, cache.readChunk(key)); }
                catch (java.io.IOException e) { throw new IllegalStateException(e.getMessage(), e); }
            }
            cache.verifyHealthy();
        }
        return copyFromCommitted(source,committed,hot,hotEntities);
    }

    static WorldScene copyFromCommitted(WorldScene source,Map<Long,List<BlockRecord>> cached,
            Map<Long,List<BlockRecord>> hot,Map<Long,Map<String,EntityRecord>> hotEntities) {
        WorldScene copy = ProjectData.metadataCopy(source);
        Map<Long, List<BlockRecord>> chunks = new LinkedHashMap<>();
        for (BlockRecord block : source.blocks) if (block != null)
            chunks.computeIfAbsent(ProjectData.chunkKey(source, block), k -> new ArrayList<>()).add(block);
        Map<Long,List<BlockRecord>> committed=new LinkedHashMap<>(cached);
        committed.putAll(hot);
        for (var entry : committed.entrySet()) {
            long key = entry.getKey();
            ChunkSnapshot snapshot = source.chunkSnapshots.get(ChunkPos.getX(key) + "," + ChunkPos.getZ(key));
            if (snapshot == null || !snapshot.isDone()) continue;
            chunks.put(key, ProjectData.mergeChunk(source, chunks.getOrDefault(key, List.of()), entry.getValue(), snapshot));
        }
        copy.blocks = new ArrayList<>();
        chunks.values().forEach(copy.blocks::addAll);
        // Entities are updated by UUID, not appended repeatedly on every resume.
        Map<String, EntityRecord> entities = new LinkedHashMap<>();
        for (EntityRecord entity : source.entities) if (entity != null) entities.put(entity.uuid, entity);
        for (var group : hotEntities.entrySet()) {
            if (source.chunkSnapshots.containsKey(ChunkPos.getX(group.getKey()) + "," + ChunkPos.getZ(group.getKey())))
                for (EntityRecord entity : group.getValue().values()) {
                    EntityRecord before = entities.get(entity.uuid);
                    if (before == null || entity.fullNbt != null && !entity.fullNbt.isBlank()) entities.put(entity.uuid, entity);
                }
        }
        copy.entities = new ArrayList<>(entities.values());
        copy.chunkSnapshots = new LinkedHashMap<>();
        for (var entry : source.chunkSnapshots.entrySet()) {
            ChunkSnapshot snapshot = entry.getValue();
            // The committed snapshot remains exportable even while its next revision is being scanned.
            if (snapshot != null)
                copy.chunkSnapshots.put(entry.getKey(), snapshot.copy());
        }
        copy.pendingChunks = source.pendingChunks == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.pendingChunks);
        // Unknown or partial staged scans never enter an exported chunk.
        copy.chunkCacheFolder = null;
        copy.updatedAt = java.time.Instant.now().toString();
        return copy;
    }
}
