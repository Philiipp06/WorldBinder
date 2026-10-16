package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongToIntFunction;

/**
 * Per-region status textures (one pixel per chunk) for the live map. They are rebuilt only when the capture
 * revision or the active filters change and are scaled up with nearest filtering for crisp chunk tiles.
 */
public final class MapStatusOverlay {
    private static final int MAX_TEXTURES = 160;
    private static int serial;
    private final Map<Long, Entry> entries = new HashMap<>();

    public void clear() {
        Minecraft client = Minecraft.getInstance();
        for (Entry entry : entries.values()) {
            client.getTextureManager().release(entry.id);
        }
        entries.clear();
    }

    /** Rebuilds the overlay for every visible region whose signature changed. */
    public void update(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ, long signature, LongToIntFunction colorForChunk) {
        int span = (maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1);
        if (span <= 0 || span > MAX_TEXTURES) {
            return;
        }
        evictOutside(minRegionX - 2, minRegionZ - 2, maxRegionX + 2, maxRegionZ + 2);
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                long key = MapRegionTextures.regionKey(rx, rz);
                Entry entry = entries.get(key);
                if (entry != null && entry.signature == signature) {
                    continue;
                }
                if (entry == null) {
                    entry = new Entry(rx, rz);
                    entries.put(key, entry);
                }
                boolean any = false;
                int baseX = rx * MapRegionTextures.REGION_CHUNKS;
                int baseZ = rz * MapRegionTextures.REGION_CHUNKS;
                for (int z = 0; z < MapRegionTextures.REGION_CHUNKS; z++) {
                    for (int x = 0; x < MapRegionTextures.REGION_CHUNKS; x++) {
                        int color = colorForChunk.applyAsInt(ChunkPos.pack(baseX + x, baseZ + z));
                        entry.image.setPixel(x, z, color);
                        any |= (color >>> 24) != 0;
                    }
                }
                entry.signature = signature;
                entry.visible = any;
                entry.texture.upload();
            }
        }
    }

    public void draw(GuiGraphicsExtractor context, double originX, double originY, double zoom,
                     int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                Entry entry = entries.get(MapRegionTextures.regionKey(rx, rz));
                if (entry == null || !entry.visible) {
                    continue;
                }
                MapRegionTextures.blitRegion(context, entry.id, originX + rx * (double) MapRegionTextures.REGION_BLOCKS * zoom,
                        originY + rz * (double) MapRegionTextures.REGION_BLOCKS * zoom, MapRegionTextures.REGION_BLOCKS * zoom);
            }
        }
    }

    private void evictOutside(int minX, int minZ, int maxX, int maxZ) {
        if (entries.size() < MAX_TEXTURES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.rx < minX || entry.rx > maxX || entry.rz < minZ || entry.rz > maxZ) {
                client.getTextureManager().release(entry.id);
                iterator.remove();
            }
        }
    }

    private static final class Entry {
        private final int rx;
        private final int rz;
        private final NativeImage image;
        private final DynamicTexture texture;
        private final Identifier id;
        private long signature = Long.MIN_VALUE;
        private boolean visible;

        private Entry(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            this.image = new NativeImage(MapRegionTextures.REGION_CHUNKS, MapRegionTextures.REGION_CHUNKS, true);
            this.texture = new DynamicTexture(() -> "worldbinder live map status", image);
            this.id = Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, "live_map/status_" + (serial++));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }
}
