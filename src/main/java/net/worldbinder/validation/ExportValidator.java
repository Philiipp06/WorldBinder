package net.worldbinder.validation;

import net.worldbinder.WorldBinder;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.ChunkCaptureStatus;
import net.worldbinder.scene.WorldScene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class ExportValidator {
    private ExportValidator() {}

    public static ExportValidationReport validate(WorldScene scene, Path folder) {
        ExportValidationReport report = new ExportValidationReport();
        report.exportName = scene == null || scene.name == null ? folder.getFileName().toString() : scene.name;
        report.folder = folder.toAbsolutePath().toString();
        report.blocks = scene == null ? 0 : scene.blockCount();
        report.blockEntities = scene == null ? 0 : scene.blockEntityCount();
        report.entities = scene == null ? 0 : scene.entityCount();
        report.snapshots = scene == null ? 0 : scene.chunkSnapshotCount();
        report.mapIds = scene == null ? 0 : scene.mapCount();
        report.chunks = report.snapshots;
        report.expectedChunks = expectedChunks(scene);
        report.missingChunks = Math.max(0, report.expectedChunks - report.snapshots);

        report.levelDat = Files.isRegularFile(folder.resolve("level.dat"));
        report.sessionLock = Files.isRegularFile(folder.resolve("session.lock"));
        Path overworld = folder.resolve("dimensions").resolve("minecraft").resolve("overworld");
        report.regionFiles = hasFiles(overworld.resolve("region"), ".mca") || hasFiles(folder.resolve("region"), ".mca");
        report.entityFiles = hasFiles(overworld.resolve("entities"), ".mca") || hasFiles(folder.resolve("entities"), ".mca");
        report.poiFiles = Files.isDirectory(overworld.resolve("poi")) || Files.isDirectory(folder.resolve("poi"));
        report.archiveJson = Files.isRegularFile(folder.resolve("worldbinder/worldbinder_archive.json"));
        report.manifestJson = Files.isRegularFile(folder.resolve("worldbinder/worldbinder_manifest.json"));
        report.metadataJson = Files.isRegularFile(folder.resolve("worldbinder/metadata.json"));
        report.resourcePack = Files.isRegularFile(folder.resolve("resourcepacks").resolve("resources.zip")) || Files.isRegularFile(folder.resolve("resources.zip"));
        report.zipFile = Files.isRegularFile(folder.resolveSibling(folder.getFileName().toString() + ".zip"));
        report.vanillaWorld = report.levelDat || report.sessionLock || report.regionFiles || report.entityFiles || report.poiFiles || report.metadataJson;
        if (!report.vanillaWorld && scene != null) {
            report.archiveJson = true;
        }

        if (scene != null && scene.chunkSnapshots != null) {
            int expectedHeight = Math.max(1, Math.abs(scene.sizeY));
            int checked = 0;
            long budgetStart = System.nanoTime();
            for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
                if (snapshot == null) continue;
                ChunkCaptureStatus status = snapshot.effectiveStatus();
                if (status == ChunkCaptureStatus.DONE) report.exportableSnapshots++;
                if (status == ChunkCaptureStatus.PARTIAL || status == ChunkCaptureStatus.RECOVERY) report.partialSnapshots++;
                if (status == ChunkCaptureStatus.QUEUED || status == ChunkCaptureStatus.SCANNING) report.activeSnapshots++;
                if (status == ChunkCaptureStatus.FAILED) report.failedSnapshots++;
                if (snapshot.qualityScore(expectedHeight) < 0.65D) report.lowQualitySnapshots++;
                if (snapshot.lightEstimated) report.lightEstimated = true;
                if (++checked % 4096 == 0) {
                    budgetStart = throttleArchiveWork(budgetStart);
                }
            }
        }

        score(report);
        classify(report);
        return report;
    }

    public static void writeReport(WorldScene scene, Path folder, ExportValidationReport report) {
        try {
            Path wb = folder.resolve("worldbinder");
            Files.createDirectories(wb);
            Files.writeString(wb.resolve("export_validation.json"), WorldBinder.GSON.toJson(report));
            Files.writeString(wb.resolve("EXPORT_VALIDATION.txt"), toText(report));
        } catch (IOException exception) {
            WorldBinder.LOGGER.warn("Failed to write export validation report", exception);
        }
    }

    public static String shortLine(ExportValidationReport report) {
        String status = report.status == null ? "UNKNOWN" : report.status;
        String issues = report.missingChunks > 0 || report.partialSnapshots > 0 || report.failedSnapshots > 0
                ? " • M:" + report.missingChunks + " P:" + report.partialSnapshots + " F:" + report.failedSnapshots
                : "";
        return status + " • Quality " + report.score + "% (" + report.grade() + ") • " + report.chunks + " chunks" + issues;
    }

    private static long throttleArchiveWork(long budgetStartNanos) {
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

    private static void score(ExportValidationReport r) {
        int score = 0;
        if (!r.vanillaWorld) { score += 28; r.passed.add("WorldBinder scene/archive metadata loaded"); }
        if (r.levelDat) { score += 14; r.passed.add("level.dat present"); } else if (r.vanillaWorld) r.warnings.add("level.dat missing");
        if (r.sessionLock) { score += 4; r.passed.add("session.lock present"); } else if (r.vanillaWorld) r.warnings.add("session.lock missing");
        if (r.regionFiles) { score += 18; r.passed.add("overworld region files present"); } else if (r.vanillaWorld) r.warnings.add("No overworld region files were written");
        if (r.entityFiles || r.entities == 0 || !r.vanillaWorld) { score += 10; r.passed.add("entities region ready"); } else { r.entityDataMissing = true; r.warnings.add("Entities captured but no entity region files found"); }
        if (r.poiFiles) { score += 4; r.passed.add("poi folder present"); } else if (r.vanillaWorld) r.warnings.add("poi folder missing");
        if (r.archiveJson) { score += 7; r.passed.add("WorldBinder archive present"); } else r.warnings.add("WorldBinder archive missing");
        if (r.manifestJson) { score += 5; r.passed.add("manifest present"); } else r.warnings.add("manifest missing");
        if (r.blocks > 0) score += 12; else r.warnings.add("No blocks captured");
        if (r.snapshots > 0) score += 8; else r.warnings.add("No minimap/chunk snapshots captured");
        if (r.blockEntities > 0) score += 5; else r.warnings.add("No block entities captured or available");
        if (r.entities > 0) score += 5; else r.warnings.add("No entities captured or available");
        if (r.resourcePack) score += 3;
        if (r.zipFile) score += 2;
        if (r.failedSnapshots > 0) {
            score -= Math.min(16, r.failedSnapshots * 2);
            r.regionWriteErrors += r.failedSnapshots;
            r.warnings.add(r.failedSnapshots + " chunk snapshot(s) marked as failed");
        }
        if (r.missingChunks > 0) {
            score -= Math.min(12, Math.max(1, r.missingChunks / 10));
            r.warnings.add(r.missingChunks + " chunk snapshot(s) missing from the expected archive area");
        }
        if (r.partialSnapshots > 0) {
            score -= Math.min(10, r.partialSnapshots);
            r.warnings.add(r.partialSnapshots + " chunk snapshot(s) are partial/recovery only");
        }
        if (r.vanillaWorld && !r.regionFiles && r.blocks > 0) {
            r.regionWriteErrors++;
        }
        if (r.lowQualitySnapshots > 0) {
            score -= Math.min(10, r.lowQualitySnapshots);
            r.warnings.add(r.lowQualitySnapshots + " chunk snapshot(s) have low coverage quality");
        }
        if (r.lightEstimated) r.warnings.add("Light data is estimated from the client export");
        r.score = Math.max(0, Math.min(100, score));
    }

    private static void classify(ExportValidationReport r) {
        if (r.score < 45 || r.regionWriteErrors > 0 || r.failedSnapshots > 0) {
            r.status = "FAILED";
        } else if (r.score < 80 || r.missingChunks > 0 || r.partialSnapshots > 0 || r.entityDataMissing || r.lowQualitySnapshots > 0) {
            r.status = "WARNING";
        } else {
            r.status = "OK";
        }
    }

    private static int expectedChunks(WorldScene scene) {
        if (scene == null || scene.sizeX <= 0 || scene.sizeZ <= 0) {
            return 0;
        }
        long x = Math.max(1L, (scene.sizeX + 15L) / 16L);
        long z = Math.max(1L, (scene.sizeZ + 15L) / 16L);
        return (int) Math.min(Integer.MAX_VALUE, x * z);
    }

    private static boolean hasFiles(Path folder, String suffix) {
        if (!Files.isDirectory(folder)) return false;
        try (Stream<Path> stream = Files.list(folder)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(suffix));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String toText(ExportValidationReport r) {
        StringBuilder builder = new StringBuilder();
        builder.append("WorldBinder Export Validation\n");
        builder.append("=============================\n\n");
        builder.append("Export: ").append(r.exportName).append('\n');
        builder.append("Folder: ").append(r.folder).append('\n');
        builder.append("Status: ").append(r.status).append('\n');
        builder.append("Quality: ").append(r.score).append("% (").append(r.grade()).append(")\n");
        builder.append("Type: ").append(r.vanillaWorld ? "Vanilla world folder" : "WorldBinder archive").append("\n\n");
        builder.append("Chunks: ").append(r.chunks).append('\n');
        builder.append("Expected chunks: ").append(r.expectedChunks).append('\n');
        builder.append("Missing chunks: ").append(r.missingChunks).append('\n');
        builder.append("Blocks: ").append(r.blocks).append('\n');
        builder.append("BlockEntities: ").append(r.blockEntities).append('\n');
        builder.append("Entities: ").append(r.entities).append('\n');
        builder.append("Snapshots: ").append(r.snapshots).append('\n');
        builder.append("Exportable: ").append(r.exportableSnapshots).append('\n');
        builder.append("Partial/Recovery: ").append(r.partialSnapshots).append('\n');
        builder.append("Active: ").append(r.activeSnapshots).append('\n');
        builder.append("Failed: ").append(r.failedSnapshots).append('\n');
        builder.append("Entity data missing: ").append(r.entityDataMissing).append('\n');
        builder.append("Region write errors: ").append(r.regionWriteErrors).append("\n\n");
        builder.append("Passed:\n");
        for (String value : r.passed) builder.append(" - ").append(value).append('\n');
        builder.append("\nWarnings:\n");
        if (r.warnings.isEmpty()) builder.append(" - none\n");
        for (String value : r.warnings) builder.append(" - ").append(value).append('\n');
        return builder.toString();
    }
}