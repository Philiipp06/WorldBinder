package net.worldbinder.validation;

import net.worldbinder.WorldBinder;
import net.worldbinder.scene.*;
import net.worldbinder.export.common.ExportCompatibility;
import net.worldbinder.util.Lang;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ExportValidator {
    private ExportValidator() {}

    public static ExportValidationReport validate(WorldScene scene, Path folder) {
        return validate(scene, folder, null);
    }

    public static ExportValidationReport validate(WorldScene scene, Path folder, net.worldbinder.storage.StorageProgress progress) {
        scene=net.worldbinder.export.common.DimensionExportView.prepare(net.worldbinder.export.common.ExportFiltering.apply(scene));
        if(scene.dimensions.isEmpty())return validateDimension(scene,folder,progress);
        ExportValidationReport result=null;
        for(var scope:ProjectDimensions.all(scene)){
            var view=ProjectDimensions.dimensionOnly(scope);view.exportSettings=scene.exportSettings;view.targetMinecraftVersion=scene.targetMinecraftVersion;view.player=scene.player;
            view.filtersApplied=true;view.exportRelightChunks.addAll(scope.exportRelightChunks);
            var checked=validateDimension(view,folder,progress);
            checked.errors=checked.errors.stream().map(f->Finding.of("worldbinder.dimensions.finding",scope.dimension,f)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            checked.warnings=checked.warnings.stream().map(f->Finding.of("worldbinder.dimensions.finding",scope.dimension,f)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if(result==null)result=checked;
            else{
                result.errors.addAll(checked.errors);result.warnings.addAll(checked.warnings);result.passed.addAll(checked.passed);
                result.chunks+=checked.chunks;result.expectedChunks+=checked.expectedChunks;result.missingChunks+=checked.missingChunks;
                result.blocks+=checked.blocks;result.blockEntities+=checked.blockEntities;result.entities+=checked.entities;result.snapshots+=checked.snapshots;
                result.unknownContainers+=checked.unknownContainers;result.missingMapImages+=checked.missingMapImages;result.partialMapImages+=checked.partialMapImages;
                result.missingBiomeSections+=checked.missingBiomeSections;result.missingLightSections+=checked.missingLightSections;result.relightChunks+=checked.relightChunks;
                result.exportableSnapshots+=checked.exportableSnapshots;result.partialSnapshots+=checked.partialSnapshots;result.activeSnapshots+=checked.activeSnapshots;result.failedSnapshots+=checked.failedSnapshots;
                result.unknownSelectionBlocks+=checked.unknownSelectionBlocks;result.entityFiles|=checked.entityFiles;result.poiFiles|=checked.poiFiles;result.regionFiles|=checked.regionFiles;result.lightEstimated|=checked.lightEstimated;
            }
        }
        result.excludedBlocks=scene.filterCounts==null?0:scene.filterCounts.blocks();result.excludedBlockEntities=scene.filterCounts==null?0:scene.filterCounts.blockEntities();result.excludedEntities=scene.filterCounts==null?0:scene.filterCounts.entities();
        result.mapIds=scene.mapCount();result.filesValid=result.errors.isEmpty();result.regionWriteErrors=result.errors.size();
        result.completeness=result.warnings.isEmpty()?"COMPLETE_FOR_RECORDED_SCOPE":"INCOMPLETE_OR_UNKNOWN";
        result.score=result.filesValid?Math.max(0,100-Math.min(75,result.warnings.size()*5)):0;
        result.status=!result.filesValid?"FAILED":result.warnings.isEmpty()?"OK":"WARNING";
        return result;
    }
    private static ExportValidationReport validateDimension(WorldScene scene,Path folder,net.worldbinder.storage.StorageProgress progress){
        ExportValidationReport report = new ExportValidationReport();
        report.warnings.addAll(scene.exportNotices);
        if(scene.replaySeekGaps>0)report.warnings.add(Finding.of("worldbinder.replay.seek_gaps",scene.replaySeekGaps));
        if(scene.filterCounts!=null){report.excludedBlocks=scene.filterCounts.blocks();report.excludedBlockEntities=scene.filterCounts.blockEntities();report.excludedEntities=scene.filterCounts.entities();}
        report.exportName = scene.name == null ? folder.getFileName().toString() : scene.name;
        report.folder = folder.toAbsolutePath().toString();
        report.blocks = scene.blockCount(); report.blockEntities = scene.blockEntityCount();
        report.entities = scene.entityCount(); report.snapshots = scene.chunkSnapshotCount(); report.mapIds = scene.mapCount();
        report.levelDat = Files.isRegularFile(folder.resolve("level.dat"));
        report.levelDatOld = Files.isRegularFile(folder.resolve("level.dat_old"));
        report.sessionLock = Files.isRegularFile(folder.resolve("session.lock"));
        report.archiveJson = Files.isRegularFile(folder.resolve("worldbinder/worldbinder_archive.json"));
        report.manifestJson = Files.isRegularFile(folder.resolve("worldbinder/worldbinder_manifest.json"));
        report.metadataJson = Files.isRegularFile(folder.resolve("worldbinder/metadata.json"));
        report.exportReadme = Files.isRegularFile(folder.resolve("README.md"));
        report.resourcePack = Files.isRegularFile(folder.resolve("resources.zip")) || Files.isRegularFile(folder.resolve("resourcepacks/resources.zip"));
        report.modernSavedData = Files.isRegularFile(folder.resolve("data/minecraft/world_gen_settings.dat"));
        report.vanillaWorld = report.levelDat || report.sessionLock || Files.isDirectory(folder.resolve("region")) || Files.isDirectory(folder.resolve("dimensions"));
        if(scene.resourcePackExpected&&!report.resourcePack&&report.vanillaWorld)
            report.warnings.add(Finding.of("worldbinder.finding.recorded_resource_pack_is_not_enabled_in_this_world_inspect_the_archive_pack_reference_and"));
        var expected = WorldReadback.expected(scene);
        report.expectedChunks = expected.size();
        var relight = net.worldbinder.export.common.ExportLightPolicy.invalidatedChunks(scene);
        report.relightChunks = (int) expected.stream().filter(relight::contains).count();
        if (report.relightChunks > 0) report.warnings.add(Finding.of("worldbinder.export.light_recalculation", report.relightChunks));
        if (report.vanillaWorld) WorldReadback.check(scene, folder, report, expected, relight, progress);
        else { report.chunks = report.expectedChunks; report.passed.add(Finding.of("worldbinder.finding.archive_deserialized_vanilla_file_read_back_not_applicable")); }
        report.regionFiles = report.chunks > 0 && report.vanillaWorld;
        report.filesValid = report.errors.isEmpty();
        for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
            if (snapshot.isDone()) report.exportableSnapshots++;
            else if (snapshot.effectiveStatus() == ChunkCaptureStatus.FAILED) report.failedSnapshots++;
            else report.partialSnapshots++;
            if (snapshot.sections == null || snapshot.sections.isEmpty()) {
                report.missingBiomeSections++; report.missingLightSections++;
            } else for (var section : snapshot.sections.values()) {
                if (!section.hasBiomes()) report.missingBiomeSections++;
                if (!section.hasBlockLight() || !section.hasSkyLight()) report.missingLightSections++;
            }
        }
        report.activeSnapshots = scene.pendingChunks.size();
        if(scene.selectionBounds!=null){
            report.unknownSelectionBlocks=scene.selectionBounds.volume()-net.worldbinder.selection.RecordedCoverage.knownVolume(scene,scene.selectionBounds);
            if(report.unknownSelectionBlocks>0)report.warnings.add(Finding.of("worldbinder.selection.unknown",report.unknownSelectionBlocks));
        }
        for (BlockRecord block : scene.blocks) {
            if (isContainer(ExportCompatibility.id(block.state))) {
                String key = ContainerRecord.blockKey(scene.originX + block.x, scene.originY + block.y, scene.originZ + block.z);
                ContainerRecord known = scene.containers.get(key);
                if (known == null || !known.type.equals(ExportCompatibility.id(block.state)) || !known.complete()) report.unknownContainers++;
            }
        }
        for (EntityRecord entity : scene.entities) if (isContainer(entity.type)) {
            var known=scene.containers.get(ContainerRecord.entityKey(entity.uuid));if(known==null||!known.complete())report.unknownContainers++;
        }
        long unknownSlots=scene.containers.values().stream().mapToLong(c->c.slotCount-c.knownCount()).sum();
        if(scene.player!=null&&scene.player.enderChest!=null)unknownSlots+=scene.player.enderChest.slotCount-scene.player.enderChest.knownCount();
        if(unknownSlots>0)report.warnings.add(Finding.of("worldbinder.containers.partial",unknownSlots));
        if(scene.storageNotes.stream().anyMatch(n->n.contains("worldbinder.capture_profile.history")))report.warnings.add(Finding.of("worldbinder.capture_profile.history"));
        long unknownNbt=scene.blocks.stream().filter(b->b.hasBlockEntity&&(b.blockEntityNbt==null||b.blockEntityNbt.isBlank())).count();
        if(unknownNbt>0)report.warnings.add(Finding.of("worldbinder.finding.value_block_entity_payload_s_unavailable", unknownNbt));
        long unknownEntityNbt=scene.entities.stream().filter(e->e.fullNbt==null||e.fullNbt.isBlank()).count();
        if(unknownEntityNbt>0)report.warnings.add(Finding.of("worldbinder.finding.value_entity_payload_s_unavailable_basic_recorded_attributes_used", unknownEntityNbt));
        for (int id : scene.mapIds) {
            CapturedMap map = scene.maps.get(id);
            if (map == null || map.receivedPixels() == 0 || map.nbt == null) report.missingMapImages++;
            else {
                if (map.receivedPixels() < 16384) report.partialMapImages++;
                if (!map.centerKnown) report.warnings.add(Finding.of("worldbinder.finding.map_value_center_was_not_received_image_and_received_metadata_retained", id));
            }
        }
        if (report.unknownContainers > 0) report.warnings.add(Finding.of("worldbinder.finding.value_container_s_contents_unknown_not_verified_empty", report.unknownContainers));
        if (report.missingMapImages > 0) report.warnings.add(Finding.of("worldbinder.finding.value_map_image_s_never_received", report.missingMapImages));
        if (report.partialMapImages > 0) report.warnings.add(Finding.of("worldbinder.finding.value_map_image_s_only_partially_received", report.partialMapImages));
        if (report.missingBiomeSections > 0) report.warnings.add(Finding.of("worldbinder.finding.value_section_s_legacy_chunk_s_original_biome_data_unavailable_fallback_used", report.missingBiomeSections));
        if (report.missingLightSections > 0) report.warnings.add(Finding.of("worldbinder.finding.value_section_s_legacy_chunk_s_original_light_unavailable_target_game_must_relight", report.missingLightSections));
        if (scene.chunkSnapshots.isEmpty() && !scene.blocks.isEmpty()) report.warnings.add(Finding.of("worldbinder.finding.legacy_archive_chunk_coverage_original_biomes_and_light_unknown"));
        if (report.activeSnapshots + report.partialSnapshots + report.failedSnapshots > 0) report.warnings.add(Finding.of("worldbinder.finding.recording_has_pending_partial_or_failed_chunks_committed_chunk_revisions_were_retained"));
        if (scene.worldIdentity == null) report.warnings.add(Finding.of("worldbinder.finding.world_server_identity_unavailable_in_this_archive"));
        if (scene.exportSettings.includePlayer) {
            if (scene.player == null || scene.player.nbt == null) report.warnings.add(Finding.of("worldbinder.finding.own_player_state_unavailable_prepared_player_used"));
            else {
                if (scene.player.enderChest == null) report.warnings.add(Finding.of("worldbinder.finding.own_ender_chest_contents_unknown"));
                if (!scene.player.statsReceived) report.warnings.add(Finding.of("worldbinder.finding.own_statistics_not_received"));
                if (!scene.player.advancementsReceived) report.warnings.add(Finding.of("worldbinder.finding.own_advancements_not_received"));
            }
        }
        for (var impact : ExportCompatibility.analyze(scene)) {
            var finding=Finding.of("worldbinder.finding.version_impact_value_value_value", impact.count(), impact.source(), impact.replacement());
            if(impact.kind().equals("generation"))report.passed.add(finding);else if(!impact.kind().equals("scope"))report.warnings.add(finding);
        }
        report.lightEstimated = report.missingLightSections > 0 || report.relightChunks > 0;
        report.completeness = report.warnings.isEmpty() ? "COMPLETE_FOR_RECORDED_SCOPE" : "INCOMPLETE_OR_UNKNOWN";
        report.score = report.filesValid ? Math.max(0, 100 - Math.min(75, report.warnings.size() * 5)) : 0;
        report.regionWriteErrors = report.errors.size();
        report.status = !report.filesValid ? "FAILED" : report.warnings.isEmpty() ? "OK" : "WARNING";
        return report;
    }

    public static boolean isContainer(String id) {
        return id != null && (id.endsWith(":chest") || id.endsWith(":trapped_chest") || id.endsWith(":barrel") || id.endsWith("shulker_box")
                || id.endsWith(":furnace") || id.endsWith(":blast_furnace") || id.endsWith(":smoker") || id.endsWith(":hopper")
                || id.endsWith(":dispenser") || id.endsWith(":dropper") || id.endsWith(":brewing_stand") || id.contains("chest_boat")
                || id.contains("chest_raft") || id.endsWith(":chest_minecart") || id.endsWith(":hopper_minecart"));
    }

    public static void writeReport(WorldScene scene, Path folder, ExportValidationReport report) {
        try {
            Path metadata = folder.resolve("worldbinder"); Files.createDirectories(metadata);
            Files.writeString(metadata.resolve("export_validation.json"), WorldBinder.GSON.toJson(report));
            Files.writeString(metadata.resolve("EXPORT_VALIDATION.txt"), toText(report));
        } catch (IOException exception) { throw new FindingUncheckedIOException(Finding.of("worldbinder.error.persist_validation"), exception); }
    }
    public static String shortLine(ExportValidationReport report) {
        return Lang.string("worldbinder.validation.summary", report.filesValid ? Lang.string("worldbinder.validation.valid") : Lang.string("worldbinder.validation.invalid"),
                report.chunks, report.warnings.size(), report.errors.size());
    }
    private static String toText(ExportValidationReport report) {
        StringBuilder text = new StringBuilder("WorldBinder 1.7.0\n");
        for(String language:new String[]{"de_de","en_us"}){
            text.append('\n').append(Lang.in(language,"worldbinder.generated.report_header",Lang.in(language,report.filesValid?"worldbinder.validation.valid":"worldbinder.validation.invalid"),Lang.in(language,report.completeness.equals("COMPLETE_FOR_RECORDED_SCOPE")?"worldbinder.report.complete":"worldbinder.report.incomplete"),report.expectedChunks,report.chunks,report.missingChunks,report.unknownSelectionBlocks));
            text.append('\n').append(Lang.in(language,"worldbinder.filters.summary",report.excludedBlocks,report.excludedBlockEntities,report.excludedEntities)).append('\n');
            for(Finding pass:report.passed)text.append('\n').append(Lang.in(language,"worldbinder.generated.pass",pass));
            for(Finding error:report.errors)text.append('\n').append(Lang.in(language,"worldbinder.generated.error",error));
            for(Finding warning:report.warnings)text.append('\n').append(Lang.in(language,"worldbinder.generated.warning",warning));
            text.append('\n');
        }
        return text.append('\n').append(ExportLegalNoticeText()).toString();
    }
    private static String ExportLegalNoticeText() { return net.worldbinder.export.common.ExportLegalNotice.text(); }
}
