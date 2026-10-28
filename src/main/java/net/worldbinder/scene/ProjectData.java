package net.worldbinder.scene;

import net.worldbinder.WorldBinder;
import java.util.*;

/** Archive operations do not depend on an active server connection. */
public final class ProjectData {
    private ProjectData() {}

    public static WorldScene metadataCopy(WorldScene source) {
        // Copy new metadata here as well: explicit field-by-field copies used to silently drop additions.
        var json = new com.google.gson.JsonObject();
        Set<String> payloads = Set.of("blocks", "entities", "chunkSnapshots", "pendingChunks", "dimensions");
        try {
            for (var field : WorldScene.class.getFields()) {
                if (!payloads.contains(field.getName())&&!java.lang.reflect.Modifier.isTransient(field.getModifiers())) json.add(field.getName(), WorldBinder.GSON.toJsonTree(field.get(source)));
            }
        } catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        WorldScene copy=WorldBinder.GSON.fromJson(json, WorldScene.class);copy.resourcePackSource=source.resourcePackSource;
        copy.dimensions=new LinkedHashMap<>(source.dimensions==null?Map.of():source.dimensions);
        copy.exportNotices=new ArrayList<>(source.exportNotices==null?List.of():source.exportNotices);
        return copy;
    }

    public static long chunkKey(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }
    public static long chunkKey(WorldScene scene, BlockRecord block) {
        return chunkKey((scene.originX + block.x) >> 4, (scene.originZ + block.z) >> 4);
    }

    /** Replace only a successfully captured vertical range; absence inside that range represents air. */
    public static List<BlockRecord> mergeChunk(WorldScene scene, List<BlockRecord> previous,
                                               List<BlockRecord> current, ChunkSnapshot snapshot) {
        Map<String, BlockRecord> result = new LinkedHashMap<>();
        for (BlockRecord block : previous) {
            int y = scene.originY + block.y;
            boolean replaced = snapshot.captureBox != null ? net.worldbinder.selection.CaptureBounds.from(snapshot.captureBox).contains(scene.originX+block.x,y,scene.originZ+block.z)
                    : snapshot.captureMinY != Integer.MIN_VALUE && y >= snapshot.captureMinY && y <= snapshot.captureMaxY;
            if (snapshot.captureBox == null && snapshot.replacementRanges != null) for (int[] range : snapshot.replacementRanges)
                if (range.length == 2 && y >= range[0] && y <= range[1]) replaced = true;
            if (!replaced)
                result.put(position(block), block);
        }
        Map<String, BlockRecord> old = new HashMap<>();
        for (BlockRecord block : previous) old.put(position(block), block);
        for (BlockRecord block : current) {
            BlockRecord before = old.get(position(block));
            // Unavailable block entity payload must never erase an older payload for the same block.
            if (before != null && sameBlockType(block, before) && block.hasBlockEntity
                    && (block.blockEntityNbt == null || block.blockEntityNbt.isBlank())) {
                block = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(block), BlockRecord.class);
                block.blockEntityNbt = before.blockEntityNbt;
            }
            if (before != null && sameBlockType(block, before) && block.blockEntityNbt != null && before.blockEntityNbt != null) {
                try {
                    var prior = net.minecraft.nbt.TagParser.parseCompoundFully(before.blockEntityNbt).copy();
                    var currentNbt = net.minecraft.nbt.TagParser.parseCompoundFully(block.blockEntityNbt).copy();
                    // Client block entity defaults are not an authoritative menu inventory update.
                    if (prior.get("Items") != null) currentNbt.put("Items", prior.get("Items").copy());
                    block = WorldBinder.GSON.fromJson(WorldBinder.GSON.toJson(block), BlockRecord.class);
                    block.blockEntityNbt = prior.merge(currentNbt).toString();
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_archived_block_entity"), exception); }
            }
            result.put(position(block), block);
        }
        return new ArrayList<>(result.values());
    }

    public static String position(BlockRecord block) { return block.x + "," + block.y + "," + block.z; }

    private static boolean sameBlockType(BlockRecord a, BlockRecord b) {
        return net.worldbinder.export.common.ExportCompatibility.id(a.state).equals(net.worldbinder.export.common.ExportCompatibility.id(b.state));
    }

