package net.worldbinder.validation;

import net.minecraft.SharedConstants;
import net.worldbinder.util.Lang;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.common.*;
import net.worldbinder.export.family.WorldExportModules;
import net.worldbinder.scene.*;
import net.worldbinder.version.*;
import java.nio.file.*;
import java.util.*;

final class WorldReadback {
    private WorldReadback() {}

    static Set<Long> expected(WorldScene scene) {
        Set<Long> keys = new LinkedHashSet<>();
        if (scene == null) return keys;
        scene.blocks.forEach(b -> keys.add(ProjectData.chunkKey(scene, b)));
        scene.chunkSnapshots.values().stream().filter(s -> s.isDone() || s.verifiedEmpty).forEach(s -> keys.add(ProjectData.chunkKey(s.chunkX, s.chunkZ)));
        return keys;
    }

    static void check(WorldScene scene, Path folder, ExportValidationReport report, Set<Long> expected, Set<Long> relight, net.worldbinder.storage.StorageProgress progress) {
        var target = TargetMinecraftVersion.resolve(scene.targetMinecraftVersion);
        int version = target.effectiveDataVersion();
        var profile = VersionRegistry.resolve(target);
        try {
            CompoundTag data = read(folder.resolve("level.dat")).getCompound("Data").orElseThrow();
            require(data.getInt("DataVersion").orElse(-1) == version, Finding.of("worldbinder.finding.level_dat_dataversion_does_not_match_target"));
            Path generation = folder.resolve("data/minecraft/world_gen_settings.dat");
            CompoundTag settings = profile.writesWorldGenSettingsFile() ? read(generation).getCompound("data").orElseThrow()
                    : data.getCompound("WorldGenSettings").orElseGet(CompoundTag::new);
            if (version >= 2566) {
                CompoundTag dimensions = settings.getCompound("dimensions").orElseThrow();
                require(dimensions.contains("minecraft:overworld") && dimensions.contains("minecraft:the_nether") && dimensions.contains("minecraft:the_end"), Finding.of("worldbinder.finding.missing_vanilla_dimension_definitions"));
            }
            report.passed.add(Finding.of("worldbinder.finding.level_dat_and_world_generation_nbt_reopened_for_target_value", target.name()));
        } catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.world_metadata_value", Finding.detail(exception))); }

        report.expectedChunks = expected.size();
        Set<Long> primary = null;
        Map<Long,List<BlockRecord>> blocks = new LinkedHashMap<>();
        Map<Long,List<EntityRecord>> recordedEntities = new LinkedHashMap<>();
        for (BlockRecord block : scene.blocks) blocks.computeIfAbsent(ProjectData.chunkKey(scene, block), k -> new ArrayList<>()).add(block);
        for (var entity : scene.entities) recordedEntities.computeIfAbsent(entityChunk(scene,entity), k -> new ArrayList<>()).add(entity);
        Map<String,Tag> expectedPalette = new HashMap<>();
        Set<Tag> decodedPalette = new HashSet<>();
        var layouts = WorldExportModules.forProfile(profile).layouts(new ExportContext(scene,folder,profile));
        int[] checked = {0};
        int total = Math.max(1,expected.size()*layouts.size());
        for (var layout : layouts) {
            var written = RegionReadback.visit(layout.regionFolder(),report.errors,(key,root) -> {
                String label = layout.label()+" chunk "+(int)(long)key+","+(int)(key>>32);
                if (expected.contains(key)) try {
                    chunk(scene,root,key,blocks.getOrDefault(key,List.of()),version,relight.contains(key),expectedPalette,decodedPalette);
                } catch(Exception exception) { report.errors.add(Finding.of("worldbinder.finding.value_value",label,Finding.detail(exception))); }
                if(layout.embedEntitiesInChunks()) checkEntities(scene,key,root.getCompound("Level").orElse(root),recordedEntities,version,layout.label(),false,report);
                checked[0]++;
                if(progress!=null&&(checked[0]%16==0||checked[0]==total)) progress.update(net.worldbinder.storage.StorageStage.VALIDATE,
                        Lang.stored("worldbinder.storage.validation_chunks",checked[0],total),0.82D+0.12D*Math.min(1D,checked[0]/(double)total));
            });
            if(primary==null){primary=written;report.chunks=written.size();}
            for(long key:expected) if(!written.contains(key)) report.errors.add(Finding.of("worldbinder.finding.value_missing_from_written_regions",layout.label()+" chunk "+(int)key+","+(int)(key>>32)));
            if(layout.writeEntities()) {
                var entities=RegionReadback.visit(layout.entityFolder(),report.errors,(key,root) -> checkEntities(scene,key,root,recordedEntities,version,layout.label(),true,report));
                report.entityFiles |= !entities.isEmpty();
                for(var entry:recordedEntities.entrySet()) if(!entities.contains(entry.getKey()))
                    checkEntities(scene,entry.getKey(),null,recordedEntities,version,layout.label(),false,report);
            }
            if(layout.writePoi()) {RegionReadback.visit(layout.poiFolder(),report.errors,(key,root)->{});report.poiFiles|=Files.isDirectory(layout.poiFolder());}
        }
        Set<Long> observed = primary == null ? Set.of() : primary;
        report.missingChunks = (int) expected.stream().filter(k -> !observed.contains(k)).count();
        auxiliary(scene, folder, version, report);
        if (report.errors.isEmpty()) report.passed.add(Finding.of("worldbinder.finding.all_expected_chunks_packed_block_biome_arrays_and_exported_auxiliary_files_reopened"));
    }

