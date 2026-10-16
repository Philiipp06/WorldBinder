package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.util.FileNames;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongToIntFunction;

/**
 * Exports the live map as a single PNG (downscaled when larger than {@link #MAX_SIDE}) or as 256x256 region
 * tiles named {@code r.<x>.<z>.png}, ready for web map viewers. Rendering runs on a background thread.
 */
public final class MapExporter {
    public static final int MAX_SIDE = 8192;

    public enum Mode { FULL, TILES }

    public record Result(Path path, int width, int height, int chunks, int files) {
    }

    private MapExporter() {
    }

    /**
     * @param statusColors optional per-chunk overlay (ARGB, alpha blended), evaluated on the client thread
     *                     before the export starts; {@code null} exports terrain only.
     */
    public static CompletableFuture<Result> export(Map<Long, ChunkSnapshot> captured, Mode mode, boolean hillshade,
                                                   LongToIntFunction statusColors) {
        Map<Long, ChunkSnapshot> merged = MapData.mergedCopy(captured);
        Map<Long, Integer> overlay = new java.util.HashMap<>();
        if (statusColors != null) {
            for (long key : merged.keySet()) {
                int color = statusColors.applyAsInt(key);
                if ((color >>> 24) != 0) overlay.put(key, color);
            }
        }
        String folderName = FileNames.cleanBaseName(worldName()) + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path root = WorldBinderPaths.RECOVERY_ROOT.resolve("map_exports").resolve(folderName);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(root);
                return mode == Mode.TILES ? exportTiles(merged, overlay, hillshade, root) : exportFull(merged, overlay, hillshade, root);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static Result exportFull(Map<Long, ChunkSnapshot> merged, Map<Long, Integer> overlay, boolean hillshade, Path root) throws IOException {
        int[] bounds = boundsOf(merged);
        if (bounds == null) {
            throw new IOException("No map data to export");
        }
        int blocksX = (bounds[2] - bounds[0] + 1) * 16;
        int blocksZ = (bounds[3] - bounds[1] + 1) * 16;
        int step = 1;
        while (blocksX / step > MAX_SIDE || blocksZ / step > MAX_SIDE) step *= 2;
        int width = Math.max(1, blocksX / step);
        int height = Math.max(1, blocksZ / step);
        int finalStep = step;
        Path file = root.resolve("map.png");
        try (NativeImage image = new NativeImage(width, height, true)) {
            for (Map.Entry<Long, ChunkSnapshot> entry : merged.entrySet()) {
                long key = entry.getKey();
                int cx = (int) key;
                int cz = (int) (key >> 32);
                int baseX = (cx - bounds[0]) * 16;
                int baseZ = (cz - bounds[1]) * 16;
                Integer tint = overlay.get(key);
                MapRegionTextures.renderChunk(entry.getValue(), merged::get, cx, cz, hillshade, (x, z, argb) -> {
                    int px = baseX + x;
                    int pz = baseZ + z;
                    if (px % finalStep != 0 || pz % finalStep != 0) return;
                    int ix = px / finalStep;
                    int iz = pz / finalStep;
                    if (ix < width && iz < height) image.setPixel(ix, iz, blend(argb, tint));
                });
            }
            image.writeToFile(file);
        }
        writeInfo(root, bounds, step, merged.size(), "map.png");
        return new Result(file, width, height, merged.size(), 1);
    }

    private static Result exportTiles(Map<Long, ChunkSnapshot> merged, Map<Long, Integer> overlay, boolean hillshade, Path root) throws IOException {
        Set<Long> regions = new HashSet<>();
        for (long key : merged.keySet()) {
            regions.add(MapRegionTextures.regionKey(((int) key) >> 4, ((int) (key >> 32)) >> 4));
        }
        Path tiles = root.resolve("tiles");
        Files.createDirectories(tiles);
        int files = 0;
        for (long regionKey : regions) {
            int rx = (int) regionKey;
            int rz = (int) (regionKey >> 32);
            try (NativeImage image = new NativeImage(MapRegionTextures.REGION_BLOCKS, MapRegionTextures.REGION_BLOCKS, true)) {
                for (int z = 0; z < MapRegionTextures.REGION_CHUNKS; z++) {
                    for (int x = 0; x < MapRegionTextures.REGION_CHUNKS; x++) {
                        int cx = rx * MapRegionTextures.REGION_CHUNKS + x;
                        int cz = rz * MapRegionTextures.REGION_CHUNKS + z;
                        long key = ChunkPos.pack(cx, cz);
                        ChunkSnapshot snapshot = merged.get(key);
                        if (snapshot == null) continue;
                        int px = x * 16;
                        int pz = z * 16;
                        Integer tint = overlay.get(key);
                        MapRegionTextures.renderChunk(snapshot, merged::get, cx, cz, hillshade,
                                (lx, lz, argb) -> image.setPixel(px + lx, pz + lz, blend(argb, tint)));
                    }
                }
                image.writeToFile(tiles.resolve(String.format(Locale.ROOT, "r.%d.%d.png", rx, rz)));
                files++;
            }
        }
        int[] bounds = boundsOf(merged);
        writeInfo(root, bounds == null ? new int[4] : bounds, 1, merged.size(), "tiles/r.<regionX>.<regionZ>.png (256x256 px, 1 px = 1 block, region = 16x16 chunks)");
        return new Result(tiles, MapRegionTextures.REGION_BLOCKS, MapRegionTextures.REGION_BLOCKS, merged.size(), files);
    }

    private static int blend(int base, Integer tint) {
        if (tint == null || (base >>> 24) == 0) {
            return base;
        }
        float a = (tint >>> 24) / 255.0F;
        int r = Math.round(((base >> 16) & 255) * (1 - a) + ((tint >> 16) & 255) * a);
        int g = Math.round(((base >> 8) & 255) * (1 - a) + ((tint >> 8) & 255) * a);
        int b = Math.round((base & 255) * (1 - a) + (tint & 255) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int[] boundsOf(Map<Long, ChunkSnapshot> merged) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : merged.keySet()) {
            int cx = (int) key;
            int cz = (int) (key >> 32);
            minX = Math.min(minX, cx);
            minZ = Math.min(minZ, cz);
            maxX = Math.max(maxX, cx);
            maxZ = Math.max(maxZ, cz);
        }
        return minX > maxX ? null : new int[] {minX, minZ, maxX, maxZ};
    }

    private static void writeInfo(Path root, int[] bounds, int step, int chunks, String files) throws IOException {
        try (Writer writer = Files.newBufferedWriter(root.resolve("map_info.txt"))) {
            writer.write("WorldBinder live map export\n");
            writer.write("World: " + worldName() + "\n");
            writer.write("Chunks: " + chunks + "\n");
            writer.write(String.format(Locale.ROOT, "Chunk bounds: x %d..%d, z %d..%d%n", bounds[0], bounds[2], bounds[1], bounds[3]));
            writer.write(String.format(Locale.ROOT, "Block origin (top-left pixel): x %d, z %d%n", bounds[0] * 16, bounds[1] * 16));
            writer.write("Blocks per pixel: " + step + "\n");
            writer.write("Files: " + files + "\n");
        }
    }

    private static String worldName() {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.getSingleplayerServer() != null) {
                return client.getSingleplayerServer().getWorldData().getLevelName();
            }
            if (client.getCurrentServer() != null) {
                return client.getCurrentServer().ip;
            }
        } catch (RuntimeException exception) {
            WorldBinder.LOGGER.debug("Could not resolve world name for map export", exception);
        }
        return "world";
    }
}
