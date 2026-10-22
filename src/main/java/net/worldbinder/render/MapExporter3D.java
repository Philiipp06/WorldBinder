package net.worldbinder.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.world.level.ChunkPos;
import net.worldbinder.export.common.ExportLegalNotice;
import net.worldbinder.scene.ChunkSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 3D exports of the live map:
 * <ul>
 *     <li>{@link Mode#MODEL}: the known surface as a colored block mesh with real overhangs (bridges, tracks,
 *     tree crowns) where block spans are known, written as binary glTF ({@code .glb}: Blender, Windows 3D Viewer,
 *     web viewers) and, for smaller areas, as OBJ with vertex colors. Flat areas are merged into large quads;
 *     very large areas are merged into 2x2, 4x4 … block cells.</li>
 *     <li>{@link Mode#RENDER}: the current 3D view in high resolution, anti-aliased (2x2 supersampling), once with
 *     a transparent and once with a sky background.</li>
 * </ul>
 * Every export folder contains a {@code map_info.txt} with the legal notice.
 */
public final class MapExporter3D {
    public enum Mode { MODEL, RENDER }

    private static final int MAX_CELLS = 1_500_000;
    private static final int MAX_OBJ_VERTICES = 2_000_000;
    private static final int MAX_RENDER_SIDE = 6144;
    private static final int SUPERSAMPLE = 2;
    private static final int STRIP = 256;

    private MapExporter3D() {
    }

    public static CompletableFuture<MapExporter.Result> export(Map<Long, ChunkSnapshot> captured, Mode mode, MapRenderer3D.Camera camera,
                                                              int viewWidth, int viewHeight, boolean shading) {
        Map<Long, ChunkSnapshot> merged = MapData.mergedCopy(captured);
        Map<Long, MapVoxels.Columns> voxels = MapVoxels.get().copy();
        Path root = MapExporter.newExportFolder();
        String world = MapExporter.worldName();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(root);
                return mode == Mode.MODEL ? exportModel(merged, voxels, root, world, camera == null ? 1.0D : camera.heightScale())
                        : exportRender(merged, voxels, root, world, camera, viewWidth, viewHeight, shading);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    // ------------------------------------------------------------------ render

    private static MapExporter.Result exportRender(Map<Long, ChunkSnapshot> merged, Map<Long, MapVoxels.Columns> voxels, Path root, String world,
                                                   MapRenderer3D.Camera camera, int viewWidth, int viewHeight, boolean shading) throws IOException {
        if (camera == null) {
            throw new IOException("3D view is not available");
        }
        double factor = Math.min(4.0D, MAX_RENDER_SIDE / (double) Math.max(viewWidth, viewHeight));
        int w = Math.max(64, (int) Math.round(viewWidth * factor));
        int h = Math.max(64, (int) Math.round(viewHeight * factor));
        int fullW = w * SUPERSAMPLE;
        int fullH = h * SUPERSAMPLE;
        MapRenderer3D.Camera big = camera.withScale(camera.scale() * factor * SUPERSAMPLE);
        int[] range = Map3DView.heightRange(merged, voxels, big, fullW, fullH);
        MapRenderer3D.ColumnSource source = MapRenderer3D.source(voxels, merged::get);
        MapRenderer3D.ColumnSource shadows = MapRenderer3D.source(voxels, merged::get);
        Path file = root.resolve("map_3d.png");
        Path sky = root.resolve("map_3d_sky.png");
        try (NativeImage image = new NativeImage(w, h, true); NativeImage background = new NativeImage(w, h, true)) {
            for (int strip = 0; strip < fullW; strip += STRIP * SUPERSAMPLE) {
                int stripW = Math.min(STRIP * SUPERSAMPLE, fullW - strip);
                MapRenderer3D.Frame frame = new MapRenderer3D.Frame(stripW, fullH, false, strip, 0, fullW, fullH);
                MapRenderer3D.render(source, shadows, big, frame, range[0], range[1], shading);
                int outX0 = strip / SUPERSAMPLE;
                for (int y = 0; y < h; y++) {
                    int skyColor = skyColor(y, h);
                    for (int x = 0; x < stripW / SUPERSAMPLE; x++) {
                        long a = 0, r = 0, g = 0, b = 0;
                        for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                            int row = (y * SUPERSAMPLE + sy) * stripW;
                            for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                                int argb = frame.argb[row + x * SUPERSAMPLE + sx];
                                int alpha = argb >>> 24;
                                a += alpha;
                                r += ((argb >> 16) & 255) * alpha;
                                g += ((argb >> 8) & 255) * alpha;
                                b += (argb & 255) * alpha;
                            }
                        }
                        int samples = SUPERSAMPLE * SUPERSAMPLE;
                        int pixel = 0;
                        if (a > 0) {
                            pixel = (int) (a / samples) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                        }
                        image.setPixel(outX0 + x, y, pixel);
                        background.setPixel(outX0 + x, y, over(pixel, skyColor));
                    }
                }
            }
            image.writeToFile(file);
            background.writeToFile(sky);
        }
        try (Writer writer = Files.newBufferedWriter(root.resolve("map_info.txt"))) {
            writer.write("WorldBinder live map 3D render\n");
            writer.write("World: " + world + "\n");
            writer.write(String.format(Locale.ROOT, "Center: x %.1f, z %.1f, pivot height %.1f%n", camera.targetX(), camera.targetZ(), camera.baseY()));
            writer.write(String.format(Locale.ROOT, "Rotation: %.0f deg, tilt: %.0f deg, height scale: %.0f%%%n",
                    camera.yawDegrees(), camera.pitchDegrees(), camera.heightScale() * 100.0D));
            writer.write("Size: " + w + " x " + h + " px (" + SUPERSAMPLE + "x" + SUPERSAMPLE + " supersampled)\n");
            writer.write("Files: map_3d.png (transparent background), map_3d_sky.png (sky background)\n");
            writer.write("Chunks with block spans: " + voxels.size() + " (other chunks are drawn from the height map)\n");
            writer.write(ExportLegalNotice.text());
        }
        return new MapExporter.Result(file, w, h, merged.size(), 2);
    }

    private static int skyColor(int y, int h) {
        float t = y / (float) Math.max(1, h - 1);
        int top = 0xFF9CC7F2;
        int bottom = 0xFFE4F0FB;
        int r = Math.round(((top >> 16) & 255) * (1 - t) + ((bottom >> 16) & 255) * t);
        int g = Math.round(((top >> 8) & 255) * (1 - t) + ((bottom >> 8) & 255) * t);
        int b = Math.round((top & 255) * (1 - t) + (bottom & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int over(int front, int back) {
        int a = front >>> 24;
        if (a == 255) return front;
        if (a == 0) return back;
        float t = a / 255.0F;
        int r = Math.round(((front >> 16) & 255) * t + ((back >> 16) & 255) * (1 - t));
        int g = Math.round(((front >> 8) & 255) * t + ((back >> 8) & 255) * (1 - t));
        int b = Math.round((front & 255) * t + (back & 255) * (1 - t));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ------------------------------------------------------------------ model

    private static MapExporter.Result exportModel(Map<Long, ChunkSnapshot> merged, Map<Long, MapVoxels.Columns> voxels, Path root, String world,
                                                  double heightScale) throws IOException {
        Map<Long, Boolean> known = new HashMap<>();
        for (long key : merged.keySet()) known.put(key, Boolean.TRUE);
        for (long key : voxels.keySet()) known.put(key, Boolean.TRUE);
        int minCX = Integer.MAX_VALUE, minCZ = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE, maxCZ = Integer.MIN_VALUE;
        for (long key : known.keySet()) {
            int cx = (int) key;
            int cz = (int) (key >> 32);
            minCX = Math.min(minCX, cx);
            minCZ = Math.min(minCZ, cz);
            maxCX = Math.max(maxCX, cx);
            maxCZ = Math.max(maxCZ, cz);
        }
        if (minCX > maxCX) {
            throw new IOException("No map data to export");
        }
        int blocksX = (maxCX - minCX + 1) * 16;
        int blocksZ = (maxCZ - minCZ + 1) * 16;
        int step = 1;
        while ((long) known.size() * 256L / ((long) step * step) > MAX_CELLS || (long) (blocksX / step) * (blocksZ / step) > MAX_CELLS * 4L) {
            step *= 2;
        }
        int gw = (blocksX + step - 1) / step;
        int gd = (blocksZ + step - 1) / step;
        int originX = minCX * 16;
        int originZ = minCZ * 16;
        boolean useSpans = step == 1;
        MapRenderer3D.ColumnSource source = MapRenderer3D.source(useSpans ? voxels : Map.of(), merged::get);

        // column spans of the grid (top span for merged cells)
        int[][] cells = new int[gw * gd][];
        int floor = Integer.MAX_VALUE;
        int[] buffer = new int[MapVoxels.MAX_SPANS * MapVoxels.INTS_PER_SPAN + 8];
        for (int gz = 0; gz < gd; gz++) {
            for (int gx = 0; gx < gw; gx++) {
                int[] best = null;
                for (int dz = 0; dz < step; dz++) {
                    for (int dx = 0; dx < step; dx++) {
                        int x = originX + gx * step + dx;
                        int z = originZ + gz * step + dz;
                        if (!known.containsKey(ChunkPos.pack(x >> 4, z >> 4))) continue;
                        int count = source.spans(x, z, buffer);
                        if (count == 0) continue;
                        if (best == null || buffer[0] > best[0]) {
                            best = java.util.Arrays.copyOf(buffer, useSpans ? count * MapVoxels.INTS_PER_SPAN : MapVoxels.INTS_PER_SPAN);
                        }
                    }
                }
                if (best != null) {
                    cells[gz * gw + gx] = best;
                    for (int i = 0; i < best.length; i += MapVoxels.INTS_PER_SPAN) {
                        floor = Math.min(floor, best[i + 1] <= MapVoxels.FLOOR ? best[i] : best[i + 1]);
                    }
                }
            }
        }
        if (floor == Integer.MAX_VALUE) {
            throw new IOException("No map data to export");
        }
        floor -= 3;
        float vs = (float) heightScale;
        Mesh mesh = new Mesh();

        // top faces of the uppermost spans, greedily merged into rectangles of equal height and color
        boolean[] done = new boolean[gw * gd];
        for (int gz = 0; gz < gd; gz++) {
            for (int gx = 0; gx < gw; gx++) {
                int index = gz * gw + gx;
                int[] cell = cells[index];
                if (cell == null || done[index]) continue;
                int top = cell[0];
                int color = cell[2];
                int width = 1;
                while (gx + width < gw && sameTop(cells[index + width], top, color) && !done[index + width]) width++;
                int depth = 1;
                outer:
                while (gz + depth < gd) {
                    for (int i = 0; i < width; i++) {
                        int other = (gz + depth) * gw + gx + i;
                        if (!sameTop(cells[other], top, color) || done[other]) break outer;
                    }
                    depth++;
                }
                for (int dz = 0; dz < depth; dz++) {
                    for (int dx = 0; dx < width; dx++) done[(gz + dz) * gw + gx + dx] = true;
                }
                float x0 = gx * step, z0 = gz * step, x1 = (gx + width) * step, z1 = (gz + depth) * step;
                float y = (top + 1) * vs;
                mesh.quad(x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, color);
            }
        }

        // lower spans' top faces, bottoms of hanging spans and exposed side faces
        for (int gz = 0; gz < gd; gz++) {
            for (int gx = 0; gx < gw; gx++) {
                int[] cell = cells[gz * gw + gx];
                if (cell == null) continue;
                float x0 = gx * step, z0 = gz * step, x1 = x0 + step, z1 = z0 + step;
                for (int i = 0; i < cell.length; i += MapVoxels.INTS_PER_SPAN) {
                    int top = cell[i];
                    int bottom = Math.max(cell[i + 1], floor);
                    int topColor = cell[i + 2];
                    int sideColor = cell[i + 3];
                    float yTop = (top + 1) * vs;
                    float yBottom = bottom * vs;
                    if (i > 0) {
                        mesh.quad(x0, yTop, z0, x0, yTop, z1, x1, yTop, z1, x1, yTop, z0, topColor);
                    }
                    if (cell[i + 1] > floor) {
                        mesh.quad(x0, yBottom, z0, x1, yBottom, z0, x1, yBottom, z1, x0, yBottom, z1, MapRenderer3D.shade(sideColor, 0.5F));
                    }
                    int[] north = gz > 0 ? cells[(gz - 1) * gw + gx] : null;
                    int[] south = gz < gd - 1 ? cells[(gz + 1) * gw + gx] : null;
                    int[] west = gx > 0 ? cells[gz * gw + gx - 1] : null;
                    int[] east = gx < gw - 1 ? cells[gz * gw + gx + 1] : null;
                    for (int[] range : exposed(bottom, top + 1, north, floor)) {
                        float a = range[1] * vs, b = range[0] * vs;
                        mesh.quad(x1, a, z0, x1, b, z0, x0, b, z0, x0, a, z0, MapRenderer3D.shade(sideColor, 0.80F));
                    }
                    for (int[] range : exposed(bottom, top + 1, south, floor)) {
                        float a = range[1] * vs, b = range[0] * vs;
                        mesh.quad(x0, a, z1, x0, b, z1, x1, b, z1, x1, a, z1, MapRenderer3D.shade(sideColor, 0.72F));
                    }
                    for (int[] range : exposed(bottom, top + 1, west, floor)) {
                        float a = range[1] * vs, b = range[0] * vs;
                        mesh.quad(x0, a, z0, x0, b, z0, x0, b, z1, x0, a, z1, MapRenderer3D.shade(sideColor, 0.66F));
                    }
                    for (int[] range : exposed(bottom, top + 1, east, floor)) {
                        float a = range[1] * vs, b = range[0] * vs;
                        mesh.quad(x1, a, z1, x1, b, z1, x1, b, z0, x1, a, z0, MapRenderer3D.shade(sideColor, 0.86F));
                    }
                }
            }
        }
        if (mesh.vertexCount == 0) {
            throw new IOException("No map data to export");
        }
        Path glb = root.resolve("map_3d.glb");
        writeGlb(mesh, glb);
        int files = 1;
        boolean obj = mesh.vertexCount <= MAX_OBJ_VERTICES;
        if (obj) {
            writeObj(mesh, root.resolve("map_3d.obj"));
            files++;
        }
        try (Writer writer = Files.newBufferedWriter(root.resolve("map_info.txt"))) {
            writer.write("WorldBinder live map 3D model\n");
            writer.write("World: " + world + "\n");
            writer.write("Chunks: " + known.size() + " (" + (useSpans ? voxels.size() + " with block spans and overhangs" : "height map only") + ")\n");
            writer.write(String.format(Locale.ROOT, "Model origin (0,0,0) = block x %d, y 0, z %d%n", originX, originZ));
            writer.write("Units: 1 = 1 block" + (step > 1 ? " (surface merged into " + step + "x" + step + " block cells)" : "") + "\n");
            writer.write(String.format(Locale.ROOT, "Height scale: %.0f%%%n", heightScale * 100.0D));
            writer.write("Vertices: " + mesh.vertexCount + ", triangles: " + mesh.indexCount / 3 + "\n");
            writer.write("Files: map_3d.glb (glTF 2.0 binary, vertex colors, unlit)" + (obj ? ", map_3d.obj (OBJ with vertex colors)" : "") + "\n");
            writer.write("Note: only surfaces known to the map are included, not complete block data or caves.\n");
            writer.write(ExportLegalNotice.text());
        }
        return new MapExporter.Result(glb, gw, gd, known.size(), files);
    }

    private static boolean sameTop(int[] cell, int top, int color) {
        return cell != null && cell[0] == top && cell[2] == color;
    }

    /** Parts of [from, to) not covered by any span of the neighbor column. */
    private static java.util.List<int[]> exposed(int from, int to, int[] neighbor, int floor) {
        java.util.List<int[]> result = new java.util.ArrayList<>();
        result.add(new int[] {from, to});
        if (neighbor == null) {
            return result;
        }
        for (int i = 0; i < neighbor.length; i += MapVoxels.INTS_PER_SPAN) {
            int coverFrom = Math.max(neighbor[i + 1], floor);
            int coverTo = neighbor[i] + 1;
            java.util.List<int[]> next = new java.util.ArrayList<>();
            for (int[] range : result) {
                if (coverTo <= range[0] || coverFrom >= range[1]) {
                    next.add(range);
                    continue;
                }
                if (coverFrom > range[0]) next.add(new int[] {range[0], coverFrom});
                if (coverTo < range[1]) next.add(new int[] {coverTo, range[1]});
            }
            result = next;
            if (result.isEmpty()) break;
        }
        return result;
    }

    /** Growable vertex/index buffers: position (3 floats), color (ARGB). */
    private static final class Mesh {
        float[] positions = new float[3 * 65536];
        int[] colors = new int[65536];
        int[] indices = new int[98304];
        int vertexCount;
        int indexCount;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                  float dx, float dy, float dz, int argb) {
            ensure(4, 6);
            int base = vertexCount;
            vertex(ax, ay, az, argb);
            vertex(bx, by, bz, argb);
            vertex(cx, cy, cz, argb);
            vertex(dx, dy, dz, argb);
            indices[indexCount++] = base;
            indices[indexCount++] = base + 1;
            indices[indexCount++] = base + 2;
            indices[indexCount++] = base;
            indices[indexCount++] = base + 2;
            indices[indexCount++] = base + 3;
        }

        private void vertex(float x, float y, float z, int argb) {
            int p = vertexCount * 3;
            positions[p] = x;
            positions[p + 1] = y;
            positions[p + 2] = z;
            colors[vertexCount] = argb;
            vertexCount++;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        private void ensure(int vertices, int idx) {
            if (vertexCount + vertices > colors.length) {
                int size = colors.length * 2;
                positions = java.util.Arrays.copyOf(positions, size * 3);
                colors = java.util.Arrays.copyOf(colors, size);
            }
            if (indexCount + idx > indices.length) {
                indices = java.util.Arrays.copyOf(indices, indices.length * 2);
            }
        }
    }

    private static void writeGlb(Mesh mesh, Path file) throws IOException {
        int positionBytes = mesh.vertexCount * 12;
        int colorBytes = mesh.vertexCount * 4;
        int indexBytes = mesh.indexCount * 4;
        int binLength = positionBytes + colorBytes + indexBytes;
        String copyright = ExportLegalNotice.shortLine().replace("\\", "\\\\").replace("\"", "\\\"");
        String json = String.format(Locale.ROOT, """
                {"asset":{"version":"2.0","generator":"WorldBinder live map","copyright":"%s"},\
                "scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0,"name":"WorldBinder map"}],\
                "extensionsUsed":["KHR_materials_unlit"],\
                "materials":[{"name":"map","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1},"doubleSided":true,"extensions":{"KHR_materials_unlit":{}}}],\
                "meshes":[{"primitives":[{"attributes":{"POSITION":0,"COLOR_0":1},"indices":2,"material":0}]}],\
                "buffers":[{"byteLength":%d}],\
                "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":%d,"target":34962},\
                {"buffer":0,"byteOffset":%d,"byteLength":%d,"target":34962},\
                {"buffer":0,"byteOffset":%d,"byteLength":%d,"target":34963}],\
                "accessors":[{"bufferView":0,"componentType":5126,"count":%d,"type":"VEC3","min":[%s,%s,%s],"max":[%s,%s,%s]},\
                {"bufferView":1,"componentType":5121,"normalized":true,"count":%d,"type":"VEC4"},\
                {"bufferView":2,"componentType":5125,"count":%d,"type":"SCALAR"}]}""",
                copyright, binLength, positionBytes, positionBytes, colorBytes, positionBytes + colorBytes, indexBytes,
                mesh.vertexCount, num(mesh.minX), num(mesh.minY), num(mesh.minZ), num(mesh.maxX), num(mesh.maxY), num(mesh.maxZ),
                mesh.vertexCount, mesh.indexCount);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonPadded = (jsonBytes.length + 3) & ~3;
        int binPadded = (binLength + 3) & ~3;
        int total = 12 + 8 + jsonPadded + 8 + binPadded;
        try (OutputStream out = new java.io.BufferedOutputStream(Files.newOutputStream(file), 1 << 20)) {
            ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(0x46546C67).putInt(2).putInt(total);
            header.putInt(jsonPadded).putInt(0x4E4F534A);
            out.write(header.array());
            out.write(jsonBytes);
            for (int i = jsonBytes.length; i < jsonPadded; i++) out.write(' ');
            ByteBuffer binHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            binHeader.putInt(binPadded).putInt(0x004E4942);
            out.write(binHeader.array());
            ByteBuffer chunk = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < mesh.vertexCount * 3; i++) {
                if (chunk.remaining() < 4) flush(chunk, out);
                chunk.putFloat(mesh.positions[i]);
            }
            for (int i = 0; i < mesh.vertexCount; i++) {
                if (chunk.remaining() < 4) flush(chunk, out);
                int c = mesh.colors[i];
                chunk.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 0xFF);
            }
            for (int i = 0; i < mesh.indexCount; i++) {
                if (chunk.remaining() < 4) flush(chunk, out);
                chunk.putInt(mesh.indices[i]);
            }
            flush(chunk, out);
            for (int i = binLength; i < binPadded; i++) out.write(0);
        }
    }

    private static void flush(ByteBuffer buffer, OutputStream out) throws IOException {
        out.write(buffer.array(), 0, buffer.position());
        buffer.clear();
    }

    private static String num(float value) {
        return Float.toString(value);
    }

    private static void writeObj(Mesh mesh, Path file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(Files.newBufferedWriter(file), 1 << 20)) {
            writer.write("# WorldBinder live map 3D model (vertex colors: v x y z r g b)\n");
            writer.write("# " + ExportLegalNotice.shortLine() + "\n");
            writer.write("o WorldBinder_map\n");
            StringBuilder line = new StringBuilder(64);
            for (int i = 0; i < mesh.vertexCount; i++) {
                int c = mesh.colors[i];
                line.setLength(0);
                line.append("v ").append(trim(mesh.positions[i * 3])).append(' ').append(trim(mesh.positions[i * 3 + 1])).append(' ')
                        .append(trim(mesh.positions[i * 3 + 2])).append(' ')
                        .append(color((c >> 16) & 255)).append(' ').append(color((c >> 8) & 255)).append(' ').append(color(c & 255)).append('\n');
                writer.write(line.toString());
            }
            for (int i = 0; i < mesh.indexCount; i += 3) {
                line.setLength(0);
                line.append("f ").append(mesh.indices[i] + 1).append(' ').append(mesh.indices[i + 1] + 1).append(' ').append(mesh.indices[i + 2] + 1).append('\n');
                writer.write(line.toString());
            }
        }
    }

    private static String trim(float value) {
        if (value == Math.rint(value)) return Integer.toString((int) value);
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String color(int channel) {
        return String.format(Locale.ROOT, "%.3f", channel / 255.0F);
    }
}
