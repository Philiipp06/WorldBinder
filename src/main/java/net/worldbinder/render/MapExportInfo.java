package net.worldbinder.render;

import net.worldbinder.export.common.ExportLegalNotice;
import net.worldbinder.util.Lang;

import java.util.Locale;
import java.util.function.Function;

/** Human-readable map metadata; file-format syntax and geometry stay independent of the UI language. */
public final class MapExportInfo {
    private MapExportInfo() {}

    public static String image2d(String world, int[] bounds, int step, int chunks, int detail, boolean tiles) {
        return bilingual(language -> {
            String files = tiles ? Lang.in(language, "worldbinder.generated.map.tiles")
                    : Lang.in(language, "worldbinder.generated.map.image_file", detail);
            return line(language, "image_title") + world(language, world)
                + line(language, "chunks", chunks)
                + line(language, "bounds", bounds[0], bounds[2], bounds[1], bounds[3])
                + line(language, "origin", bounds[0] * 16, bounds[1] * 16)
                + line(language, "blocks_per_pixel", step)
                + line(language, "files", files);
        });
    }

    public static String image3d(String world, double x, double z, double baseY, double yaw, double pitch,
                                 double heightScale, int width, int height, int supersample, int spans) {
        return bilingual(language -> line(language, "render_title") + world(language, world)
                + line(language, "center", number(x, 1), number(z, 1), number(baseY, 1))
                + line(language, "rotation", number(yaw, 0), number(pitch, 0), number(heightScale * 100, 0))
                + line(language, "size", width, height, supersample, supersample)
                + line(language, "render_files") + line(language, "spans", spans));
    }

    public static String model(String world, int chunks, int spans, boolean useSpans, int originX, int originZ,
                               int step, double heightScale, int vertices, int triangles, boolean obj) {
        return bilingual(language -> {
            String coverage = useSpans ? Lang.in(language, "worldbinder.generated.map.model_spans", spans)
                    : Lang.in(language, "worldbinder.generated.map.heightmap");
            return line(language, "model_title") + world(language, world)
                + line(language, "model_chunks", chunks, coverage)
                + line(language, "model_origin", originX, originZ)
                + line(language, "units", step > 1 ? Lang.in(language, "worldbinder.generated.map.cells", step, step) : "")
                + line(language, "height_scale", number(heightScale * 100, 0))
                + line(language, "geometry", vertices, triangles)
                + line(language, "files", Lang.in(language, "worldbinder.generated.map.glb_file")
                    + (obj ? ", " + Lang.in(language, "worldbinder.generated.map.obj_file") : ""))
                + line(language, "surface_warning");
        });
    }

    public static String objHeader() {
        return "# " + Lang.in("de_de", "worldbinder.generated.map.obj_header") + "\n# "
                + Lang.in("en_us", "worldbinder.generated.map.obj_header") + "\n";
    }

    private static String bilingual(Function<String, String> section) {
        return section.apply("de_de") + "\n" + section.apply("en_us") + ExportLegalNotice.text();
    }
    private static String world(String language, String world) { return line(language, "world", world); }
    private static String line(String language, String key, Object... args) {
        return Lang.in(language, "worldbinder.generated.map." + key, args) + "\n";
    }
    private static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
