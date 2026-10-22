package net.worldbinder.render;

import net.minecraft.world.level.ChunkPos;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.Map;

/**
 * CPU renderer for the 3D live map and the 3D render export.
 *
 * <p>The world is drawn in an orthographic view that can be rotated (yaw) and tilted (pitch). Every screen
 * column is a vertical slice through the world; the renderer walks that slice exactly cell by cell (2D DDA) from
 * the front to the back. Each block column consists of one or more solid spans (see {@link MapVoxels}), so air
 * under bridges, roller coasters or trees stays visible. For every cell the front faces of all spans are drawn
 * first, then their top faces from the highest down. A per-column "next uncovered row" structure makes sure every
 * pixel is written once by the nearest surface, and lets a column stop as soon as it is completely covered.
 *
 * <p>Shading: directional light per face orientation, block-grid variation on top faces, sun shadows cast by
 * spans towards the south-east, contact darkening at the foot of walls and highlighted edges. The renderer has no
 * GPU or game state, so it runs on a background thread for the live view and for exports.
 */
public final class MapRenderer3D {
    /**
     * @param targetX      world X the view rotates around (screen center)
     * @param targetZ      world Z the view rotates around
     * @param baseY        world Y that appears at the screen center
     * @param yawDegrees   0 = north up; increases clockwise
     * @param pitchDegrees 90 = straight down (identical to the 2D map), 20 = flat view
     * @param scale        screen pixels per block
     * @param heightScale  vertical exaggeration (1 = true scale)
     */
    public record Camera(double targetX, double targetZ, double baseY, double yawDegrees, double pitchDegrees,
                         double scale, double heightScale) {
        double sinYaw() { return Math.sin(Math.toRadians(yawDegrees)); }
        double cosYaw() { return Math.cos(Math.toRadians(yawDegrees)); }
        double sinPitch() { return Math.sin(Math.toRadians(pitchDegrees)); }
        double cosPitch() { return Math.cos(Math.toRadians(pitchDegrees)); }

        public Camera withScale(double newScale) {
            return new Camera(targetX, targetZ, baseY, yawDegrees, pitchDegrees, newScale, heightScale);
        }
    }

    /** Spans of block columns: writes {top, bottom, topColor, sideColor} groups into {@code out}, returns the count. */
    public interface ColumnSource {
        int spans(int x, int z, int[] out);
    }

    /** Output buffers; hit arrays hold the world column per pixel (Integer.MIN_VALUE = empty), depth the view depth. */
    public static final class Frame {
        public final int width;
        public final int height;
        public final int[] argb;
        public final int[] hitX;
        public final int[] hitZ;
        public final float[] depth;
        /** Window of a larger virtual screen (exports render in strips): offset and full size of that screen. */
        public final int offsetX;
        public final int offsetY;
        public final int fullWidth;
        public final int fullHeight;

        public Frame(int width, int height, boolean hits) {
            this(width, height, hits, 0, 0, width, height);
        }

        public Frame(int width, int height, boolean hits, int offsetX, int offsetY, int fullWidth, int fullHeight) {
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.fullWidth = fullWidth;
            this.fullHeight = fullHeight;
            this.argb = new int[width * height];
            this.hitX = hits ? new int[width * height] : null;
            this.hitZ = hits ? new int[width * height] : null;
            this.depth = hits ? new float[width * height] : null;
        }
    }

    private static final float FACE_WEST = 0.80F;
    private static final float FACE_EAST = 0.64F;
    private static final float FACE_NORTH = 0.58F;
    private static final float FACE_SOUTH = 0.90F;

    private MapRenderer3D() {
    }

    // ------------------------------------------------------------------ camera math

    /** Screen position {x, y, depth} (in frame pixels) of a world point. */
    public static double[] project(Camera camera, int width, int height, double x, double y, double z) {
        double sy = camera.sinYaw();
        double cy = camera.cosYaw();
        double dx = x - camera.targetX();
        double dz = z - camera.targetZ();
        double right = dx * cy + dz * sy;
        double depth = dx * sy - dz * cy;
        double screenX = width / 2.0 + right * camera.scale();
        double screenY = height / 2.0 - (depth * camera.sinPitch() + (y - camera.baseY()) * camera.heightScale() * camera.cosPitch()) * camera.scale();
        return new double[] {screenX, screenY, depth};
    }

