package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * GPU texture cache for the live map and the HUD radar. The world is split into regions of 16x16 chunks
 * (256x256 blocks, one pixel per block column). Each region is a {@link DynamicTexture} that is only repainted
 * for chunks whose snapshot changed, so drawing the map is a handful of textured quads instead of hundreds of
 * thousands of fills.
 */
public final class MapRegionTextures {
    public static final int REGION_CHUNKS = 16;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
    private static final int MAX_REGIONS = 96;
    private static final MapRegionTextures SHARED = new MapRegionTextures();
    private static int textureSerial;

    /** Receives one finished ARGB pixel for a block column (local 0..15 coordinates). */
    @FunctionalInterface
    public interface PixelSink {
        void set(int localX, int localZ, int argb);
    }

    private final Map<Long, Region> regions = new LinkedHashMap<>(64, 0.75F, true);
    private final Map<Long, Long> paintedVersions = new HashMap<>();
    private final ArrayDeque<Long> paintQueue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private boolean hillshade = true;
    private boolean markers;

    public static MapRegionTextures shared() {
        return SHARED;
    }

    private MapRegionTextures() {
    }

    public int regionCount() {
        return regions.size();
    }

    public int pendingChunks() {
        return paintQueue.size();
    }

    /** Releases every GPU texture (world change, disconnect). */
    public void clear() {
        Minecraft client = Minecraft.getInstance();
        for (Region region : regions.values()) {
            region.close(client);
        }
        regions.clear();
        paintedVersions.clear();
        paintQueue.clear();
        queued.clear();
    }

    public void sync(Map<Long, ChunkSnapshot> snapshots, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, boolean hillshade) {
        sync((LongFunction<ChunkSnapshot>) snapshots::get, minChunkX, minChunkZ, maxChunkX, maxChunkZ, hillshade);
    }

