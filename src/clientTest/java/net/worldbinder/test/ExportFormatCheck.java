package net.worldbinder.test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.worldbinder.export.common.WorldExportEngine;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Checks exported worlds with the game's own codecs, so format changes between Minecraft versions (like the
 * 26.3 block state palette change from Name/Properties to id/properties) fail the test instead of producing
 * worlds full of air.
 */
final class ExportFormatCheck {
    private ExportFormatCheck() {
    }

    /** Palette entries written for the running version must decode to the same block state. */
    static void paletteMatchesRunningVersion() {
        int running = SharedConstants.getCurrentVersion().dataVersion().version();
        List<String> states = List.of(
                "minecraft:stone",
                "minecraft:air",
                "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                "minecraft:grass_block[snowy=false]",
                "minecraft:note_block[instrument=banjo,note=3,powered=false]",
                "minecraft:oak_leaves[distance=3,persistent=true,waterlogged=false]");
        for (String state : states) {
            Tag entry = WorldExportEngine.paletteEntry(state, running);
            BlockState decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(null);
            BlockState expected = BlockStateStrings.parse(state);
            check(decoded != null, "Palette entry decodes with the game codec: " + state + " -> " + entry);
            check(decoded.equals(expected), "Palette entry keeps the block state: " + state + " -> " + decoded);
        }
    }

    /** Validates an exported world folder: chunk palettes, dimension set and saved data formats. */
    static void exportedWorld(Path folder, String targetVersion) throws IOException {
        int running = SharedConstants.getCurrentVersion().dataVersion().version();
        int target = TargetMinecraftVersion.resolve(targetVersion).effectiveDataVersion();
        int solid = 0;
        int checked = 0;
        for (Path region : regionFiles(folder)) {
            for (CompoundTag chunk : chunks(region)) {
                Tag dataVersion = chunk.get("DataVersion");
                check(dataVersion != null, "Exported chunk has a DataVersion");
                if (!(chunk.get("sections") instanceof ListTag sections)) continue;
                for (int i = 0; i < sections.size(); i++) {
                    if (!(sections.get(i) instanceof CompoundTag section)) continue;
                    if (!(section.get("block_states") instanceof CompoundTag blockStates)) continue;
                    if (!(blockStates.get("palette") instanceof ListTag palette)) continue;
                    for (int p = 0; p < palette.size(); p++) {
                        Tag entry = palette.get(p);
                        checked++;
                        if (target == running) {
                            BlockState decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(null);
                            check(decoded != null, "Exported palette entry is readable by this Minecraft version: " + entry);
                            if (!decoded.isAir()) solid++;
                        } else if (!entry.toString().contains("air")) {
                            solid++;
                        }
                    }
                }
            }
        }
        check(checked > 0, "Exported world contains block palettes");
        check(solid > 0, "Exported world contains solid blocks");

        Path worldGen = folder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        if (Files.isRegularFile(worldGen)) {
            String text = NbtIo.readCompressed(worldGen, NbtAccounter.unlimitedHeap()).toString();
            check(text.contains("minecraft:the_nether") && text.contains("minecraft:the_end"),
                    "World generation settings contain the vanilla dimension set (no experimental warning)");
        }
        Path stopwatches = folder.resolve("data").resolve("minecraft").resolve("stopwatches.dat");
        if (Files.isRegularFile(stopwatches)) {
            CompoundTag root = NbtIo.readCompressed(stopwatches, NbtAccounter.unlimitedHeap());
            check(root.get("data") instanceof CompoundTag data && data.get("stopwatches") instanceof CompoundTag,
                    "Stopwatches saved data is a map");
        }
    }

    private static List<Path> regionFiles(Path folder) throws IOException {
        List<Path> result = new java.util.ArrayList<>();
        for (Path base : List.of(folder.resolve("region"), folder.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region"))) {
            if (!Files.isDirectory(base)) continue;
            try (var stream = Files.list(base)) {
                stream.filter(path -> path.toString().endsWith(".mca")).forEach(result::add);
            }
        }
        return result;
    }

    private static List<CompoundTag> chunks(Path region) throws IOException {
        byte[] data = Files.readAllBytes(region);
        List<CompoundTag> result = new java.util.ArrayList<>();
        if (data.length < 8192) return result;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        for (int i = 0; i < 1024; i++) {
            int location = buffer.getInt(i * 4);
            if (location == 0) continue;
            int offset = (location >>> 8) * 4096;
            if (offset + 5 > data.length) continue;
            int length = buffer.getInt(offset);
            int type = data[offset + 4];
            InputStream raw = new ByteArrayInputStream(data, offset + 5, length - 1);
            InputStream decoded = type == 1 ? new GZIPInputStream(raw) : type == 2 ? new InflaterInputStream(raw) : raw;
            try (DataInputStream input = new DataInputStream(decoded)) {
                result.add(NbtIo.read(input, NbtAccounter.unlimitedHeap()));
            }
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
