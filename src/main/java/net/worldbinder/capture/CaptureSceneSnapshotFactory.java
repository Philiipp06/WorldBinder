package net.worldbinder.capture;

import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.BlockRecordChunkCache;
import net.worldbinder.version.TargetMinecraftVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

final class CaptureSceneSnapshotFactory {
    private CaptureSceneSnapshotFactory() {
    }

    static WorldScene copyForStorage(WorldScene source, boolean completedOnly,
                                     BlockRecordChunkCache blockCache,
                                     Map<Long, List<BlockRecord>> stagedBlocks,
                                     Map<Long, List<BlockRecord>> hotBlocks,
                                     Map<Long, Map<String, EntityRecord>> hotEntities,
                                     LongPredicate exportableChunk) {
        WorldScene copy = copyMetadata(source, blockCache);
        copy.blocks = new ArrayList<>();
        Set<Long> blockPayloadChunks = new LinkedHashSet<>();
        Set<Long> cachedChunks = blockCache == null ? Collections.emptySet() : blockCache.chunkKeys();

        if (source.blocks != null) {
            for (BlockRecord block : source.blocks) {
                long key = block == null ? Long.MIN_VALUE : blockChunkKey(source, block);
                if (block != null && (!completedOnly || exportableChunk.test(key)) && !cachedChunks.contains(key)) {
                    copy.blocks.add(block);
                    blockPayloadChunks.add(key);
                }
            }
        }
        if (blockCache != null) {
            blockCache.readInto(copy.blocks, key -> !completedOnly || exportableChunk.test(key), blockPayloadChunks);
        }
        appendBlocks(copy.blocks, blockPayloadChunks, stagedBlocks, completedOnly, exportableChunk);
        appendBlocks(copy.blocks, blockPayloadChunks, hotBlocks, completedOnly, exportableChunk);

        copy.entities = new ArrayList<>();
        if (source.entities != null) {
            for (EntityRecord entity : source.entities) {
                long key = entity == null ? Long.MIN_VALUE : entityChunkKey(source, entity);
                if (entity != null && (!completedOnly
                        || exportableChunk.test(key) && blockPayloadChunks.contains(key))) {
                    copy.entities.add(entity);
                }
            }
        }
        for (Map.Entry<Long, Map<String, EntityRecord>> entry : hotEntities.entrySet()) {
            if ((!completedOnly || exportableChunk.test(entry.getKey())
                    && blockPayloadChunks.contains(entry.getKey())) && entry.getValue() != null) {
                copy.entities.addAll(entry.getValue().values());
            }
        }

        copy.chunkSnapshots = new LinkedHashMap<>();
        if (source.chunkSnapshots != null) {
            for (Map.Entry<String, ChunkSnapshot> entry : source.chunkSnapshots.entrySet()) {
                ChunkSnapshot snapshot = entry.getValue();
                long key = snapshot == null ? Long.MIN_VALUE : ChunkPos.pack(snapshot.chunkX, snapshot.chunkZ);
                if (!completedOnly || exportableChunk.test(key)) {
                    copy.chunkSnapshots.put(entry.getKey(), snapshot);
                }
            }
        }
        return copy;
    }

    private static WorldScene copyMetadata(WorldScene source, BlockRecordChunkCache blockCache) {
        WorldScene copy = new WorldScene();
        copy.formatVersion = source.formatVersion;
        copy.archiveType = source.archiveType;
        copy.name = source.name;
        copy.createdAt = source.createdAt;
        copy.minecraftVersion = source.minecraftVersion;
        copy.targetMinecraftVersion = source.targetMinecraftVersion == null || source.targetMinecraftVersion.isBlank()
                ? TargetMinecraftVersion.normalize(WorldBinder.config().targetMinecraftVersion)
                : TargetMinecraftVersion.normalize(source.targetMinecraftVersion);
        copy.targetGenerationProfile = TargetMinecraftVersion.profileLabel(copy.targetMinecraftVersion);
        copy.dimension = source.dimension;
        copy.originX = source.originX;
        copy.originY = source.originY;
        copy.originZ = source.originZ;
        copy.hasPlayerSpawn = source.hasPlayerSpawn;
        copy.playerSpawnX = source.playerSpawnX;
        copy.playerSpawnY = source.playerSpawnY;
        copy.playerSpawnZ = source.playerSpawnZ;
        copy.playerSpawnYaw = source.playerSpawnYaw;
        copy.playerSpawnPitch = source.playerSpawnPitch;
        copy.sizeX = source.sizeX;
        copy.sizeY = source.sizeY;
        copy.sizeZ = source.sizeZ;
        copy.includesBlockEntityNbt = source.includesBlockEntityNbt;
        copy.includesEntityNbt = source.includesEntityNbt;
        copy.gameRulesNbt = source.gameRulesNbt;
        copy.includesMapData = source.includesMapData;
        copy.includesAdvancements = source.includesAdvancements;
        copy.includesStats = source.includesStats;
        copy.compressedZip = source.compressedZip;
        copy.chunkCacheFolder = blockCache == null ? source.chunkCacheFolder : cacheFolderName(blockCache);
        copy.mapIds = source.mapIds == null ? new ArrayList<>() : new ArrayList<>(source.mapIds);
        copy.storageNotes = source.storageNotes == null ? new ArrayList<>() : new ArrayList<>(source.storageNotes);
        return copy;
    }

    private static void appendBlocks(List<BlockRecord> target, Set<Long> payloadChunks,
                                     Map<Long, List<BlockRecord>> source, boolean completedOnly,
                                     LongPredicate exportableChunk) {
        for (Map.Entry<Long, List<BlockRecord>> entry : source.entrySet()) {
            if ((!completedOnly || exportableChunk.test(entry.getKey()))
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                target.addAll(entry.getValue());
                payloadChunks.add(entry.getKey());
            }
        }
    }

    private static long blockChunkKey(WorldScene scene, BlockRecord block) {
        return ChunkPos.pack((scene.originX + block.x) >> 4, (scene.originZ + block.z) >> 4);
    }

    private static long entityChunkKey(WorldScene scene, EntityRecord entity) {
        int worldX = scene.originX + (int) Math.floor(entity.x);
        int worldZ = scene.originZ + (int) Math.floor(entity.z);
        return ChunkPos.pack(worldX >> 4, worldZ >> 4);
    }

    private static String cacheFolderName(BlockRecordChunkCache cache) {
        return cache.root() == null || cache.root().getFileName() == null
                ? null : cache.root().getFileName().toString();
    }
}
