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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Vertical structure for the 3D map. A height map only knows the topmost block of each column, so bridges,
 * roller coasters, trees or overhangs turn into solid walls. This store samples the chunks the client has loaded
 * into up to {@link #MAX_SPANS} solid "spans" per column (top, bottom, top color, side color), with the air in
 * between preserved. Plants, rails and other blocks without collision only tint the span below them.
 *
 * <p>Sampling runs on the client thread in small steps while the 3D map is open, a capture runs or explore mode
 * is on. Columns are immutable once stored, so the background renderer can read them without locking.
 */
public final class MapVoxels {
    public static final int MAX_SPANS = 6;
    public static final int INTS_PER_SPAN = 4;
    private static final MapVoxels INSTANCE = new MapVoxels();
    private static final int MAX_CHUNKS = 9000;
    private static final int CHECKS_PER_TICK = 128;
    private static final int SAMPLES_PER_TICK = 3;
    private static final long NEARBY_REFRESH_MILLIS = 8_000L;
    private static final int DEPTH_LIMIT = 192;
    private static final int GROUND_DEPTH = 48;
    /** Bottom value of spans that continue down to the bottom of the map. */
    public static final int FLOOR = -100_000;

    /** Spans of one chunk: {@code data[offsets[i] .. offsets[i+1])} holds {top, bottom, topColor, sideColor} groups. */
    public static final class Columns {
        public final int[] offsets;
        public final int[] data;
        public final int minTop;
        public final int maxTop;

        Columns(int[] offsets, int[] data, int minTop, int maxTop) {
            this.offsets = offsets;
            this.data = data;
            this.minTop = minTop;
            this.maxTop = maxTop;
        }
    }

    private final Map<Long, Columns> chunks = new HashMap<>();
    private final Map<Long, Long> sampledAt = new HashMap<>();
    private ClientLevel level;
    private long revision;
    private int ringCursor;
    private long demandUntil;

    public static MapVoxels get() {
        return INSTANCE;
    }

    private MapVoxels() {
    }

    public long revision() {
        return revision;
    }

    public int size() {
        return chunks.size();
    }

    public Columns columns(long chunkKey) {
        return chunks.get(chunkKey);
    }

    /** Copy of the chunk references (columns are immutable) for the background renderer and exports. */
    public Map<Long, Columns> copy() {
        return new HashMap<>(chunks);
    }

    /** Keeps sampling active for a moment; called every frame while the 3D map is visible. */
    public void demand() {
        demandUntil = System.currentTimeMillis() + 2_000L;
    }

    public void clear() {
        chunks.clear();
        sampledAt.clear();
        ringCursor = 0;
        revision++;
    }

    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        if (client.level != level) {
            level = client.level;
            clear();
        }
        long now = System.currentTimeMillis();
        boolean capturing = false;
        try {
            capturing = net.worldbinder.client.WorldBinderClient.capture().isCapturing();
        } catch (RuntimeException ignored) {
            // capture service not ready yet
        }
        if (now > demandUntil && !capturing && !WorldBinder.config().mapExploreMode) {
            return;
        }
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(2, Math.min(32, client.options.getEffectiveRenderDistance()));
        int side = radius * 2 + 1;
        int total = side * side;
        int samples = 0;
        for (int i = 0; i < CHECKS_PER_TICK && samples < SAMPLES_PER_TICK; i++) {
            int index = ringCursor++ % total;
            int[] offset = spiral(index);
            if (Math.abs(offset[0]) > radius || Math.abs(offset[1]) > radius) {
                continue;
            }
            int cx = playerChunkX + offset[0];
            int cz = playerChunkZ + offset[1];
            long key = ChunkPos.pack(cx, cz);
            Long last = sampledAt.get(key);
            boolean nearby = Math.abs(offset[0]) <= 2 && Math.abs(offset[1]) <= 2;
            if (last != null && !(nearby && now - last > NEARBY_REFRESH_MILLIS)) {
                continue;
            }
            LevelChunk chunk = client.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            chunks.put(key, sample(client.level, chunk, cx, cz, client.player.blockPosition().getY()));
            sampledAt.put(key, now);
            samples++;
            revision++;
        }
        if (ringCursor > total * 4) {
            ringCursor = 0;
        }
        if (chunks.size() > MAX_CHUNKS) {
            trim(playerChunkX, playerChunkZ, radius * 3);
        }
    }

    static Columns sample(ClientLevel level, LevelChunk chunk, int cx, int cz, int playerY) {
        boolean ceiling = level.dimensionType().hasCeiling();
        int minY = level.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] offsets = new int[257];
        int[] data = new int[256 * 2 * INTS_PER_SPAN];
        int size = 0;
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int column = lz * 16 + lx;
                offsets[column] = size;
                int worldX = (cx << 4) + lx;
                int worldZ = (cz << 4) + lz;
                int y = ceiling ? Math.min(playerY + 8, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz))
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                if (ceiling) {
                    while (y > minY && chunk.getBlockState(pos.set(worldX, y, worldZ)).isAir()) y--;
                }
                int start = y;
                int spans = 0;
                int decoration = 0;
                while (y >= minY && spans < MAX_SPANS && start - y < DEPTH_LIMIT) {
                    BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                    int kind = kind(state, level, pos);
                    if (kind == EMPTY) {
                        decoration = 0;
                        y--;
                        continue;
                    }
                    if (kind == DECORATION) {
                        decoration = BlockMapColors.color(state, level, pos);
                        y--;
                        continue;
                    }
                    int top = y;
                    int topColor = BlockMapColors.color(state, level, pos);
                    if (decoration != 0) {
                        topColor = mix(topColor, decoration, 0.4F);
                    }
                    decoration = 0;
                    int sideColor = topColor;
                    y--;
                    boolean sideSampled = false;
                    boolean ground = false;
                    while (true) {
                        if (y < minY) {
                            break;
                        }
                        if (top - y >= GROUND_DEPTH || start - y >= DEPTH_LIMIT) {
                            ground = true; // deep enough: treat as solid down to the floor
                            break;
                        }
                        BlockState below = chunk.getBlockState(pos.set(worldX, y, worldZ));
                        int belowKind = kind(below, level, pos);
                        if (belowKind != kind) {
                            break;
                        }
                        if (!sideSampled) {
                            sideColor = BlockMapColors.color(below, level, pos);
                            sideSampled = true;
                        }
                        y--;
                    }
                    int bottom = ground ? FLOOR : y + 1;
                    if (kind == FLUID) {
                        // deeper water gets darker, like on vanilla maps
                        int depth = ground ? GROUND_DEPTH : top - bottom + 1;
                        float shade = 1.0F - Math.min(0.45F, depth * 0.035F);
                        topColor = MapRenderer3D.shade(topColor, shade);
                        sideColor = MapRenderer3D.shade(topColor, 0.92F);
                    }
                    if (size + INTS_PER_SPAN > data.length) {
                        data = java.util.Arrays.copyOf(data, data.length * 2);
                    }
                    data[size++] = top;
                    data[size++] = bottom;
                    data[size++] = topColor;
                    data[size++] = sideColor;
                    spans++;
                    if (spans == 1) {
                        minTop = Math.min(minTop, top);
                        maxTop = Math.max(maxTop, top);
                    }
                    if (ground) {
                        break;
                    }
                }
            }
        }
        offsets[256] = size;
        if (minTop > maxTop) {
            minTop = maxTop = minY;
        }
        return new Columns(offsets, java.util.Arrays.copyOf(data, size), minTop, maxTop);
    }

    private static final int EMPTY = 0;
    private static final int DECORATION = 1;
    private static final int SOLID = 2;
    private static final int FLUID = 3;

    private static int kind(BlockState state, ClientLevel level, BlockPos pos) {
        if (state.isAir()) {
            return EMPTY;
        }
        if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
            return FLUID;
        }
        try {
            if (state.getCollisionShape(level, pos).isEmpty()) {
                return DECORATION;
            }
        } catch (RuntimeException ignored) {
            // shapes of some modded blocks need server data; treat them as solid
        }
        return SOLID;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private void trim(int centerX, int centerZ, int keepRadius) {
        Iterator<Map.Entry<Long, Columns>> iterator = chunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Columns> entry = iterator.next();
            int cx = (int) (long) entry.getKey();
            int cz = (int) (entry.getKey() >> 32);
            if (Math.abs(cx - centerX) > keepRadius || Math.abs(cz - centerZ) > keepRadius) {
                iterator.remove();
                sampledAt.remove(entry.getKey());
            }
        }
        revision++;
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
