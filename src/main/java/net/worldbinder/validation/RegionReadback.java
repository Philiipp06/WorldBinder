package net.worldbinder.validation;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Independent, read-only Anvil reader: validation must never create/repair the files it inspects. */
public final class RegionReadback {
    private RegionReadback() {}
    public static Map<Long, CompoundTag> read(Path directory, List<Finding> errors) {
        Map<Long, CompoundTag> chunks = new LinkedHashMap<>();
        visit(directory, errors, chunks::put);
        return chunks;
    }

    /** Validate/decompress one chunk at a time; large worlds must not retain all decoded NBT. */
    public static Set<Long> visit(Path directory, List<Finding> errors, java.util.function.BiConsumer<Long,CompoundTag> visitor) {
        Set<Long> chunks = new LinkedHashSet<>();
        if (directory == null || !Files.isDirectory(directory)) return chunks;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".mca")).toList()) {
                String[] name = path.getFileName().toString().split("\\.");
                try {
                    int rx = Integer.parseInt(name[1]), rz = Integer.parseInt(name[2]);
                    readFile(path, rx, rz, chunks, errors, visitor);
                } catch (Exception exception) { errors.add(Finding.of("worldbinder.finding.value_value", path.getFileName(), Finding.detail(exception))); }
            }
        } catch (IOException exception) { errors.add(Finding.of("worldbinder.finding.value_value", directory, Finding.detail(exception))); }
        return chunks;
    }

    private static void readFile(Path path, int rx, int rz, Set<Long> chunks, List<Finding> errors, java.util.function.BiConsumer<Long,CompoundTag> visitor) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 8192 || file.length() % 4096 != 0) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_region_size_header"));
            int[] locations = new int[1024]; for (int i = 0; i < 1024; i++) locations[i] = file.readInt();
            BitSet allocated = new BitSet(); allocated.set(0, 2);
            for (int index = 0; index < 1024; index++) {
                if (locations[index] == 0) continue;
                int x = rx * 32 + index % 32, z = rz * 32 + index / 32;
                String label = path.getFileName() + " chunk " + x + "," + z;
                try {
                    int sector = locations[index] >>> 8, count = locations[index] & 255;
                    if (sector < 2 || count == 0 || ((long) sector + count) * 4096 > file.length()) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.region_offset_outside_file"));
                    int used = allocated.nextSetBit(sector);
                    if (used >= 0 && used < sector + count) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.overlapping_chunk_sectors"));
                    allocated.set(sector, sector + count);
                    file.seek(sector * 4096L);
                    int length = file.readInt(), compression = file.readUnsignedByte();
                    if (length < 1 || length > count * 4096 - 4) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_compressed_chunk_length"));
                    InputStream raw;
                    if ((compression & 128) != 0) raw = Files.newInputStream(path.resolveSibling("c." + x + "." + z + ".mcc"));
                    else { byte[] data = new byte[length - 1]; file.readFully(data); raw = new ByteArrayInputStream(data); }
                    try (raw; InputStream decoded = switch (compression & 127) {
                        case 1 -> new GZIPInputStream(raw); case 2 -> new InflaterInputStream(raw); case 3 -> raw;
                        default -> throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.unsupported_anvil_compression_value", compression));
                    }; DataInputStream input = new DataInputStream(decoded)) {
                        CompoundTag nbt = NbtIo.read(input, NbtAccounter.create(64 * 1024 * 1024L));
                        if (nbt == null) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.missing_chunk_nbt"));
                        long key=net.worldbinder.scene.ProjectData.chunkKey(x, z);
                        chunks.add(key);
                        visitor.accept(key, nbt);
                    }
                } catch (Exception exception) { errors.add(Finding.of("worldbinder.finding.value_value", label, Finding.detail(exception))); }
            }
        }
    }

    public static int[] unpack(long[] data, int count, int bits, boolean contiguous) {
        int expected = contiguous ? (count * bits + 63) / 64 : (count + (64 / bits) - 1) / (64 / bits);
        if (data.length != expected) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.packed_array_length_value_expected_value", data.length, expected));
        int[] values = new int[count]; long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            int index, shift;
            if (contiguous) { index = (i * bits) >>> 6; shift = (i * bits) & 63; }
            else { index = i / (64 / bits); shift = (i % (64 / bits)) * bits; }
            long value = data[index] >>> shift;
            if (contiguous && shift + bits > 64) value |= data[index + 1] << (64 - shift);
            values[i] = (int) (value & mask);
        }
        return values;
    }
}
