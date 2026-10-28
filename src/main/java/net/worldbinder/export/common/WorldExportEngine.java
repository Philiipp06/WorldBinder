package net.worldbinder.export.common;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.worldbinder.WorldBinder;
import net.worldbinder.export.VanillaWorldExporter;
import net.worldbinder.export.api.ExportContext;
import net.worldbinder.export.api.ExportLayoutSpec;
import net.worldbinder.export.api.WorldExportModule;
import net.worldbinder.export.family.WorldExportModules;
import net.worldbinder.version.VersionProfile;
import net.worldbinder.version.VersionRegistry;
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.util.Lang;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class WorldExportEngine {
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final int BIOME_SECTION_SIZE = 4 * 4 * 4;
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private WorldExportEngine() {
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder) throws IOException {
        return export(scene, worldFolder, null);
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder, StorageProgress progress) throws IOException {
        scene=DimensionExportView.prepare(ExportFiltering.apply(scene));
        Files.createDirectories(worldFolder);
        VersionProfile profile = VersionRegistry.resolve(targetVersion(scene));
        WorldExportModule module = WorldExportModules.forProfile(profile);
        if (profile.writesGameRulesFile() || profile.writesWorldGenSettingsFile()) {
            Files.createDirectories(worldFolder.resolve("data").resolve("minecraft"));
        }

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.stored("worldbinder.export.progress.collecting_chunks"), 0.20D);
        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.stored("worldbinder.export.progress.writing_level"), 0.26D);
        WorldExportMetadataWriter.write(scene, worldFolder, profile);
        ExportSavedDataWriter.writeReleaseFiles(worldFolder, profile);

        int totalChunks=0,totalBlocks=0,totalBlockEntities=0,totalEntities=0;
        for(var scope:net.worldbinder.scene.ProjectDimensions.all(scene)){
        scope.targetMinecraftVersion=scene.targetMinecraftVersion;scope.exportSettings=scene.exportSettings;
        ExportContext context=new ExportContext(scope,worldFolder,profile);
        Map<ChunkKey, ChunkBuilder> chunks=collectChunks(scope);
        totalChunks+=(int)chunks.values().stream().filter(ChunkBuilder::hasBlockSections).count();totalBlocks+=scope.blockCount();totalBlockEntities+=scope.blockEntityCount();totalEntities+=scope.entityCount();
        List<ExportLayoutSpec> layouts = module.layouts(context);
        int layoutIndex = 0;
        ExportLayoutSpec writtenLayout = null;
        for (ExportLayoutSpec layout : layouts) {
            layoutIndex++;
            if(writtenLayout!=null && canMirror(writtenLayout,layout))
                mirrorLayout(scope,writtenLayout,layout,progress,layoutIndex,layouts.size());
            else {
                writeLayout(scope, chunks, layout, progress, layoutIndex, layouts.size());
                writtenLayout=layout;
            }
        }

        }
        ExportReadmeWriter.write(scene, worldFolder, totalChunks);
        return new VanillaWorldExporter.ExportResult(worldFolder,totalChunks,totalBlocks,totalBlockEntities,totalEntities);
    }

    public static Path overworldFolder(Path worldFolder) {
        return worldFolder;
    }
    public static int dimensionMinY(WorldScene scene,int version){
        if(version<2860)return 0;
        if(scene.dimensionMinY!=Integer.MIN_VALUE)return scene.dimensionMinY;
        return "minecraft:the_nether".equals(scene.dimension)||"minecraft:the_end".equals(scene.dimension)?0:-64;
    }
    public static int heightmapBits(WorldScene scene,int version){
        int height=version<2860?256:scene.dimensionHeight>0?scene.dimensionHeight:"minecraft:the_nether".equals(scene.dimension)||"minecraft:the_end".equals(scene.dimension)?256:384;
        return 32-Integer.numberOfLeadingZeros(height);
    }
    public static String chunkStatus(int version){return version<1952?"fullchunk":version<2860?"full":"minecraft:full";}

    private static boolean canMirror(ExportLayoutSpec source,ExportLayoutSpec target) {
        return source.writeEntities()==target.writeEntities() && source.writePoi()==target.writePoi()
                && source.embedEntitiesInChunks()==target.embedEntitiesInChunks()
                && (source.entityFolder()==null)==(target.entityFolder()==null)
                && (source.poiFolder()==null)==(target.poiFolder()==null);
    }

    /** The compatibility layouts contain identical chunk coordinates and payloads. Encode/compress only once. */
    private static void mirrorLayout(WorldScene scene,ExportLayoutSpec source,ExportLayoutSpec target,
                                      StorageProgress progress,int index,int count) throws IOException {
        Files.createDirectories(target.dimensionFolder());
        if(isModernDimensionMirror(target.dimensionFolder()))
            ExportSavedDataWriter.writeDimensionSavedData(target.dimensionFolder(),targetVersion(scene).effectiveDataVersion());
        Map<Path,Path> copies=new LinkedHashMap<>();
        copyPlan(source.regionFolder(),target.regionFolder(),copies);
        if(target.writeEntities())copyPlan(source.entityFolder(),target.entityFolder(),copies);
        if(target.writePoi())copyPlan(source.poiFolder(),target.poiFolder(),copies);
        int copied=0;
        for(var entry:copies.entrySet()) {
            Files.copy(entry.getKey(),entry.getValue());copied++;
            if(progress!=null&&(copied%8==0||copied==copies.size())) {
                double ratio=copied/(double)Math.max(1,copies.size());
                progress.update(StorageStage.VANILLA_WORLD,Lang.stored("worldbinder.export.progress.mirror",target.label(),copied,copies.size()),
                        .30D+((index-1+ratio)/Math.max(1,count))*.36D);
            }
        }
    }

    private static void copyPlan(Path source,Path target,Map<Path,Path> copies) throws IOException {
        if(source==null||target==null)return;
        Files.createDirectories(target);
        try(var files=Files.list(source)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) copies.put(file,target.resolve(file.getFileName()));
        }
    }

    private static void writeLayout(WorldScene scene, Map<ChunkKey, ChunkBuilder> chunks, ExportLayoutSpec layout,
                                    StorageProgress progress, int layoutIndex, int layoutCount) throws IOException {
        Files.createDirectories(layout.dimensionFolder());
        Files.createDirectories(layout.regionFolder());
        if (isModernDimensionMirror(layout.dimensionFolder())) {
            ExportSavedDataWriter.writeDimensionSavedData(layout.dimensionFolder(), targetVersion(scene).effectiveDataVersion());
        }
        if (layout.writeEntities() && layout.entityFolder() != null) {
            Files.createDirectories(layout.entityFolder());
        }
        if (layout.writePoi() && layout.poiFolder() != null) {
            Files.createDirectories(layout.poiFolder());
        }

        RegionStorageInfo regionKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "chunk");
        RegionStorageInfo entityKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "entities");
        RegionStorageInfo poiKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "poi");

        RegionWriter entityWriter = null;
        RegionWriter poiWriter = null;
        try (RegionWriter regionWriter = new RegionWriter(regionKey, layout.regionFolder())) {
            if (layout.writeEntities() && layout.entityFolder() != null) {
                entityWriter = new RegionWriter(entityKey, layout.entityFolder());
            }
            if (layout.writePoi() && layout.poiFolder() != null) {
                poiWriter = new RegionWriter(poiKey, layout.poiFolder());
            }

            int written = 0;
            int total = Math.max(1, chunks.size());
            int version = targetVersion(scene).effectiveDataVersion();
            Map<String,String> states = new HashMap<>();
            for (ChunkBuilder chunk : chunks.values()) {
                chunk.prepare(scene, version, states);
                if(chunk.hasBlockSections())regionWriter.write(chunk.pos(), chunk.toChunkNbt(scene, layout.embedEntitiesInChunks()));
                if (entityWriter != null) {
                    entityWriter.write(chunk.pos(), chunk.toEntitiesNbt(scene));
                }
                if (poiWriter != null&&chunk.hasBlockSections()) {
                    poiWriter.write(chunk.pos(), chunk.toPoiNbt(scene));
                }
                chunk.release();
                written++;
                if (progress != null && (written == total || written % 16 == 0)) {
                    double ratio = written / (double) total;
                    double base = 0.30D + ((layoutIndex - 1) / (double) Math.max(1, layoutCount)) * 0.36D;
                    double span = 0.36D / Math.max(1, layoutCount);
                    progress.update(StorageStage.VANILLA_WORLD,
                            Lang.stored("worldbinder.export.progress.writing_chunks", written, total, layout.label()),
                            base + ratio * span);
                }
            }
        } finally {
            if (entityWriter != null) {
                entityWriter.close();
            }
            if (poiWriter != null) {
                poiWriter.close();
            }
        }
    }

    private static boolean isModernDimensionMirror(Path folder) {
        if (folder == null) {
            return false;
        }
        Path parent = folder.getParent();
        Path grandParent = parent == null ? null : parent.getParent();
        return parent != null
                && grandParent != null
                && "minecraft".equals(parent.getFileName().toString())
                && "dimensions".equals(grandParent.getFileName().toString());
    }

    private static Map<ChunkKey, ChunkBuilder> collectChunks(WorldScene scene) {
        Map<ChunkKey, ChunkBuilder> chunks = new TreeMap<>();
        int version = targetVersion(scene).effectiveDataVersion();
        if (scene.chunkSnapshots != null) for (var snapshot : scene.chunkSnapshots.values()) {
            if (snapshot == null || (!snapshot.isDone() && !snapshot.verifiedEmpty)) continue;
            ChunkBuilder builder = chunks.computeIfAbsent(new ChunkKey(snapshot.chunkX, snapshot.chunkZ), ChunkBuilder::new);
            builder.snapshot = snapshot;
        }
        if (scene.blocks != null) {
                for (BlockRecord record : scene.blocks) {
                    int absX = scene.originX + record.x;
                    int absY = scene.originY + record.y;
                    int absZ = scene.originZ + record.z;
                    if (version < 2860 && (absY < 0 || absY > 255)) continue;
                    ChunkKey key = new ChunkKey(Math.floorDiv(absX, 16), Math.floorDiv(absZ, 16));
                    chunks.computeIfAbsent(key, ChunkBuilder::new).records.add(record);
                }
        }
        if (scene.entities != null) {
            for (EntityRecord record : scene.entities) {
                if (record == null) {
                    continue;
                }
                double absX = scene.originX + record.x;
                double absZ = scene.originZ + record.z;
                int chunkX = Math.floorDiv((int) Math.floor(absX), 16);
                int chunkZ = Math.floorDiv((int) Math.floor(absZ), 16);
                ChunkKey key = new ChunkKey(chunkX, chunkZ);
                chunks.computeIfAbsent(key,ChunkBuilder::new).addEntity(record);
            }
        }
        var relight = ExportLightPolicy.invalidatedChunks(scene);
        chunks.values().forEach(chunk -> chunk.lightInvalidated = relight.contains(net.worldbinder.scene.ProjectData.chunkKey(chunk.key.x, chunk.key.z)));
        return chunks;
    }

    private static TargetMinecraftVersion.Entry targetVersion(WorldScene scene) {
        String target = scene == null ? null : scene.targetMinecraftVersion;
        return TargetMinecraftVersion.resolve(target);
    }

    private record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
        @Override
        public int compareTo(ChunkKey other) {
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(z, other.z);
        }
    }

    private static final class ChunkBuilder {
        private final ChunkKey key;
        private net.worldbinder.scene.ChunkSnapshot snapshot;
        private boolean lightInvalidated;
        private final Map<Integer, SectionBuilder> sections = new TreeMap<>();
        private final List<CompoundTag> blockEntities = new ArrayList<>();
        private final List<EntityRecord> entities = new ArrayList<>();
        private final List<BlockRecord> records = new ArrayList<>();
        private final int[] heightmap = new int[16 * 16];

        private ChunkBuilder(ChunkKey key) {
            this.key = key;
            for (int i = 0; i < heightmap.length; i++) {
                heightmap[i] = 0;
            }
        }

        private ChunkPos pos() {
            return new ChunkPos(key.x, key.z);
        }

        private boolean hasBlockSections() {
            return snapshot != null || !records.isEmpty();
        }

        private void prepare(WorldScene scene, int version, Map<String,String> mappedStates) {
            if (snapshot != null && snapshot.sections != null) for (var entry : snapshot.sections.entrySet()) {
                if (version < 2860 && (entry.getKey() < 0 || entry.getKey() > 15)) continue;
                sections.computeIfAbsent(entry.getKey(), SectionBuilder::new).original = entry.getValue();
            }
            for (BlockRecord record : records) {
                String mapped = mappedStates.computeIfAbsent(record.state, raw -> ExportCompatibility.state(scene, raw, version));
                addBlock(scene, scene.originX+record.x, scene.originY+record.y, scene.originZ+record.z, record, mapped, version);
            }
            ensureChunkHasAtLeastOneSection();
        }

        private void release() {
            // Keep only references to recorded blocks between layouts; section arrays belong to one chunk.
            sections.clear(); blockEntities.clear(); java.util.Arrays.fill(heightmap, 0);
        }

        private void addBlock(WorldScene scene, int absX, int absY, int absZ, BlockRecord record, String mapped, int version) {
            int sectionY = Math.floorDiv(absY, 16);
            int localX = Math.floorMod(absX, 16);
            int localY = Math.floorMod(absY, 16);
            int localZ = Math.floorMod(absZ, 16);
            sections.computeIfAbsent(sectionY, SectionBuilder::new).set(localX, localY, localZ, mapped);
            int hmIndex = localZ * 16 + localX;
            if (!mapped.equals("minecraft:air") && !mapped.equals("minecraft:cave_air") && !mapped.equals("minecraft:void_air")) {
                int offset = 1-dimensionMinY(scene,version);
                heightmap[hmIndex] = Math.max(heightmap[hmIndex], Math.max(0, absY + offset));
            }
            if (record.hasBlockEntity && record.blockEntityNbt != null && !record.blockEntityNbt.isBlank()
                    && BlockEntityDataConverter.compatible(ExportCompatibility.id(record.state),ExportCompatibility.id(mapped),version)) {
                CompoundTag blockEntity = BlockEntityDataConverter.convert(parseCompound(record.blockEntityNbt),version);
                if (blockEntity != null) {
                    blockEntity.putInt("x", absX);
                    blockEntity.putInt("y", absY);
                    blockEntity.putInt("z", absZ);
                    blockEntity.putBoolean("keepPacked", false);
                    var captured = scene.containers.get(net.worldbinder.scene.ContainerRecord.blockKey(absX, absY, absZ));
                    if (captured != null && captured.type.equals(ExportCompatibility.id(record.state))) {
                        blockEntity.put("Items", ItemDataConverter.items(captured, version,blockEntity.getList("Items").orElseGet(ListTag::new)));
                        blockEntity.remove("LootTable"); blockEntity.remove("LootTableSeed");
                    }
                    ItemDataConverter.inventoryLists(blockEntity, version);
                    blockEntities.add(blockEntity);
                }
            }
        }

        private void addEntity(EntityRecord record) {
            entities.add(record);
        }

        private void ensureChunkHasAtLeastOneSection() {
            if (sections.isEmpty()) {
                sections.put(0, new SectionBuilder(0));
            }
        }

        private CompoundTag toChunkNbt(WorldScene scene, boolean includeEntities) {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            chunk.putInt("xPos", key.x);
            chunk.putInt("yPos",Math.floorDiv(dimensionMinY(scene,targetVersion(scene).effectiveDataVersion()),16));
            chunk.putInt("zPos", key.z);
            chunk.putLong("LastUpdate", 0L);
            chunk.putLong("InhabitedTime", 0L);
            chunk.putString("Status", chunkStatus(targetVersion(scene).effectiveDataVersion()));
            chunk.putBoolean("isLightOn", !lightInvalidated && snapshot != null && !snapshot.lightEstimated);
            chunk.put("sections", sectionsNbt(targetVersion(scene).effectiveDataVersion()));
            chunk.put("block_entities", blockEntitiesNbt());
            chunk.put("Heightmaps", heightmapsNbt(scene,targetVersion(scene).effectiveDataVersion()));
            chunk.put("block_ticks", new ListTag());
            chunk.put("fluid_ticks", new ListTag());
            chunk.put("PostProcessing", new ListTag());
            chunk.put("structures", structuresNbt());
            if (includeEntities) {
                chunk.put("Entities", entityListNbt(scene));
            }
            int version = targetVersion(scene).effectiveDataVersion();
            if (version < 2860) {
                if(version<1952)chunk.putBoolean("LightPopulated",!lightInvalidated&&snapshot!=null&&!snapshot.lightEstimated);
                chunk.put("Sections", chunk.get("sections")); chunk.remove("sections");
                chunk.put("TileEntities", chunk.get("block_entities")); chunk.remove("block_entities");
                chunk.put("TileTicks", chunk.get("block_ticks")); chunk.remove("block_ticks");
                chunk.put("LiquidTicks", chunk.get("fluid_ticks")); chunk.remove("fluid_ticks");
                chunk.put("Structures", chunk.get("structures")); chunk.remove("structures");
                chunk.putIntArray("Biomes", legacyBiomes(version));
                chunk.remove("DataVersion"); chunk.remove("yPos");
                CompoundTag root = new CompoundTag(); root.putInt("DataVersion", version); root.put("Level", chunk); return root;
            }
            return chunk;
        }

        private int[] legacyBiomes(int version) {
            int[] values = new int[version < 2225 ? 256 : 1024];
            java.util.Arrays.fill(values, 1);
            for (var entry : sections.entrySet()) {
                var original = entry.getValue().original;
                if (original == null || !original.hasBiomes()) continue;
                int sectionY = entry.getKey();
                if (version >= 2225) for (int i = 0; i < 64; i++) {
                    int index = sectionY * 64 + i;
                    if (index >= 0 && index < values.length) values[index] = LegacyBiomes.numeric(original.biomes[i], version);
                }
                else for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    values[z * 16 + x] = LegacyBiomes.numeric(original.biomes[(3 << 4) | ((z >> 2) << 2) | (x >> 2)], version);
            }
            return values;
        }

        private ListTag sectionsNbt(int dataVersion) {
            ListTag list = new ListTag();
            for (SectionBuilder section : sections.values()) {
                if (dataVersion >= 2860 || section.sectionY >= 0 && section.sectionY <= 15) list.add(section.toNbt(dataVersion, !lightInvalidated));
            }
            return list;
        }

        private ListTag blockEntitiesNbt() {
            ListTag list = new ListTag();
            for (CompoundTag blockEntity : blockEntities) {
                list.add(blockEntity);
            }
            return list;
        }

        private CompoundTag heightmapsNbt(WorldScene scene,int version) {
            int bits=heightmapBits(scene,version);
            long[] packed = version < 2529 ? packContiguous(heightmap,bits) : packValues(heightmap,bits);
            CompoundTag heightmaps = new CompoundTag();
            heightmaps.putLongArray("MOTION_BLOCKING", packed);
            heightmaps.putLongArray("WORLD_SURFACE", packed);
            return heightmaps;
        }

        private CompoundTag structuresNbt() {
            CompoundTag structures = new CompoundTag();
            structures.put("starts", new CompoundTag());
            structures.put("References", new CompoundTag());
            return structures;
        }

        private CompoundTag toEntitiesNbt(WorldScene scene) {
            CompoundTag root = new CompoundTag();
            root.put("Entities", entityListNbt(scene));
            root.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            root.put("Position", new IntArrayTag(new int[]{key.x, key.z}));
            return root;
        }

        private ListTag entityListNbt(WorldScene scene) {
            ListTag list = new ListTag();
            for (EntityRecord entity : entities) {
                CompoundTag nbt = normalizedEntityNbt(scene, entity);
                list.add(nbt);
            }
            return list;
        }

        private CompoundTag toPoiNbt(WorldScene scene) {
            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            root.put("Sections", new CompoundTag());
            return root;
        }
    }

    private static final class SectionBuilder {
        private final int sectionY;
        private final String[] states = new String[SECTION_SIZE];
        private net.worldbinder.scene.ChunkSectionData original;

        private SectionBuilder(int sectionY) {
            this.sectionY = sectionY;
        }

        private void set(int x, int y, int z, String state) {
            states[index(x, y, z)] = state == null || state.isBlank() ? "minecraft:air" : state;
        }

        private CompoundTag toNbt(int dataVersion, boolean retainLight) {
            CompoundTag section = new CompoundTag();
            section.putByte("Y", (byte) sectionY);
            CompoundTag blockStates = blockStatesNbt(dataVersion);
            if (dataVersion < 2860) {
                section.put("Palette", blockStates.get("palette"));
                section.putLongArray("BlockStates", blockStates.getLongArray("data").orElse(new long[256]));
            } else {
                section.put("block_states", blockStates);
                section.put("biomes", biomesNbt(dataVersion));
            }
            if (retainLight && original != null && original.hasSkyLight()) section.putByteArray("SkyLight", original.skyLight.clone());
            if (retainLight && original != null && original.hasBlockLight()) section.putByteArray("BlockLight", original.blockLight.clone());
            // The pre-1.14 reader unconditionally constructs 2048-byte nibble arrays. Absent arrays crash it.
            // Request relighting without stale source light; the 1.13 full-chunk reader may leave it dark (reported in compatibility review).
            if(dataVersion<1952){
                if(!section.contains("BlockLight"))section.putByteArray("BlockLight",new byte[2048]);
                if(!section.contains("SkyLight"))section.putByteArray("SkyLight",new byte[2048]);
            }
            return section;
        }

        private CompoundTag blockStatesNbt(int dataVersion) {
            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            paletteIndex.put("minecraft:air", 0);
            int[] values = new int[SECTION_SIZE];
            for (int i = 0; i < SECTION_SIZE; i++) {
                String state = states[i] == null ? "minecraft:air" : states[i];
                Integer index = paletteIndex.get(state);
                if (index == null) {
                    index = paletteIndex.size();
                    paletteIndex.put(state, index);
                }
                values[i] = index;
            }

            CompoundTag blockStates = new CompoundTag();
            ListTag palette = new ListTag();
            for (String state : paletteIndex.keySet()) {
                palette.add(paletteEntry(state, dataVersion));
            }
            blockStates.put("palette", palette);
            if (paletteIndex.size() > 1) {
                int bits = Math.max(4, bitsFor(paletteIndex.size() - 1));
                blockStates.putLongArray("data", dataVersion < 2529 ? packContiguous(values, bits) : packValues(values, bits));
            }
            return blockStates;
        }

        private CompoundTag biomesNbt(int version) {
            CompoundTag biomes = new CompoundTag();
            ListTag palette = new ListTag();
            Map<String, Integer> indices = new LinkedHashMap<>();
            int[] values = new int[64];
            String[] originalBiomes = original != null && original.hasBiomes() ? original.biomes : null;
            for (int i = 0; i < values.length; i++) {
                String biome = originalBiomes == null ? DEFAULT_BIOME : ExportCompatibility.biome(originalBiomes[i], version);
                values[i] = indices.computeIfAbsent(biome, k -> indices.size());
            }
            for (String biome : indices.keySet()) palette.add(StringTag.valueOf(biome));
            biomes.put("palette", palette);
            if (indices.size() > 1) biomes.putLongArray("data", packValues(values, bitsFor(indices.size() - 1)));
            return biomes;
        }

        private static int index(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
    }

    /**
     * First data version (Minecraft 26.3) that stores block states as {@code "id"} strings for default states or
     * {@code {id, properties}} compounds. Older versions use {@code {Name, Properties}}; 26.3 cannot read that
     * format with its own data version (it only upgrades older data versions), so writing the old format there
     * turns every block into air.
     */
    public static final int ID_PROPERTIES_STATE_FORMAT = 5023;

    /** Block state palette entry in the format of the target data version. */
    public static Tag paletteEntry(String state, int dataVersion) {
        TargetBlockRegistry.requireReadable(state,dataVersion);
        if (dataVersion < ID_PROPERTIES_STATE_FORMAT) {
            return blockStateNbt(state);
        }
        ParsedState parsed = parseState(state);
        if (parsed.properties.isEmpty()) {
            return StringTag.valueOf(parsed.name);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", parsed.name);
        CompoundTag properties = new CompoundTag();
        for (Map.Entry<String, String> entry : parsed.properties.entrySet()) {
            properties.putString(entry.getKey(), entry.getValue());
        }
        tag.put("properties", properties);
        return tag;
    }

    private static CompoundTag blockStateNbt(String state) {
        ParsedState parsed = parseState(state);
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", parsed.name);
        if (!parsed.properties.isEmpty()) {
            CompoundTag properties = new CompoundTag();
            for (Map.Entry<String, String> entry : parsed.properties.entrySet()) {
                properties.putString(entry.getKey(), entry.getValue());
            }
            tag.put("Properties", properties);
        }
        return tag;
    }

    private static ParsedState parseState(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedState("minecraft:air", Map.of());
        }
        int start = raw.indexOf('[');
        if (start < 0 || !raw.endsWith("]")) {
            return new ParsedState(raw, Map.of());
        }
        String name = raw.substring(0, start);
        String body = raw.substring(start + 1, raw.length() - 1);
        Map<String, String> properties = new LinkedHashMap<>();
        if (!body.isBlank()) {
            for (String part : body.split(",")) {
                int equals = part.indexOf('=');
                if (equals > 0 && equals < part.length() - 1) {
                    properties.put(part.substring(0, equals), part.substring(equals + 1));
                }
            }
        }
        return new ParsedState(name, properties);
    }

    private record ParsedState(String name, Map<String, String> properties) {
    }

    private static CompoundTag parseCompound(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return null;
        }
        try {
            return TagParser.parseCompoundFully(snbt).copy();
        } catch (CommandSyntaxException exception) {
            WorldBinder.LOGGER.warn("Failed to parse captured SNBT: {}", snbt, exception);
            return null;
        }
    }

    public static CompoundTag normalizedEntityNbt(WorldScene scene, EntityRecord entity) {
        CompoundTag nbt = parseCompound(entity.fullNbt);
        if (nbt == null) {
            nbt = new CompoundTag();
        }
        if(entity.uuid!=null){try{
            java.util.UUID uuid=java.util.UUID.fromString(entity.uuid);long most=uuid.getMostSignificantBits(),least=uuid.getLeastSignificantBits();
            nbt.putIntArray("UUID",new int[]{(int)(most>>32),(int)most,(int)(least>>32),(int)least});
            if(targetVersion(scene).effectiveDataVersion()<2566){nbt.putLong("UUIDMost",most);nbt.putLong("UUIDLeast",least);}
        }catch(IllegalArgumentException ignored){}}

        // EntityRecord stores coordinates relative to the capture origin so scenes can be moved.
        // Vanilla entity region files need absolute world coordinates inside the exported save.
        double absX = scene.originX + entity.x;
        double absY = scene.originY + entity.y;
        double absZ = scene.originZ + entity.z;

        nbt.putString("id", exportEntityType(scene, entity.type));

        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(absX));
        pos.add(DoubleTag.valueOf(absY));
        pos.add(DoubleTag.valueOf(absZ));
        nbt.put("Pos", pos);

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(entity.yaw));
        rotation.add(FloatTag.valueOf(entity.pitch));
        nbt.put("Rotation", rotation);

        nbt.putBoolean("NoGravity", entity.noGravity);
        nbt.putBoolean("Glowing", entity.glowing);
        nbt.putBoolean("Invisible", entity.invisible);
        var contents = scene.containers.get(net.worldbinder.scene.ContainerRecord.entityKey(entity.uuid));
        if (contents != null && contents.type.equals(entity.type)) {
            nbt.put("Items", ItemDataConverter.items(contents, targetVersion(scene).effectiveDataVersion(),nbt.getList("Items").orElseGet(ListTag::new)));
            nbt.remove("LootTable"); nbt.remove("LootTableSeed");
        }
        ItemDataConverter.inventoryLists(nbt, targetVersion(scene).effectiveDataVersion());
        if (targetVersion(scene).effectiveDataVersion() < 4325 && nbt.getIntArray("block_pos").isPresent()) {
            int[] anchor = nbt.getIntArray("block_pos").orElseThrow();
            if (anchor.length == 3) { nbt.putInt("TileX",anchor[0]); nbt.putInt("TileY",anchor[1]); nbt.putInt("TileZ",anchor[2]); }
        }
        return nbt;
    }

    private static String exportEntityType(WorldScene scene, String type) {
        return ExportCompatibility.entityType(type,targetVersion(scene).effectiveDataVersion());
    }

    private static int bitsFor(int maxValue) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(maxValue));
    }

    private static long[] packValues(int[] values, int bitsPerValue) {
        int valuesPerLong = Math.max(1, 64 / bitsPerValue);
        long mask = (1L << bitsPerValue) - 1L;
        long[] packed = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * bitsPerValue;
            packed[longIndex] |= ((long) values[i] & mask) << bitIndex;
        }
        return packed;
    }

    private static long[] packContiguous(int[] values, int bits) {
        long[] packed = new long[(values.length * bits + 63) / 64];
        for (int i = 0; i < values.length; i++) {
            int offset = i * bits, index = offset >>> 6, shift = offset & 63;
            packed[index] |= (long) values[i] << shift;
            if (shift + bits > 64) packed[index + 1] |= (long) values[i] >>> (64 - shift);
        }
        return packed;
    }

    private static final class RegionWriter implements AutoCloseable {
        private final RegionStorageInfo storageKey;
        private final Path directory;
        private final Map<Long, RegionFile> files = new HashMap<>();

        private RegionWriter(RegionStorageInfo storageKey, Path directory) throws IOException {
            this.storageKey = storageKey;
            this.directory = directory;
            Files.createDirectories(directory);
        }

        private void write(ChunkPos pos, CompoundTag nbt) throws IOException {
            RegionFile file = file(pos);
            try (DataOutputStream output = file.getChunkDataOutputStream(pos)) {
                NbtIo.write(nbt, output);
            }
        }

        private RegionFile file(ChunkPos pos) throws IOException {
            long key = ChunkPos.pack(pos.getRegionX(), pos.getRegionZ());
            RegionFile existing = files.get(key);
            if (existing != null) {
                return existing;
            }
            Path path = directory.resolve(String.format(Locale.ROOT, "r.%d.%d.mca", pos.getRegionX(), pos.getRegionZ()));
            RegionFile created = new RegionFile(storageKey, path, directory, false);
            files.put(key, created);
            return created;
        }

        @Override
        public void close() throws IOException {
            IOException first = null;
            for (RegionFile file : files.values()) {
                try {
                    file.close();
                } catch (IOException exception) {
                    if (first == null) {
                        first = exception;
                    } else {
                        first.addSuppressed(exception);
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        }
    }
}