    /** Moves the camera target by a screen-space drag (pixels), keeping the view orientation. */
    public static double[] panTarget(Camera camera, double dragX, double dragY) {
        double sy = camera.sinYaw();
        double cy = camera.cosYaw();
        double right = -dragX / camera.scale();
        double depth = dragY / (camera.scale() * Math.max(0.2D, camera.sinPitch()));
        return new double[] {camera.targetX() + right * cy + depth * sy, camera.targetZ() + right * sy - depth * cy};
    }

    // ------------------------------------------------------------------ sources

    /**
     * Column source over voxel spans (preferred) and height-map snapshots (fallback: one span reaching down to the
     * floor). Not thread-safe: create one per render thread.
     */
    public static ColumnSource source(Map<Long, MapVoxels.Columns> voxels, java.util.function.LongFunction<ChunkSnapshot> snapshots) {
        return new ColumnSource() {
            private long voxelKey = Long.MIN_VALUE;
            private MapVoxels.Columns voxelChunk;
            private long snapshotKey = Long.MIN_VALUE;
            private ChunkSnapshot snapshotChunk;

            @Override
            public int spans(int x, int z, int[] out) {
                long key = ChunkPos.pack(x >> 4, z >> 4);
                if (key != voxelKey) {
                    voxelKey = key;
                    voxelChunk = voxels == null ? null : voxels.get(key);
                }
                int column = (z & 15) * 16 + (x & 15);
                if (voxelChunk != null) {
                    int from = voxelChunk.offsets[column];
                    int to = voxelChunk.offsets[column + 1];
                    int length = Math.min(to - from, out.length);
                    if (length > 0) {
                        System.arraycopy(voxelChunk.data, from, out, 0, length);
                        return length / MapVoxels.INTS_PER_SPAN;
                    }
                }
                if (key != snapshotKey) {
                    snapshotKey = key;
                    snapshotChunk = snapshots == null ? null : snapshots.apply(key);
                }
                ChunkSnapshot snapshot = snapshotChunk;
                if (snapshot == null || snapshot.heights == null || snapshot.heights.length != 256) {
                    return 0;
                }
                int height = snapshot.heights[column];
                int count = 0;
                if (snapshot.markerHeights != null && snapshot.markerHeights.length == 256 && snapshot.markerHeights[column] != Integer.MIN_VALUE
                        && snapshot.markerHeights[column] > height && net.worldbinder.WorldBinder.config().mapShowInvisibleBlocks) {
                    int marker = snapshot.markerColors[column];
                    out[0] = snapshot.markerHeights[column];
                    out[1] = snapshot.markerHeights[column];
                    out[2] = marker;
                    out[3] = shade(marker, 0.8F);
                    count = 1;
                }
                if (height == Integer.MIN_VALUE) {
                    return count;
                }
                int color = snapshot.colors == null || snapshot.colors[column] == 0 ? 0xFF6F7680 : 0xFF000000 | snapshot.colors[column];
                int o = count * MapVoxels.INTS_PER_SPAN;
                out[o] = height;
                out[o + 1] = MapVoxels.FLOOR;
                out[o + 2] = color;
                out[o + 3] = shade(color, 0.86F);
                return count + 1;
            }
        };
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Renders the scene. {@code minHeight/maxHeight} bound the known surface heights and limit the march.
     */
    public static void render(ColumnSource source, Camera camera, Frame frame, int minHeight, int maxHeight, boolean shading) {
        render(source, source, camera, frame, minHeight, maxHeight, shading);
    }

    /** As above, with a separate source instance for shadow lookups (keeps both chunk caches warm). */
    public static void render(ColumnSource source, ColumnSource shadowSource, Camera camera, Frame frame, int minHeight, int maxHeight, boolean shading) {
        int width = frame.width;
        int height = frame.height;
        java.util.Arrays.fill(frame.argb, 0);
        if (frame.hitX != null) {
            java.util.Arrays.fill(frame.hitX, Integer.MIN_VALUE);
            java.util.Arrays.fill(frame.hitZ, Integer.MIN_VALUE);
            java.util.Arrays.fill(frame.depth, Float.POSITIVE_INFINITY);
        }
        double s = camera.scale();
        double sinYaw = camera.sinYaw();
        double cosYaw = camera.cosYaw();
        double sinP = Math.max(0.02D, camera.sinPitch());
        double cosP = Math.max(0.0D, camera.cosPitch());
        double hs = camera.heightScale();
        double centerY = frame.fullHeight / 2.0 - frame.offsetY;
        double lowTop = (minHeight - camera.baseY()) * hs * cosP;
        double highTop = (maxHeight + 1 - camera.baseY()) * hs * cosP;
        double depthMin = ((centerY - height) / s - highTop) / sinP - 1.5D;
        double depthMax = (centerY / s - lowTop) / sinP + 1.5D;
        int floor = minHeight - 4;

        // per-face light depending on which side of the block faces the viewer
        float xFace = sinYaw > 0 ? FACE_WEST : FACE_EAST;   // ray steps +x -> sees the west face
        float zFace = -cosYaw > 0 ? FACE_NORTH : FACE_SOUTH; // ray steps +z -> sees the north face

        Renderer renderer = new Renderer(source, shadowSource, frame, height, shading);
        double dirX = sinYaw;
        double dirZ = -cosYaw;
        for (int column = 0; column < width; column++) {
            double right = (column + frame.offsetX + 0.5D - frame.fullWidth / 2.0D) / s;
            double originX = camera.targetX() + right * cosYaw + depthMin * dirX;
            double originZ = camera.targetZ() + right * sinYaw + depthMin * dirZ;
            renderer.beginColumn(column);
            int cellX = (int) Math.floor(originX);
            int cellZ = (int) Math.floor(originZ);
            int stepX = dirX > 0 ? 1 : -1;
            int stepZ = dirZ > 0 ? 1 : -1;
            double tDeltaX = Math.abs(dirX) < 1.0E-9 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dirX);
            double tDeltaZ = Math.abs(dirZ) < 1.0E-9 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(dirZ);
            double tMaxX = Math.abs(dirX) < 1.0E-9 ? Double.POSITIVE_INFINITY
                    : (dirX > 0 ? (cellX + 1 - originX) : (originX - cellX)) * tDeltaX;
            double tMaxZ = Math.abs(dirZ) < 1.0E-9 ? Double.POSITIVE_INFINITY
                    : (dirZ > 0 ? (cellZ + 1 - originZ) : (originZ - cellZ)) * tDeltaZ;
            double t = 0.0D;
            float face = zFace;
            double total = depthMax - depthMin;
            while (t <= total) {
                double tNext = Math.min(tMaxX, tMaxZ);
                double dEnter = depthMin + t;
                double dExit = depthMin + Math.min(tNext, total + 1.0D);
                if (renderer.cell(cellX, cellZ, dEnter, dExit, face, centerY, s, sinP, cosP, hs, camera.baseY(), floor)) {
                    break; // column fully covered
                }
                if (tMaxX < tMaxZ) {
                    cellX += stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    face = xFace;
                } else {
                    cellZ += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    face = zFace;
                }
            }
        }
    }

