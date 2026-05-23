package net.worldbinder.scene;

import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.validation.ExportValidationReport;
import net.worldbinder.validation.ExportValidator;
import net.minecraft.client.Minecraft;
import net.worldbinder.status.WorldBinderActivityLog;

import java.io.IOException;
import java.io.BufferedWriter;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneLibrary {
    public static final String ARCHIVE_FILE = "worldbinder_archive.json";
    public static final String MANIFEST_FILE = "worldbinder_manifest.json";
    public static final String README_FILE = "WORLD_BINDER_README.txt";
    public static final String WORLDBINDER_FOLDER = "worldbinder";

    private volatile List<Path> cachedArchives = new ArrayList<>();
    private Map<Path, String> cachedValidationLines = new ConcurrentHashMap<>();

    public SceneLibrary() {
        refresh();
    }

    public void refresh() {
        WorldBinderPaths.ensureBaseFolders();
        List<Path> result = new ArrayList<>();
        collect(WorldBinderPaths.SCENES, result);
        collect(WorldBinderPaths.MINECRAFT_SAVES, result);
        collect(WorldBinderPaths.WORLDS, result);
        cachedArchives = result.stream()
                .distinct()
                .sorted(Comparator.comparing(this::lastModified).reversed())
                .toList();
        Map<Path, String> validation = new ConcurrentHashMap<>();
        for (Path archive : cachedArchives) {
            String previous = cachedValidationLines.get(archive);
            validation.put(archive, previous != null ? previous : quickArchiveLine(archive));
        }
        cachedValidationLines = validation;
    }

    public List<Path> scenes() {
        return cachedArchives;
    }

    public Optional<Path> latest() {
        refresh();
        return cachedArchives.stream().findFirst();
    }

    public List<Path> recoverySessions() {
        return cachedArchives.stream().filter(this::isRecovery).toList();
    }

    public int recoveryCount() {
        return recoverySessions().size();
    }

    public boolean isRecovery(Path path) {
        return path != null && Files.isDirectory(path) && path.getFileName().toString().startsWith("_recovery_");
    }

    public boolean canFinalizeRecovery(Path path) {
        return isRecovery(path) && Files.exists(archivePath(path)) && !"writing".equals(recoveryState(path));
    }

    public String recoveryState(Path path) {
        if (!isRecovery(path)) {
            return "none";
        }
        Path manifest = path.resolve(WORLDBINDER_FOLDER).resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            return Files.exists(archivePath(path)) ? "complete" : "missing";
        }
        try {
            String json = Files.readString(manifest);
            return extractJsonString(json, "status", Files.exists(archivePath(path)) ? "complete" : "missing");
        } catch (IOException exception) {
            return Files.exists(archivePath(path)) ? "complete" : "unknown";
        }
    }

    public boolean canOpen(Path path) {
        return path != null && Files.exists(path);
    }

    public void save(WorldScene scene, Path path) throws IOException {
        WorldBinderPaths.ensureBaseFolders();
        Files.createDirectories(path.getParent());
        writeArchiveFileAtomic(scene, path);
        refresh();
    }

    public Path saveWorldFolder(WorldScene scene, Path folder) throws IOException {
        return saveWorldFolder(scene, folder, null);
    }

    public Path saveWorldFolder(WorldScene scene, Path folder, StorageProgress progress) throws IOException {
        WorldBinderPaths.ensureBaseFolders();
        if (progress != null) progress.update(StorageStage.VALIDATE, "Preparing save folder", 0.08D);
        Files.createDirectories(folder);
        Path metadataFolder = folder.resolve(WORLDBINDER_FOLDER);
        Files.createDirectories(metadataFolder);

        if (progress != null) progress.update(StorageStage.METADATA, "Writing WorldBinder archive", 0.16D);
        Path archive = metadataFolder.resolve(ARCHIVE_FILE);
        writeArchiveFileAtomic(scene, archive);

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, "Writing level.dat and region files", 0.34D);
        VanillaWorldExporter.ExportResult export = VanillaWorldExporter.export(scene, folder, progress);

        if (progress != null) progress.update(StorageStage.MAP_DATA, "Writing maps, stats and metadata", 0.62D);
        writeMapDataPlaceholders(scene, folder);
        writePlayerMetadata(scene, folder);
        writeCaptureMetadata(scene, folder, export);

        if (progress != null) progress.update(StorageStage.RESOURCE_PACK, "Copying server resource pack", 0.75D);
        copyLatestServerResourcePack(folder);

        if (net.worldbinder.WorldBinder.config().autoDeleteRecovery && !folder.getFileName().toString().startsWith("_recovery_")) {
            deleteRecoveryFolders();
        }

        writePreviewThumbnail(scene, metadataFolder.resolve("preview.png"));
        writeReadme(scene, folder, export);

        if (progress != null) progress.update(StorageStage.VALIDATE, "Validating vanilla save quality", 0.82D);
        ExportValidationReport validation = ExportValidator.validate(scene, folder);
        ExportValidator.writeReport(scene, folder, validation);
        writeManifest(scene, metadataFolder, export, validation);
        scene.storageNotes.add("Export validation: " + validation.status + " " + validation.score + "% (" + validation.grade() + ")");

        if (net.worldbinder.WorldBinder.config().zipWorldExport && !folder.getFileName().toString().startsWith("_recovery_")) {
            if (progress != null) progress.update(StorageStage.ZIP, "Compressing export ZIP", 0.88D);
            zipFolder(folder, folder.resolveSibling(folder.getFileName().toString() + ".zip"));
            scene.compressedZip = true;
        }

        refresh();
        return folder;
    }

    public Path saveRecoverySnapshot(WorldScene scene, Path folder) throws IOException {
        WorldBinderPaths.ensureBaseFolders();
        Files.createDirectories(folder);
        Path metadataFolder = folder.resolve(WORLDBINDER_FOLDER);
        Files.createDirectories(metadataFolder);
        writeRecoveryManifest(metadataFolder, scene, "writing", null);

        Path archive = metadataFolder.resolve(ARCHIVE_FILE);
        Path tmp = metadataFolder.resolve(ARCHIVE_FILE + ".tmp");
        writeArchiveFile(scene, tmp);
        try {
            WorldScene verified = readArchiveFile(tmp);
            if (verified == null) {
                throw new IOException("Recovery archive verification returned null");
            }
        } catch (Exception exception) {
            writeRecoveryManifest(metadataFolder, scene, "failed", exception.getMessage());
            throw new IOException("Recovery archive verification failed", exception);
        }
        moveReplace(tmp, archive);
        writeRecoveryManifest(metadataFolder, scene, "complete", null);
        WorldBinderActivityLog.add("Recovery save written in safe mode");
        refresh();
        return folder;
    }

    public void markRecoveryFailed(Path folder, String error) {
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder.resolve(WORLDBINDER_FOLDER));
            writeRecoveryManifest(folder.resolve(WORLDBINDER_FOLDER), null, "failed", error);
            refresh();
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to mark recovery as failed", exception);
        }
    }

    public WorldScene read(Path path) throws IOException {
        Path archive = Files.isDirectory(path) ? archivePath(path) : path;
        return readArchiveFile(archive);
    }


    public Path exportPreviewThumbnail(Path archiveOrFolder) throws IOException {
        WorldScene scene = read(archiveOrFolder);
        Path target;
        if (Files.isDirectory(archiveOrFolder)) {
            Path metadataFolder = archiveOrFolder.resolve(WORLDBINDER_FOLDER);
            Files.createDirectories(metadataFolder);
            target = metadataFolder.resolve("preview.png");
        } else {
            String fileName = archiveOrFolder.getFileName().toString();
            target = archiveOrFolder.resolveSibling(fileName.replaceFirst("\\.json$", "") + ".preview.png");
        }
        writePreviewThumbnail(scene, target);
        WorldBinderActivityLog.add("Archive preview exported");
        return target;
    }

    private void writePreviewThumbnail(WorldScene scene, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        int width = 512;
        int height = 288;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new java.awt.Color(8, 8, 16, 255));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new java.awt.Color(255, 85, 255, 255));
            graphics.fillRect(0, 0, width, 4);
            graphics.setColor(new java.awt.Color(94, 3, 252, 255));
            graphics.fillRect(0, height - 4, width, 4);

            if (scene == null || scene.chunkSnapshots == null || scene.chunkSnapshots.isEmpty()) {
                return;
            }

            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
                if (snapshot == null) continue;
                minX = Math.min(minX, snapshot.chunkX);
                minZ = Math.min(minZ, snapshot.chunkZ);
                maxX = Math.max(maxX, snapshot.chunkX);
                maxZ = Math.max(maxZ, snapshot.chunkZ);
            }
            if (minX == Integer.MAX_VALUE) {
                return;
            }
            int chunksX = Math.max(1, maxX - minX + 1);
            int chunksZ = Math.max(1, maxZ - minZ + 1);
            int cell = Math.max(1, Math.min((width - 48) / chunksX, (height - 48) / chunksZ));
            int mapW = chunksX * cell;
            int mapH = chunksZ * cell;
            int startX = (width - mapW) / 2;
            int startY = (height - mapH) / 2;

            int rendered = 0;
            long budgetStart = System.nanoTime();
            boolean drawDetails = cell >= 4 && scene.chunkSnapshots.size() <= 20_000;
            for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
                if (snapshot == null) continue;
                int x = startX + (snapshot.chunkX - minX) * cell;
                int y = startY + (snapshot.chunkZ - minZ) * cell;
                int w = Math.max(1, cell);
                int h = Math.max(1, cell);
                if (x + w < 0 || y + h < 0 || x >= width || y >= height) {
                    continue;
                }
                graphics.setColor(new java.awt.Color(snapshot.averageColor(), true));
                graphics.fillRect(x, y, w, h);
                if (drawDetails) {
                    if (snapshot.exportError) {
                        graphics.setColor(new java.awt.Color(255, 85, 85, 220));
                    } else if (snapshot.entityCount > 0) {
                        graphics.setColor(new java.awt.Color(255, 85, 255, 210));
                    } else {
                        graphics.setColor(new java.awt.Color(85, 255, 170, 90));
                    }
                    graphics.drawRect(x, y, Math.max(1, cell - 1), Math.max(1, cell - 1));
                }
                if (++rendered % 2048 == 0) {
                    budgetStart = throttleArchiveWork(budgetStart);
                }
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", target.toFile());
    }

    public Path finalizeRecovery(Path recoveryFolder) throws IOException {
        if (!canFinalizeRecovery(recoveryFolder)) {
            throw new IOException("Not a WorldBinder recovery folder: " + recoveryFolder);
        }
        WorldScene recovered = read(recoveryFolder);
        WorldScene safeScene = recoverySafeCopy(recovered);
        String base = recoveryFolder.getFileName().toString().replaceFirst("^_recovery_", "");
        if (safeScene.name == null || safeScene.name.isBlank()) {
            safeScene.name = base;
        }
        safeScene.archiveType = "world";
        safeScene.storageNotes.add("Finalized from recovery. Active/queued/scanning/error chunks were not exported as safe final chunks.");
        Path target = uniqueFinalizedFolder(base);
        saveWorldFolder(safeScene, target);
        if (Files.exists(recoveryFolder)) {
            deleteRecursive(recoveryFolder);
        }
        WorldBinderActivityLog.add("Recovery finalized: " + target.getFileName());
        refresh();
        return target;
    }

    public Path saveAsArchive(Path archiveOrFolder) throws IOException {
        WorldScene scene = read(archiveOrFolder);
        String base = scene == null || scene.name == null || scene.name.isBlank()
                ? archiveOrFolder.getFileName().toString()
                : scene.name;
        Path target = uniqueSceneArchive(base);
        save(scene, target);
        WorldBinderActivityLog.add("Archive copy saved: " + target.getFileName());
        refresh();
        return target;
    }

    public ExportValidationReport validateArchive(Path archiveOrFolder) throws IOException {
        if (archiveOrFolder == null || !Files.exists(archiveOrFolder)) {
            throw new IOException("Archive does not exist");
        }
        WorldScene scene = read(archiveOrFolder);
        Path folder = Files.isDirectory(archiveOrFolder) ? archiveOrFolder : archiveOrFolder.getParent();
        ExportValidationReport report = ExportValidator.validate(scene, folder);
        if (Files.isDirectory(archiveOrFolder)) {
            ExportValidator.writeReport(scene, archiveOrFolder, report);
            writeLastValidationManifest(archiveOrFolder.resolve(WORLDBINDER_FOLDER), scene, report);
        }
        cachedValidationLines.put(archiveOrFolder, ExportValidator.shortLine(report));
        return report;
    }

    private WorldScene recoverySafeCopy(WorldScene source) {
        WorldScene copy = new WorldScene();
        if (source == null) {
            return copy;
        }
        copy.formatVersion = source.formatVersion;
        copy.archiveType = source.archiveType;
        copy.name = source.name;
        copy.createdAt = source.createdAt;
        copy.minecraftVersion = source.minecraftVersion;
        copy.targetMinecraftVersion = source.targetMinecraftVersion;
        copy.targetGenerationProfile = source.targetGenerationProfile;
        copy.dimension = source.dimension;
        copy.originX = source.originX;
        copy.originY = source.originY;
        copy.originZ = source.originZ;
        copy.sizeX = source.sizeX;
        copy.sizeY = source.sizeY;
        copy.sizeZ = source.sizeZ;
        copy.includesBlockEntityNbt = source.includesBlockEntityNbt;
        copy.includesEntityNbt = source.includesEntityNbt;
        copy.gameRulesNbt = source.gameRulesNbt;
        copy.includesMapData = source.includesMapData;
        copy.includesAdvancements = source.includesAdvancements;
        copy.includesStats = source.includesStats;
        copy.compressedZip = false;
        copy.mapIds = source.mapIds == null ? new ArrayList<>() : new ArrayList<>(source.mapIds);
        copy.storageNotes = source.storageNotes == null ? new ArrayList<>() : new ArrayList<>(source.storageNotes);

        Set<Long> exportableChunks = new LinkedHashSet<>();
        if (source.chunkSnapshots != null) {
            copy.chunkSnapshots = new LinkedHashMap<>();
            for (Map.Entry<String, ChunkSnapshot> entry : source.chunkSnapshots.entrySet()) {
                ChunkSnapshot snapshot = entry.getValue();
                if (snapshot == null) continue;
                ChunkCaptureStatus status = snapshot.effectiveStatus();
                if (status == ChunkCaptureStatus.DONE || status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.RECOVERY) {
                    if (status == ChunkCaptureStatus.DONE) {
                        exportableChunks.add(chunkKey(snapshot.chunkX, snapshot.chunkZ));
                    }
                    copy.chunkSnapshots.put(entry.getKey(), snapshot);
                }
            }
        }

        copy.blocks = new ArrayList<>();
        if (source.blocks != null) {
            for (BlockRecord block : source.blocks) {
                if (block != null && exportableChunks.contains(blockChunkKey(source, block))) {
                    copy.blocks.add(block);
                }
            }
        }
        copy.entities = new ArrayList<>();
        if (source.entities != null) {
            for (EntityRecord entity : source.entities) {
                if (entity != null && exportableChunks.contains(entityChunkKey(source, entity))) {
                    copy.entities.add(entity);
                }
            }
        }
        return copy;
    }

    public void deleteArchive(Path archiveOrFolder) throws IOException {
        if (archiveOrFolder == null || !Files.exists(archiveOrFolder)) {
            return;
        }
        deleteRecursive(archiveOrFolder);
        WorldBinderActivityLog.add("Archive deleted: " + archiveOrFolder.getFileName());
        refresh();
    }

    private Path uniqueFinalizedFolder(String baseName) {
        String clean = net.worldbinder.util.FileNames.cleanBaseName(baseName == null || baseName.isBlank() ? "worldbinder_recovered" : baseName);
        Path candidate = WorldBinderPaths.MINECRAFT_SAVES.resolve(clean);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = WorldBinderPaths.MINECRAFT_SAVES.resolve(clean + "_" + index);
            index++;
        }
        return candidate;
    }

    private Path uniqueSceneArchive(String baseName) {
        String clean = net.worldbinder.util.FileNames.cleanBaseName(baseName == null || baseName.isBlank() ? "worldbinder_archive" : baseName);
        Path candidate = WorldBinderPaths.SCENES.resolve(clean + ".json");
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = WorldBinderPaths.SCENES.resolve(clean + "_" + index + ".json");
            index++;
        }
        return candidate;
    }

    private long throttleArchiveWork(long budgetStartNanos) {
        long budgetNanos = Math.max(1L, WorldBinder.config().effectiveMaxArchiveWorkMs()) * 1_000_000L;
        if (System.nanoTime() - budgetStartNanos < budgetNanos) {
            return budgetStartNanos;
        }
        try {
            Thread.sleep(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return System.nanoTime();
    }

    private void moveRecursive(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(source, target);
        }
    }

    public String validationLine(Path path) {
        return cachedValidationLines.computeIfAbsent(path, this::readValidationLine);
    }

    private String readValidationLine(Path path) {
        Path report = Files.isDirectory(path) ? path.resolve(WORLDBINDER_FOLDER).resolve("export_validation.json") : null;
        if (isRecovery(path)) {
            String state = recoveryState(path);
            if ("writing".equals(state)) return "Recovery writing/incomplete";
            if ("failed".equals(state)) return "Recovery failed - keep previous complete save";
            if (report == null || !Files.isRegularFile(report)) return "Recovery " + state;
        }
        if (report == null || !Files.isRegularFile(report)) {
            String manifestLine = readManifestValidationLine(path);
            return manifestLine == null ? "No validation report" : manifestLine;
        }
        try {
            String json = Files.readString(report);
            String status = extractJsonString(json, "status", "UNKNOWN");
            String score = extractJsonNumber(json, "score", "?");
            String missing = extractJsonNumber(json, "missingChunks", "0");
            String partial = extractJsonNumber(json, "partialSnapshots", "0");
            String failed = extractJsonNumber(json, "failedSnapshots", "0");
            String suffix = ("0".equals(missing) && "0".equals(partial) && "0".equals(failed)) ? "" : " • M:" + missing + " P:" + partial + " F:" + failed;
            return status + " • Quality " + score + "% • " + gradeFromScore(score) + suffix;
        } catch (IOException exception) {
            return "Validation unavailable";
        }
    }


    private String readManifestValidationLine(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return null;
        }
        Path manifest = path.resolve(WORLDBINDER_FOLDER).resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            String json = Files.readString(manifest);
            if (!json.contains("\"lastValidation\"")) {
                return null;
            }
            String status = extractJsonString(json, "status", "UNKNOWN");
            String score = extractJsonNumber(json, "score", "?");
            String missing = extractJsonNumber(json, "missingChunks", "0");
            String partial = extractJsonNumber(json, "partialChunks", "0");
            String failed = extractJsonNumber(json, "failedChunks", "0");
            String suffix = ("0".equals(missing) && "0".equals(partial) && "0".equals(failed)) ? "" : " • M:" + missing + " P:" + partial + " F:" + failed;
            return status + " • Quality " + score + "% • " + gradeFromScore(score) + suffix;
        } catch (IOException exception) {
            return null;
        }
    }

    private String quickArchiveLine(Path path) {
        if (isRecovery(path)) {
            return "Recovery " + recoveryState(path);
        }
        return Files.isDirectory(path) ? "Folder archive" : "Scene archive";
    }

    private void writeArchiveFileAtomic(WorldScene scene, Path archive) throws IOException {
        Files.createDirectories(archive.getParent());
        Path tmp = archive.resolveSibling(archive.getFileName().toString() + ".tmp");
        writeArchiveFile(scene, tmp);
        readArchiveFile(tmp);
        moveReplace(tmp, archive);
    }

    private void writeArchiveFile(WorldScene scene, Path archive) throws IOException {
        Files.createDirectories(archive.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            WorldBinder.GSON.toJson(scene, writer);
            writer.flush();
        }
        forceFile(archive);
    }

    private WorldScene readArchiveFile(Path archive) throws IOException {
        try (Reader reader = Files.newBufferedReader(archive)) {
            return WorldBinder.GSON.fromJson(reader, WorldScene.class);
        }
    }

    private void writeRecoveryManifest(Path metadataFolder, WorldScene scene, String status, String error) throws IOException {
        Files.createDirectories(metadataFolder);
        String manifest = "{\n" +
                "  \"format\": \"worldbinder-recovery-v1\",\n" +
                "  \"status\": \"" + escape(status) + "\",\n" +
                "  \"updatedAt\": \"" + Instant.now() + "\",\n" +
                "  \"name\": \"" + escape(scene == null ? "" : scene.name) + "\",\n" +
                "  \"chunks\": " + (scene == null ? 0 : scene.chunkSnapshotCount()) + ",\n" +
                "  \"blocks\": " + (scene == null ? 0 : scene.blockCount()) + ",\n" +
                "  \"entities\": " + (scene == null ? 0 : scene.entityCount()) +
                (error == null || error.isBlank() ? "\n" : ",\n  \"error\": \"" + escape(error) + "\"\n") +
                "}\n";
        writeStringAtomic(metadataFolder.resolve(MANIFEST_FILE), manifest);
    }

    private void writeStringAtomic(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        forceFile(tmp);
        moveReplace(tmp, target);
    }

    private void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void forceFile(Path path) {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (IOException ignored) {
        }
    }

    private static String extractJsonString(String json, String key, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String extractJsonNumber(String json, String key, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)").matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String gradeFromScore(String score) {
        try {
            int value = Integer.parseInt(score);
            if (value >= 92) return "Excellent";
            if (value >= 80) return "Good";
            if (value >= 65) return "Usable";
            if (value >= 45) return "Partial";
        } catch (NumberFormatException ignored) {
        }
        return "Needs review";
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkZ) << 32) | (chunkX & 0xffffffffL);
    }

    private static long blockChunkKey(WorldScene scene, BlockRecord block) {
        int worldX = scene.originX + block.x;
        int worldZ = scene.originZ + block.z;
        return chunkKey(worldX >> 4, worldZ >> 4);
    }

    private static long entityChunkKey(WorldScene scene, EntityRecord entity) {
        int worldX = scene.originX + (int) Math.floor(entity.x);
        int worldZ = scene.originZ + (int) Math.floor(entity.z);
        return chunkKey(worldX >> 4, worldZ >> 4);
    }

    private void collect(Path folder, List<Path> result) {
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json") || Files.isDirectory(path))
                    .filter(path -> !path.getFileName().toString().equals(MANIFEST_FILE))
                    .filter(path -> !path.getFileName().toString().equals(ARCHIVE_FILE) || !folder.equals(WorldBinderPaths.WORLDS))
                    .filter(path -> Files.isDirectory(path) || path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        if (Files.isDirectory(path)) {
                            if (Files.exists(archivePath(path))) {
                                result.add(path);
                            }
                        } else {
                            result.add(path);
                        }
                    });
        } catch (IOException exception) {
            WorldBinder.LOGGER.error("Failed to refresh archive library", exception);
        }
    }

    private void writeManifest(WorldScene scene, Path folder, VanillaWorldExporter.ExportResult export, ExportValidationReport validation) throws IOException {
        String manifest = "{\n" +
                "  \"format\": \"worldbinder-vanilla-world-v1\",\n" +
                "  \"createdAt\": \"" + Instant.now() + "\",\n" +
                "  \"name\": \"" + escape(scene.name) + "\",\n" +
                "  \"minecraftVersion\": \"" + escape(scene.minecraftVersion) + "\",\n" +
                "  \"targetMinecraftVersion\": \"" + escape(scene.targetMinecraftVersion) + "\",\n" +
                "  \"targetGenerationProfile\": \"" + escape(scene.targetGenerationProfile) + "\",\n" +
                "  \"vanillaWorldFolder\": \"" + escape(export.folder().toAbsolutePath().toString()) + "\",\n" +
                "  \"regionFiles\": true,\n" +
                "  \"entityRegionFiles\": true,\n" +
                "  \"poiRegionFiles\": true,\n" +
                "  \"chunks\": " + export.chunks() + ",\n" +
                "  \"blocks\": " + scene.blockCount() + ",\n" +
                "  \"blockEntities\": " + scene.blockEntityCount() + ",\n" +
                "  \"entities\": " + scene.entityCount() + ",\n" +
                "  \"chunkSnapshots\": " + scene.chunkSnapshotCount() + ",\n" +
                "  \"maps\": " + scene.mapCount() + ",\n" +
                "  \"advancements\": " + scene.includesAdvancements + ",\n" +
                "  \"stats\": " + scene.includesStats + ",\n" +
                "  \"zip\": " + net.worldbinder.WorldBinder.config().zipWorldExport + ",\n" +
                "  \"validation\": true,\n" +
                "  \"preview\": true" +
                validationJsonSuffix(validation) + "\n" +
                "}\n";
        writeStringAtomic(folder.resolve(MANIFEST_FILE), manifest);
    }

    private void writeLastValidationManifest(Path metadataFolder, WorldScene scene, ExportValidationReport validation) throws IOException {
        Files.createDirectories(metadataFolder);
        Path manifest = metadataFolder.resolve(MANIFEST_FILE);
        if (Files.isRegularFile(manifest)) {
            String json = Files.readString(manifest);
            int end = json.lastIndexOf('}');
            if (end > 0 && !json.contains("\"lastValidation\"")) {
                String prefix = json.substring(0, end).stripTrailing();
                String updated = prefix + validationJsonSuffix(validation) + "\n}\n";
                writeStringAtomic(manifest, updated);
                return;
            }
        }
        String fallback = "{\n" +
                "  \"format\": \"worldbinder-validation-v1\",\n" +
                "  \"updatedAt\": \"" + Instant.now() + "\",\n" +
                "  \"name\": \"" + escape(scene == null ? "" : scene.name) + "\"" +
                validationJsonSuffix(validation) + "\n" +
                "}\n";
        writeStringAtomic(manifest, fallback);
    }

    private String validationJsonSuffix(ExportValidationReport validation) {
        if (validation == null) {
            return "";
        }
        return ",\n" +
                "  \"lastValidation\": {\n" +
                "    \"status\": \"" + escape(validation.status) + "\",\n" +
                "    \"score\": " + validation.score + ",\n" +
                "    \"grade\": \"" + escape(validation.grade()) + "\",\n" +
                "    \"missingChunks\": " + validation.missingChunks + ",\n" +
                "    \"partialChunks\": " + validation.partialSnapshots + ",\n" +
                "    \"failedChunks\": " + validation.failedSnapshots + ",\n" +
                "    \"entityDataMissing\": " + validation.entityDataMissing + ",\n" +
                "    \"regionWriteErrors\": " + validation.regionWriteErrors + ",\n" +
                "    \"updatedAt\": \"" + Instant.now() + "\"\n" +
                "  }";
    }

    private void writeReadme(WorldScene scene, Path folder, VanillaWorldExporter.ExportResult export) throws IOException {
        Files.writeString(folder.resolve(README_FILE),
                "WorldBinder Vanilla World Export\n" +
                "================================\n\n" +
                "This is a vanilla-shaped Minecraft save folder generated by WorldBinder.\n" +
                "It contains level.dat and region data under dimensions/minecraft/overworld/.\n\n" +
                "Archive: " + scene.name + "\n" +
                "Target output: " + scene.targetMinecraftVersion + " (" + scene.targetGenerationProfile + ")\n" +
                "Chunks: " + export.chunks() + "\n" +
                "Blocks: " + scene.blockCount() + "\n" +
                "Block entities: " + scene.blockEntityCount() + "\n" +
                "Entities: " + scene.entityCount() + "\n" +
                "Chunk snapshots: " + scene.chunkSnapshotCount() + "\n\n" +
                "WorldBinder metadata folder: " + WORLDBINDER_FOLDER + "\n" +
                "Main archive file: " + WORLDBINDER_FOLDER + "/" + ARCHIVE_FILE + "\n" +
                "Manifest: " + WORLDBINDER_FOLDER + "/" + MANIFEST_FILE + "\n\n" +
                "Note: client-side exports can only contain data the client actually received.\n");
    }



    private void writeMapDataPlaceholders(WorldScene scene, Path folder) {
        if (!net.worldbinder.WorldBinder.config().exportMaps || scene.mapIds == null || scene.mapIds.isEmpty()) {
            return;
        }
        try {
            Path data = folder.resolve("data").resolve("minecraft");
            Files.createDirectories(data);
            for (Integer id : scene.mapIds) {
                if (id == null) continue;
                Path file = data.resolve("map_" + id + ".dat");
                if (!Files.exists(file)) {
                    Files.writeString(file, "WorldBinder map entry for observed map id " + id + "\nFull map image data depends on what the client received.\n");
                }
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to write map data entries", exception);
        }
    }

    private void writePlayerMetadata(WorldScene scene, Path folder) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        try {
            if (net.worldbinder.WorldBinder.config().exportAdvancements) {
                Path advancements = folder.resolve("players").resolve("advancements");
                Files.createDirectories(advancements);
                Files.writeString(advancements.resolve(client.player.getUUID().toString() + ".json"), "{\n  \"worldbinder\": true,\n  \"note\": \"Client-side advancement export. Full server progress is not exposed reliably on every server.\"\n}\n");
            }
            if (net.worldbinder.WorldBinder.config().exportStats) {
                Path stats = folder.resolve("players").resolve("stats");
                Files.createDirectories(stats);
                Files.writeString(stats.resolve(client.player.getUUID().toString() + ".json"), "{\n  \"worldbinder\": true,\n  \"DataVersion\": 4440,\n  \"stats\": {},\n  \"note\": \"Stats folder prepared by WorldBinder.\"\n}\n");
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to write player metadata", exception);
        }
    }

    private void writeCaptureMetadata(WorldScene scene, Path folder, VanillaWorldExporter.ExportResult export) {
        if (!net.worldbinder.WorldBinder.config().exportMetadata) {
            return;
        }
        try {
            Path meta = folder.resolve(WORLDBINDER_FOLDER).resolve("metadata.json");
            Files.createDirectories(meta.getParent());
            Files.writeString(meta, "{\n" +
                    "  \"name\": \"" + escape(scene.name) + "\",\n" +
                    "  \"createdAt\": \"" + escape(scene.createdAt) + "\",\n" +
                    "  \"dimension\": \"" + escape(scene.dimension) + "\",\n" +
                    "  \"targetMinecraftVersion\": \"" + escape(scene.targetMinecraftVersion) + "\",\n" +
                    "  \"targetGenerationProfile\": \"" + escape(scene.targetGenerationProfile) + "\",\n" +
                    "  \"blocks\": " + scene.blockCount() + ",\n" +
                    "  \"entities\": " + scene.entityCount() + ",\n" +
                    "  \"maps\": " + scene.mapCount() + ",\n" +
                    "  \"chunks\": " + export.chunks() + "\n" +
                    "}\n");
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to write capture metadata", exception);
        }
    }

    private void zipFolder(Path folder, Path zipFile) throws IOException {
        Files.deleteIfExists(zipFile);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> stream = Files.walk(folder)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String entryName = folder.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private void copyLatestServerResourcePack(Path worldFolder) {
        if (!WorldBinder.config().includeServerResourcePack) {
            return;
        }
        List<Path> roots = List.of(
                WorldBinderPaths.GAME_DIR.resolve("server-resource-packs"),
                WorldBinderPaths.GAME_DIR.resolve("downloads"),
                WorldBinderPaths.GAME_DIR.resolve("resourcepacks")
        );
        try {
            Optional<Path> latest = Optional.empty();
            for (Path root : roots) {
                if (!Files.isDirectory(root)) continue;
                try (Stream<Path> stream = Files.walk(root, 3)) {
                    Optional<Path> candidate = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> isLikelyPack(path))
                            .max(Comparator.comparing(this::lastModified));
                    if (candidate.isPresent() && (latest.isEmpty() || lastModified(candidate.get()) > lastModified(latest.get()))) {
                        latest = candidate;
                    }
                }
            }
            if (latest.isPresent()) {
                Path resourcePacks = worldFolder.resolve("resourcepacks");
                Files.createDirectories(resourcePacks);
                Files.copy(latest.get(), resourcePacks.resolve("resources.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(latest.get(), worldFolder.resolve("resources.zip"), StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(worldFolder.resolve("WORLD_BINDER_RESOURCEPACK.txt"),
                        "WorldBinder copied the newest detected server/resource pack cache into this save.\n" +
                        "Minecraft looks for a per-world pack at resources.zip, so the pack is also mirrored there.\n" +
                        "A second copy stays in resourcepacks/resources.zip for manual inspection.\n" +
                        "Source: " + latest.get().toAbsolutePath() + "\n" +
                        "If this is the wrong pack, replace resources.zip manually with the server pack you want.\n");
            }
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to copy server resource pack into WorldBinder save", exception);
        }
    }

    private boolean isLikelyPack(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".png")) {
            return false;
        }
        return name.endsWith(".zip") || !name.contains(".");
    }

    private void deleteRecoveryFolders() {
        for (Path recovery : recoverySessions()) {
            try {
                deleteRecursive(recovery);
            } catch (IOException exception) {
                WorldBinder.LOGGER.warn("Failed to delete old recovery folder {}", recovery, exception);
            }
        }
    }

    private void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private long lastModified(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Path archive = archivePath(path);
                return Files.exists(archive) ? Files.getLastModifiedTime(archive).toMillis() : Files.getLastModifiedTime(path).toMillis();
            }
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static Path archivePath(Path folder) {
        Path nested = folder.resolve(WORLDBINDER_FOLDER).resolve(ARCHIVE_FILE);
        if (Files.exists(nested)) {
            return nested;
        }
        return folder.resolve(ARCHIVE_FILE);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
