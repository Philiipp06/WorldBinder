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
    private BlockRecordChunkCache cache;
    private int recordCount;

    String start(String archiveName) {
        clear();
        cache = createCache(archiveName);
        return cacheFolderName();
    }

    String resume(WorldScene scene, String archiveName) {
        clear();
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

    boolean commit(long chunkKey) {
        List<BlockRecord> staged = stagedChunks.remove(chunkKey);
        if (staged == null || staged.isEmpty()) {
            return cachedPayloadChunks.contains(chunkKey) || hotChunks.containsKey(chunkKey)
                    || cache != null && cache.hasChunk(chunkKey);
        }
        if (cache != null) {
            try {
                cache.writeChunk(chunkKey, staged);
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
        stagedChunks.clear();
        hotChunks.clear();
        cachedPayloadChunks.clear();
        cache = null;
        recordCount = 0;
    }

    int recordCount() {
        return recordCount;
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
