package net.worldbinder.scene;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Compact lossless section/map arrays; also reads numeric/boolean arrays written by early schema-3 builds. */
public final class ArchiveArrays {
    private static final int MAX_BYTES = 1024 * 1024;
    private ArchiveArrays() {}
    public static GsonBuilder configure(GsonBuilder builder) {
        return builder.registerTypeAdapter(byte[].class, new TypeAdapter<byte[]>() {
            public void write(JsonWriter writer, byte[] bytes) throws IOException { writer.value(encode(bytes)); }
            public byte[] read(JsonReader reader) throws IOException {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); reader.beginArray();
                    while (reader.hasNext()) { if (bytes.size() >= MAX_BYTES) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_byte_array_too_large")); bytes.write(reader.nextInt()); }
                    reader.endArray(); return bytes.toByteArray();
                }
                return decode(reader.nextString());
            }
        }.nullSafe()).registerTypeAdapter(boolean[].class, new TypeAdapter<boolean[]>() {
            public void write(JsonWriter writer, boolean[] flags) throws IOException {
                byte[] packed = new byte[(flags.length + 7) / 8];
                for (int i = 0; i < flags.length; i++) if (flags[i]) packed[i >> 3] |= (byte)(1 << (i & 7));
                writer.value(flags.length + ":" + encode(packed));
            }
            public boolean[] read(JsonReader reader) throws IOException {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    List<Boolean> values = new ArrayList<>(); reader.beginArray();
                    while (reader.hasNext()) { if (values.size() >= MAX_BYTES) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_coverage_too_large")); values.add(reader.nextBoolean()); }
                    reader.endArray(); boolean[] result = new boolean[values.size()]; for (int i=0;i<result.length;i++) result[i]=values.get(i);return result;
                }
                String encoded = reader.nextString(); int delimiter = encoded.indexOf(':');
                if (delimiter <= 0) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_archive_coverage_encoding"));
                int length = Integer.parseInt(encoded.substring(0,delimiter));
                if (length < 0 || length > MAX_BYTES) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_archive_coverage_length"));
                byte[] packed = decode(encoded.substring(delimiter+1));
                if (packed.length != (length + 7) / 8) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.truncated_archive_coverage"));
                boolean[] flags = new boolean[length]; for (int i=0;i<length;i++) flags[i]=(packed[i>>3] & (1 << (i & 7)))!=0; return flags;
            }
        }.nullSafe());
    }
    private static String encode(byte[] bytes) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream output = new DeflaterOutputStream(compressed)) { output.write(bytes); }
        return "zlib64:" + Base64.getEncoder().encodeToString(compressed.toByteArray());
    }
    private static byte[] decode(String value) throws IOException {
        if (!value.startsWith("zlib64:")) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.unknown_archive_array_encoding"));
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value.substring(7))))) {
            byte[] decoded = input.readNBytes(MAX_BYTES + 1);
            if (decoded.length > MAX_BYTES) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_array_exceeds_size_limit"));
            return decoded;
        } catch (IllegalArgumentException exception) { throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_archive_array"), exception); }
    }
}
