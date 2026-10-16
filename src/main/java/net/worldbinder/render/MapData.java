package net.worldbinder.render;

import net.worldbinder.capture.SceneCaptureService;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongFunction;

/** Combined map data for the live map, radar and export: captured chunks first, explored chunks second. */
public final class MapData {
    private MapData() {
    }

    public static LongFunction<ChunkSnapshot> lookup(Map<Long, ChunkSnapshot> captured) {
        MapExplorer explorer = MapExplorer.get();
        if (explorer.size() == 0) {
            return captured::get;
        }
        return key -> {
            ChunkSnapshot snapshot = captured.get(key);
            return snapshot != null ? snapshot : explorer.snapshot(key);
        };
    }

    public static LongFunction<ChunkSnapshot> lookup(SceneCaptureService capture) {
        return lookup(capture == null ? Map.of() : capture.chunkSnapshotsView());
    }

    /** Copy of every known chunk (explored first, captured overriding) for background export. */
    public static Map<Long, ChunkSnapshot> mergedCopy(Map<Long, ChunkSnapshot> captured) {
        Map<Long, ChunkSnapshot> merged = new HashMap<>(MapExplorer.get().view());
        merged.putAll(captured);
        return merged;
    }

    /** Chunk bounds {minX, minZ, maxX, maxZ} of all known data, or {@code null} when nothing is known. */
    public static int[] bounds(Map<Long, ChunkSnapshot> captured) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : captured.keySet()) {
            int cx = (int) key;
            int cz = (int) (key >> 32);
            minX = Math.min(minX, cx);
            minZ = Math.min(minZ, cz);
            maxX = Math.max(maxX, cx);
            maxZ = Math.max(maxZ, cz);
        }
        MapExplorer explorer = MapExplorer.get();
        if (explorer.hasBounds()) {
            minX = Math.min(minX, explorer.minChunkX());
            minZ = Math.min(minZ, explorer.minChunkZ());
            maxX = Math.max(maxX, explorer.maxChunkX());
            maxZ = Math.max(maxZ, explorer.maxChunkZ());
        }
        return minX > maxX ? null : new int[] {minX, minZ, maxX, maxZ};
    }
}