    /**
     * Queues chunks in the visible chunk rectangle whose snapshot changed since they were painted.
     * Cost is proportional to the visible area, never to the total number of captured chunks.
     */
    public void sync(LongFunction<ChunkSnapshot> lookup, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, boolean hillshade) {
        boolean markers = net.worldbinder.WorldBinder.config().mapShowInvisibleBlocks;
        if (this.hillshade != hillshade || this.markers != markers) {
            this.hillshade = hillshade;
            this.markers = markers;
            paintedVersions.clear();
        }
        int area = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (area <= 0 || area > 40_000) {
            return;
        }
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                long key = ChunkPos.pack(cx, cz);
                long version = versionOf(lookup.apply(key));
                Long painted = paintedVersions.get(key);
                if (painted == null ? version != 0L : painted != version) {
                    if (queued.add(key)) {
                        paintQueue.add(key);
                    }
                }
            }
        }
    }

    public void paint(Map<Long, ChunkSnapshot> snapshots, long budgetNanos) {
        paint((LongFunction<ChunkSnapshot>) snapshots::get, budgetNanos);
    }

    /** Paints queued chunks within the time budget and uploads touched regions. */
    public void paint(LongFunction<ChunkSnapshot> lookup, long budgetNanos) {
        if (paintQueue.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + Math.max(300_000L, budgetNanos);
        Set<Region> touched = new HashSet<>();
        int painted = 0;
        while (!paintQueue.isEmpty() && (painted < 16 || System.nanoTime() < deadline)) {
            long key = paintQueue.removeFirst();
            queued.remove(key);
            ChunkSnapshot snapshot = lookup.apply(key);
            int cx = (int) key;
            int cz = (int) (key >> 32);
            Region region = snapshot == null ? regions.get(regionKey(cx >> 4, cz >> 4)) : region(cx >> 4, cz >> 4);
            if (region != null) {
                int px = (cx - region.rx * REGION_CHUNKS) * 16;
                int pz = (cz - region.rz * REGION_CHUNKS) * 16;
                NativeImage image = region.image;
                if (snapshot == null) {
                    image.fillRect(px, pz, 16, 16, 0);
                } else {
                    renderChunk(snapshot, lookup, cx, cz, hillshade, (x, z, argb) -> image.setPixel(px + x, pz + z, argb));
                }
                touched.add(region);
            }
            long version = versionOf(snapshot);
            if (version == 0L) {
                paintedVersions.remove(key);
            } else {
                paintedVersions.put(key, version);
            }
            painted++;
        }
        for (Region region : touched) {
            region.texture.upload();
        }
    }

    /** Draws all cached regions intersecting the given region window. Coordinates are in GUI pixels. */
    public void drawTerrain(GuiGraphicsExtractor context, double originX, double originY, double zoom,
                            int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                Region region = regions.get(regionKey(rx, rz));
                if (region == null) {
                    continue;
                }
                blitRegion(context, region.textureId, originX + rx * (double) REGION_BLOCKS * zoom,
                        originY + rz * (double) REGION_BLOCKS * zoom, REGION_BLOCKS * zoom);
            }
        }
    }

    /** Draws any region-sized texture (terrain or the per-chunk status overlay) at the given GUI position. */
    public static void blitRegion(GuiGraphicsExtractor context, Identifier id, double x, double y, double size) {
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        float scale = (float) (size / REGION_BLOCKS);
        context.pose().scale(scale, scale);
        context.blit(id, 0, 0, REGION_BLOCKS, REGION_BLOCKS, 0.0F, 1.0F, 0.0F, 1.0F);
        context.pose().popMatrix();
    }

    /**
     * Computes the final map pixels of one chunk: vanilla map palette colors plus optional relief shading that
     * compares each column with its north and west neighbors (also across chunk borders).
     */
    public static void renderChunk(ChunkSnapshot snapshot, LongFunction<ChunkSnapshot> lookup, int cx, int cz, boolean hillshade, PixelSink sink) {
        boolean detailed = snapshot.colors != null && snapshot.colors.length == 256 && snapshot.heights != null && snapshot.heights.length == 256;
        int average = opaque(snapshot.averageColor());
        if (!detailed) {
            int color = snapshot.hasSnapshot || snapshot.sampleCount() > 0 ? average : 0;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) sink.set(x, z, color);
            return;
        }
        boolean markers = snapshot.markerHeights != null && snapshot.markerHeights.length == 256
                && net.worldbinder.WorldBinder.config().mapShowInvisibleBlocks;
        ChunkSnapshot north = hillshade && lookup != null ? lookup.apply(ChunkPos.pack(cx, cz - 1)) : null;
        ChunkSnapshot west = hillshade && lookup != null ? lookup.apply(ChunkPos.pack(cx - 1, cz)) : null;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = z * 16 + x;
                int height = snapshot.heights[index];
                if (markers && snapshot.markerHeights[index] != Integer.MIN_VALUE && snapshot.markerHeights[index] >= height) {
                    // invisible marker above the terrain: tint the terrain, hatched every other column
                    int base = height == Integer.MIN_VALUE ? 0xFF2A2F36 : opaque(snapshot.colors[index] == 0 ? average : snapshot.colors[index]);
                    float t = ((x + z) & 1) == 0 ? 0.72F : 0.5F;
                    sink.set(x, z, mix(base, snapshot.markerColors[index], t));
                    continue;
                }
                if (height == Integer.MIN_VALUE) {
                    sink.set(x, z, snapshot.hasSnapshot ? withAlpha(average, 0x90) : 0);
                    continue;
                }
                int color = snapshot.colors[index] == 0 ? average : opaque(snapshot.colors[index]);
                if (hillshade) {
                    int northHeight = z > 0 ? snapshot.heights[index - 16] : heightAt(north, x, 15, height);
                    int westHeight = x > 0 ? snapshot.heights[index - 1] : heightAt(west, 15, z, height);
                    if (northHeight == Integer.MIN_VALUE) northHeight = height;
                    if (westHeight == Integer.MIN_VALUE) westHeight = height;
                    float slope = (height - northHeight) * 0.65F + (height - westHeight) * 0.35F;
                    float factor = slope > 0.0F ? 1.0F + Math.min(0.22F, slope * 0.06F)
                            : slope < 0.0F ? 1.0F - Math.min(0.28F, -slope * 0.07F) : 1.0F;
                    factor *= 0.94F + Math.max(-0.06F, Math.min(0.10F, (height - 62) / 900.0F));
                    color = shade(color, factor);
                }
                sink.set(x, z, color);
            }
        }
    }

    private Region region(int rx, int rz) {
        long key = regionKey(rx, rz);
        Region region = regions.get(key);
        if (region != null) {
            return region;
        }
        while (regions.size() >= MAX_REGIONS) {
            Iterator<Map.Entry<Long, Region>> iterator = regions.entrySet().iterator();
            Map.Entry<Long, Region> eldest = iterator.next();
            iterator.remove();
            forgetRegion(eldest.getValue());
            eldest.getValue().close(Minecraft.getInstance());
        }
        region = new Region(rx, rz);
        regions.put(key, region);
        return region;
    }

    private void forgetRegion(Region region) {
        int baseX = region.rx * REGION_CHUNKS;
        int baseZ = region.rz * REGION_CHUNKS;
        for (int z = 0; z < REGION_CHUNKS; z++) {
            for (int x = 0; x < REGION_CHUNKS; x++) {
                paintedVersions.remove(ChunkPos.pack(baseX + x, baseZ + z));
            }
        }
    }

    private static int heightAt(ChunkSnapshot snapshot, int x, int z, int fallback) {
        if (snapshot == null || snapshot.heights == null || snapshot.heights.length != 256) {
            return fallback;
        }
        return snapshot.heights[z * 16 + x];
    }

    private static long versionOf(ChunkSnapshot snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        return (snapshot.renderVersion() << 20) ^ System.identityHashCode(snapshot) ^ (snapshot.hasSnapshot ? 1L : 2L);
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int shade(int color, float factor) {
        int r = Math.min(255, Math.round(((color >> 16) & 255) * factor));
        int g = Math.min(255, Math.round(((color >> 8) & 255) * factor));
        int b = Math.min(255, Math.round((color & 255) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static long regionKey(int rx, int rz) {
        return ((long) rz << 32) | (rx & 0xFFFFFFFFL);
    }

    private static final class Region {
        private final int rx;
        private final int rz;
        private final NativeImage image;
        private final DynamicTexture texture;
        private final Identifier textureId;

        private Region(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            this.image = new NativeImage(REGION_BLOCKS, REGION_BLOCKS, true);
            this.image.fillRect(0, 0, REGION_BLOCKS, REGION_BLOCKS, 0);
            this.texture = new DynamicTexture(() -> "worldbinder live map region", image);
            this.textureId = Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, "live_map/region_" + (textureSerial++));
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }

        private void close(Minecraft client) {
            client.getTextureManager().release(textureId);
        }
    }
}
