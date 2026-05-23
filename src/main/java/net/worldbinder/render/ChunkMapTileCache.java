package net.worldbinder.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkMapTileCache {
    private static final int MAX_TILES = 4096;

    private final Map<Long, CachedTile> tiles = new LinkedHashMap<>(256, 0.75F, true);

    public void clear() {
        tiles.clear();
    }

    public void draw(GuiGraphicsExtractor context, long key, ChunkSnapshot snapshot, int x, int y, int size) {
        if (snapshot == null || !snapshot.hasSnapshot) {
            return;
        }
        CachedTile tile = tiles.get(key);
        long version = snapshot.renderVersion();
        if (tile == null || tile.version != version) {
            tile = new CachedTile(version, snapshot);
            tiles.put(key, tile);
            trim();
        }
        tile.draw(context, x, y, size);
    }

    private void trim() {
        if (tiles.size() <= MAX_TILES) {
            return;
        }
        Iterator<Long> iterator = tiles.keySet().iterator();
        while (tiles.size() > MAX_TILES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static final class CachedTile {
        private final long version;
        private final int average;
        private final int[] lod4;
        private final int[] lod8;
        private final int[] lod16;

        private CachedTile(long version, ChunkSnapshot snapshot) {
            this.version = version;
            this.average = snapshot.averageColor();
            this.lod16 = new int[16 * 16];
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    lod16[z * 16 + x] = snapshot.hasSample(x, z) ? snapshot.colorAt(x, z) : average;
                }
            }
            this.lod8 = downsample(lod16, 16, 8);
            this.lod4 = downsample(lod16, 16, 4);
        }

        private void draw(GuiGraphicsExtractor context, int x, int y, int size) {
            if (size <= 24) {
                context.fill(x, y, x + size, y + size, average);
                return;
            }
            if (size <= 48) {
                drawGrid(context, x, y, size, lod4, 4);
                return;
            }
            if (size <= 80) {
                drawGrid(context, x, y, size, lod8, 8);
                return;
            }
            drawGrid(context, x, y, size, lod16, 16);
        }

        private static void drawGrid(GuiGraphicsExtractor context, int x, int y, int size, int[] colors, int cells) {
            for (int z = 0; z < cells; z++) {
                int y1 = y + z * size / cells;
                int y2 = y + (z + 1) * size / cells;
                for (int lx = 0; lx < cells; lx++) {
                    int x1 = x + lx * size / cells;
                    int x2 = x + (lx + 1) * size / cells;
                    context.fill(x1, y1, x2, y2, colors[z * cells + lx]);
                }
            }
        }

        private static int[] downsample(int[] source, int sourceCells, int targetCells) {
            int[] target = new int[targetCells * targetCells];
            int scale = sourceCells / targetCells;
            for (int z = 0; z < targetCells; z++) {
                for (int x = 0; x < targetCells; x++) {
                    long a = 0;
                    long r = 0;
                    long g = 0;
                    long b = 0;
                    int count = 0;
                    for (int oz = 0; oz < scale; oz++) {
                        for (int ox = 0; ox < scale; ox++) {
                            int color = source[(z * scale + oz) * sourceCells + (x * scale + ox)];
                            a += (color >>> 24) & 255;
                            r += (color >>> 16) & 255;
                            g += (color >>> 8) & 255;
                            b += color & 255;
                            count++;
                        }
                    }
                    target[z * targetCells + x] = ((int) (a / count) << 24) | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
                }
            }
            return target;
        }
    }
}
