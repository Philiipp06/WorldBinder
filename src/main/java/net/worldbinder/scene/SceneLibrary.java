package net.worldbinder.scene;

import net.worldbinder.WorldBinder;
import net.worldbinder.io.WorldBinderPaths;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.export.common.ExportReadmeWriter;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.validation.ExportValidationReport;
import net.worldbinder.validation.ExportValidator;
import net.minecraft.client.Minecraft;
import net.worldbinder.status.WorldBinderActivityLog;
import net.worldbinder.util.Lang;

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
    // Identical JSON schema and adapters, without millions of indentation writes in block payloads.
    private static final com.google.gson.Gson ARCHIVE_JSON = WorldBinder.GSON.newBuilder()
            .setFormattingStyle(com.google.gson.FormattingStyle.COMPACT).create();
    public static final String ARCHIVE_FILE = "worldbinder_archive.json";
    public static final String MANIFEST_FILE = "worldbinder_manifest.json";
    public static final String README_FILE = "README.md";
    public static final String WORLDBINDER_FOLDER = "worldbinder";

    private volatile List<Path> cachedArchives = new ArrayList<>();
    private Map<Path, String> cachedValidationLines = new ConcurrentHashMap<>();
    private volatile String cachedLanguage=Lang.language();

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
        ProjectData.discoverMapIds(scene);
        preservePack(scene,path.getParent(),false);
        WorldBinderPaths.ensureBaseFolders();
        Files.createDirectories(path.getParent());
        writeArchiveFileAtomic(scene, path);
        refresh();
    }

    public Path saveWorldFolder(WorldScene scene, Path folder) throws IOException {
        return saveWorldFolder(scene, folder, null);
    }

    public Path saveWorldFolder(WorldScene scene, Path folder, StorageProgress progress) throws IOException {
        scene.compressedZip=WorldBinder.config().zipWorldExport;
        Path destination = folder;
        int suffix = 2;
        while (Files.exists(destination)) destination = folder.resolveSibling(folder.getFileName() + "_r" + suffix++);
        Files.createDirectories(destination.getParent());
        Path staging = destination.resolveSibling("." + destination.getFileName() + ".writing-" + java.util.UUID.randomUUID());
        try {
            ExportValidationReport checked = writeWorldFolder(scene, staging, progress);
            if (!checked.filesValid) throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.written_world_failed_read_back_validation_value", checked.errors));
            checked.folder = destination.toAbsolutePath().toString();
            ExportValidator.writeReport(scene, staging, checked);
            Path zip = staging.resolveSibling(staging.getFileName() + ".zip");
            if (net.worldbinder.WorldBinder.config().zipWorldExport) {
                if(progress!=null)progress.update(StorageStage.ZIP,Lang.stored("worldbinder.storage.progress.compress_zip"),0.9D);
                zipFolder(staging,zip);
            }
            try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(staging, destination); }
            if (Files.exists(zip)) Files.move(zip, destination.resolveSibling(destination.getFileName() + ".zip"));
            refresh();
            return destination;
        } catch (Exception exception) {
            // Keep the failed staging folder and its report for diagnosis. Never touch the previous revision.
            if (exception instanceof IOException io) throw io;
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.project_save_failed_previous_revision_retained_staging_value", staging), exception);
        }
    }

    private ExportValidationReport writeWorldFolder(WorldScene scene, Path folder, StorageProgress progress) throws IOException {
        ProjectData.discoverMapIds(scene);
        WorldBinderPaths.ensureBaseFolders();
        if (progress != null) progress.update(StorageStage.VALIDATE, Lang.stored("worldbinder.storage.progress.prepare_folder"), 0.08D);
        Files.createDirectories(folder);
        Path metadataFolder = folder.resolve(WORLDBINDER_FOLDER);
        Files.createDirectories(metadataFolder);
        preservePack(scene,folder,true);

        if (progress != null) progress.update(StorageStage.METADATA, Lang.stored("worldbinder.storage.progress.write_archive"), 0.16D);
        Path archive = metadataFolder.resolve(ARCHIVE_FILE);
        writeArchiveFileAtomic(scene, archive);
        WorldScene exportScene=net.worldbinder.export.common.DimensionExportView.prepare(net.worldbinder.export.common.ExportFiltering.apply(scene));

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.stored("worldbinder.storage.progress.write_regions"), 0.34D);
        VanillaWorldExporter.ExportResult export = VanillaWorldExporter.export(exportScene, folder, progress);

        if (progress != null) progress.update(StorageStage.MAP_DATA, Lang.stored("worldbinder.storage.progress.write_metadata"), 0.62D);
        writePlayerMetadata(exportScene, folder);
        writeCaptureMetadata(exportScene, folder, export);
        net.worldbinder.export.common.ServerListsWriter.write(scene, folder);

        if (progress != null) progress.update(StorageStage.RESOURCE_PACK, Lang.stored("worldbinder.storage.progress.copy_pack"), 0.75D);


        writePreviewThumbnail(scene, metadataFolder.resolve("preview.png"));
        writeReadme(exportScene, folder, export);

        if (progress != null) progress.update(StorageStage.VALIDATE, Lang.stored("worldbinder.storage.progress.validate"), 0.82D);
        ExportValidationReport validation = ExportValidator.validate(exportScene, folder, progress);
        ExportValidator.writeReport(scene, folder, validation);
        writeManifest(scene, metadataFolder, export, validation);
        scene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.archive.validation_note", net.worldbinder.validation.Finding.of(validationStatusKey(validation.status)), validation.score, net.worldbinder.validation.Finding.of(validation.gradeKey())));

        return validation;
    }

    public Path saveRecoverySnapshot(WorldScene scene, Path folder) throws IOException {
        if (Files.isRegularFile(archivePath(folder)) && !RecoveryProjects.sameOwner(scene, read(folder)))
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.recovery_belongs_to_another_project_world_or_dimension_value", folder));
        ProjectData.discoverMapIds(scene);
        preservePack(scene,folder,false);
        WorldBinderPaths.ensureBaseFolders();
        Files.createDirectories(folder);
        Path metadataFolder = folder.resolve(WORLDBINDER_FOLDER);
        Files.createDirectories(metadataFolder);

        Path archive = metadataFolder.resolve(ARCHIVE_FILE);
        Path tmp = metadataFolder.resolve(ARCHIVE_FILE + ".tmp");
        writeArchiveFile(scene, tmp);
        try {
            WorldScene verified = readArchiveFile(tmp);
            if (verified == null) {
                throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.recovery_archive_verification_returned_null"));
            }
        } catch (Exception exception) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.recovery_archive_verification_failed"), exception);
        }
        // The archive is the commit point. All fallible metadata writes must precede its replacement.
        Path manifest=metadataFolder.resolve(MANIFEST_FILE);
        byte[] previousManifest=Files.isRegularFile(manifest)?Files.readAllBytes(manifest):null;
        writeRecoveryManifest(metadataFolder, scene, "complete", null);
        try{moveReplace(tmp, archive);}
        catch(IOException failure){
            try{
                if(previousManifest==null)Files.deleteIfExists(manifest);
                else{Path restore=manifest.resolveSibling(MANIFEST_FILE+".restore");Files.write(restore,previousManifest);forceFile(restore);moveReplace(restore,manifest);}
            }catch(IOException restoreFailure){failure.addSuppressed(restoreFailure);}
            throw failure;
        }
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.recovery_saved"));
        refresh();
        return folder;
    }

    public void markRecoveryFailed(Path folder, String error) {
        if (folder == null) {
            return;
        }
        if (Files.isRegularFile(archivePath(folder))) return; // Last complete recovery remains usable.
        try {
            Files.createDirectories(folder.resolve(WORLDBINDER_FOLDER));
            writeRecoveryManifest(folder.resolve(WORLDBINDER_FOLDER), null, "failed", error);
            refresh();
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.scene.mark_recovery_failed"), exception);
        }
    }

    public WorldScene read(Path path) throws IOException {
        Path archive = Files.isDirectory(path) ? archivePath(path) : path;
        WorldScene scene=readArchiveFile(archive);
        Path base=Files.isDirectory(path)?path:path.getParent();
        if(!Files.isDirectory(path)&&ARCHIVE_FILE.equals(path.getFileName().toString())
                &&base.getFileName()!=null&&WORLDBINDER_FOLDER.equals(base.getFileName().toString()))base=base.getParent();
        if(scene.resourcePackFile!=null){
            Path expected=base.resolve(scene.resourcePackFile).normalize();
            if(!expected.toAbsolutePath().startsWith(base.toAbsolutePath().normalize()))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.resource_pack_path_leaves_archive_folder"));
            if(Files.isRegularFile(expected))scene.resourcePackSource=expected.toString();
        } else if(Files.isDirectory(path)) {
            for(Path candidate:List.of(path.resolve("resourcepacks/resources.zip"),path.resolve("resources.zip")))if(Files.isRegularFile(candidate)){
                scene.resourcePackSource=candidate.toString();scene.resourcePackExpected=true;break;
            }
        }
        return scene;
    }

    private void preservePack(WorldScene scene,Path folder,boolean runtime) throws IOException {
        if(scene.resourcePackSource==null&&WorldBinder.config().includeServerResourcePack){
            Minecraft client=Minecraft.getInstance();
            if(client!=null&&client.level!=null&&scene.worldIdentity!=null
                    &&scene.worldIdentity.equals(net.worldbinder.capture.WorldIdentity.current(client)))
                ServerResourcePackLocator.findActiveServerPack().ifPresent(path->{scene.resourcePackSource=path.toString();scene.resourcePackExpected=true;});
        }
        if(scene.resourcePackSource==null)return;
        Path source=Path.of(scene.resourcePackSource);
        if(!Files.isRegularFile(source))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.previously_captured_resource_pack_is_unavailable_value", source.getFileName()));
        String hash=packHash(source);
        if(scene.resourcePackSha256!=null&&!scene.resourcePackSha256.equals(hash))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.archived_resource_pack_checksum_differs_previous_project_retained"));
        Path target=runtime&&WorldBinder.config().includeServerResourcePack
                ?bundledResourcePackPath(folder,net.worldbinder.version.TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion())
                :(runtime?folder.resolve(WORLDBINDER_FOLDER):folder).resolve("pack-"+hash+".zip");
        Files.createDirectories(target.getParent());
        if(!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())&&!Files.exists(target))Files.copy(source,target);
        if(!packHash(target).equals(hash))throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.resource_pack_copy_verification_failed"));
        scene.resourcePackFile=folder.relativize(target).toString().replace('\\','/');
        scene.resourcePackSha256=hash;scene.resourcePackExpected=true;
    }

    private static String packHash(Path path) throws IOException {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))>=0)if(read>0)digest.update(buffer,0,read);}
            return java.util.HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
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
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.preview_exported"));
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

    /** True for a normal world export that still contains its WorldBinder archive and can be exported again. */
    public boolean canReexport(Path path) {
        return path != null && !isRecovery(path) && Files.isDirectory(path) && Files.exists(archivePath(path));
    }

    /**
     * Exports the archive stored in an existing world export again into a new save folder, using the current
     * exporter (for example to repair worlds written by an older WorldBinder version). The original folder is kept.
     */
    public Path reexport(Path exportFolder) throws IOException {
        if (!canReexport(exportFolder)) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.not_a_worldbinder_world_export_value", exportFolder));
        }
        return reexport(exportFolder, read(exportFolder));
    }

    public Path reexport(Path exportFolder, WorldScene scene) throws IOException {
        String base = exportFolder.getFileName().toString() + "_" + net.worldbinder.ui.component.WbBrand.VERSION.replace(".", "_");
        Path target = uniqueFinalizedFolder(base);
        saveWorldFolder(scene, target);
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.reexported", target.getFileName()));
        refresh();
        return target;
    }

    public Path finalizeRecovery(Path recoveryFolder) throws IOException {
        if (!canFinalizeRecovery(recoveryFolder)) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.not_a_worldbinder_recovery_folder_value", recoveryFolder));
        }
        WorldScene recovered = read(recoveryFolder);
        WorldScene safeScene = recoverySafeCopy(recovered);
        String base = recoveryFolder.getFileName().toString().replaceFirst("^_recovery_", "");
        if (safeScene.name == null || safeScene.name.isBlank()) {
            safeScene.name = base;
        }
        safeScene.archiveType = "world";
        safeScene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.archive.recovery_finalized_note"));
        Path target = uniqueFinalizedFolder(base);
        saveWorldFolder(safeScene, target);
        deleteChunkCacheFolder(recovered == null ? null : recovered.chunkCacheFolder);
        if (Files.exists(recoveryFolder)) {
            deleteRecursive(recoveryFolder);
        }
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.recovery_finalized", target.getFileName()));
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
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.archive_copied", target.getFileName()));
        refresh();
        return target;
    }

    public ExportValidationReport validateArchive(Path archiveOrFolder) throws IOException {
        if (archiveOrFolder == null || !Files.exists(archiveOrFolder)) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_does_not_exist"));
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
        copy = ProjectData.metadataCopy(source);

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
        String cacheFolder = null;
        if (isRecovery(archiveOrFolder)) {
            try {
                WorldScene scene = read(archiveOrFolder);
                cacheFolder = scene == null ? null : scene.chunkCacheFolder;
            } catch (IOException ignored) {
            }
        }
        deleteRecursive(archiveOrFolder);
        deleteChunkCacheFolder(cacheFolder);
        WorldBinderActivityLog.add(net.worldbinder.util.Lang.stored("worldbinder.activity.archive_deleted", archiveOrFolder.getFileName()));
        refresh();
    }

    private void deleteChunkCacheFolder(String cacheFolder) {
        if (cacheFolder == null || cacheFolder.isBlank()) {
            return;
        }
        String name = cacheFolder.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9_.-]", "");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            return;
        }
        Path cacheRoot = WorldBinderPaths.CACHE_ROOT.resolve(name).normalize();
        if (!Files.isDirectory(cacheRoot)) {
            return;
        }
        try {
            deleteRecursive(cacheRoot);
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.chunk_cache.clean_failed", cacheRoot), exception);
        }
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
        if(!cachedLanguage.equals(Lang.language())){cachedValidationLines.clear();cachedLanguage=Lang.language();}
        return cachedValidationLines.computeIfAbsent(path, this::readValidationLine);
    }

    private String readValidationLine(Path path) {
        Path report = Files.isDirectory(path) ? path.resolve(WORLDBINDER_FOLDER).resolve("export_validation.json") : null;
        if (isRecovery(path)) {
            String state = recoveryState(path);
            if ("writing".equals(state)) return Lang.string("worldbinder.archive.recovery_writing");
            if ("failed".equals(state)) return Lang.string("worldbinder.archive.recovery_failed");
            if (report == null || !Files.isRegularFile(report)) return Lang.string("worldbinder.archive.recovery_status",recoveryLabel(state));
        }
        if (report == null || !Files.isRegularFile(report)) {
            String manifestLine = readManifestValidationLine(path);
            return manifestLine == null ? Lang.string("worldbinder.validation.no_report") : manifestLine;
        }
        try {
            String json = Files.readString(report);
            String status = extractJsonString(json, "status", "UNKNOWN");
            String score = extractJsonNumber(json, "score", "?");
            String missing = extractJsonNumber(json, "missingChunks", "0");
            String partial = extractJsonNumber(json, "partialSnapshots", "0");
            String failed = extractJsonNumber(json, "failedSnapshots", "0");
            String suffix = ("0".equals(missing) && "0".equals(partial) && "0".equals(failed)) ? "" : Lang.string("worldbinder.archive.validation_counts", missing, partial, failed);
            return Lang.string("worldbinder.archive.quality_summary", validationStatus(status), score, gradeFromScore(score), suffix);
        } catch (IOException exception) {
            return Lang.string("worldbinder.archive.validation_unavailable");
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
            String suffix = ("0".equals(missing) && "0".equals(partial) && "0".equals(failed)) ? "" : Lang.string("worldbinder.archive.validation_counts", missing, partial, failed);
            return Lang.string("worldbinder.archive.quality_summary", validationStatus(status), score, gradeFromScore(score), suffix);
        } catch (IOException exception) {
            return null;
        }
    }

    private String quickArchiveLine(Path path) {
        if (isRecovery(path)) {
            return Lang.string("worldbinder.archive.recovery_status",recoveryLabel(recoveryState(path)));
        }
        return Lang.string(Files.isDirectory(path)?"worldbinder.archive.world_folder":"worldbinder.archive.scene");
    }
    private static String validationStatus(String status){return Lang.string(validationStatusKey(status));}
    private static String validationStatusKey(String status){return "worldbinder.validation.status."+switch(status){case "PASS","OK"->"pass";case "WARN","WARNING"->"warn";case "FAIL","FAILED"->"fail";default->"unknown";};}
    public static String recoveryLabel(String state){
        return Lang.string("worldbinder.recovery.state."+(Set.of("none","complete","writing","missing","failed","unknown").contains(state)?state:"unknown"));
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
            ARCHIVE_JSON.toJson(scene, writer);
            writer.flush();
        }
        forceFile(archive);
    }

    private WorldScene readArchiveFile(Path archive) throws IOException {
        try {
            // Modern archives place the persistent ID near the beginning. Inspect it without keeping
            // a second, multi-million-block JSON object tree alive alongside the actual project.
            boolean identified=false;
            try(var header=WorldBinder.GSON.newJsonReader(Files.newBufferedReader(archive))){
                header.beginObject();while(header.hasNext()){
                    if(header.nextName().equals("projectId")){identified=header.peek()!=com.google.gson.stream.JsonToken.NULL;if(identified)identified=!header.nextString().isBlank();break;}
                    header.skipValue();
                }
            }
            WorldScene scene;
            try(Reader reader=Files.newBufferedReader(archive)){scene=WorldBinder.GSON.fromJson(reader,WorldScene.class);}
            boolean legacy = scene != null && (scene.formatVersion < 3||!identified);
            ProjectData.normalize(scene, legacy);
            if (!identified || scene.projectId == null || scene.projectId.isBlank()) {
                // Preserve the exact identity algorithm used for legacy folders before streaming was
                // introduced. Those IDs can already own recovery data and must not change on upgrade.
                try(Reader reader=Files.newBufferedReader(archive)){var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();scene.projectId=java.util.UUID.nameUUIDFromBytes((archive.toAbsolutePath().normalize()+":"+json).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
            }
            return scene;
        } catch (RuntimeException exception) {
            throw new net.worldbinder.validation.FindingIOException(net.worldbinder.validation.Finding.of("worldbinder.error.unreadable_worldbinder_archive_value", archive.getFileName()), exception);
        }
    }

    private void writeRecoveryManifest(Path metadataFolder, WorldScene scene, String status, String error) throws IOException {
        Files.createDirectories(metadataFolder);
        String manifest = "{\n" +
                "  \"format\": \"worldbinder-recovery-v1\",\n" +
                "  \"status\": \"" + escape(status) + "\",\n" +
                "  \"updatedAt\": \"" + Instant.now() + "\",\n" +
                "  \"name\": \"" + escape(scene == null ? "" : scene.name) + "\",\n" +
                "  \"projectId\": \"" + escape(scene == null ? "" : scene.projectId) + "\",\n" +
                "  \"worldIdentity\": \"" + escape(scene == null ? "" : scene.worldIdentity) + "\",\n" +
                "  \"dimension\": \"" + escape(scene == null ? "" : scene.dimension) + "\",\n" +
                "  \"chunks\": " + (scene == null ? 0 : scene.projectChunkCount()) + ",\n" +
                "  \"blocks\": " + (scene == null ? 0 : scene.projectBlockCount()) + ",\n" +
                "  \"entities\": " + (scene == null ? 0 : scene.projectEntityCount()) + ",\n" +
                "  \"chunkCacheFolder\": \"" + escape(scene == null ? "" : scene.chunkCacheFolder) + "\"" +
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
            if (value >= 92) return Lang.string("worldbinder.quality.excellent");
            if (value >= 80) return Lang.string("worldbinder.quality.good");
            if (value >= 65) return Lang.string("worldbinder.quality.usable");
            if (value >= 45) return Lang.string("worldbinder.quality.partial");
        } catch (NumberFormatException ignored) {
        }
        return Lang.string("worldbinder.quality.review");
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
            WorldBinder.LOGGER.error(Lang.string("worldbinder.log.scene.refresh_library_failed"), exception);
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
                "  \"blocks\": " + scene.projectBlockCount() + ",\n" +
                "  \"blockEntities\": " + scene.projectBlockEntityCount() + ",\n" +
                "  \"entities\": " + scene.projectEntityCount() + ",\n" +
                "  \"chunkSnapshots\": " + scene.projectChunkCount() + ",\n" +
                "  \"maps\": " + scene.projectMapCount() + ",\n" +
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
        ExportReadmeWriter.write(scene, folder, export.chunks());
    }



    private void writeMapDataPlaceholders(WorldScene scene, Path folder) {
    }

    private void writePlayerMetadata(WorldScene scene, Path folder) throws IOException {
        net.worldbinder.export.common.AuxiliaryDataWriter.write(scene, folder);
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
                    "  \"blocks\": " + scene.projectBlockCount() + ",\n" +
                    "  \"entities\": " + scene.projectEntityCount() + ",\n" +
                    "  \"maps\": " + scene.projectMapCount() + ",\n" +
                    "  \"chunks\": " + export.chunks() + "\n" +
                    "}\n");
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.scene.capture_metadata_failed"), exception);
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

    /** Minecraft 26.x loads the bundled world pack from {@code resourcepacks/resources.zip}; older versions from the world root. */
    public static Path bundledResourcePackPath(Path worldFolder, int targetDataVersion) {
        return targetDataVersion >= 4786 ? worldFolder.resolve("resourcepacks").resolve("resources.zip") : worldFolder.resolve("resources.zip");
    }

    public void cleanupRecovery(WorldScene owner) {
        refresh();
        for (Path recovery : recoverySessions()) {
            try {
                Path root = WorldBinderPaths.RECOVERY_ROOT.toAbsolutePath().normalize();
                Path candidate = recovery.toAbsolutePath().normalize();
                if (candidate.startsWith(root) && !candidate.equals(root) && RecoveryProjects.sameOwner(owner, read(recovery)))
                    deleteRecursive(candidate);
            } catch (IOException exception) {
                WorldBinder.LOGGER.warn(Lang.string("worldbinder.log.scene.old_recovery_delete_failed", recovery), exception);
            }
        }
        refresh();
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
