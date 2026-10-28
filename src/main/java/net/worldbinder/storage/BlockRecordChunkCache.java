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
import java.util.Map;
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
    private final Set<Long> indexedChunks = ConcurrentHashMap.newKeySet();
    private final Map<Long, Throwable> failures = new ConcurrentHashMap<>();
    private final Map<Long,CompletableFuture<Void>> lastWrites=new ConcurrentHashMap<>();
    private final Set<Long> durableChunks=ConcurrentHashMap.newKeySet();

    public BlockRecordChunkCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        Path index = root.resolve("chunks.index");
        if (Files.exists(index)) for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            boolean deleted = line.startsWith("-");
            Long key = keyFromFile(root.resolve(deleted ? line.substring(1) : line));
            if (key == null) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_committed_chunk_cache_index_value", index));
            if (deleted) scheduledChunks.remove(key); else scheduledChunks.add(key);
        }
        indexedChunks.addAll(scheduledChunks);
        try (Stream<Path> files = Files.list(root)) {
            files.filter(path -> path.getFileName().toString().endsWith(".wbcache")).map(BlockRecordChunkCache::keyFromFile)
                    .filter(java.util.Objects::nonNull).forEach(scheduledChunks::add);
        }
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
        if (records == null) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.missing_chunk_payload"));
        List<BlockRecord> valid = new ArrayList<>(records.size());
        for (BlockRecord record : records) {
            if (record == null || record.state == null || record.state.isBlank()) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_chunk_block_record"));
            valid.add(copyRecord(record));
        }

        Files.createDirectories(root);
        scheduledChunks.add(key);
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                writeChunkNow(key, valid);
                durableChunks.add(key);
            } catch (IOException exception) {
                failures.put(key, exception);
                throw new CompletionException(exception);
            }
        }, writer);

        lastWrites.put(key,future);pendingWrites.add(future);
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
        verifyFailures();
    }

    /** Render/tick status must never wait for the disk writer or inspect every file. Full checks run on save. */
    public boolean hasWriteFailure(long key){return failures.containsKey(key);}
    public boolean hasWriteFailures(){return !failures.isEmpty();}
    public boolean writePending(long key){var write=lastWrites.get(key);return write!=null&&!write.isDone();}
    public boolean hasDurableChunk(long key){return durableChunks.contains(key);}

    public void verifyHealthy() {
        flushWrites();
        for (long key : scheduledChunks) try {
            if (!Files.isRegularFile(chunkFile(key)) || Files.size(chunkFile(key)) < 12) throw invalidChunk(key, "missing_or_empty", null);
        } catch (IOException exception) { throw new IllegalStateException(exception.getMessage(), exception); }
        verifyFailures();
    }

    private void verifyFailures() {
        if (!failures.isEmpty()) {
            Throwable cause = failures.values().iterator().next();
            throw new IllegalStateException(cause.getMessage(), cause);
        }
    }

    public List<BlockRecord> readChunk(long key) throws IOException {
        flushWrites();
        return readChunkWithoutFlush(key);
    }

    /** Writer-queue barrier: later captures may continue, but cannot overwrite this recovery snapshot's payloads. */
    public CompletableFuture<Map<Long,List<BlockRecord>>> snapshotAsync() {
        Set<Long> keys=Set.copyOf(scheduledChunks);
        return CompletableFuture.supplyAsync(()->{
            verifyFailures();Map<Long,List<BlockRecord>> result=new java.util.LinkedHashMap<>();
            try { for(long key:keys)result.put(key,readChunkWithoutFlush(key)); }
            catch(IOException exception){throw new CompletionException(exception);}
            return result;
        },writer);
    }

    public int readInto(List<BlockRecord> target, LongPredicate predicate, Set<Long> payloadChunks) {
        if (target == null) return 0;
        flushWrites();
        int added = 0;
        try {
            for (long key : chunkKeys()) {
                if (predicate != null && !predicate.test(key)) {
                    continue;
                }
                List<BlockRecord> records = readChunkWithoutFlush(key);
                target.addAll(records);
                added += records.size();
                if (payloadChunks != null) {
                    payloadChunks.add(key);
                }
            }
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
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
            throw new java.io.UncheckedIOException(exception);
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
        flushWrites();
        try {
            Files.deleteIfExists(chunkFile(key));
            Files.writeString(root.resolve("chunks.index"), "-" + chunkFile(key).getFileName() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            scheduledChunks.remove(key);
            indexedChunks.remove(key);
            lastWrites.remove(key);durableChunks.remove(key);
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
                throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.empty_chunk_cache_write"));
            }
            moveReplace(temp, target);
            // Append only after the payload is durable. The journal keeps deleted expected files visible after restart.
            if (!indexedChunks.contains(key)) {
                Files.writeString(root.resolve("chunks.index"), target.getFileName() + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                indexedChunks.add(key);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<BlockRecord> readChunkWithoutFlush(long key) throws IOException {
        Path file = chunkFile(key);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ)))) {
            int magic = input.readInt();
            int version = input.readInt();
            int count = input.readInt();
            if (magic != MAGIC || version != VERSION || count < 0 || count > MAX_RECORDS_PER_CHUNK) {
                throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_cache_header_count"));
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
                if (record.state == null || record.state.isBlank()) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.missing_cached_block_state"));
                records.add(record);
            }
            if (input.read() != -1) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.trailing_cache_payload"));
            return records;
        } catch (IOException exception) {
            if (Files.isRegularFile(file)) quarantineDamagedFile(file, exception.getMessage());
            throw invalidChunk(key, "missing_or_corrupt", exception);
        } catch (RuntimeException exception) {
            quarantineDamagedFile(file, exception.getMessage());
            throw invalidChunk(key, "missing_or_corrupt", exception);
        }
    }

    private IOException invalidChunk(long key, String reason, Throwable cause) {
        IOException error = new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.cache." + reason,
                ChunkPos.getX(key), ChunkPos.getZ(key), chunkFile(key)), cause);
        failures.put(key, error); return error;
    }

    public void stopWriter() { writer.shutdown(); }

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
        if (length == -1) return null;
        if (length < -1) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_cache_string_length"));
        if (length > MAX_STRING_BYTES) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.log.chunk_cache.string_too_large"));
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException(Lang.string("worldbinder.log.chunk_cache.unexpected_end"));
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void quarantineDamagedFile(Path file, String reason) {
        Long key = keyFromFile(file);
        if (key != null) failures.put(key, new IOException(reason));
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