    private static void chunk(WorldScene scene, CompoundTag root, long key, List<BlockRecord> expected, int version, boolean relight, Map<String,Tag> expectedPalette, Set<Tag> decodedPalette) {
        require(root.getInt("DataVersion").orElse(-1) == version, Finding.of("worldbinder.finding.chunk_dataversion_does_not_match_target"));
        CompoundTag chunk = version < 2860 ? root.getCompound("Level").orElseThrow() : root;
        require(chunk.getString("Status").orElse("").equals(WorldExportEngine.chunkStatus(version)),Finding.of("worldbinder.finding.chunk_status_mismatch",chunk.getString("Status").orElse("?"),WorldExportEngine.chunkStatus(version)));
        if (relight) require(!chunk.getBoolean("isLightOn").orElse(false), Finding.of("worldbinder.finding.converted_chunk_incorrectly_claims_valid_original_light"));
        if(relight&&version<1952)require(!chunk.getBoolean("LightPopulated").orElse(true),Finding.of("worldbinder.finding.legacy_converted_chunk_incorrectly_claims_populated_light"));
        require(chunk.getInt("xPos").orElse(Integer.MIN_VALUE) == (int) key && chunk.getInt("zPos").orElse(Integer.MIN_VALUE) == (int)(key >> 32), Finding.of("worldbinder.finding.incorrect_chunk_coordinates"));
        ListTag sections = chunk.getList(version < 2860 ? "Sections" : "sections").orElseThrow();
        Map<Integer, ListTag> palettes = new HashMap<>(); Map<Integer, int[]> indices = new HashMap<>();
        ChunkSnapshot snapshot = scene.chunkSnapshots.get((int) key + "," + (int)(key >> 32));
        for (Tag raw : sections) {
            require(raw instanceof CompoundTag, Finding.of("worldbinder.finding.non_compound_section")); CompoundTag section = (CompoundTag) raw;
            int y = section.getByte("Y").orElseThrow();
            require(!palettes.containsKey(y), Finding.of("worldbinder.finding.duplicate_section_value", y));
            if (version < 2860) require(y >= 0 && y <= 15, Finding.of("worldbinder.finding.section_outside_legacy_height"));
            CompoundTag states = version < 2860 ? section : section.getCompound("block_states").orElseThrow();
            ListTag palette = states.getList(version < 2860 ? "Palette" : "palette").orElseThrow();
            require(!palette.isEmpty() && palette.size() <= 4096, Finding.of("worldbinder.finding.invalid_block_palette_size"));
            for (Tag entry : palette) {
                String id = paletteId(entry, version);
                require(id.contains(":"), Finding.of("worldbinder.finding.invalid_block_identifier"));
                TargetBlockRegistry.checkEntry(entry,version);
                if (version == SharedConstants.getCurrentVersion().dataVersion().version() && id.startsWith("minecraft:") && decodedPalette.add(entry))
                    require(BlockState.CODEC.parse(NbtOps.INSTANCE, entry).result().isPresent(), Finding.of("worldbinder.finding.game_cannot_decode_palette_entry_value", entry));
            }
            int[] values = packed(states, version < 2860 ? "BlockStates" : "data", palette.size(), 4096, 4, version < 2529);
            palettes.put(y, palette); indices.put(y, values);
            var original = snapshot == null ? null : snapshot.sections.get(y);
            for (String layer : List.of("BlockLight", "SkyLight")) if (section.contains(layer))
                require(section.getByteArray(layer).orElseThrow().length == 2048, Finding.of("worldbinder.finding.invalid_value_length_at_section_value", layer, y));
            if (relight) {
                if(version<1952)require(Arrays.equals(new byte[2048],section.getByteArray("BlockLight").orElse(null))
                        &&Arrays.equals(new byte[2048],section.getByteArray("SkyLight").orElse(null)),Finding.of("worldbinder.finding.invalid_legacy_placeholder_light_at_section_value", y));
                else require(!section.contains("BlockLight") && !section.contains("SkyLight"), Finding.of("worldbinder.finding.stale_original_light_retained_after_conversion_at_section_value", y));
            }
            else {
                if (original != null && original.hasBlockLight()) require(Arrays.equals(original.blockLight, section.getByteArray("BlockLight").orElse(null)), Finding.of("worldbinder.finding.block_light_changed_at_section_value", y));
                if (original != null && original.hasSkyLight()) require(Arrays.equals(original.skyLight, section.getByteArray("SkyLight").orElse(null)), Finding.of("worldbinder.finding.sky_light_changed_at_section_value", y));
            }
            if (version >= 2860) {
                CompoundTag biomes = section.getCompound("biomes").orElseThrow();
                ListTag biomePalette = biomes.getList("palette").orElseThrow();
                require(!biomePalette.isEmpty(), Finding.of("worldbinder.finding.empty_biome_palette"));
                int[] biomeIndices = packed(biomes, "data", biomePalette.size(), 64, 1, false);
                if (original != null && original.hasBiomes()) for (int i = 0; i < 64; i++)
                    require(biomePalette.get(biomeIndices[i]).asString().orElse("").equals(ExportCompatibility.biome(original.biomes[i],version)), Finding.of("worldbinder.finding.biome_mismatch_at_section_value_sample_value", y, i));
            }
        }
        for (BlockRecord block : expected) {
            int x = scene.originX + block.x, y = scene.originY + block.y, z = scene.originZ + block.z;
            if (version < 2860 && (y < 0 || y > 255)) continue;
            int sectionY = y >> 4; if(!palettes.containsKey(sectionY)) throw new FindingException(Finding.of("worldbinder.finding.missing_section_value", sectionY));
            Tag actual = palettes.get(sectionY).get(indices.get(sectionY)[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]);
            Tag wanted = expectedPalette.computeIfAbsent(block.state, raw -> WorldExportEngine.paletteEntry(ExportCompatibility.state(scene,raw,version),version));
            if(!actual.equals(wanted)) throw new FindingException(Finding.of("worldbinder.finding.block_mismatch_at_value_value_value_value_expected_value", x, y, z, actual, wanted));
        }
        ListTag blockEntities = chunk.getList(version < 2860 ? "TileEntities" : "block_entities").orElseThrow();
        Map<String,BlockRecord> expectedBlockEntities=new HashMap<>();
        for(var block:expected){int x=scene.originX+block.x,y=scene.originY+block.y,z=scene.originZ+block.z;
            if((version>=2860||y>=0&&y<=255)&&block.hasBlockEntity&&block.blockEntityNbt!=null&&BlockEntityDataConverter.compatible(ExportCompatibility.id(block.state),ExportCompatibility.id(ExportCompatibility.state(scene,block.state)),version))expectedBlockEntities.put(ContainerRecord.blockKey(x,y,z),block);}
        for (Tag tag : blockEntities) {
            require(tag instanceof CompoundTag, Finding.of("worldbinder.finding.invalid_block_entity")); CompoundTag be = (CompoundTag) tag;
            int x = be.getInt("x").orElseThrow(), y = be.getInt("y").orElseThrow(), z = be.getInt("z").orElseThrow();
            BlockRecord recorded=expectedBlockEntities.remove(ContainerRecord.blockKey(x,y,z));require(recorded!=null,Finding.of("worldbinder.finding.unexpected_block_entity",x,y,z));
            require(ProjectData.chunkKey(x >> 4, z >> 4) == key, Finding.of("worldbinder.finding.block_entity_in_wrong_chunk"));
            var container = scene.containers.get(ContainerRecord.blockKey(x,y,z));
            if (container != null)
                require(ItemDataConverter.knownSlotsMatch(container,version,be.get("Items")), Finding.of("worldbinder.finding.container_slots_items_differ_at_value_value_value", x, y, z));
            CompoundTag wanted=BlockEntityDataConverter.convert(AuxiliaryDataWriter.parse(recorded.blockEntityNbt),version);
            if(container!=null){wanted.put("Items",ItemDataConverter.items(container,version,wanted.getList("Items").orElseGet(ListTag::new)));wanted.remove("LootTable");wanted.remove("LootTableSeed");}
            ItemDataConverter.inventoryLists(wanted,version);
            for(String field:wanted.keySet())if(!Set.of("x","y","z","keepPacked").contains(field))
                require(Objects.equals(wanted.get(field),be.get(field)),Finding.of("worldbinder.legacy.block_entity_payload_differ",x,y,z,field));
        }
        for (BlockRecord block : expected) {
            int x=scene.originX+block.x,y=scene.originY+block.y,z=scene.originZ+block.z;
            if (version<2860&&(y<0||y>255)||block.blockEntityNbt==null||!block.hasBlockEntity
                    ||!BlockEntityDataConverter.compatible(ExportCompatibility.id(block.state),ExportCompatibility.id(ExportCompatibility.state(scene,block.state)),version)) continue;
            require(blockEntities.stream().anyMatch(t->t instanceof CompoundTag be&&be.getInt("x").orElse(Integer.MIN_VALUE)==x
                    &&be.getInt("y").orElse(Integer.MIN_VALUE)==y&&be.getInt("z").orElse(Integer.MIN_VALUE)==z),Finding.of("worldbinder.finding.missing_block_entity_at_value_value_value", x, y, z));
        }
        CompoundTag heightmaps=chunk.getCompound("Heightmaps").orElseThrow();
        for(String type:List.of("MOTION_BLOCKING","WORLD_SURFACE")) RegionReadback.unpack(heightmaps.getLongArray(type).orElseThrow(),256,WorldExportEngine.heightmapBits(scene,version),version<2529);
        if (version < 2860) require(chunk.getIntArray("Biomes").orElseThrow().length == (version < 2225 ? 256 : 1024), Finding.of("worldbinder.finding.invalid_legacy_biome_array_length"));
    }

