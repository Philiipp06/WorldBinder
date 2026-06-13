package net.worldbinder.storage;

import net.minecraft.world.level.ChunkPos;
import net.worldbinder.WorldBinder;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.util.Lang;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongPredicate;
import java.util.stream.Stream;

public final class BlockRecordChunkCache {
    private static final int MAGIC = 0x57424348;
    private static final int VERSION = 1;
    private static final int MAX_RECORDS_PER_CHUNK = 16 * 16 * 4096;
    private static final int MAX_STRING_BYTES = 8 * 1024 * 1024;

    private final Path root;
    private final ExecutorService writer;
    private final Set<CompletableFuture<Void>> pendingWrites = ConcurrentHashMap.newKeySet();
    private final Set<Long> scheduledChunks = ConcurrentHashMap.newKeySet();

    public BlockRecordChunkCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WorldBinder Chunk Cache");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Path root() {
        return root;
    }

    public void writeChunk(long key, List<BlockRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<BlockRecord> valid = new ArrayList<>(records.size());
        for (BlockRecord record : records) {
            if (record != null && record.state != null && !record.state.isBlank()) {
                valid.add(copyRecord(record));
            }
        }
        if (valid.isEmpty()) {
            return;
        }

        Files.createDirectories(root);
        scheduledChunks.add(key);
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                writeChunkNow(key, valid);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, writer);

        pendingWrites.add(future);
        future.whenComplete((ignored, throwable) -> {
            pendingWrites.remove(future);
            if (throwable != null) {
                scheduledChunks.remove(key);
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.write_failed_chunk", ChunkPos.getX(key), ChunkPos.getZ(key)), throwable);
            }
        });
    }

    public void flushWrites() {
        for (CompletableFuture<Void> future : List.copyOf(pendingWrites)) {
            try {
                future.join();
            } catch (CompletionException exception) {
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.write_failed"), exception.getCause() == null ? exception : exception.getCause());
            }
        }
    }

    public List<BlockRecord> readChunk(long key) throws IOException {
        flushWrites();
        Path file = chunkFile(key);
        if (!Files.isRegularFile(file) || Files.size(file) <= 0L) {
            return List.of();
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ)))) {
            int magic = input.readInt();
            int version = input.readInt();
            int count = input.readInt();
            if (magic != MAGIC || version != VERSION || count < 0 || count > MAX_RECORDS_PER_CHUNK) {
                quarantineDamagedFile(file, Lang.string("worldbinder.log.chunk_cache.invalid_header"));
                return List.of();
            }
            List<BlockRecord> records = new ArrayList<>(Math.min(count, 4096));
            for (int i = 0; i < count; i++) {
                BlockRecord record = new BlockRecord();
                record.x = input.readInt();
                record.y = input.readInt();
                record.z = input.readInt();
                record.hasBlockEntity = input.readBoolean();
                record.state = readString(input);
                record.blockEntityNbt = readString(input);
                if (record.state != null && !record.state.isBlank()) {
                    records.add(record);
                }
            }
            return records;
        } catch (EOFException exception) {
            quarantineDamagedFile(file, Lang.string("worldbinder.log.chunk_cache.truncated_file"));
            return List.of();
        } catch (IOException exception) {
            quarantineDamagedFile(file, exception.getClass().getSimpleName());
            return List.of();
        } catch (RuntimeException exception) {
            quarantineDamagedFile(file, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    public int readInto(List<BlockRecord> target, LongPredicate predicate, Set<Long> payloadChunks) {
        if (target == null || !Files.isDirectory(root)) {
            return 0;
        }
        flushWrites();
        int added = 0;
        try (Stream<Path> stream = Files.list(root)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".wbcache")).toList()) {
                Long key = keyFromFile(file);
                if (key == null || predicate != null && !predicate.test(key)) {
                    continue;
                }
                List<BlockRecord> records = readChunkWithoutFlush(key);
                if (records.isEmpty()) {
                    continue;
                }
                target.addAll(records);
                added += records.size();
                if (payloadChunks != null) {
                    payloadChunks.add(key);
                }
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.read_failed", root), exception);
        }
        return added;
    }

    public Set<Long> chunkKeys() {
        flushWrites();
        Set<Long> keys = new LinkedHashSet<>(scheduledChunks);
        if (!Files.isDirectory(root)) {
            return keys;
        }
        try (Stream<Path> stream = Files.list(root)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".wbcache")).toList()) {
                Long key = keyFromFile(file);
                if (key != null) {
                    keys.add(key);
                }
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.read_failed", root), exception);
        }
        return keys;
    }

    public boolean hasChunk(long key) {
        if (scheduledChunks.contains(key)) {
            return true;
        }
        try {
            return Files.isRegularFile(chunkFile(key)) && Files.size(chunkFile(key)) > 0L;
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.verify_failed", chunkFile(key)), exception);
            return false;
        }
    }

    public void deleteChunk(long key) {
        try {
            Files.deleteIfExists(chunkFile(key));
            scheduledChunks.remove(key);
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.delete_failed", chunkFile(key)), exception);
        }
    }

    public void deleteAll() {
        flushWrites();
        writer.shutdown();
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.clean_failed", root), exception);
        }
    }

    private void writeChunkNow(long key, List<BlockRecord> records) throws IOException {
        Path target = chunkFile(key);
        Path temp = target.resolveSibling(target.getFileName().toString() + "." + Thread.currentThread().threadId() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(records.size());
                for (BlockRecord record : records) {
                    output.writeInt(record.x);
                    output.writeInt(record.y);
                    output.writeInt(record.z);
                    output.writeBoolean(record.hasBlockEntity);
                    writeString(output, record.state);
                    writeString(output, record.blockEntityNbt);
                }
            }
            if (Files.size(temp) <= 0L) {
                Files.deleteIfExists(temp);
                scheduledChunks.remove(key);
                return;
            }
            moveReplace(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<BlockRecord> readChunkWithoutFlush(long key) throws IOException {
        Path file = chunkFile(key);
        if (!Files.isRegularFile(file) || Files.size(file) <= 0L) {
            return List.of();
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ)))) {
            int magic = input.readInt();
            int version = input.readInt();
            int count = input.readInt();
            if (magic != MAGIC || version != VERSION || count < 0 || count > MAX_RECORDS_PER_CHUNK) {
                quarantineDamagedFile(file, Lang.string("worldbinder.log.chunk_cache.invalid_header"));
                return List.of();
            }
            List<BlockRecord> records = new ArrayList<>(Math.min(count, 4096));
            for (int i = 0; i < count; i++) {
                BlockRecord record = new BlockRecord();
                record.x = input.readInt();
                record.y = input.readInt();
                record.z = input.readInt();
                record.hasBlockEntity = input.readBoolean();
                record.state = readString(input);
                record.blockEntityNbt = readString(input);
                if (record.state != null && !record.state.isBlank()) {
                    records.add(record);
                }
            }
            return records;
        } catch (EOFException exception) {
            quarantineDamagedFile(file, Lang.string("worldbinder.log.chunk_cache.truncated_file"));
            return List.of();
        } catch (IOException exception) {
            quarantineDamagedFile(file, exception.getClass().getSimpleName());
            return List.of();
        } catch (RuntimeException exception) {
            quarantineDamagedFile(file, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private Path chunkFile(long key) {
        return root.resolve("c." + ChunkPos.getX(key) + "." + ChunkPos.getZ(key) + ".wbcache");
    }

    private static BlockRecord copyRecord(BlockRecord source) {
        BlockRecord copy = new BlockRecord();
        copy.x = source.x;
        copy.y = source.y;
        copy.z = source.z;
        copy.state = source.state;
        copy.hasBlockEntity = source.hasBlockEntity;
        copy.blockEntityNbt = source.blockEntityNbt;
        return copy;
    }

    private static Long keyFromFile(Path file) {
        String name = file.getFileName().toString();
        if (!name.startsWith("c.") || !name.endsWith(".wbcache")) {
            return null;
        }
        String body = name.substring(2, name.length() - ".wbcache".length());
        int split = body.lastIndexOf('.');
        if (split <= 0 || split >= body.length() - 1) {
            return null;
        }
        try {
            int chunkX = Integer.parseInt(body.substring(0, split));
            int chunkZ = Integer.parseInt(body.substring(split + 1));
            return ChunkPos.pack(chunkX, chunkZ);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            return null;
        }
        if (length > MAX_STRING_BYTES) {
            throw new IOException(Lang.string("worldbinder.log.chunk_cache.string_too_large"));
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(Lang.string("worldbinder.log.chunk_cache.unexpected_end"));
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void quarantineDamagedFile(Path file, String reason) {
        Path target = file.resolveSibling(file.getFileName().toString() + ".corrupt");
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.damaged_moved", file.getFileName(), target.getFileName(), reason));
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.damaged_move_failed", file), exception);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
