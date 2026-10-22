package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.ChunkSnapshot;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The 3D live map view. Frames are rendered by {@link MapRenderer3D} on a background thread from a copy of the
 * map data, so the game keeps its frame rate; the last finished frame is shown until the next one is ready.
 * While the camera moves, frames are rendered at half resolution; once it rests, a full resolution frame
 * follows. Pure data changes (new chunks) re-render at most once per second.
 */
public final class Map3DView {
    private static final Map3DView SHARED = new Map3DView();
    private static final long MOTION_HOLD_NANOS = 180_000_000L;
    private static final long DATA_REFRESH_NANOS = 1_000_000_000L;
    private static int serial;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "WorldBinder 3D map");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private record Result(MapRenderer3D.Frame frame, MapRenderer3D.Camera camera, long cameraSignature, long dataSignature,
                          int minHeight, int maxHeight, long renderNanos) {
    }

    private NativeImage image;
    private DynamicTexture texture;
    private Identifier textureId;
    private MapRenderer3D.Frame shownFrame;
    private MapRenderer3D.Camera shownCamera;
    private long shownCameraSignature = Long.MIN_VALUE;
    private long shownDataSignature = Long.MIN_VALUE;
    private long shownAtNanos;
    private volatile Result finished;
    private boolean busy;
    private long submittedCameraSignature = Long.MIN_VALUE;
    private long submittedDataSignature = Long.MIN_VALUE;
    private MapRenderer3D.Camera lastRequested;
    private long lastMotionNanos;
    private int minHeight;
    private int maxHeight = 128;
    private long lastRenderNanos;

    public static Map3DView shared() {
        return SHARED;
    }

    private Map3DView() {
    }

    public boolean hasFrame() {
        return shownFrame != null;
    }

    public long lastRenderMicros() {
        return lastRenderNanos / 1000L;
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public void invalidate() {
        shownCameraSignature = Long.MIN_VALUE;
        submittedCameraSignature = Long.MIN_VALUE;
    }

    /** Drops the shown frame (used when the view is closed, so reopening never flashes an old image). */
    public void reset() {
        invalidate();
        shownFrame = null;
        shownCamera = null;
    }

    /**
     * Schedules rendering when needed, picks up finished frames and draws the newest one into the GUI rectangle.
     *
     * @param requested  camera in GUI pixels (scale = GUI pixels per block)
     * @param resolution texture pixels per GUI pixel for resting frames
     * @param fast       render at preview resolution (transition animation)
     */
    public void draw(GuiGraphicsExtractor context, Map<Long, ChunkSnapshot> captured, MapRenderer3D.Camera requested,
                     int x, int y, int guiWidth, int guiHeight, double resolution, long dataSignature, boolean shading, boolean fast) {
        long now = System.nanoTime();
        if (lastRequested == null || !similar(lastRequested, requested)) {
            lastMotionNanos = now;
        }
        lastRequested = requested;
        boolean moving = fast || now - lastMotionNanos < MOTION_HOLD_NANOS;
        double effective = moving ? Math.max(0.5D, resolution * 0.5D) : resolution;
        int w = Math.max(16, (int) Math.round(guiWidth * effective));
        int h = Math.max(16, (int) Math.round(guiHeight * effective));
        MapRenderer3D.Camera scaled = requested.withScale(requested.scale() * effective);
        long cameraSignature = cameraSignature(scaled, w, h, shading);

        Result result = finished;
        if (result != null) {
            finished = null;
            busy = false;
            show(result);
        }
        boolean cameraChanged = cameraSignature != shownCameraSignature;
        boolean dataChanged = dataSignature != shownDataSignature && now - shownAtNanos > DATA_REFRESH_NANOS;
        boolean alreadySubmitted = cameraSignature == submittedCameraSignature && (dataSignature == submittedDataSignature || !dataChanged);
        if (!busy && (cameraChanged || dataChanged) && !alreadySubmitted) {
            submit(captured, scaled, w, h, cameraSignature, dataSignature, shading);
        }
        if (textureId != null) {
            context.blit(textureId, x, y, x + guiWidth, y + guiHeight, 0.0F, 1.0F, 0.0F, 1.0F);
        }
    }

    private void submit(Map<Long, ChunkSnapshot> captured, MapRenderer3D.Camera camera, int w, int h, long cameraSignature,
                        long dataSignature, boolean shading) {
        busy = true;
        submittedCameraSignature = cameraSignature;
        submittedDataSignature = dataSignature;
        Map<Long, ChunkSnapshot> snapshots = MapData.mergedCopy(captured);
        Map<Long, MapVoxels.Columns> voxels = MapVoxels.get().copy();
        try {
            worker.execute(() -> {
                long start = System.nanoTime();
                try {
                    int[] range = heightRange(snapshots, voxels, camera, w, h);
                    MapRenderer3D.Frame frame = new MapRenderer3D.Frame(w, h, true);
                    MapRenderer3D.render(MapRenderer3D.source(voxels, snapshots::get), MapRenderer3D.source(voxels, snapshots::get),
                            camera, frame, range[0], range[1], shading);
                    finished = new Result(frame, camera, cameraSignature, dataSignature, range[0], range[1], System.nanoTime() - start);
                } catch (RuntimeException exception) {
                    WorldBinder.LOGGER.warn("3D map rendering failed", exception);
                    finished = new Result(null, camera, cameraSignature, dataSignature, 0, 128, System.nanoTime() - start);
                }
            });
        } catch (RuntimeException exception) {
            busy = false;
        }
    }

    private void show(Result result) {
        lastRenderNanos = result.renderNanos();
        shownAtNanos = System.nanoTime();
        shownCameraSignature = result.cameraSignature();
        shownDataSignature = result.dataSignature();
        if (result.frame() == null) {
            return;
        }
        MapRenderer3D.Frame frame = result.frame();
        if (image == null || image.getWidth() != frame.width || image.getHeight() != frame.height) {
            allocate(frame.width, frame.height);
        }
        int[] pixels = frame.argb;
        int w = frame.width;
        for (int row = 0; row < frame.height; row++) {
            int offset = row * w;
            for (int column = 0; column < w; column++) {
                image.setPixel(column, row, pixels[offset + column]);
            }
        }
        texture.upload();
        shownFrame = frame;
        shownCamera = result.camera();
        minHeight = result.minHeight();
        maxHeight = result.maxHeight();
    }

    /** World column {x, z} under a GUI position relative to the view origin, or {@code null}. */
    public int[] hit(double localX, double localY, int guiWidth, int guiHeight) {
        int offset = offset(localX, localY, guiWidth, guiHeight);
        if (offset < 0 || shownFrame.hitX[offset] == Integer.MIN_VALUE) {
            return null;
        }
        return new int[] {shownFrame.hitX[offset], shownFrame.hitZ[offset]};
    }

    /** View depth of the terrain under a GUI position, or +infinity when nothing is drawn there. */
    public float depthAt(double localX, double localY, int guiWidth, int guiHeight) {
        int offset = offset(localX, localY, guiWidth, guiHeight);
        return offset < 0 ? Float.POSITIVE_INFINITY : shownFrame.depth[offset];
    }

    private int offset(double localX, double localY, int guiWidth, int guiHeight) {
        MapRenderer3D.Frame frame = shownFrame;
        if (frame == null || frame.hitX == null || guiWidth <= 0 || guiHeight <= 0) {
            return -1;
        }
        int px = (int) Math.floor(localX * frame.width / (double) guiWidth);
        int py = (int) Math.floor(localY * frame.height / (double) guiHeight);
        if (px < 0 || py < 0 || px >= frame.width || py >= frame.height) {
            return -1;
        }
        return py * frame.width + px;
    }

    /** GUI position {x, y, depth} (relative to the view origin) of a world point in the shown frame. */
    public double[] project(double worldX, double worldY, double worldZ, int guiWidth, int guiHeight) {
        MapRenderer3D.Camera camera = shownCamera;
        MapRenderer3D.Frame frame = shownFrame;
        if (camera == null || frame == null) {
            return null;
        }
        double[] p = MapRenderer3D.project(camera, frame.width, frame.height, worldX, worldY, worldZ);
        p[0] = p[0] * guiWidth / frame.width;
        p[1] = p[1] * guiHeight / frame.height;
        return p;
    }

    /** Camera of the shown frame (yaw/pitch used for markers), or {@code null}. */
    public MapRenderer3D.Camera shownCamera() {
        return shownCamera;
    }

    static int[] heightRange(Map<Long, ChunkSnapshot> snapshots, Map<Long, MapVoxels.Columns> voxels, MapRenderer3D.Camera camera, int w, int h) {
        double reach = Math.hypot(w, h) / camera.scale() / Math.max(0.25D, Math.sin(Math.toRadians(camera.pitchDegrees()))) / 2.0D + 32.0D;
        int radius = (int) Math.min(128, Math.ceil(reach / 16.0D));
        int centerX = (int) Math.floor(camera.targetX()) >> 4;
        int centerZ = (int) Math.floor(camera.targetZ()) >> 4;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
            for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                long key = ChunkPos.pack(cx, cz);
                MapVoxels.Columns columns = voxels.get(key);
                if (columns != null) {
                    min = Math.min(min, columns.minTop);
                    max = Math.max(max, columns.maxTop);
                    continue;
                }
                ChunkSnapshot snapshot = snapshots.get(key);
                if (snapshot == null || snapshot.heights == null || snapshot.heights.length != 256) continue;
                for (int i = 0; i < 256; i += 17) {
                    int value = snapshot.heights[i];
                    if (value == Integer.MIN_VALUE) continue;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }
        if (min > max) {
            min = (int) camera.baseY() - 16;
            max = (int) camera.baseY() + 16;
        }
        return new int[] {min - 12, max + 12};
    }

    private static boolean similar(MapRenderer3D.Camera a, MapRenderer3D.Camera b) {
        return Math.abs(a.targetX() - b.targetX()) < 0.01D && Math.abs(a.targetZ() - b.targetZ()) < 0.01D
                && Math.abs(a.baseY() - b.baseY()) < 0.05D && Math.abs(a.yawDegrees() - b.yawDegrees()) < 0.01D
                && Math.abs(a.pitchDegrees() - b.pitchDegrees()) < 0.01D && Math.abs(a.scale() - b.scale()) < 0.0005D
                && Math.abs(a.heightScale() - b.heightScale()) < 0.001D;
    }

    private static long cameraSignature(MapRenderer3D.Camera camera, int w, int h, boolean shading) {
        long value = 17L;
        value = value * 31L + Math.round(camera.targetX() * 32.0D);
        value = value * 31L + Math.round(camera.targetZ() * 32.0D);
        value = value * 31L + Math.round(camera.baseY() * 8.0D);
        value = value * 31L + Math.round(camera.yawDegrees() * 20.0D);
        value = value * 31L + Math.round(camera.pitchDegrees() * 20.0D);
        value = value * 31L + Math.round(camera.scale() * 1000.0D);
        value = value * 31L + Math.round(camera.heightScale() * 100.0D);
        value = value * 31L + w * 7919L + h;
        return value * 31L + (shading ? 1 : 0);
    }

    private void allocate(int w, int h) {
        Minecraft client = Minecraft.getInstance();
        if (textureId != null) {
            client.getTextureManager().release(textureId);
        }
        image = new NativeImage(w, h, true);
        texture = new DynamicTexture(() -> "worldbinder live map 3d", image);
        textureId = Identifier.fromNamespaceAndPath(WorldBinder.MOD_ID, "live_map/view3d_" + (serial++));
        client.getTextureManager().register(textureId, texture);
    }
}