    private static int[] packed(CompoundTag holder, String field, int paletteSize, int count, int minimumBits, boolean contiguous) {
        if (paletteSize == 1 && !holder.contains(field)) return new int[count];
        int bits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
        int[] values = RegionReadback.unpack(holder.getLongArray(field).orElseThrow(), count, bits, contiguous);
        for (int value : values) if(value >= paletteSize) throw new FindingException(Finding.of("worldbinder.finding.palette_index_outside_palette"));
        return values;
    }
    private static String paletteId(Tag entry, int version) {
        if (version >= WorldExportEngine.ID_PROPERTIES_STATE_FORMAT && entry instanceof StringTag string) return string.value();
        require(entry instanceof CompoundTag, Finding.of("worldbinder.finding.incorrect_block_state_format_for_target"));
        CompoundTag compound = (CompoundTag) entry;
        return compound.getString(version >= WorldExportEngine.ID_PROPERTIES_STATE_FORMAT ? "id" : "Name").orElseThrow();
    }
    private static boolean matchesEntity(CompoundTag nbt, WorldScene scene, EntityRecord entity) {
        if (nbt.get("Pos") instanceof ListTag pos && pos.size() == 3) {
            int version=TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion();
            if(!nbt.getString("id").orElse("").equals(ExportCompatibility.entityType(entity.type,version)))return false;
            double[] wantedPos={scene.originX+entity.x,scene.originY+entity.y,scene.originZ+entity.z};
            for(int i=0;i<3;i++)if(Math.abs(pos.getDouble(i).orElse(Double.NaN)-wantedPos[i])>1e-7||!Double.isFinite(pos.getDouble(i).orElse(Double.NaN)))return false;
            var rotation=nbt.getList("Rotation").orElseGet(ListTag::new);
            if(rotation.size()!=2||!Float.isFinite(rotation.getFloat(0).orElse(Float.NaN))||!Float.isFinite(rotation.getFloat(1).orElse(Float.NaN))||Math.abs(rotation.getFloat(0).orElse(Float.NaN)-entity.yaw)>1e-4||Math.abs(rotation.getFloat(1).orElse(Float.NaN)-entity.pitch)>1e-4)return false;
            if (entity.uuid != null) {
                java.util.UUID uuid = java.util.UUID.fromString(entity.uuid);
                int[] wanted = {(int)(uuid.getMostSignificantBits() >> 32), (int)uuid.getMostSignificantBits(),
                        (int)(uuid.getLeastSignificantBits() >> 32), (int)uuid.getLeastSignificantBits()};
                if (!Arrays.equals(wanted, nbt.getIntArray("UUID").orElse(null))) return false;
            }
            var contents = scene.containers.get(ContainerRecord.entityKey(entity.uuid));
            return contents == null || ItemDataConverter.knownSlotsMatch(contents,TargetMinecraftVersion.resolve(scene.targetMinecraftVersion).effectiveDataVersion(),nbt.get("Items"));
        }
        return false;
    }