    public static void discoverMapIds(WorldScene scene) {
        if(scene.dimensions!=null)scene.dimensions.values().forEach(ProjectData::discoverMapIds);
        for (BlockRecord block : scene.blocks) observeMaps(scene, block.blockEntityNbt);
        for (EntityRecord entity : scene.entities) observeMaps(scene, entity.fullNbt);
        for (ContainerRecord container : scene.containers.values()) for (String item : container.items) observeMaps(scene, item);
        if (scene.player != null) { observeMaps(scene, scene.player.nbt); if (scene.player.enderChest != null)
            for (String item : scene.player.enderChest.items) observeMaps(scene, item); }
    }
    private static void observeMaps(WorldScene scene, String snbt) {
        if (snbt == null || !snbt.contains("map")) return;
        try { observeMaps(scene, net.minecraft.nbt.TagParser.parseCompoundFully(snbt)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.cannot_inspect_captured_item_data"), exception); }
    }
    private static void observeMaps(WorldScene scene, net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.CompoundTag compound) {
            if (compound.get("components") instanceof net.minecraft.nbt.CompoundTag components) {
                Integer id = components.getInt("minecraft:map_id").orElse(null);
                if (id != null && !scene.mapIds.contains(id)) scene.mapIds.add(id);
            }
            if (compound.getString("id").orElse("").equals("minecraft:filled_map") && compound.get("tag") instanceof net.minecraft.nbt.CompoundTag legacy) {
                Integer id = legacy.getInt("map").orElse(null); if (id != null && !scene.mapIds.contains(id)) scene.mapIds.add(id);
            }
            for (String key : compound.keySet()) observeMaps(scene,compound.get(key));
        } else if (tag instanceof net.minecraft.nbt.ListTag list) for (var entry : list) observeMaps(scene,entry);
    }

    public static void normalize(WorldScene scene, boolean legacy) {
        if (scene == null) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_contains_no_world"));
        if (scene.formatVersion > 4) throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.archive_requires_a_newer_worldbinder_version"));
        if (scene.storageNotes == null) scene.storageNotes = new ArrayList<>();
        if (legacy) {
            scene.worldIdentity = null;
            scene.storageNotes.add(net.worldbinder.util.Lang.stored("worldbinder.project.legacy_missing"));
        }
        if (scene.exportSettings == null) scene.exportSettings = new ExportSettings();
        if(scene.exportSettings.filters==null)scene.exportSettings.filters=new ExportFilterSettings();
        if(scene.exportSettings.filters.entityIds==null)scene.exportSettings.filters.entityIds=new LinkedHashSet<>();
        if(scene.exportSettings.filters.blockIds==null)scene.exportSettings.filters.blockIds=new LinkedHashSet<>();
        if (scene.exportSettings.generation == null) scene.exportSettings.generation = ExportSettings.Generation.VOID;
        if (scene.exportSettings.replacements == null) scene.exportSettings.replacements = new LinkedHashMap<>();
        if (scene.exportSettings.seed == null) scene.exportSettings.seed = "";
        if (scene.containers == null) scene.containers = new LinkedHashMap<>();
        if (scene.seenPlayers == null) scene.seenPlayers = new LinkedHashMap<>();
        if(scene.captureSettings==null)scene.captureSettings=new CaptureSettings();
        scene.captureSettings.normalize();
        if(scene.formatVersion<4)scene.captureSettings.multiDimension=false;
        if(scene.dimensions==null)scene.dimensions=new LinkedHashMap<>();
        if(scene.recordedChunkChanges==null)scene.recordedChunkChanges=new LinkedHashMap<>();
        ProjectDimensions.validateChildren(scene);
        scene.containers.values().forEach(ContainerMerging::validate);
        if (scene.maps == null) scene.maps = new LinkedHashMap<>();
        if (scene.pendingChunks == null) scene.pendingChunks = new LinkedHashMap<>();
        if (scene.chunkSnapshots == null) scene.chunkSnapshots = new LinkedHashMap<>();
        if (scene.blocks == null) scene.blocks = new ArrayList<>();
        if (scene.entities == null) scene.entities = new ArrayList<>();
        if (scene.mapIds == null) scene.mapIds = new ArrayList<>();
        for (CapturedMap map : scene.maps.values()) {
            if (map == null || map.colors == null || map.colors.length != 16384 || map.received == null || map.received.length != 16384)
                throw new net.worldbinder.validation.FindingException(net.worldbinder.validation.Finding.of("worldbinder.error.invalid_map_image_or_coverage_in_archive"));
        }
        for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
            if (snapshot.sections == null) snapshot.sections = new LinkedHashMap<>();
            if (legacy) { snapshot.hasBiomeData = false; snapshot.lightEstimated = true; }
        }
        scene.formatVersion = 4;
    }
}
