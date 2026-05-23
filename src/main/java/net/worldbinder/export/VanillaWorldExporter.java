package net.worldbinder.export;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
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
import net.worldbinder.scene.BlockRecord;
import net.worldbinder.scene.ChunkSnapshot;
import net.worldbinder.scene.EntityRecord;
import net.worldbinder.scene.WorldScene;
import net.worldbinder.storage.StorageProgress;
import net.worldbinder.storage.StorageStage;
import net.worldbinder.util.BlockStateStrings;
import net.worldbinder.version.TargetMinecraftVersion;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class VanillaWorldExporter {
    private static final int SECTION_SIZE = 16 * 16 * 16;
    private static final int BIOME_SECTION_SIZE = 4 * 4 * 4;
    private static final int LIGHT_ARRAY_SIZE = 2048;
    private static final String DEFAULT_BIOME = "minecraft:plains";

    private VanillaWorldExporter() {
    }

    public static ExportResult export(WorldScene scene, Path worldFolder) throws IOException {
        return export(scene, worldFolder, null);
    }

    public static ExportResult export(WorldScene scene, Path worldFolder, StorageProgress progress) throws IOException {
        Files.createDirectories(worldFolder);
        Path overworldFolder = overworldFolder(worldFolder);
        Path regionFolder = overworldFolder.resolve("region");
        Path entityFolder = overworldFolder.resolve("entities");
        Path poiFolder = overworldFolder.resolve("poi");
        Files.createDirectories(regionFolder);
        Files.createDirectories(entityFolder);
        Files.createDirectories(poiFolder);
        Files.createDirectories(worldFolder.resolve("data").resolve("minecraft"));

        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, "Collecting captured chunks", 0.20D);
        Map<ChunkKey, ChunkBuilder> chunks = collectChunks(scene);
        if (progress != null) progress.update(StorageStage.VANILLA_WORLD, "Writing level.dat and world generation settings", 0.26D);
        writeLevelDat(scene, worldFolder);
        writeWorldGenSettingsFile(scene, worldFolder);
        writeGameRulesFile(scene, worldFolder);
        writeSessionLock(worldFolder);
        writeIconPlaceholder(worldFolder);

        RegionStorageInfo regionKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "chunk");
        RegionStorageInfo entityKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "entities");
        RegionStorageInfo poiKey = new RegionStorageInfo("worldbinder", Level.OVERWORLD, "poi");

        try (RegionWriter regionWriter = new RegionWriter(regionKey, regionFolder);
             RegionWriter entityWriter = new RegionWriter(entityKey, entityFolder);
             RegionWriter poiWriter = new RegionWriter(poiKey, poiFolder)) {
            int written = 0;
            int total = Math.max(1, chunks.size());
            for (ChunkBuilder chunk : chunks.values()) {
                regionWriter.write(chunk.pos(), chunk.toChunkNbt(scene));
                entityWriter.write(chunk.pos(), chunk.toEntitiesNbt(scene));
                poiWriter.write(chunk.pos(), chunk.toPoiNbt(scene));
                written++;
                if (progress != null && (written == total || written % 16 == 0)) {
                    double ratio = written / (double) total;
                    progress.update(StorageStage.VANILLA_WORLD, "Writing overworld chunks " + written + " / " + total, 0.30D + ratio * 0.36D);
                }
            }
        }

        writeWorldBinderInfo(scene, worldFolder, chunks.size());
        return new ExportResult(worldFolder, chunks.size(), scene.blockCount(), scene.blockEntityCount(), scene.entityCount());
    }

    public static Path overworldFolder(Path worldFolder) {
        return worldFolder;
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
                chunks.computeIfAbsent(key, ChunkBuilder::new).addEntity(record);
            }
        }
        addSnapshotFallbackChunks(scene, chunks);
        return chunks;
    }


    private static void addSnapshotFallbackChunks(WorldScene scene, Map<ChunkKey, ChunkBuilder> chunks) {
        if (scene.chunkSnapshots == null || scene.chunkSnapshots.isEmpty()) {
            return;
        }
        for (ChunkSnapshot snapshot : scene.chunkSnapshots.values()) {
            if (snapshot == null) {
                continue;
            }
            ChunkKey key = new ChunkKey(snapshot.chunkX, snapshot.chunkZ);
            ChunkBuilder builder = chunks.computeIfAbsent(key, ChunkBuilder::new);
            if (builder.hasBlockSections()) {
                continue;
            }
            int added = 0;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = z * 16 + x;
                    if (snapshot.heights == null || index >= snapshot.heights.length || snapshot.heights[index] == Integer.MIN_VALUE) {
                        continue;
                    }
                    int y = snapshot.heights[index];
                    String state = snapshot.states != null && index < snapshot.states.length && snapshot.states[index] != null && !snapshot.states[index].isBlank()
                            ? snapshot.states[index]
                            : "minecraft:stone";
                    builder.addSyntheticBlock((snapshot.chunkX << 4) + x, y, (snapshot.chunkZ << 4) + z, state);
                    added++;
                }
            }
            if (added == 0 && snapshot.hasSnapshot) {
                builder.addSyntheticBlock((snapshot.chunkX << 4) + 8, Math.max(-64, scene.originY), (snapshot.chunkZ << 4) + 8, "minecraft:stone");
            }
        }
    }
    private static void writeLevelDat(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag data = new CompoundTag();
        TargetMinecraftVersion.Entry targetVersion = targetVersion(scene);
        data.putInt("DataVersion", targetVersion.effectiveDataVersion());
        data.putString("LevelName", scene.name == null || scene.name.isBlank() ? "WorldBinder Export" : scene.name);
        data.putLong("LastPlayed", System.currentTimeMillis());
        data.putLong("Time", 0L);
        data.putLong("DayTime", 6000L);
        data.putInt("version", 19133);
        data.putBoolean("initialized", true);
        data.putBoolean("allowCommands", true);
        data.putBoolean("WasModded", true);
        data.putBoolean("hardcore", false);
        data.putByte("Difficulty", (byte) 2);
        data.putBoolean("DifficultyLocked", false);
        data.putInt("GameType", 1);
        data.putInt("SpawnX", scene.originX);
        data.putInt("SpawnY", Math.max(-64, scene.originY));
        data.putInt("SpawnZ", scene.originZ);
        data.putFloat("SpawnAngle", 0.0F);
        data.putInt("clearWeatherTime", 0);
        data.putInt("rainTime", 0);
        data.putInt("thunderTime", 0);
        data.putBoolean("raining", false);
        data.putBoolean("thundering", false);
        data.put("GameRules", gameRules(scene));
        data.put("WorldGenSettings", worldGenSettings(scene));
        data.put("DragonFight", new CompoundTag());
        data.put("Version", versionInfo(targetVersion));
        data.put("DataPacks", dataPacks());
        data.put("ServerBrands", singleStringList("WorldBinder"));
        data.put("Player", playerNbt(scene));

        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, worldFolder.resolve("level.dat"));
    }


    private static void writeWorldGenSettingsFile(WorldScene scene, Path worldFolder) throws IOException {
        CompoundTag settings = worldGenSettings(scene);
        CompoundTag savedData = new CompoundTag();
        savedData.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
        savedData.put("data", settings);

        writeWorldGenSettingsFile(worldFolder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat"), savedData);
    }

    private static void writeWorldGenSettingsFile(Path target, CompoundTag savedData) throws IOException {
        Files.createDirectories(target.getParent());
        NbtIo.writeCompressed(savedData, target);
    }

    private static void writeGameRulesFile(WorldScene scene, Path worldFolder) throws IOException {
        TargetMinecraftVersion.Entry target = targetVersion(scene);
        if (!target.usesGameRulesFile()) {
            return;
        }
        CompoundTag data = new CompoundTag();
        CompoundTag exported = gameRules(scene);
        for (String key : exported.keySet()) {
            String value = exported.getString(key).orElse("false");
            putTypedGameRule(data, namespacedSnakeGameRule(key), value);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", target.effectiveDataVersion());
        root.put("data", data);
        Path targetFile = worldFolder.resolve("data").resolve("minecraft").resolve("game_rules.dat");
        Files.createDirectories(targetFile.getParent());
        NbtIo.writeCompressed(root, targetFile);
    }

    private static void putTypedGameRule(CompoundTag tag, String key, String value) {
        String cleaned = value == null ? "false" : value.trim();
        if ("true".equalsIgnoreCase(cleaned) || "false".equalsIgnoreCase(cleaned)) {
            tag.putBoolean(key, Boolean.parseBoolean(cleaned));
            return;
        }
        try {
            tag.putInt(key, Integer.parseInt(cleaned));
        } catch (NumberFormatException ignored) {
            tag.putString(key, cleaned);
        }
    }

    private static String namespacedSnakeGameRule(String key) {
        if (key == null || key.isBlank()) {
            return "minecraft:unknown";
        }
        String raw = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder result = new StringBuilder("minecraft:");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c == '-' ? '_' : c);
            }
        }
        return result.toString();
    }

    private static CompoundTag gameRules(WorldScene scene) {
        CompoundTag rules = defaultGameRules();
        if (scene.gameRulesNbt != null && !scene.gameRulesNbt.isBlank()) {
            String raw = scene.gameRulesNbt.trim();
            if (raw.contains("=") && !raw.startsWith("{")) {
                for (String part : raw.split(";")) {
                    int split = part.indexOf('=');
                    if (split <= 0 || split >= part.length() - 1) continue;
                    String key = part.substring(0, split).trim();
                    String value = part.substring(split + 1).trim();
                    if (!key.isBlank() && !value.isBlank()) {
                        rules.putString(key, value);
                    }
                }
            } else {
                try {
                    CompoundTag overrides = TagParser.parseCompoundFully(raw);
                    for (String key : overrides.keySet()) {
                        String value = overrides.getString(key).orElse(null);
                        if (value == null && overrides.get(key) != null) {
                            value = overrides.get(key).toString().replace("\"", "");
                        }
                        if (value != null && !value.isBlank()) {
                            rules.putString(key, value);
                        }
                    }
                } catch (CommandSyntaxException exception) {
                    WorldBinder.LOGGER.warn("Failed to parse captured game rules. Falling back to safe defaults.", exception);
                }
            }
        }
        return rules;
    }

    private static CompoundTag defaultGameRules() {
        CompoundTag rules = new CompoundTag();
        putRule(rules, "announceAdvancements", "true");
        putRule(rules, "commandBlockOutput", "false");
        putRule(rules, "disableElytraMovementCheck", "false");
        putRule(rules, "disableRaids", "true");
        putRule(rules, "doDaylightCycle", "false");
        putRule(rules, "doEntityDrops", "true");
        putRule(rules, "doFireTick", "false");
        putRule(rules, "doImmediateRespawn", "false");
        putRule(rules, "doInsomnia", "false");
        putRule(rules, "doLimitedCrafting", "false");
        putRule(rules, "doMobLoot", "true");
        putRule(rules, "doMobSpawning", "false");
        putRule(rules, "doPatrolSpawning", "false");
        putRule(rules, "doTileDrops", "true");
        putRule(rules, "doTraderSpawning", "false");
        putRule(rules, "doVinesSpread", "false");
        putRule(rules, "doWardenSpawning", "false");
        putRule(rules, "doWeatherCycle", "false");
        putRule(rules, "drowningDamage", "true");
        putRule(rules, "fallDamage", "true");
        putRule(rules, "fireDamage", "true");
        putRule(rules, "forgiveDeadPlayers", "true");
        putRule(rules, "keepInventory", "true");
        putRule(rules, "logAdminCommands", "false");
        putRule(rules, "maxCommandChainLength", "65536");
        putRule(rules, "maxEntityCramming", "24");
        putRule(rules, "mobGriefing", "false");
        putRule(rules, "naturalRegeneration", "true");
        putRule(rules, "playersSleepingPercentage", "100");
        putRule(rules, "randomTickSpeed", "0");
        putRule(rules, "reducedDebugInfo", "false");
        putRule(rules, "sendCommandFeedback", "true");
        putRule(rules, "showDeathMessages", "true");
        putRule(rules, "spawnRadius", "0");
        putRule(rules, "spectatorsGenerateChunks", "true");
        putRule(rules, "universalAnger", "false");
        return rules;
    }

    private static void putRule(CompoundTag rules, String key, String value) {
        rules.putString(key, value);
    }

    private static CompoundTag worldGenSettings(WorldScene scene) {
        CompoundTag settings = new CompoundTag();
        settings.putLong("seed", 0L);
        settings.putBoolean("generate_features", false);
        settings.putBoolean("bonus_chest", false);
        settings.putString("worldbinder_target_version", targetVersion(scene).name());
        settings.putString("worldbinder_generation_profile", targetVersion(scene).profile().name());

        CompoundTag dimensions = new CompoundTag();
        CompoundTag overworld = new CompoundTag();
        overworld.putString("type", "minecraft:overworld");
        overworld.put("generator", flatGenerator());
        dimensions.put("minecraft:overworld", overworld);
        settings.put("dimensions", dimensions);
        return settings;
    }

    private static CompoundTag flatGenerator() {
        CompoundTag generator = new CompoundTag();
        generator.putString("type", "minecraft:flat");
        CompoundTag settingsTag = new CompoundTag();
        settingsTag.putString("biome", DEFAULT_BIOME);
        ListTag layers = new ListTag();
        CompoundTag airLayer = new CompoundTag();
        airLayer.putInt("height", 1);
        airLayer.putString("block", "minecraft:air");
        layers.add(airLayer);
        settingsTag.put("layers", layers);
        settingsTag.put("structure_overrides", new ListTag());
        settingsTag.putBoolean("features", false);
        settingsTag.putBoolean("lakes", false);
        generator.put("settings", settingsTag);
        return generator;
    }

    private static CompoundTag versionInfo(TargetMinecraftVersion.Entry targetVersion) {
        CompoundTag version = new CompoundTag();
        version.putString("Name", targetVersion.name());
        version.putInt("Id", targetVersion.effectiveDataVersion());
        version.putBoolean("Snapshot", false);
        version.putString("Series", SharedConstants.getCurrentVersion().dataVersion().series());
        return version;
    }

    private static CompoundTag dataPacks() {
        CompoundTag dataPacks = new CompoundTag();
        dataPacks.put("Enabled", singleStringList("vanilla"));
        dataPacks.put("Disabled", new ListTag());
        return dataPacks;
    }

    private static CompoundTag playerNbt(WorldScene scene) {
        CompoundTag player = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(scene.originX + 0.5D));
        pos.add(DoubleTag.valueOf(Math.max(-64, scene.originY + 2.0D)));
        pos.add(DoubleTag.valueOf(scene.originZ + 0.5D));
        player.put("Pos", pos);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(0.0F));
        rotation.add(FloatTag.valueOf(0.0F));
        player.put("Rotation", rotation);
        player.putInt("playerGameType", 1);
        player.putShort("Health", (short) 20);
        player.putFloat("foodLevel", 20.0F);
        player.put("Inventory", new ListTag());
        return player;
    }

    private static ListTag singleStringList(String value) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(value));
        return list;
    }

    private static void writeSessionLock(Path worldFolder) throws IOException {
        Files.writeString(worldFolder.resolve("session.lock"), Long.toString(System.currentTimeMillis()));
    }

    private static void writeIconPlaceholder(Path worldFolder) throws IOException {
        // Keep this intentionally empty for now. Minecraft does not require icon.png.
    }

    private static void writeWorldBinderInfo(WorldScene scene, Path worldFolder, int chunkCount) throws IOException {
        Files.writeString(worldFolder.resolve("WORLD_BINDER_VANILLA_EXPORT.txt"),
                "WorldBinder Vanilla Export\n" +
                        "==========================\n\n" +
                        "Created: " + Instant.now() + "\n" +
                        "Archive: " + scene.name + "\n" +
                        "Minecraft: " + scene.minecraftVersion + "\n" +
                        "Target output: " + targetVersion(scene).name() + " (" + targetVersion(scene).profile().label() + ")\n" +
                        "Chunks: " + chunkCount + "\n" +
                        "Blocks: " + scene.blockCount() + "\n" +
                        "Block entities: " + scene.blockEntityCount() + "\n" +
                        "Entities: " + scene.entityCount() + "\n\n" +
                        "This folder contains a vanilla-shaped save: level.dat, region/*.mca, entities/*.mca where supported, poi/*.mca where supported and resources.zip when available.\n" +
                        "Only data actually seen/captured by the client can be exported. Server-hidden data cannot be reconstructed.\n");
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

        private CompoundTag toChunkNbt(WorldScene scene) {
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
            ListTag list = new ListTag();
            for (EntityRecord entity : entities) {
                CompoundTag nbt = normalizedEntityNbt(scene, entity);
                list.add(nbt);
            }
            root.put("Entities", list);
            root.putInt("DataVersion", targetVersion(scene).effectiveDataVersion());
            root.put("Position", new IntArrayTag(new int[]{key.x, key.z}));
            return root;
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

    public record ExportResult(Path folder, int chunks, int blocks, int blockEntities, int entities) {
    }
}