    /** Per-render state: span buffers, shadow lookups and the per-column coverage structure. */
    private static final class Renderer {
        private final ColumnSource source;
        private final ColumnSource shadowSource;
        private final Frame frame;
        private final int height;
        private final boolean shading;
        private final int[] spans = new int[MapVoxels.MAX_SPANS * MapVoxels.INTS_PER_SPAN + 8];
        private final int[] shadowSpans = new int[MapVoxels.MAX_SPANS * MapVoxels.INTS_PER_SPAN + 8];
        /** parent[y + 1] = next possibly uncovered row at or above y (-1 = none). */
        private final int[] parent;
        private int column;

        Renderer(ColumnSource source, ColumnSource shadowSource, Frame frame, int height, boolean shading) {
            this.source = source;
            this.shadowSource = shadowSource;
            this.frame = frame;
            this.height = height;
            this.shading = shading;
            this.parent = new int[height + 1];
        }

        void beginColumn(int column) {
            this.column = column;
            for (int i = 0; i <= height; i++) parent[i] = i - 1;
        }

        /** Largest uncovered row <= y, or -1. */
        private int find(int y) {
            if (y >= height) y = height - 1;
            int index = y + 1;
            while (index > 0 && parent[index] != index - 1) {
                int next = parent[index] + 1;
                parent[index] = parent[next];
                index = next;
            }
            return index - 1;
        }

        private boolean covered() {
            return find(height - 1) < 0;
        }