    private static long entityChunk(WorldScene scene,EntityRecord entity) {
        return ProjectData.chunkKey((scene.originX+(int)Math.floor(entity.x))>>4,(scene.originZ+(int)Math.floor(entity.z))>>4);
    }
    private static void checkEntities(WorldScene scene,long key,CompoundTag root,Map<Long,List<EntityRecord>> recorded,int version,String label,boolean header,ExportValidationReport report) {
        var expected=recorded.getOrDefault(key,List.of());
        var actual=root==null?new ListTag():root.getList("Entities").orElseGet(ListTag::new);
        if(expected.size()!=actual.size()) report.errors.add(Finding.of("worldbinder.finding.entity_count_mismatch",(int)key,(int)(key>>32),expected.size(),actual.size()));
        if(header&&(root.getInt("DataVersion").orElse(-1)!=version||!Arrays.equals(root.getIntArray("Position").orElse(null),new int[]{(int)key,(int)(key>>32)})))
            report.errors.add(Finding.of("worldbinder.finding.value_entity_region_chunk_value_value_invalid_entity_region_header",label,(int)key,(int)(key>>32)));
        for(var entity:expected) if(actual.stream().noneMatch(t->t instanceof CompoundTag e&&matchesEntity(e,scene,entity)))
            report.errors.add(Finding.of("worldbinder.finding.value_entity_value_in_chunk_value_value_missing_unreadable",label,entity.uuid,(int)key,(int)(key>>32)));
    }

