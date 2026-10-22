package net.worldbinder.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Explore mode for the live map: samples the surface of chunks the client has already loaded into lightweight
 * map snapshots, without starting a capture and without storing any blocks. Only colors and heights are kept,
 * in memory, for the current dimension.
 */
public final class MapExplorer {
    private static final MapExplorer INSTANCE = new MapExplorer();
    private static final int MAX_CHUNKS = 30_000;
    private static final int CHECKS_PER_TICK = 96;
    private static final int SAMPLES_PER_TICK = 4;
    private static final long NEARBY_REFRESH_MILLIS = 15_000L;

    private final Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
    private final Map<Long, Long> sampledAt = new HashMap<>();
    private ClientLevel level;
    private long revision;
    private int ringCursor;
    private int minChunkX = Integer.MAX_VALUE;
    private int minChunkZ = Integer.MAX_VALUE;
    private int maxChunkX = Integer.MIN_VALUE;
    private int maxChunkZ = Integer.MIN_VALUE;

    public static MapExplorer get() {
        return INSTANCE;
    }

    private MapExplorer() {
    }

    public ChunkSnapshot snapshot(long key) {
        return snapshots.get(key);
    }

    public int size() {
        return snapshots.size();
    }

    public long revision() {
        return revision;
    }

    public Map<Long, ChunkSnapshot> view() {
        return java.util.Collections.unmodifiableMap(snapshots);
    }

    public boolean hasBounds() {
        return minChunkX <= maxChunkX;
    }

    public int minChunkX() { return minChunkX; }
    public int minChunkZ() { return minChunkZ; }
    public int maxChunkX() { return maxChunkX; }
    public int maxChunkZ() { return maxChunkZ; }

    public void clear() {
        snapshots.clear();
        sampledAt.clear();
        ringCursor = 0;
        minChunkX = minChunkZ = Integer.MAX_VALUE;
        maxChunkX = maxChunkZ = Integer.MIN_VALUE;
        revision++;
    }

    public void tick(Minecraft client) {
        if (!WorldBinder.config().mapExploreMode || client.level == null || client.player == null) {
            return;
        }
        if (client.level != level) {
            level = client.level;
            clear();
        }
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(2, Math.min(32, client.options.getEffectiveRenderDistance()));
        int side = radius * 2 + 1;
        int total = side * side;
        long now = System.currentTimeMillis();
        int samples = 0;
        for (int i = 0; i < CHECKS_PER_TICK && samples < SAMPLES_PER_TICK; i++) {
            int index = ringCursor++ % total;
            int dx = spiralX(index);
            int dz = spiralZ(index);
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                continue;
            }
            int cx = playerChunkX + dx;
            int cz = playerChunkZ + dz;
            long key = ChunkPos.pack(cx, cz);
            Long last = sampledAt.get(key);
            boolean nearby = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
            if (last != null && !(nearby && now - last > NEARBY_REFRESH_MILLIS)) {
                continue;
            }
            LevelChunk chunk = client.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            sample(client.level, chunk, cx, cz, client.player.blockPosition().getY());
            sampledAt.put(key, now);
            samples++;
        }
        if (ringCursor > total * 4) {
            ringCursor = 0;
        }
        if (snapshots.size() > MAX_CHUNKS) {
            trim(playerChunkX, playerChunkZ, radius * 3);
        }
    }

    private void sample(ClientLevel level, LevelChunk chunk, int cx, int cz, int playerY) {
        ChunkSnapshot snapshot = new ChunkSnapshot(cx, cz);
        boolean ceiling = level.dimensionType().hasCeiling();
        int minY = level.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = (cx << 4) + lx;
                int worldZ = (cz << 4) + lz;
                int y;
                if (ceiling) {
                    y = Math.min(playerY + 8, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz));
                    while (y > minY && chunk.getBlockState(pos.set(worldX, y, worldZ)).isAir()) y--;
                } else {
                    y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                }
                if (y < minY) {
                    continue;
                }
                BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                if (state.isAir()) {
                    continue;
                }
                int color = BlockMapColors.color(state, level, pos);
                snapshot.sample(lx, lz, y, "", color);
            }
        }
        snapshots.put(ChunkPos.pack(cx, cz), snapshot);
        minChunkX = Math.min(minChunkX, cx);
        minChunkZ = Math.min(minChunkZ, cz);
        maxChunkX = Math.max(maxChunkX, cx);
        maxChunkZ = Math.max(maxChunkZ, cz);
        revision++;
    }

    private void trim(int centerX, int centerZ, int keepRadius) {
        Iterator<Map.Entry<Long, ChunkSnapshot>> iterator = snapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, ChunkSnapshot> entry = iterator.next();
            int cx = (int) (long) entry.getKey();
            int cz = (int) (entry.getKey() >> 32);
            if (Math.abs(cx - centerX) > keepRadius || Math.abs(cz - centerZ) > keepRadius) {
                iterator.remove();
                sampledAt.remove(entry.getKey());
            }
        }
        revision++;
    }

    /** Square spiral walk starting at the player chunk: index 0 is (0,0), then ring by ring outwards. */
    private static int spiralX(int index) {
        return spiral(index)[0];
    }

    private static int spiralZ(int index) {
        return spiral(index)[1];
    }

    private static int[] spiral(int index) {
        if (index == 0) {
            return new int[] {0, 0};
        }
        int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        int sideLength = ring * 2;
        int maxIndex = (ring * 2 + 1) * (ring * 2 + 1) - 1;
        int offset = maxIndex - index;
        if (offset < sideLength) return new int[] {ring - offset, ring};
        offset -= sideLength;
        if (offset < sideLength) return new int[] {-ring, ring - offset};
        offset -= sideLength;
        if (offset < sideLength) return new int[] {-ring + offset, -ring};
        offset -= sideLength;
        return new int[] {ring, -ring + offset};
    }
}
