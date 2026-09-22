package net.worldbinder.export.common;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.worldbinder.util.BlockStateStrings;
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
    private static final int LIGHT_ARRAY_SIZE = 2048;
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private WorldExportEngine() {
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder) throws IOException {
        return export(scene, worldFolder, null);
    }

    public static VanillaWorldExporter.ExportResult export(WorldScene scene, Path worldFolder, StorageProgress progress) throws IOException {
        Files.createDirectories(worldFolder);
        VersionProfile profile = VersionRegistry.resolve(targetVersion(scene));
        WorldExportModule module = WorldExportModules.forProfile(profile);
        ExportContext context = new ExportContext(scene, worldFolder, profile);
        if (profile.writesGameRulesFile() || profile.writesWorldGenSettingsFile()) {
            Files.createDirectories(worldFolder.resolve("data").resolve("minecraft"));
        }

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.string("worldbinder.export.progress.collecting_chunks"), 0.20D);
        Map<ChunkKey, ChunkBuilder> chunks = collectChunks(scene);
        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, Lang.string("worldbinder.export.progress.writing_level"), 0.26D);
        WorldExportMetadataWriter.write(scene, worldFolder, profile);
        ExportSavedDataWriter.writeReleaseFiles(worldFolder, profile);

        List<ExportLayoutSpec> layouts = module.layouts(context);
        int layoutIndex = 0;
        for (ExportLayoutSpec layout : layouts) {
            layoutIndex++;
            writeLayout(scene, chunks, layout, progress, layoutIndex, layouts.size());
        }

        ExportReadmeWriter.write(scene, worldFolder, chunks.size());
        return new VanillaWorldExporter.ExportResult(worldFolder, chunks.size(), scene.blockCount(), scene.blockEntityCount(), scene.entityCount());
    }

    public static Path overworldFolder(Path worldFolder) {
        return worldFolder;
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
            for (ChunkBuilder chunk : chunks.values()) {
                regionWriter.write(chunk.pos(), chunk.toChunkNbt(scene, layout.embedEntitiesInChunks()));
                if (entityWriter != null) {
                    entityWriter.write(chunk.pos(), chunk.toEntitiesNbt(scene));
                }
                if (poiWriter != null) {
                    poiWriter.write(chunk.pos(), chunk.toPoiNbt(scene));
                }
                written++;
                if (progress != null && (written == total || written % 16 == 0)) {
                    double ratio = written / (double) total;
                    double base = 0.30D + ((layoutIndex - 1) / (double) Math.max(1, layoutCount)) * 0.36D;
                    double span = 0.36D / Math.max(1, layoutCount);
                    progress.update(StorageStage.VANILLA_WORLD,
                            Lang.string("worldbinder.export.progress.writing_chunks", written, total, layout.label()),
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
        if (scene.blocks != null) {
            targetVersionHolder.set(targetVersion(scene));
            try {
                for (BlockRecord record : scene.blocks) {
                    int absX = scene.originX + record.x;
                    int absY = scene.originY + record.y;
                    int absZ = scene.originZ + record.z;
                    ChunkKey key = new ChunkKey(Math.floorDiv(absX, 16), Math.floorDiv(absZ, 16));
                    chunks.computeIfAbsent(key, ChunkBuilder::new).addBlock(absX, absY, absZ, record);
                }
            } finally {
                targetVersionHolder.remove();
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
                ChunkBuilder builder = chunks.get(key);
                if (builder != null && builder.hasBlockSections()) {
                    builder.addEntity(record);
                }
            }
        }
        return chunks;
    }

    private static TargetMinecraftVersion.Entry targetVersion(WorldScene scene) {
        String target = scene == null ? null : scene.targetMinecraftVersion;
        return TargetMinecraftVersion.resolve(target);
    }

    private static String exportState(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        TargetMinecraftVersion.Entry target = targetVersionHolder.get();
        String downgraded = target == null ? state : downgradeBlockState(state, target);
        return normalizeBlockStateForExport(downgraded);
    }

    private static String normalizeBlockStateForExport(String state) {
        if (state == null || state.isBlank()) {
            return "minecraft:air";
        }
        try {
            net.minecraft.world.level.block.state.BlockState parsed = BlockStateStrings.parse(state);
            return parsed == null ? state : BlockStateStrings.toCommandString(parsed);
        } catch (Throwable ignored) {
            int propertyStart = state.indexOf('[');
            return propertyStart > 0 ? state.substring(0, propertyStart) : "minecraft:air";
        }
    }

    private static String downgradeBlockState(String state, TargetMinecraftVersion.Entry target) {
        String name = parseState(state).name();
        int dataVersion = target.effectiveDataVersion();
        if (dataVersion < 3463 && (name.contains("cherry") || name.contains("bamboo_block") || name.contains("pink_petals") || name.contains("decorated_pot") || name.contains("suspicious_gravel"))) {
            return "minecraft:oak_planks";
        }
        if (dataVersion < 3105 && (name.contains("mangrove") || name.contains("mud") || name.contains("sculk") || name.contains("frog") || name.contains("ochre_froglight") || name.contains("verdant_froglight") || name.contains("pearlescent_froglight"))) {
            return "minecraft:dirt";
        }
        if (dataVersion < 2724 && (name.contains("deepslate") || name.contains("tuff") || name.contains("calcite") || name.contains("amethyst") || name.contains("copper") || name.contains("dripstone") || name.contains("azalea") || name.contains("moss") || name.contains("candle"))) {
            return name.contains("ore") ? "minecraft:stone" : "minecraft:cobblestone";
        }
        if (dataVersion < 2566 && (name.contains("crimson") || name.contains("warped") || name.contains("basalt") || name.contains("blackstone") || name.contains("soul_soil") || name.contains("netherite") || name.contains("ancient_debris") || name.contains("shroomlight"))) {
            return "minecraft:netherrack";
        }
        return state;
    }

    private static final ThreadLocal<TargetMinecraftVersion.Entry> targetVersionHolder = new ThreadLocal<>();

    private record ChunkKey(int x, int z) implements Comparable<ChunkKey> {
        @Override
        public int compareTo(ChunkKey other) {
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(z, other.z);
        }
    }

    private static final class ChunkBuilder {
        private final ChunkKey key;
        private final Map<Integer, SectionBuilder> sections = new TreeMap<>();
        private final List<CompoundTag> blockEntities = new ArrayList<>();
        private final List<EntityRecord> entities = new ArrayList<>();
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
            return !sections.isEmpty();
        }

        private void addSyntheticBlock(int absX, int absY, int absZ, String state) {
            int sectionY = Math.floorDiv(absY, 16);
            int localX = Math.floorMod(absX, 16);
            int localY = Math.floorMod(absY, 16);
            int localZ = Math.floorMod(absZ, 16);
            sections.computeIfAbsent(sectionY, SectionBuilder::new).set(localX, localY, localZ, exportState(state));
            int hmIndex = localZ * 16 + localX;
            heightmap[hmIndex] = Math.max(heightmap[hmIndex], Math.max(0, absY + 65));
        }

        private void addBlock(int absX, int absY, int absZ, BlockRecord record) {
            int sectionY = Math.floorDiv(absY, 16);
            int localX = Math.floorMod(absX, 16);
            int localY = Math.floorMod(absY, 16);
            int localZ = Math.floorMod(absZ, 16);
            sections.computeIfAbsent(sectionY, SectionBuilder::new).set(localX, localY, localZ, exportState(record.state));
            int hmIndex = localZ * 16 + localX;
            heightmap[hmIndex] = Math.max(heightmap[hmIndex], Math.max(0, absY + 65));
            if (record.hasBlockEntity && record.blockEntityNbt != null && !record.blockEntityNbt.isBlank()) {
                CompoundTag blockEntity = parseCompound(record.blockEntityNbt);
                if (blockEntity != null) {
                    blockEntity.putInt("x", absX);
                    blockEntity.putInt("y", absY);
                    blockEntity.putInt("z", absZ);
                    blockEntity.putBoolean("keepPacked", false);
                    blockEntities.add(blockEntity);
                }
            }
        }

        private void addEntity(EntityRecord record) {
            entities.add(record);
            ensureChunkHasAtLeastOneSection();
        }

        private void ensureChunkHasAtLeastOneSection() {
            if (sections.isEmpty()) {
                sections.put(-4, new SectionBuilder(-4));
            }
        }

        private CompoundTag toChunkNbt(WorldScene scene, boolean includeEntities) {
            CompoundTag chunk = new CompoundTag();
            chunk.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            chunk.putInt("xPos", key.x);
            chunk.putInt("yPos", -4);
            chunk.putInt("zPos", key.z);
            chunk.putLong("LastUpdate", 0L);
            chunk.putLong("InhabitedTime", 0L);
            chunk.putString("Status", "minecraft:full");
            chunk.putBoolean("isLightOn", true);
            chunk.put("sections", sectionsNbt());
            chunk.put("block_entities", blockEntitiesNbt());
            chunk.put("Heightmaps", heightmapsNbt());
            chunk.put("block_ticks", new ListTag());
            chunk.put("fluid_ticks", new ListTag());
            chunk.put("PostProcessing", new ListTag());
            chunk.put("structures", structuresNbt());
            if (includeEntities) {
                chunk.put("Entities", entityListNbt(scene));
            }
            return chunk;
        }

        private ListTag sectionsNbt() {
            ListTag list = new ListTag();
            for (SectionBuilder section : sections.values()) {
                list.add(section.toNbt());
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

        private CompoundTag heightmapsNbt() {
            long[] packed = packValues(heightmap, 9);
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

        private SectionBuilder(int sectionY) {
            this.sectionY = sectionY;
        }

        private void set(int x, int y, int z, String state) {
            states[index(x, y, z)] = state == null || state.isBlank() ? "minecraft:air" : state;
        }

        private CompoundTag toNbt() {
            CompoundTag section = new CompoundTag();
            section.putByte("Y", (byte) sectionY);
            section.put("block_states", blockStatesNbt());
            section.put("biomes", biomesNbt());
            section.putByteArray("SkyLight", filledLight((byte) 0xFF));
            section.putByteArray("BlockLight", filledLight((byte) 0x00));
            return section;
        }

        private CompoundTag blockStatesNbt() {
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
                palette.add(blockStateNbt(state));
            }
            blockStates.put("palette", palette);
            if (paletteIndex.size() > 1) {
                int bits = Math.max(4, bitsFor(paletteIndex.size() - 1));
                blockStates.putLongArray("data", packValues(values, bits));
            }
            return blockStates;
        }

        private CompoundTag biomesNbt() {
            CompoundTag biomes = new CompoundTag();
            ListTag palette = new ListTag();
            palette.add(StringTag.valueOf(DEFAULT_BIOME));
            biomes.put("palette", palette);
            return biomes;
        }

        private static int index(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
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
            return TagParser.parseCompoundFully(snbt);
        } catch (CommandSyntaxException exception) {
            WorldBinder.LOGGER.warn("Failed to parse captured SNBT: {}", snbt, exception);
            return null;
        }
    }

    private static CompoundTag normalizedEntityNbt(WorldScene scene, EntityRecord entity) {
        CompoundTag nbt = parseCompound(entity.fullNbt);
        if (nbt == null) {
            nbt = new CompoundTag();
        }

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
        normalizeEntityForTarget(nbt, targetVersion(scene));
        return nbt;
    }

    private static void normalizeEntityForTarget(CompoundTag nbt, TargetMinecraftVersion.Entry target) {
        if (target.effectiveDataVersion() < 3837) {
            downgradeModernItemStack(nbt, "item");
            downgradeModernItemStack(nbt, "Item");
            downgradeItemStackList(nbt, "HandItems");
            downgradeItemStackList(nbt, "ArmorItems");
        }
    }

    private static void downgradeItemStackList(CompoundTag holder, String key) {
        if (!(holder.get(key) instanceof ListTag list)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag item) {
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("value", item);
                downgradeModernItemStack(wrapper, "value");
            }
        }
    }

    private static void downgradeModernItemStack(CompoundTag holder, String key) {
        if (!(holder.get(key) instanceof CompoundTag item)) {
            return;
        }
        String id = item.getString("id").orElse("minecraft:air");
        item.putString("id", id);
        item.putByte("Count", (byte) readIntTag(item, "count", 1));
        item.remove("count");

        if (item.get("components") instanceof CompoundTag components) {
            CompoundTag tag = item.get("tag") instanceof CompoundTag existingTag ? existingTag : new CompoundTag();
            int damage = readIntTag(components, "minecraft:damage", Integer.MIN_VALUE);
            if (damage != Integer.MIN_VALUE) {
                tag.putInt("Damage", damage);
            }
            int customModelData = readIntTag(components, "minecraft:custom_model_data", Integer.MIN_VALUE);
            if (customModelData != Integer.MIN_VALUE) {
                tag.putInt("CustomModelData", customModelData);
            }
            String customName = readStringTag(components, "minecraft:custom_name");
            if (customName != null && !customName.isBlank()) {
                CompoundTag display = tag.get("display") instanceof CompoundTag existingDisplay ? existingDisplay : new CompoundTag();
                display.putString("Name", customName);
                tag.put("display", display);
            }
            if (!tag.keySet().isEmpty()) {
                item.put("tag", tag);
            }
            item.remove("components");
        }
    }

    private static int readIntTag(CompoundTag tag, String key, int fallback) {
        if (tag == null || tag.get(key) == null) {
            return fallback;
        }
        try {
            String raw = tag.get(key).toString().replace("\"", "");
            int suffix = raw.endsWith("b") || raw.endsWith("s") || raw.endsWith("l") || raw.endsWith("f") || raw.endsWith("d") ? 1 : 0;
            return Integer.parseInt(suffix == 1 ? raw.substring(0, raw.length() - 1) : raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readStringTag(CompoundTag tag, String key) {
        return tag == null ? null : tag.getString(key).orElse(null);
    }

    private static String exportEntityType(WorldScene scene, String type) {
        String id = type == null || type.isBlank() ? "minecraft:marker" : type;
        int dataVersion = targetVersion(scene).effectiveDataVersion();
        if (dataVersion < 3337 && (id.equals("minecraft:block_display") || id.equals("minecraft:item_display") || id.equals("minecraft:text_display") || id.equals("minecraft:interaction"))) {
            return "minecraft:armor_stand";
        }
        if (dataVersion < 3463 && (id.equals("minecraft:camel") || id.equals("minecraft:sniffer"))) {
            return "minecraft:pig";
        }
        if (dataVersion < 3953 && (id.equals("minecraft:bogged") || id.equals("minecraft:breeze") || id.equals("minecraft:wind_charge") || id.equals("minecraft:ominous_item_spawner"))) {
            return "minecraft:marker";
        }
        return id;
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

    private static byte[] filledLight(byte value) {
        byte[] light = new byte[LIGHT_ARRAY_SIZE];
        for (int i = 0; i < light.length; i++) {
            light[i] = value;
        }
        return light;
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