        /** Returns true when the column is completely covered. */
        boolean cell(int x, int z, double dEnter, double dExit, float face, double centerY, double s, double sinP, double cosP,
                     double hs, double baseY, int floor) {
            int count = source.spans(x, z, spans);
            if (count == 0) {
                return false;
            }
            double enterBase = centerY - dEnter * sinP * s;
            double exitBase = centerY - dExit * sinP * s;
            double rise = hs * cosP * s;
            // front faces (nearest surface of this cell)
            for (int i = 0; i < count; i++) {
                int o = i * MapVoxels.INTS_PER_SPAN;
                int top = spans[o];
                int bottom = Math.max(spans[o + 1], floor);
                double yTop = enterBase - (top + 1 - baseY) * rise;
                double yBottom = enterBase - (bottom - baseY) * rise;
                int a = (int) Math.ceil(yTop);
                int b = (int) Math.ceil(yBottom);
                if (b <= 0 || a >= height || b <= a) continue;
                int side = spans[o + 3];
                paintSide(a, b, side, face, x, z, (float) dEnter, spans[o + 1] <= floor);
            }
            // top faces, highest first
            for (int i = 0; i < count; i++) {
                int o = i * MapVoxels.INTS_PER_SPAN;
                int top = spans[o];
                double yNear = enterBase - (top + 1 - baseY) * rise;
                double yFar = exitBase - (top + 1 - baseY) * rise;
                int a = (int) Math.ceil(yFar);
                int b = (int) Math.ceil(yNear);
                if (b <= 0 || a >= height || b <= a) continue;
                paintTop(a, b, spans[o + 2], x, z, top, (float) ((dEnter + dExit) * 0.5D));
            }
            return covered();
        }

        private void paintSide(int a, int b, int color, float face, int x, int z, float depth, boolean toFloor) {
            int from = Math.max(0, a);
            int to = Math.min(height, b);
            int r = find(to - 1);
            if (r < from) return;
            int base = shade(color, face);
            int faceHeight = Math.max(1, b - a);
            while (r >= from) {
                int pixel = base;
                if (shading) {
                    if (r == a) {
                        pixel = shade(pixel, 0.78F); // crease under the top edge
                    } else if (!toFloor) {
                        // contact darkening towards the bottom of hanging spans
                        int fromBottom = b - 1 - r;
                        if (fromBottom < 3) pixel = shade(pixel, 0.86F + fromBottom * 0.04F);
                    } else {
                        float t = Math.min(1.0F, (r - a) / (float) Math.max(6, Math.min(faceHeight, 48)));
                        pixel = shade(pixel, 1.0F - t * 0.16F);
                    }
                }
                put(r, pixel, x, z, depth);
                r = find(r - 1);
            }
        }

        private void paintTop(int a, int b, int color, int x, int z, int top, float depth) {
            int from = Math.max(0, a);
            int to = Math.min(height, b);
            int r = find(to - 1);
            if (r < from) return;
            float light = 1.0F;
            if (shading) {
                light = ((x + z) & 1) == 0 ? 1.0F : 0.965F;
                if (inShadow(x, z, top)) light *= 0.70F;
            }
            int base = shade(color, light);
            int highlight = shading ? shade(base, 1.10F) : base;
            boolean edges = b - a >= 3;
            while (r >= from) {
                int pixel = edges && r == b - 1 ? highlight : base;
                put(r, pixel, x, z, depth);
                r = find(r - 1);
            }
        }

        /** True when a span casts a shadow onto this top face (sun from the north-west, about 40° high). */
        private boolean inShadow(int x, int z, int top) {
            for (int k = 1; k <= 8; k++) {
                int count = shadowSource.spans(x - k, z - k, shadowSpans);
                if (count == 0) continue;
                double rayHeight = top + 1 + k * 1.2D;
                for (int i = 0; i < count; i++) {
                    int o = i * MapVoxels.INTS_PER_SPAN;
                    if (shadowSpans[o] + 1 >= rayHeight && shadowSpans[o + 1] <= rayHeight) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void put(int row, int pixel, int x, int z, float depth) {
            int offset = row * frame.width + column;
            frame.argb[offset] = pixel;
            if (frame.hitX != null) {
                frame.hitX[offset] = x;
                frame.hitZ[offset] = z;
                frame.depth[offset] = depth;
            }
            parent[row + 1] = row - 1 + 0; // row is now covered: point to the row above
        }
    }

    public static int shade(int color, float factor) {
        int r = Math.max(0, Math.min(255, Math.round(((color >> 16) & 255) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(((color >> 8) & 255) * factor)));
        int b = Math.max(0, Math.min(255, Math.round((color & 255) * factor)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
