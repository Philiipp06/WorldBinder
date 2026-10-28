package net.worldbinder.export.common;

import net.worldbinder.scene.ProjectDimensions;
import net.worldbinder.scene.SeenPlayer;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Optional server-download lists next to an export:
 * <ul>
 *     <li>{@code worldbinder/players.csv}: the opt-in player roster (tab list and players seen in the world).</li>
 *     <li>{@code worldbinder/dimensions.txt}: a tree of every project dimension with its export folder and contents.</li>
 * </ul>
 */
public final class ServerListsWriter {
    public static final String PLAYERS_FILE = "players.csv";
    public static final String DIMENSIONS_FILE = "dimensions.txt";
    static final String[] CSV_HEADER = {"uuid", "name", "display_name", "self", "in_tab_list", "seen_in_world", "first_seen",
            "last_seen", "game_mode", "latency_ms", "dimension", "x", "y", "z", "samples"};

    private ServerListsWriter() {
    }

    public static void write(WorldScene scene, Path worldFolder) throws IOException {
        Path metadata = worldFolder.resolve("worldbinder");
        Files.createDirectories(metadata);
        if (scene.exportSettings != null && scene.exportSettings.includePlayerList) {
            Files.writeString(metadata.resolve(PLAYERS_FILE), playersCsv(scene), StandardCharsets.UTF_8);
        }
        if (scene.exportSettings == null || scene.exportSettings.includeDimensionTree) {
            Files.writeString(metadata.resolve(DIMENSIONS_FILE), dimensionTree(scene, worldFolder, Lang.language()), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ players.csv

    /** RFC 4180 CSV with a UTF-8 BOM (opens correctly in spreadsheet programs) and spreadsheet-formula protection. */
    public static String playersCsv(WorldScene scene) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(",", CSV_HEADER)).append("\r\n");
        List<SeenPlayer> players = new ArrayList<>(scene.seenPlayers == null ? List.of() : scene.seenPlayers.values());
        players.sort(Comparator.comparing((SeenPlayer player) -> player.name == null ? "" : player.name.toLowerCase(Locale.ROOT))
                .thenComparing(player -> player.uuid == null ? "" : player.uuid));
        for (SeenPlayer player : players) {
            String[] row = {
                    player.uuid, player.name, player.displayName, Boolean.toString(player.self), Boolean.toString(player.inTabList),
                    Boolean.toString(player.seenInWorld), time(player.firstSeen), time(player.lastSeen), player.gameMode,
                    player.latencyMs < 0 ? "" : Integer.toString(player.latencyMs), player.dimension,
                    player.hasPosition ? Integer.toString(player.x) : "", player.hasPosition ? Integer.toString(player.y) : "",
                    player.hasPosition ? Integer.toString(player.z) : "", Integer.toString(player.sightings)
            };
            for (int i = 0; i < row.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(cell(row[i]));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    static String cell(String value) {
        if (value == null) return "";
        String text = value.replace("\r", " ").replace("\n", " ");
        // Spreadsheet programs execute cells starting with these characters as formulas (CSV injection).
        if (!text.isEmpty() && "=+-@\t".indexOf(text.charAt(0)) >= 0 && !text.matches("-?\\d+")) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains(";") || text.startsWith(" ") || text.endsWith(" ")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String time(long millis) {
        return millis <= 0 ? "" : Instant.ofEpochMilli(millis).toString();
    }

    // ------------------------------------------------------------------ dimensions.txt

    public static String dimensionTree(WorldScene root, Path worldFolder, String language) {
        int dataVersion = TargetMinecraftVersion.resolve(root.targetMinecraftVersion).effectiveDataVersion();
        StringBuilder tree = new StringBuilder();
        tree.append(Lang.in(language, "worldbinder.generated.dimensions.title")).append('\n');
        tree.append(Lang.in(language, "worldbinder.generated.dimensions.project", text(root.name), text(root.backendName == null ? root.worldIdentity : root.backendName))).append('\n');
        tree.append(Lang.in(language, "worldbinder.generated.dimensions.target", text(root.targetMinecraftVersion), dataVersion)).append('\n');
        List<WorldScene> dimensions = ProjectDimensions.all(root);
        tree.append(Lang.in(language, "worldbinder.generated.dimensions.count", dimensions.size())).append("\n\n");
        tree.append(text(worldFolder.getFileName() == null ? root.name : worldFolder.getFileName().toString())).append("/\n");
        for (int i = 0; i < dimensions.size(); i++) {
            WorldScene dimension = dimensions.get(i);
            boolean last = i == dimensions.size() - 1;
            String branch = last ? "└── " : "├── ";
            String indent = last ? "    " : "│   ";
            String id = ProjectDimensions.canonical(dimension.dimension);
            String folder = exportFolder(id, dataVersion);
            tree.append(branch).append(id == null ? "?" : id).append(dimension == root ? "  " + Lang.in(language, "worldbinder.generated.dimensions.active") : "").append('\n');
            tree.append(indent).append("├── ").append(Lang.in(language, "worldbinder.generated.dimensions.folder", folder, regionFiles(worldFolder.resolve(folder)))).append('\n');
            if (dimension.dimensionType != null) {
                tree.append(indent).append("├── ").append(Lang.in(language, "worldbinder.generated.dimensions.type", dimension.dimensionType)).append('\n');
            }
            if (dimension.dimensionMinY != Integer.MIN_VALUE && dimension.dimensionHeight > 0) {
                tree.append(indent).append("├── ").append(Lang.in(language, "worldbinder.generated.dimensions.height",
                        dimension.dimensionMinY, dimension.dimensionMinY + dimension.dimensionHeight - 1)).append('\n');
            }
            tree.append(indent).append("├── ").append(Lang.in(language, "worldbinder.generated.dimensions.chunks", dimension.chunkSnapshotCount())).append('\n');
            tree.append(indent).append("├── ").append(Lang.in(language, "worldbinder.generated.dimensions.contents",
                    dimension.blockCount(), dimension.blockEntityCount(), dimension.entityCount())).append('\n');
            tree.append(indent).append("└── ").append(Lang.in(language, "worldbinder.generated.dimensions.containers", containers(dimension))).append('\n');
        }
        tree.append('\n').append(Lang.in(language, "worldbinder.generated.dimensions.note")).append('\n');
        return tree.toString();
    }

    /** Folder of a dimension inside the exported world, following the vanilla layout of the target version. */
    public static String exportFolder(String dimension, int dataVersion) {
        if (dimension == null || "minecraft:overworld".equals(dimension)) {
            return dataVersion >= 4786 ? "dimensions/minecraft/overworld" : ".";
        }
        if (dataVersion < 4786 && "minecraft:the_nether".equals(dimension)) return "DIM-1";
        if (dataVersion < 4786 && "minecraft:the_end".equals(dimension)) return "DIM1";
        String[] parts = dimension.split(":", 2);
        return parts.length == 2 ? "dimensions/" + parts[0] + "/" + parts[1] : "dimensions/" + dimension;
    }

    private static int regionFiles(Path folder) {
        Path region = folder.resolve("region");
        if (!Files.isDirectory(region)) return 0;
        try (var stream = Files.list(region)) {
            return (int) stream.filter(path -> path.getFileName().toString().endsWith(".mca")).count();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static int containers(WorldScene dimension) {
        return dimension.containers == null ? 0 : dimension.containers.size();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