    private static void auxiliary(WorldScene scene, Path folder, int version, ExportValidationReport report) {
        for (CapturedMap map : scene.maps.values()) {
            if (map.receivedPixels() == 0 || map.nbt == null) continue;
            Path path = version >= 4786 ? folder.resolve("data/minecraft/maps/" + map.id + ".dat") : folder.resolve("data/map_" + map.id + ".dat");
            try { CompoundTag root=read(path);require(root.getInt("DataVersion").orElse(-1)==version,Finding.of("worldbinder.finding.map_dataversion_differs"));
                require(Arrays.equals(map.colors, root.getCompound("data").orElseThrow().getByteArray("colors").orElseThrow()), Finding.of("worldbinder.finding.pixel_data_differs")); }
            catch (Exception exception) { report.errors.add(Finding.of("worldbinder.finding.map_value_value", map.id, Finding.detail(exception))); }
        }
        int max=scene.mapIds.stream().mapToInt(Integer::intValue).max().orElse(-1);
        if(max>=0)try{
            CompoundTag counter;
            if(version<1952){try(var input=new java.io.DataInputStream(Files.newInputStream(folder.resolve("data/idcounts.dat")))){counter=NbtIo.read(input,NbtAccounter.create(1024*1024));}}
            else counter=read(folder.resolve(version>=4786?"data/minecraft/maps/last_id.dat":"data/idcounts.dat")).getCompound("data").orElseThrow();
            require(counter.getInt("map").orElse(-1)>=max,Finding.of("worldbinder.finding.map_counter_could_reuse_an_archived_map_id"));
        }catch(Exception exception){report.errors.add(Finding.of("worldbinder.finding.map_id_counter_value", Finding.detail(exception)));}
        PlayerReadback.check(scene, folder, version, report);
    }
    private static CompoundTag read(Path path) throws java.io.IOException { return NbtIo.readCompressed(path, NbtAccounter.create(64 * 1024 * 1024L)); }
    private static void require(boolean condition, Finding message) { if (!condition) throw new FindingException(message); }
}
